package com.jacobobryant.musicrecommender;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.acra.ACRA;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {
    public static final int REQUEST_CODE = 666;
    public static final String TAG = MainActivity.TAG;
    private SettingsFragment frag;
    public ProgressDialog dialog;

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
        if (BuildConfig.DEBUG) Log.d(TAG, "onActivityResult()");
        if (requestCode == REQUEST_CODE) {
            if (BuildConfig.DEBUG) Log.d(TAG, "correct request code");
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (BuildConfig.DEBUG) Log.d(TAG, response.getType().toString());
            if (response.getType() != AuthenticationResponse.Type.CODE) {
                // this should never happen
                return;
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "access code: " + response.getCode());

            Map<String, String> params = new HashMap<>();
            params.put("auth", response.getCode());
            params.put("redirect", SettingsFragment.REDIRECT_URI);

            String url = C.SERVER + "/spotify-login";

            JSONObject jsonBody = new JSONObject(params);
            JsonObjectRequest request = new JsonObjectRequest(url, jsonBody,
                    new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    if (BuildConfig.DEBUG) Log.d(C.TAG, "response: " + response.toString());
                    try {
                        saveSpotifyTokens(response);
                    } catch (JSONException e) {
                        throw new RuntimeException("Couldn't get spotify credentials", e);
                    }
                    SettingsActivity.this.dialog.dismiss();
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // TODO debug this -- why does it say my client id is invalid sometimes?
                    NetworkResponse response = error.networkResponse;
                    StringBuilder errmsg = new StringBuilder(
                            String.valueOf(response.statusCode));
                    if (response != null && response.data != null) {
                        errmsg.append(" -- ");
                        errmsg.append(new String(response.data));
                    }
                    ACRA.getErrorReporter().handleException(
                            new RuntimeException(errmsg.toString(), error));
                    SettingsActivity.this.dialog.dismiss();
                    SettingsActivity.this.frag.checkSpotify(false);
                    Toast.makeText(SettingsActivity.this, "Error from Spotify, try later",
                            Toast.LENGTH_LONG).show();
                }
            });
            
            MyCoolQueue.get(this).add(request);
            //frag.checkSpotify(true);
        }
    }

    public void saveSpotifyTokens(JSONObject response) throws JSONException {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        long expireTime = System.currentTimeMillis() / 1000 + response.getInt("expires_in");
        editor.putLong("spotify_expires", expireTime);
        editor.putString("spotify_token", response.getString("access_token"));
        editor.putString("spotify_refresh", response.getString("refresh_token"));
        editor.putBoolean("pref_spotify", true);

        if (BuildConfig.DEBUG) Log.d(C.TAG, "saving spotify as true");
        settings.unregisterOnSharedPreferenceChangeListener(frag);
        editor.commit();
        settings.registerOnSharedPreferenceChangeListener(frag);
        Toast.makeText(this, "Got Spotify credentials", Toast.LENGTH_LONG).show();
    }
}
