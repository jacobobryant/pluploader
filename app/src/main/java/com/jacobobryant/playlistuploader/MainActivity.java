package com.jacobobryant.playlistuploader;

import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import radams.gracenote.webapi.GracenoteException;
import radams.gracenote.webapi.GracenoteMetadata;
import radams.gracenote.webapi.GracenoteWebAPI;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "PlaylistUploader";
    public static final int PLAYLIST_LOADER = 0;
    public static final int SONG_LOADER = 1;
    public static final String KEY_ID = "id";
    private List<Object> playlists = Collections.synchronizedList(new ArrayList<Object>());
    private AtomicInteger loadersRunning;
    private GracenoteWebAPI api;

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
                Log.d(TAG, "playlist id: " + id);
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
        public void onLoadFinished(Loader<Cursor> loader, final Cursor cursor) {
            //new Thread() {
            //    public void run() {
            cursor.moveToPosition(-1);
            List<GetSongIdTask> tasks = new ArrayList<>();
            while (cursor.moveToNext()) {
                String title = cursor.getString(1);
                String artist = cursor.getString(2);
                String album = cursor.getString(3);
                GetSongIdTask task = new GetSongIdTask(artist, album, title);
                tasks.add(task);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    task.execute();
                }
                //Thread thread = new Thread() {
                //    private String title;
                //    private String artist;
                //    private String album;

                //    private Thread init(String title, String artist, String album) {
                //        this.title = title;
                //        this.artist = artist;
                //        this.album = album;
                //        return this;
                //    }

                //    public void run() {
                //        try {
                //            GracenoteMetadata results = api.searchTrack(this.artist, this.album, this.title);
                //            songs.add("foobar");
                //            Log.d(TAG, "Got results successfully");
                //        } catch (GracenoteException e) {
                //            Log.w(TAG, "Couldn't get song data for " + this.artist + "; " +
                //                    this.album + "; " + this.title, e);
                //        }
                //    }
                //}.init(title, artist, album);
                //new UploadTask(url, json).execute();

                //threads.add(thread);
                //thread.start();
                //ByteArrayOutputStream baos = new ByteArrayOutputStream();
                //System.setOut(new PrintStream(baos));
                ////String trackId = results
                //results.print();
                //Log.d(TAG, baos.toString());
            }
            new Thread() {
                private List<GetSongIdTask> tasks;
                private List<Object> playlists;
                private AtomicInteger loadersRunning;

                public void run() {
                    List<String> songs = new ArrayList<>();
                    for (GetSongIdTask task : tasks) {
                        try {
                            task.get();
                            String id = "foo";
                            if (id != null) {
                                songs.add(id);
                            }
                        } catch (ExecutionException | InterruptedException e) {
                            Log.d(TAG, "", e);
                        }
                    }
                    Log.d(TAG, "got " + songs.size() + " songs from playlist");
                    playlists.add(songs);
                    int loaders = loadersRunning.decrementAndGet();
                    if (loaders == 0) {
                        send();
                    }

                }

                public Thread init(List<GetSongIdTask> tasks, List<Object> playlists, AtomicInteger loadersRunning) {
                    this.tasks = tasks;
                    this.playlists = playlists;
                    this.loadersRunning = loadersRunning;
                    return this;
                }
            }.init(tasks, playlists, loadersRunning).start();
            //    }
            //}.start();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) { }
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
        String url = "http://192.168.26.25:8080";

        //new UploadTask(url, json).execute();
    }

    private void sendForReal(String dest, String json) throws IOException {

        int TIMEOUT_MILLISEC = 10000;  // = 10 seconds
        HttpParams httpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpConnectionParams.setSoTimeout(httpParams, TIMEOUT_MILLISEC);
        HttpClient client = new DefaultHttpClient(httpParams);

        HttpPost request = new HttpPost(dest);
        request.setEntity(new ByteArrayEntity(json.getBytes("UTF8")));

        client.execute(request);
        //HttpResponse response = client.execute(request);


        //URL url = new URL(dest);
        //HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        //try {
        //    conn.setConnectTimeout(5000);
        //    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        //    conn.setDoOutput(true);
        //    //conn.setRequestProperty("Content-Type", "application/json");
        //    //con.setRequestProperty("Accept", "application/json");
        //    conn.setRequestMethod("POST");
        //    //conn.setChunkedStreamingMode(0);
        //    //OutputStream out = new BufferedOutputStream(conn.getOutputStream());
        //    OutputStream out = conn.getOutputStream();
        //    Log.d(TAG, "sending '" + json + "' to " + dest);
        //    out.write(json.getBytes("UTF-8"));
        //    out.close();

        //    //InputStream in = new BufferedInputStream(conn.getInputStream());
        //    //String response = getStr(in);
        //    //Log.d(TAG, "got response from server: " + response);
        //} finally {
        //    conn.disconnect();
        //}
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            api = new GracenoteWebAPI(ApiKeys.GN_CLIENT_ID, ApiKeys.GN_CLIENT_TAG, ApiKeys.GN_USER_ID);
        } catch (GracenoteException e) {
            Log.d(TAG, "Couldn't create API connection", e);
        }
    }

    public void upload(View v) {
        getSupportLoaderManager().initLoader(PLAYLIST_LOADER, null, new PlaylistLoader());
        //new Thread() {
        //    public void run() {
        //        try {
        //            String album = "The Sufferer & The Witness";
        //            String escapedAlbum = StringEscapeUtils.escapeXml10(album);
        //            Log.d(TAG, "escapedAlbum: " + escapedAlbum);
        //            GracenoteMetadata results = api.searchTrack("Rise Against", escapedAlbum, "Ready To Fall");
        //            Log.d(TAG, "Got results successfully");
        //        } catch (GracenoteException e) {
        //            Log.w(TAG, "Couldn't get song data:", e);
        //        }
        //    }
        //}.start();
    }
}
