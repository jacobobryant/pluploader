package com.jacobobryant.playlistuploader;

import android.database.Cursor;
import android.net.Uri;
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
            json = new ObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            Log.wtf(TAG, "you moron!");
            return;
        }
        Log.d(TAG, "got json: " + json);
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
