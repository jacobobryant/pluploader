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
        //addPreferencesFromResource(R.xml.preferences);

        frag = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .commit();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //PreferenceManager.getDefaultSharedPreferences(this);
            //registerOnSharedPreferenceChangeListener(this);

        //Preference button = (Preference)findPreference("pref_logout");
        //button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        //    @Override
        //    public boolean onPreferenceClick(Preference preference) {   
        //        logout();
        //        return true;
        //    }
        //});
        //button = (Preference)findPreference("pref_resync");
        //button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        //    @Override
        //    public boolean onPreferenceClick(Preference preference) {   
        //        resync();
        //        return true;
        //    }
        //});
        //for (String key : new String[] {TREE_LINES, LIFE_LINES, SPOUSE_LINES, MAP_TYPE}) {
        //    ListPreference listPref = (ListPreference) findPreference(key);
        //    listPref.setSummary(listPref.getEntry());
        //}
        //getActionBar().setDisplayHomeAsUpEnabled(true);
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

    //private void resync() {
    //    Model m = Model.singleton();
    //    m.clear();
    //    try {
    //        LoginTask task = new LoginTask();
    //        task.setFinishCallback(new Runnable() {
    //            @Override
    //            public void run() {
    //                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
    //                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    //                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    //                startActivity(intent);
    //            }
    //        });
    //        task.setErrorCallback(new ErrorCallback() {
    //            public void onError(String message) {
    //                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
    //            }
    //        });
    //        task.execute();
    //    } catch (MalformedURLException e) {
    //        Log.e("FamilyMap", "bad url!");
    //    }
    //}

    //private void logout() {
    //    Log.d("FamilyMap", "logging out");
    //    Model.singleton().clearAll();
    //    Intent intent = new Intent(this, MainActivity.class);
    //    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    //    startActivity(intent);
    //}

    //@Override
    //public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    //    Preference pref = findPreference(key);
    //    if (pref instanceof ListPreference) {
    //        ListPreference listPref = (ListPreference) pref;
    //        pref.setSummary(listPref.getEntry());
    //    }
    //}

    //@Override
    //public boolean onOptionsItemSelected(MenuItem item) {
    //    switch (item.getItemId()) {
    //        case android.R.id.home:
    //            onBackPressed();
    //            return true;
    //    }
    //    return super.onOptionsItemSelected(item);
    //}
}
