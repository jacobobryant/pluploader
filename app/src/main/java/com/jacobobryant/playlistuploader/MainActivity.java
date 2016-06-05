package com.jacobobryant.playlistuploader;

import android.content.Context;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    public static final String CACHE_FILE = "track_id_cache";
    private List<Object> playlists = Collections.synchronizedList(new ArrayList<Object>());
    private AtomicInteger loadersRunning;
    private GracenoteWebAPI api;
    private Map<Metadata, String> cachedIds;


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


    private void send() {
        Log.d(TAG, "reporting to the mother ship");
        Map<String, Object> object = new HashMap<>();
        object.put("id", "my unique id");
        object.put("playlists", playlists);
        String json;
        try {
            //Map<String, Object> testObject = new HashMap<>();
            //testObject.put("foobar", "baz");
            //json = new ObjectMapper().writeValueAsString(testObject);
            json = new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            Log.wtf(TAG, "you moron!");
            return;
        }
        //Log.d(TAG, "got json: " + json);
        //String url = "http://192.168.26.25:8080";
        String url = "http://192.168.1.222:8080";

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
        //HttpGet request = new HttpGet(dest);
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

    private class GetSongIdTask extends AsyncTask<Void, Void, String> {
        private String artist;
        private String album;
        private String title;

        public GetSongIdTask(String artist, String album, String title) {
            this.artist = artist;
            this.album = album;
            this.title = title;
        }

        @Override
        protected String doInBackground(Void... foo) {
            Log.d(TAG, "entering doInBackground()");
            try {
                GracenoteMetadata results = api.searchTrack(this.artist, this.album, this.title);
                Log.d(TAG, "Got results successfully");
                return "foobar";
            } catch (GracenoteException e) {
                Log.w(TAG, "Couldn't get song data for " + this.artist + "; " +
                        this.album + "; " + this.title, e);
                Log.d(TAG, "Didn't get results successfully");
                return "ohno";

            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            FileOutputStream fout = openFileOutput(CACHE_FILE, Context.MODE_PRIVATE);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject((HashMap) cachedIds);
            oos.close();
            fout.close();
        } catch (IOException e) {
            Log.e(TAG, "well this sucks (couldn't save cached data)", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            FileInputStream fin = openFileInput(CACHE_FILE);
            ObjectInputStream ois = new ObjectInputStream(fin);
            cachedIds = (HashMap<Metadata, String>) ois.readObject();
            ois.close();
            fin.close();
        } catch (IOException | ClassNotFoundException e) {
            Log.e(TAG, "well this sucks (couldn't load cached data)", e);
            cachedIds = new HashMap<>();
        }
        try {
            api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
        } catch (GracenoteException e) {
            Log.d(TAG, "Couldn't create API connection", e);
        }
    }

    public void upload(View v) {
        getSupportLoaderManager().initLoader(PLAYLIST_LOADER, null, new PlaylistLoader());
    }
}
