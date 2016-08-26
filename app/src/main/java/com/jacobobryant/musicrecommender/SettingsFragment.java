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
    public static final String REDIRECT_URI = "musicrecommender://spotifycallback";

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
                if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "pref_spotify clicked");

                // TODO use state thang
                AuthenticationRequest request = new AuthenticationRequest.Builder(
                        SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.CODE, REDIRECT_URI)
                        .setScopes(new String[]{"playlist-read-private",
                                                "playlist-read-collaborative"})
                        .build();

                AuthenticationClient.openLoginActivity(getActivity(),
                        SettingsActivity.REQUEST_CODE, request);
            } else {
                if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "clearing spotify_token");
                if (BuildConfig.DEBUG) Log.d(MainActivity.TAG, "current spotify token: " +
                        prefs.getString("spotify_token", "NOTOKEN"));

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("spotify_token", "");
                editor.commit();
            }
        }
    }

    public void checkSpotify(boolean value) {
        ((CheckBoxPreference) findPreference("pref_spotify")).setChecked(value);
    }
}
