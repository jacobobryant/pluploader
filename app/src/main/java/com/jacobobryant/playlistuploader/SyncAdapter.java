package com.jacobobryant.playlistuploader;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import radams.gracenote.webapi.GracenoteException;
import radams.gracenote.webapi.GracenoteMetadata;
import radams.gracenote.webapi.GracenoteWebAPI;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = MainActivity.TAG;
    Context context;
    static final String CACHED_IDS_FILE = "track_id_cache";
    static final String CACHED_METADATA_FILE = "metadata_cache";
    Map<Metadata, String> cachedIds;
    Map<String, Metadata> cachedMetadata;
    GracenoteWebAPI api;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.context = context;
        init();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        this.context = context;
        init();
    }

    void init() {
        cachedIds = loadObject(CACHED_IDS_FILE, new HashMap<Metadata, String>().getClass());
        if (cachedIds == null) {
            cachedIds = new HashMap<>();
        }
        cachedMetadata = loadObject(CACHED_METADATA_FILE,
                new HashMap<String, Metadata>().getClass());
        if (cachedMetadata == null) {
            cachedMetadata = new HashMap<>();
        }
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                       ContentProviderClient provider, SyncResult syncResult) {
        try {
            Log.d(MainActivity.TAG, "onPerformSync()");
            try {
                api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
            } catch (GracenoteException e) {
                throw new RuntimeException(e);
            }
            Log.d(MainActivity.TAG, "getting user id");
            int userId;
            try {
                userId = getUserId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Log.d(MainActivity.TAG, "getting local playlists");
            List<Playlist> playlists = new LinkedList<>();
            for (int playlistId : getPlaylistIds()) {
                playlists.add(getPlaylist(playlistId));
            }
            Log.d(MainActivity.TAG, "getting recommendations");
            List<Recommendations> recommendations;
            try {
                recommendations = getRecommendations(playlists, userId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Log.d(MainActivity.TAG, "storing recommendations");
            storeRecommendations(recommendations);
            Log.d(TAG, "finished sync");
        } finally {
            Log.d(MainActivity.TAG, "saving cached objects");
            saveCachedObjects();
        }
    }

    // ========== USER ID ==========
    int getUserId() throws IOException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        int userId = settings.getInt("user_id", -1);
        if (userId != -1) {
            return userId;
        }
        userId = register();
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("user_id", userId);
        editor.commit();
        return userId;
    }

    int register() throws IOException {
        StringBuilder result = new StringBuilder();
        URL url;
        try {
            url = new URL("http://192.168.1.222:5666/register");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        Log.d(TAG, "opening connection to " + url.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(false);
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
           result.append(line);
        }
        rd.close();
        return Integer.parseInt(result.toString());
    }

    // ========== PLAYLIST IDS ==========
    List<Integer> getPlaylistIds() {
        List<Integer> ids = new LinkedList<>();
        final String[] proj = { MediaStore.Audio.Playlists._ID };
        Cursor result = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, proj, null, null, null);
        result.moveToPosition(-1);
        while (result.moveToNext()) {
            int id = result.getInt(0);
            ids.add(id);
        }
        result.close();
        return ids;
    }

    // ========== PLAYLIST ==========
    Playlist getPlaylist(int playlistId) {
        final String[] proj = {
            MediaStore.Audio.Playlists.Members.AUDIO_ID,
            MediaStore.Audio.Playlists.Members.TITLE,
            MediaStore.Audio.Playlists.Members.ARTIST,
            MediaStore.Audio.Playlists.Members.ALBUM
        };
        Cursor result = context.getContentResolver().query(
                MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                proj, null, null, null);
        result.moveToPosition(-1);
        List<String> tracks = new LinkedList<>();
        while (result.moveToNext()) {
            String title = result.getString(1);
            String artist = result.getString(2);
            String album = result.getString(3);
            try {
                String trackId = getTrackId(artist, album, title);
                tracks.add(trackId);
            } catch (GracenoteException e) {
                Log.e(MainActivity.TAG, "Couldn't get Gracenote ID for " + title, e);
            }
        }
        return new Playlist(tracks, playlistId);
    }

    // ========== RECOMMENDATIONS ==========
    List<Recommendations> getRecommendations(List<Playlist> playlists, int uid) throws IOException {
        String json = getJson(playlists, uid);
        Log.d(MainActivity.TAG, json);
        String response = upload(json);
        List<Recommendations> recommendations = parseResponse(response);
        for (int i = 0; i < recommendations.size(); i++) {
            recommendations.get(i).setPlaylistId(playlists.get(i).getId());
        }
        return recommendations;
     }

    String getJson(List<Playlist> playlists, int uid) {
        Map<String, Object> object = new HashMap<>();
        object.put("id", uid);
        object.put("playlists", Playlist.toList(playlists));
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    String upload(String json) throws IOException {
        URL url;
        try {
            url = new URL("http://192.168.1.222:5666/recommend");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // set up request
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // send request
        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(json);
        writer.flush();
        writer.close();
        os.close();

        // receive response
        StringBuilder result = new StringBuilder();
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();

        return result.toString();
    }

    List<Recommendations> parseResponse(String response) throws IOException {
        Map<String, Object> data = new ObjectMapper().readValue(response.getBytes(), Map.class);
        if (data.containsKey("error")) {
            throw new IOException("couldn't get recommendations: " + data.get("error"));
        }
        List<Recommendations> recommendations = new LinkedList<>();
        for (List<Map<String, Object>> recList :
                (List<List<Map<String, Object>>>) data.get("recommendations")) {
            recommendations.add(new Recommendations(recList));
        }
        return recommendations;
    }

    // ========== STORE ==========
    void storeRecommendations(List<Recommendations> recommendations) {
        for (Recommendations recs : recommendations) {
            Log.e(TAG, "storing recommendations for playlist with id: " + recs.getPlaylistId());

            for (Map<String, Object> recommendation : recs.getRecList()) {
                Metadata track;
                String trackId = (String) recommendation.get("track");
                try {
                    track = getMetadata(trackId);
                } catch (GracenoteException e) {
                    Log.e(TAG, "couldn't lookup track id: " + trackId);
                    continue;
                }
                double score = (double) recommendation.get("score");
                Log.d(TAG, "track: " + track.track + " by " + track.artist +
                        " (score: " + score + ")");
            }
        }
    }

    // ========== UTILS ==========
    String getTrackId(String artist, String album, String title) throws GracenoteException {
        Metadata m = new Metadata(artist, album, title);
        if (cachedIds.containsKey(m)) {
            return cachedIds.get(m);
        }
        GracenoteMetadata results = api.searchTrack(artist, album, title);
        String trackId = (String) results.getAlbum(0).get("track_gn_id");
        cachedIds.put(m, trackId);
        return trackId;
    }

    public Metadata getMetadata(String trackId) throws GracenoteException {
        try {
            if (cachedMetadata.containsKey(trackId)) {
                return cachedMetadata.get(trackId);
            }
        } catch (NullPointerException e) {
            Log.e(TAG, "cachedMetadata not initialized correctly", e);
            cachedMetadata = new HashMap<>();
        }
        GracenoteMetadata results = api.fetchAlbum(trackId);
        String track = (String) results.getAlbum(0).get("track_title");
        String album = (String) results.getAlbum(0).get("album_title");
        String artist = (String) results.getAlbum(0).get("album_artist_name");
        Metadata met = new Metadata(artist, album, track);
        cachedMetadata.put(trackId, met);
        return met;
    }

    <T> T loadObject(String filename, Class<T> type) {
        T object;
        try {
            FileInputStream fin = context.openFileInput(filename);
            ObjectInputStream ois = new ObjectInputStream(fin);
            object = type.cast(ois.readObject());
            ois.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
        return object;
    }

    void saveCachedObjects() {
        saveObject(CACHED_IDS_FILE, cachedIds);
        saveObject(CACHED_METADATA_FILE, cachedMetadata);
    }

    void saveObject(String filename, Object object) {
        try {
            FileOutputStream fout = context.openFileOutput(filename, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(object);
            oos.close();
            fout.close();
        } catch (IOException e) {
            Log.e(TAG, "couldn't save cached object", e);
        }
    }
}
