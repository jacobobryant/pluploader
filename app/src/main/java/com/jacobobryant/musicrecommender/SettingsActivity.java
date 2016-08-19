package com.jacobobryant.musicrecommender;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class SettingsActivity extends AppCompatActivity {
    public static final int REQUEST_CODE = 666;
    public static final String TAG = MainActivity.TAG;
    private SettingsFragment frag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        frag = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.d(TAG, "onActivityResult()");
        if (requestCode == REQUEST_CODE) {
            Log.d(TAG, "correct request code");
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            Log.d(TAG, response.getType().toString());
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Log.d(TAG, "access token: " + response.getAccessToken());

                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("spotify_token", response.getAccessToken());
                editor.putBoolean("pref_spotify", true);
                editor.commit();
                frag.checkSpotify();
                Toast.makeText(this, "Got Spotify credentials", Toast.LENGTH_LONG).show();
            }
        }
    }
}
