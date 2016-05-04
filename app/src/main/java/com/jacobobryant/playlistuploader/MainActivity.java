package com.jacobobryant.playlistuploader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class MainActivity extends AppCompatActivity {
    public static String TAG = "PlaylistUploader";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void upload(View v) {
        Log.d(TAG, "hello there!");
    }
}
