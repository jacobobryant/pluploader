package com.jacobobryant.playlistuploader;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import radams.gracenote.webapi.GracenoteException;
import radams.gracenote.webapi.GracenoteMetadata;
import radams.gracenote.webapi.GracenoteWebAPI;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "PlaylistUploader";
    public static final int PLAYLIST_LOADER = 0;
    public static final int SONG_LOADER = 1;
    public static final String KEY_ID = "id";
    public static final String CACHED_IDS_FILE = "track_id_cache";
    public static final String CACHED_METADATA_FILE = "metadata_cache";
    private List<Object> playlists = Collections.synchronizedList(new ArrayList<Object>());
    private AtomicInteger loadersRunning;
    private GracenoteWebAPI api;
    private Map<Metadata, String> cachedIds;
    private Map<String, Metadata> cachedMetadata;
    private int userId;

    private class PlaylistLoader implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
            final String[] PLAYLIST_PROJ = {
                MediaStore.Audio.Playlists._ID
            };
            return new CursorLoader(MainActivity.this,
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, PLAYLIST_PROJ,
                        null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            loadersRunning = new AtomicInteger(cursor.getCount());
            int songLoaderId = SONG_LOADER;
            LoaderManager.LoaderCallbacks<Cursor> songLoader = new SongLoader();
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                Bundle args = new Bundle();
                args.putInt(KEY_ID, id);
                getSupportLoaderManager().initLoader(songLoaderId++, args, songLoader);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) { }
    }

    private class SongLoader implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
            int playlistId = bundle.getInt(KEY_ID);
            final String[] SONG_PROJ = {
                MediaStore.Audio.Playlists.Members.AUDIO_ID,
                MediaStore.Audio.Playlists.Members.TITLE,
                MediaStore.Audio.Playlists.Members.ARTIST,
                MediaStore.Audio.Playlists.Members.ALBUM
            };
            return new CursorLoader(MainActivity.this,
                        MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId),
                        SONG_PROJ, null, null, null);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            new Thread() {
                private Cursor cursor;
                private List<Object> playlists;
                private AtomicInteger loadersRunning;

                public void run() {
                    cursor.moveToPosition(-1);
                    List<String> songs = new ArrayList<>();
                    while (cursor.moveToNext()) {
                        String title = cursor.getString(1);
                        String artist = cursor.getString(2);
                        String album = cursor.getString(3);
                        try {
                            String trackId = getTrackId(artist, album, title);
                            songs.add(trackId);
                        } catch (GracenoteException e) {
                            Log.e(TAG, "Couldn't get Gracenote IDs for " + title, e);
                        }
                    }
                    if (songs.size() > 0) {
                        playlists.add(songs);
                    }
                    if (loadersRunning.decrementAndGet() == 0) {
                        Log.d(TAG, "got all playlists");
                        send();
                    }
                }

                public Thread init(Cursor cursor, List<Object> playlists, AtomicInteger loadersRunning) {
                    this.cursor = cursor;
                    this.playlists = playlists;
                    this.loadersRunning = loadersRunning;
                    return this;
                }
            }.init(cursor, playlists, loadersRunning).start();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) { }
    }

    public String getTrackId(String artist, String album, String title) throws GracenoteException {
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

    private void send() {
        Log.d(TAG, "reporting to the mother ship");
        Map<String, Object> object = new HashMap<>();
        object.put("id", this.userId);
        object.put("playlists", playlists);
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            Log.wtf(TAG, "you moron!");
            return;
        }
        String url = "http://192.168.1.222:6666/upload";

        new UploadTask(url, json).execute();
    }

    private void sendForReal(String dest, String json) throws IOException {

        int TIMEOUT_MILLISEC = 10000;  // = 10 seconds
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpClient client = new DefaultHttpClient(httpParams);

        HttpPost request = new HttpPost(dest);
        request.setHeader(HTTP.CONTENT_TYPE, "application/json");
        request.setEntity(new ByteArrayEntity(json.getBytes("UTF8")));

        client.execute(request);
    }

    private static String getStr(InputStream is) {
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

    private class UploadTask extends AsyncTask<Void, Void, Void> {
        private String url;
        private String json;

        public UploadTask(String url, String json) {
            this.url = url;
            this.json = json;
        }

        @Override
        protected Void doInBackground(Void... urls) {
            try {
                sendForReal(url, json);
            } catch (IOException e) {
                Log.e(TAG, "error while uploading json", e);
            }
            return null;
        }
    }

    private class RegisterTask extends AsyncTask<Void, Void, Integer> {

        @Override
        protected Integer doInBackground(Void... foo) {
            SharedPreferences settings = getPreferences(Context.MODE_PRIVATE);
            int userId = settings.getInt("user_id", -1);
            if (userId != -1) {
                return userId;
            }
            try {
                userId = register();
            } catch (IOException e) {
                Log.e(TAG, "couldn't register", e);
                return -1;
            }
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("user_id", userId);
            editor.commit();
            return userId;
        }

        @Override
        protected void onPostExecute(Integer userId) {
            Log.d(TAG, "userId: " + userId);
            MainActivity.this.userId = userId;
            if (userId != -1) {
                getSupportLoaderManager().initLoader(PLAYLIST_LOADER, null, new PlaylistLoader());
            }
        }

        public int register() throws IOException {
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            FileOutputStream fout = openFileOutput(CACHED_IDS_FILE, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(cachedIds);
            oos.close();
            fout.close();
        } catch (IOException e) {
            Log.e(TAG, "well this sucks (couldn't save cached ids)", e);
        }
        try {
            FileOutputStream fout = openFileOutput(CACHED_METADATA_FILE, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(cachedMetadata);
            oos.close();
            fout.close();
        } catch (IOException e) {
            Log.e(TAG, "well this sucks (couldn't save cached metadata)", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Log.d(TAG, "onCreate()");
        try {
            FileInputStream fin = openFileInput(CACHED_IDS_FILE);
            ObjectInputStream ois = new ObjectInputStream(fin);
            cachedIds = (HashMap<Metadata, String>) ois.readObject();
            ois.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "well this sucks (couldn't load cached track ids)", e);
            cachedIds = new HashMap<>();
        }
        try {
            FileInputStream fin = openFileInput(CACHED_METADATA_FILE);
            ObjectInputStream ois = new ObjectInputStream(fin);
            cachedMetadata = (HashMap<String, Metadata>) ois.readObject();
            ois.close();
            fin.close();
            //Log.d(TAG, "loaded cached metadata from file");
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "well this sucks (couldn't load cached metadata)", e);
            cachedMetadata = new HashMap<>();
        }
        try {
            api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
        } catch (GracenoteException e) {
            Log.d(TAG, "Couldn't create API connection", e);
        }
    }

    public void upload(View v) {
        new RegisterTask().execute();
    }

    public void recommend(View v) {
        //Log.d(TAG, "recommend()");
        new RecommendTask().execute();
    }

    private class RecommendTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... foo) {
            String[] proj = {
                MediaStore.Audio.Playlists._ID,
                MediaStore.Audio.Playlists.NAME
            };
            String[] member_proj = {
                MediaStore.Audio.Playlists.Members.ARTIST,
                MediaStore.Audio.Playlists.Members.ALBUM,
                MediaStore.Audio.Playlists.Members.TITLE
            };

            Cursor result = getContentResolver().query(MediaStore.Audio.Playlists.getContentUri("external"), proj, null, null, null);
            result.moveToPosition(-1);
            while (result.moveToNext()) {
                int id = result.getInt(0);
                String name = result.getString(1);
                Log.d(TAG, "getting recommendations for playlist: " + name);

                List<String> playlist = new ArrayList<>();
                Cursor song_result = getContentResolver().query(
                        MediaStore.Audio.Playlists.Members.getContentUri("external", id),
                        member_proj, null, null, null);
                song_result.moveToPosition(-1);
                while (song_result.moveToNext()) {
                    String artist = song_result.getString(0);
                    String album = song_result.getString(1);
                    String title = song_result.getString(2);
                    try {
                        playlist.add(getTrackId(artist, album, title));
                    } catch (GracenoteException e) {
                        Log.e(TAG, "that sucks");
                    }
                }
                String json;
                try {
                    json = get_recommendations(playlist);
                } catch (IOException e) {
                    throw new RuntimeException("couldn't get recommendations", e);
                }
                Map<String, Object> data;
                try {
                    data = new ObjectMapper().readValue(json.getBytes(), Map.class);
                } catch (IOException e) {
                    Log.e(TAG, "received bad json: " + json);
                    continue;
                }
                if (data.containsKey("error")) {
                    Log.e(TAG, "Couldn't get recommendations: " + data.get("error"));
                    continue;
                }
                for (Map<String, Object> recommendation : (List<Map<String, Object>>) data.get("recommendations")) {
                    Metadata track;
                    String trackId = (String) recommendation.get("track");
                    try {
                        track = getMetadata(trackId);
                    } catch (GracenoteException e) {
                        Log.e(TAG, "couldn't lookup track id: " + trackId);
                        continue;
                    }
                    double score = (double) recommendation.get("score");
                    Log.d(TAG, "track: " + track.track + " by " + track.artist + " (score: " + score + ")");

                }
                Log.d(TAG, "finished getting recommendations");
            }
            return "";
        }

        @Override
        protected void onPostExecute(String json) {
        }
    }

    public String get_recommendations(List<String> playlist) throws IOException {
        //Log.d(TAG, "get_recommendations()");
        Map<String, Object> data = new HashMap<>();
        data.put("playlist", playlist);
        String json;
        try {
            json = new ObjectMapper().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("IHateJavaException", e);
        }

        URL url;
        try {
            url = new URL("http://192.168.1.222:6666/recommend");
        } catch (MalformedURLException e) {
            throw new RuntimeException("IHateJavaException", e);
        }
        //Log.d(TAG, "making http connection");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.write(json);
        writer.flush();
        writer.close();
        os.close();

        StringBuilder result = new StringBuilder();
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
           result.append(line);
        }
        rd.close();

        return result.toString();
    }
}
