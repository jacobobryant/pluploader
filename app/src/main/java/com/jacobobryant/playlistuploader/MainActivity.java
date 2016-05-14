package com.jacobobryant.playlistuploader;

import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "PlaylistUploader";
    public static final int PLAYLIST_LOADER = 0;
    public static final int SONG_LOADER = 1;
    public static final String KEY_ID = "id";
    private List<Object> playlists = Collections.synchronizedList(new ArrayList<Object>());
    private AtomicInteger loadersRunning;

    private class PlaylistLoader implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int loaderID, Bundle bundle) {
            final String[] PLAYLIST_PROJ = {
                MediaStore.Audio.Playlists._ID
            };
            return new CursorLoader(MainActivity.this,
                        MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, PLAYLIST_PROJ, null, null, null);
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
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            cursor.moveToPosition(-1);
            List<Object> songs = new ArrayList<>();
            while (cursor.moveToNext()) {
                Map<String, Object> song = new HashMap<>();
                song.put("title", cursor.getString(1));
                song.put("artist", cursor.getString(2));
                song.put("album", cursor.getString(3));
                songs.add(song);
            }
            playlists.add(songs);
            int loaders = loadersRunning.decrementAndGet();
            if (loaders == 0) {
                send();
            }
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
		new UploadTask(url, json).execute();
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
		//	//String response = getStr(in);
		//	//Log.d(TAG, "got response from server: " + response);
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void upload(View v) {
        getSupportLoaderManager().initLoader(PLAYLIST_LOADER, null, new PlaylistLoader());
    }
}
