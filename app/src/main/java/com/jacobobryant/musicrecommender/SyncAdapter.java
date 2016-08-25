package com.jacobobryant.musicrecommender;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import radams.gracenote.webapi.GracenoteException;
import radams.gracenote.webapi.GracenoteMetadata;
import radams.gracenote.webapi.GracenoteWebAPI;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final int NOTIFY_ID = 1;
    static final String CACHED_IDS_FILE = "track_id_cache";
    static final String CACHED_METADATA_FILE = "metadata_cache";
    Context context;
    Map<Metadata, String> cachedIds;
    Map<String, Metadata> cachedMetadata;
    GracenoteWebAPI api;
    SSLContext sslContext;
    NotificationManager notifyManager;
    NotificationCompat.Builder notifyBuilder;
    boolean tokenIsValid;

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

        try {
            this.sslContext = makeContext();
        } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException
                 | KeyManagementException | NoSuchProviderException e) {
            throw new RuntimeException("", e);
        }

        Log.d(C.TAG, "finished SyncAdapter.init()");
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
                              ContentProviderClient provider, SyncResult syncResult) {
        try {
            // start progress notification
            Log.d(C.TAG, "onPerformSync()");
            startProgress();

            // setup gracenote
            //try {
                //api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
                api = getGraceApi();
            //} catch (GracenoteException e) {
            //    throw new RuntimeException(e);
            //}

            // setup user id
            Log.d(C.TAG, "getting user id");
            String userId;
            //try {
                userId = getUserId();
            //} catch (IOException e) {
            //    throw new RuntimeException(e);
            //}

            // get playlists
            List<Playlist> playlists = new LinkedList<>();
            playlists.addAll(localPlaylists());
            Log.d(C.TAG, "getting spotify playlists");
            playlists.addAll(spotifyPlaylists());

            // upload playlists
            Log.d(C.TAG, "getting recommendations");
            List<Recommendations> recommendations;
            //try {
                try {
                    recommendations = getRecommendations(playlists, userId);
                } catch (IllegalArgumentException e) {
                    userId = resetUserId();
                    recommendations = getRecommendations(playlists, userId);
                }
            //} catch (IOException e) {
            //    throw new RuntimeException(e);
            //}

            // store recommendations
            Log.d(C.TAG, "storing recommendations");
            storeRecommendations(recommendations);
            Log.d(C.TAG, "finished sync");
        } catch (Exception e) {
            // TODO report exception
            throw new RuntimeException(e);
        } finally {
            // end progress notification
            endProgress();
            Intent i = new Intent("com.jacobobryant.musicrecommender.SYNC_FINISHED");
            context.sendBroadcast(i);
            Log.d(C.TAG, "saving cached objects");
            saveCachedObjects();
        }
    }

    // ========== PROGRESS NOTIFICATION ==========
    void startProgress() {
        this.notifyManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        notifyBuilder = new NotificationCompat.Builder(context);
        notifyBuilder.setContentTitle("Music Recommender")
                     .setContentText("Give you Big Surprise. He~ He~")
                     .setSmallIcon(R.drawable.ic_autorenew_white_48dp);
        notifyBuilder.setProgress(0, 0, true);
        notifyManager.notify(NOTIFY_ID, notifyBuilder.build());
    }

    void endProgress() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pintent = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notifyBuilder.setProgress(0, 0, false)
                     .setContentText("Finished getting recommendations")
                     .setContentIntent(pintent);
        notifyManager.notify(NOTIFY_ID, notifyBuilder.build());
    }

    // ========== USER INFO ==========
    GracenoteWebAPI getGraceApi() throws GracenoteException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String userId = settings.getString("gn_api_user_id", "");
        if (!userId.isEmpty()) {
            // TODO check that userid is still valid.
            Log.d(C.TAG, "using existing gracenote id: " + userId);
            return new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, userId);
        }
        GracenoteWebAPI api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG);
        userId = api.register();
        Log.d(C.TAG, "got new gracenote id: " + userId);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("gn_api_user_id", userId);
        editor.commit();
        return api;
    }

    String getUserId() throws IOException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String userId = settings.getString("user_id", "");
        if (!userId.isEmpty()) {
            // TODO check that userid is still valid.
            return userId;
        }
        userId = register();
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("user_id", userId);
        editor.commit();
        return userId;
    }

    String register() throws IOException {
        URL url;
        try {
            url = new URL(C.SERVER + "/register");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        Log.d(C.TAG, "opening connection to " + url.toString());

        HttpsURLConnection conn = makeConnection(url);
        //HttpURLConnection conn = makeTestConnection(url);

        conn.setRequestMethod("POST");
        conn.setDoOutput(false);
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        StringBuilder result = new StringBuilder();
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        return result.toString().trim();
    }
    
    String resetUserId() throws IOException {
        String newId = register();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("user_id", newId);
        editor.commit();
        return newId;
    }

    // ========== PLAYLISTS ==========
    List<Playlist> localPlaylists() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        if (!settings.getBoolean("pref_local", true) ||
                !MainActivity.isStoragePermissionGranted(context)) {
            Log.d(C.TAG, "couldn't get local playlists");
            return new LinkedList<>();
        }
        Log.d(C.TAG, "getting local playlists yo");

        // get playlist ids
        List<Playlist> playlists = new LinkedList<>();
        final String[] proj = {MediaStore.Audio.Playlists._ID,
                               MediaStore.Audio.Playlists.NAME};
        Cursor result = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, proj, null, null, null);
        result.moveToPosition(-1);
        while (result.moveToNext()) {
            int id = result.getInt(0);
            String name = result.getString(1);
            playlists.add(getPlaylist(id, name));
        }
        result.close();
        return playlists;
    }

    Playlist getPlaylist(int playlistId, String name) {
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
                Log.e(C.TAG, "Couldn't get Gracenote ID for " + title, e);
            }
        }
        return new Playlist(tracks, name, Playlist.Type.LOCAL);
    }

    List<Playlist> spotifyPlaylists() {
        // init
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        //String token = settings.getString("spotify_token", "");
        String token = getSpotifyToken();
        Log.d(C.TAG, "spotify token: " + token);
        if (token.equals("")) {
            return new LinkedList<>();
        }
        SpotifyApi api = new SpotifyApi();
        api.setAccessToken(token);
        final SpotifyService spotify = api.getService();

        // get playlists
        List<Playlist> playlists = new LinkedList<>();
        Pager<PlaylistSimple> pager = spotify.getMyPlaylists();
        Log.d(C.TAG, "got spotify playlists successfully");
        for (PlaylistSimple list : pager.items) {
            // get ids
            String[] parts = list.tracks.href.split("/");
            String userId = parts[parts.length - 4];
            String playlistId = parts[parts.length - 2];
            Log.d(C.TAG, "userid=" + userId + ", playlistId=" + playlistId);

            if (userId.equals("spotifydiscover")) {
                Log.d(C.TAG, "skipping spotify discover playlist");
                continue;
            }

            // get tracks
            List<String> tracks = getSpotifyTracks(spotify, userId, playlistId);
            playlists.add(new Playlist(tracks, list.name, Playlist.Type.SPOTIFY));
        }
        return playlists;
    }

    String getSpotifyToken() {
        tokenIsValid = true;
        Log.d(C.TAG, "getting spotify token");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String access = settings.getString("spotify_token", "");

        if (access.isEmpty()) {
            return "";
        } else {
            long expireTime = settings.getLong("spotify_expires", 0);
            if (System.currentTimeMillis() / 1000 > expireTime) {
                Log.d(C.TAG, "refreshing spotify token");
                tokenIsValid = false;
                refreshSpotifyToken();
            }
        }

        // wait until refresh finished
        long timeout = System.currentTimeMillis() + 10 * 1000;
        while (!tokenIsValid) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (System.currentTimeMillis() > timeout) {
                return "";
            }
        }
        return settings.getString("spotify_token", "");
    }

    void refreshSpotifyToken() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, String> params = new HashMap<>();
        params.put("refresh", settings.getString("spotify_refresh", ""));

        //RequestQueue queue = Volley.newRequestQueue(context);
        String url = C.SERVER + "/spotify-refresh";

        JSONObject jsonBody = new JSONObject(params);
        JsonObjectRequest request = new JsonObjectRequest(url, jsonBody,
                new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d(C.TAG, "response: " + response.toString());
                try {
                    saveSpotifyToken(response);
                } catch (JSONException e) {
                    throw new RuntimeException("Couldn't refresh spotify credentials", e);
                }
            }
        }, new VolleyErrorHandler());
        //queue.add(request);
        MyCoolQueue.get(context).add(request);
    }

    public void saveSpotifyToken(JSONObject response) throws JSONException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = settings.edit();

        long expireTime = System.currentTimeMillis() / 1000 + response.getInt("expires_in");
        editor.putLong("spotify_expires", expireTime);
        editor.putString("spotify_token", response.getString("access_token"));

        editor.commit();
        tokenIsValid = true;
    }

    List<String> getSpotifyTracks(SpotifyService spotify, String userId, String playlistId) {
        List<String> trackIds = new LinkedList<>();
        Pager<PlaylistTrack> pager = spotify.getPlaylistTracks(userId, playlistId);
        Log.d(C.TAG, "got spotify tracks successfully");
        for (PlaylistTrack track : pager.items) {
            try {
                trackIds.add(getTrackId(track.track.artists.get(0).name,
                                        track.track.album.name,
                                        track.track.name));
            } catch (GracenoteException e) {
                Log.e(C.TAG, "Couldn't get Gracenote ID for " + track.track.name, e);
            }

            Log.d(C.TAG, "track name: " + track.track.name);
            Log.d(C.TAG, "album name: " + track.track.album.name);
            Log.d(C.TAG, "artist name: " + track.track.artists.get(0).name);
        }
        return trackIds;
    }

    // ========== RECOMMENDATIONS ==========
    List<Recommendations> getRecommendations(List<Playlist> playlists, String uid)
            throws IOException {
        String json = getJson(playlists, uid);
        String response = upload(json);
        List<Recommendations> recommendations = parseResponse(response);
        for (int i = 0; i < recommendations.size(); i++) {
            recommendations.get(i).setPlaylistName(playlists.get(i).getName());
        }
        return recommendations;
    }

    String getJson(List<Playlist> playlists, String uid) {
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
            url = new URL(C.SERVER + "/upload");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        HttpsURLConnection conn = makeConnection(url);
        //HttpURLConnection conn = makeTestConnection(url);

        // set up request
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
            throw new IllegalArgumentException("got error from server: " + data.get("error"));
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
        SQLiteDatabase db = new Database(context).getWritableDatabase();
        db.beginTransaction();
        db.execSQL("DELETE FROM recommendations");
        for (Recommendations recs : recommendations) {
            Log.d(C.TAG, "storing recommendations for playlist " + recs.getPlaylistName());

            for (Map<String, Object> recommendation : recs.getRecList()) {
                Metadata track;
                String trackId = (String) recommendation.get("track");
                try {
                    track = getMetadata(trackId);
                } catch (GracenoteException e) {
                    Log.e(C.TAG, "couldn't lookup metadata for track id: " + trackId, e);
                    continue;
                }
                String score = String.valueOf(recommendation.get("score"));
                db.execSQL("INSERT INTO recommendations (playlist_name, title, artist, " +
                                "album, score) VALUES (?, ?, ?, ?, ?)",
                        new String[]{String.valueOf(recs.getPlaylistName()),
                                track.track, track.artist, track.album, score});
            }
            if (recs.getRecList().size() == 0) {
                db.execSQL("INSERT INTO recommendations (playlist_name, score) VALUES (?, -1)",
                        new String[]{String.valueOf(recs.getPlaylistName())});

            }
        }
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    // ========== UTILS ==========
    String getTrackId(String artist, String album, String title) throws GracenoteException {
        Metadata m = new Metadata(artist, album, title);
        if (cachedIds.containsKey(m)) {
            return cachedIds.get(m);
        }
        Log.d(C.TAG, "querying gracenote db for " + title);
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
            Log.e(C.TAG, "cachedMetadata not initialized correctly", e);
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
            Log.e(C.TAG, "couldn't save cached object", e);
        }
    }

    SSLContext makeContext() throws CertificateException, IOException, KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, NoSuchProviderException {
        // Load CAs from an InputStream
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = new BufferedInputStream(
                this.context.getAssets().open("jacobobryant.com.crt"));

        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
        } finally {
            caInput.close();
        }

        // Create a KeyStore containing our trusted CAs
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);

        // Create an SSLContext that uses our TrustManager
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context;
    }

    HttpsURLConnection makeConnection(URL url) throws IOException {
        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                // hee hee hee
                return true;
            }
        };

        HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
        urlConnection.setSSLSocketFactory(this.sslContext.getSocketFactory());
        urlConnection.setHostnameVerifier(hostnameVerifier);
        return urlConnection;
    }

    //HttpURLConnection makeTestConnection(URL url) throws IOException {
    //    return (HttpURLConnection) url.openConnection();
    //}
    //
}
