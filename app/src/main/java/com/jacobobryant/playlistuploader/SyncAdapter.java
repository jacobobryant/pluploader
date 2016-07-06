package com.jacobobryant.playlistuploader;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import radams.gracenote.webapi.GracenoteException;
import radams.gracenote.webapi.GracenoteMetadata;
import radams.gracenote.webapi.GracenoteWebAPI;

// TODO save cached objects to file on exit

public class SyncAdapter extends AbstractThreadedSyncAdapter {
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
        cachedIds = loadObject(CACHED_IDS_FILE, HashMap<Metadata, String>.class);
        cachedMetadata = loadObject(CACHED_METADATA_FILE, HashMap<String, Metadata>.class);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                       ContentProviderClient provider, SyncResult syncResult) {
        Log.d(MainActivity.TAG, "onPerformSync()");
        api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
        int userId;
        try {
            userId = getUserId();
        } catch (IOException e) {
            // There's nothing we can do about it...
            throw new RuntimeException(e);
        }
        List<List<String>> playlists = new LinkedList<>();
        for (int playlistId : getPlaylistIds()) {
            playlists.add(getPlaylist(playlistId));
        }
        List<Recommendation> recommendations = getRecommendations(playlists);
        storeRecommendations(recommendations, playlistId);
    }

    // ========== USER ID ==========
    int getUserId() throws IOException {
        SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
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
            url = new URL("http://192.168.1.222:6666/register");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
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
            int id = cursor.getInt(0);
            ids.add(id);
        }
        result.close();
        return ids;
    }

    // ========== PLAYLIST ==========
    List<String> getPlaylist(int playlistId) {
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
        List<String> playlist = new ArrayList<>();
        while (result.moveToNext()) {
            String title = result.getString(1);
            String artist = result.getString(2);
            String album = result.getString(3);
            try {
                String trackId = getTrackId(artist, album, title);
                songs.add(trackId);
            } catch (GracenoteException e) {
                Log.e(TAG, "Couldn't get Gracenote ID for " + title, e);
            }
        }
        return playlist;
    }

    // ========== RECOMMENDATIONS ==========
    List<Recommendation> getRecommendations(List<List<String>> playlists, int uid) {
        String json = getJson(playlists, uid);
        String url = "http://192.168.1.222:6666/upload";
        String response = upload(url, json);
        return parseResponse(response);
     }

    String getJson(List<List<String>> playlists, int uid) {
        Map<String, Object> object = new HashMap<>();
        object.put("id", uid);
        object.put("playlists", playlists);
        String json;
        try {
            return new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    String upload(String url, String json) {
        // TODO
    }

    List<Recommendation> parseResponse(String response) {
        // TODO
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

    <T> T loadObject(String filename, Class<T> type) {
        T object;
        try {
            FileInputStream fin = openFileInput(filename);
            ObjectInputStream ois = new ObjectInputStream(fin);
            object = type.cast(ois.readObject());
            ois.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            object = new T();
        }
        return object;
    }
}
