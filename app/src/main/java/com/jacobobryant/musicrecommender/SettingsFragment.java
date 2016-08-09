package com.jacobobryant.musicrecommender;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

public class SettingsFragment extends PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String SPOTIFY_CLIENT_ID = "ce0589fdfe4a4c978dd89f24b0a4b4bd";
    private static final String REDIRECT_URI = "musicrecommender://spotifycallback";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals("pref_spotify")) {
            if (prefs.getBoolean(key, false)) {
                // Don't update the settings until we get the Spotify ID
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("pref_spotify", false);
                editor.commit();
                ((CheckBoxPreference) findPreference(key)).setChecked(false);

                // Get spotify user id
                Log.d(MainActivity.TAG, "pref_spotify clicked");
                AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(
                        SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URI);
                builder.setScopes(new String[]{"user-read-private"});
                AuthenticationRequest request = builder.build();
                AuthenticationClient.openLoginActivity(getActivity(), SettingsActivity.REQUEST_CODE, request);
            } else {
                Log.d(MainActivity.TAG, "clearing spotify_token");
                Log.d(MainActivity.TAG, "current spotify token: " +
                        prefs.getString("spotify_token", "NOTOKEN"));

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("spotify_token", "");
                editor.commit();
            }
        }
    }

    public void checkSpotify() {
        ((CheckBoxPreference) findPreference("pref_spotify")).setChecked(true);
    }
}
