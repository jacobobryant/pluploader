package com.jacobobryant.playlistuploader;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "PlaylistUploader";
    public static final String AUTHORITY = "com.jacobobryant.playlistuploader";
    public static final String ACCOUNT_TYPE = "com.jacobobryant";
    public static final String ACCOUNT = "mycoolaccount";
    public static final String IP = "192.34.57.201";
    Account mAccount;

    public static class ConsentDialog extends DialogFragment {
        Dialog dialog;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            //String msg = getActivity().getResources().getString(R.string.consent_dialog_msg);
            final SpannableString msg = new SpannableString(
                    getActivity().getResources().getString(R.string.consent_dialog_msg));
            Linkify.addLinks(msg, Linkify.ALL);

            //TextView tv = new TextView(getActivity());
            //tv.setMovementMethod(LinkMovementMethod.getInstance());
            //tv.setText(R.string.consent_dialog_msg);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Consent Agreement")
                   //.setView(tv)
                   .setMessage(msg)
                   .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                           ((MainActivity) getActivity()).setConsentGiven();
                       }
                   })
                   .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) { }
                   });
            this.dialog = builder.create();
            return this.dialog;
        }

        @Override
        public void onStart() {
            ((TextView)this.dialog.findViewById(android.R.id.message))
                    .setMovementMethod(LinkMovementMethod.getInstance());
        }

        //public void setMovement() {
        //    ((TextView)this.dialog.findViewById(android.R.id.message))
        //            .setMovementMethod(LinkMovementMethod.getInstance());
        //}
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (consentGiven()) {
            mAccount = CreateSyncAccount(this);
            show();
        }
    }

    public static Account CreateSyncAccount(Context context) {
        final long SYNC_INTERVAL = 60L * 60L * 24L * 7L;
        Account newAccount = new Account(ACCOUNT, ACCOUNT_TYPE);
        AccountManager accountManager = (AccountManager) context.getSystemService(ACCOUNT_SERVICE);
        if (accountManager.addAccountExplicitly(newAccount, null, null)) {
            Log.d(TAG, "setting sync stuff");
            ContentResolver.setIsSyncable(newAccount, AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(newAccount, AUTHORITY, true);
        }
        ContentResolver.addPeriodicSync(newAccount, AUTHORITY, Bundle.EMPTY, SYNC_INTERVAL);
        Log.d(TAG, "account created");
        return newAccount;
    }

    public void sync() {
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        Log.d(TAG, "calling requestSync()");
        ContentResolver.requestSync(mAccount, AUTHORITY, settingsBundle);
        Toast.makeText(this, "Getting recommendations from server...", Toast.LENGTH_LONG).show();
    }

    public void show() {
        ExpandableListView list = (ExpandableListView) findViewById(R.id.lstRecommendations);
        ExpandableListAdapter adapter = makeAdapter();
        list.setAdapter(adapter);
        for (int position = 0; position < adapter.getGroupCount(); position++) {
            list.expandGroup(position);
        }
    }

    private ExpandableListAdapter makeAdapter() {
        // load recommendations
        SQLiteDatabase db = new Database(this).getReadableDatabase();
        Cursor result = db.rawQuery("SELECT playlist_id, title, artist, album, score " +
                                    "FROM recommendations ORDER BY playlist_id", new String[]{});
        Map<Integer, List<Recommendation>> recommendations = new HashMap<>();
        result.moveToPosition(-1);
        while (result.moveToNext()) {
            Recommendation rec = new Recommendation();
            int playlistId = result.getInt(0);
            rec.title = result.getString(1);
            rec.artist = result.getString(2);
            rec.album = result.getString(3);
            rec.score = result.getDouble(4);

            if (!recommendations.containsKey(playlistId)) {
                recommendations.put(playlistId, new LinkedList<Recommendation>());
            }
            recommendations.get(playlistId).add(rec);
        }

        // get playlist names
        Map<Integer, String> playlistNames = new HashMap<>();
        final String[] proj = {MediaStore.Audio.Playlists._ID,
                               MediaStore.Audio.Playlists.NAME};
        result = getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, proj, null, null, null);
        result.moveToPosition(-1);
        while (result.moveToNext()) {
            int id = result.getInt(0);
            String name = result.getString(1);
            playlistNames.put(id, name);
        }
        result.close();

        // put recommendations in adapter
        final String NAME = "NAME";
        List<Map<String, String>> headers = new ArrayList<>();
        List<List<Map<String, String>>> childData = new ArrayList<>();

        for (Map.Entry<Integer, List<Recommendation>> entry : recommendations.entrySet()) {
            Map<String, String> headerItem = new HashMap<>();
            int id = entry.getKey();
            headerItem.put(NAME, playlistNames.get(id));
            headers.add(headerItem);

            List<Recommendation> recList = entry.getValue();

            List<Map<String, String>> children = new ArrayList<>();
            for (Recommendation rec : recList) {
                int percent = (int) (100 * (rec.score));

                String text = rec.title + "\n" + rec.artist + " (" + percent + "%)";

                Map<String, String> childItem = new HashMap<>();
                childItem.put(NAME, text);
                children.add(childItem);
            }
            childData.add(children);
        }

        return new SimpleExpandableListAdapter(this,
                headers, R.layout.list_parent,
                new String[] { NAME }, new int[] { android.R.id.text1 },
                childData, android.R.layout.simple_expandable_list_item_2,
                new String[] { NAME }, new int[] { android.R.id.text1 });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                Log.d(TAG, "refresh()");
                if (consentGiven()) {
                    sync();
                } else {
                    getConsent();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar, menu);
        return true;
    }

    private BroadcastReceiver syncFinishedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            show();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(syncFinishedReceiver,
                new IntentFilter("com.jacobobryant.playlistuploader.SYNC_FINISHED"));
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean("first_run", true)) {
            getConsent();
            prefs.edit().putBoolean("first_run", false).commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(syncFinishedReceiver);
    }

    void getConsent() {
        //ConsentDialog newFragment = new ConsentDialog();
        //((TextView)newFragment.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance//());
		//myMethod();
        //newFragment.show(getSupportFragmentManager(), "Consent");
        //newFragment.setMovement();
        //AlertDialog d = makeDialog();
        //d.show();

        // Make the textview clickable. Must be called after show()
        //((TextView)d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

        //Intent intent = new Intent(Intent.ACTION_VIEW,
        //        Uri.parse("http://" + IP + "/consent.pdf"));
        //startActivity(intent);

        Spanned msg = Html.fromHtml(
                getResources().getString(R.string.consent_dialog_msg));
                //"Hello <a href=\"http://google.com\">there</a>");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Consent Agreement")
               .setMessage(msg)
               .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       setConsentGiven();
                   }
               })
               .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) { }
               });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView msgTxt = (TextView) alertDialog.findViewById(android.R.id.message);
        msgTxt.setMovementMethod(LinkMovementMethod.getInstance());
    }

    boolean consentGiven() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean("consent_given", false);
    }

    void setConsentGiven() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //prefs.edit().putBoolean("consent_given", true).commit();
        Toast.makeText(this, "consent given: " + consentGiven(), Toast.LENGTH_LONG).show();
        //mAccount = CreateSyncAccount(this);
        //sync();
    }

    //// Linkify the message
    //AlertDialog makeDialog() {
    //    final SpannableString msg = new SpannableString(
    //            getResources().getString(R.string.consent_dialog_msg));
    //    Linkify.addLinks(msg, Linkify.ALL);

    //    final AlertDialog d = new AlertDialog.Builder(this)
    //            .setTitle("Consent Agreement")
    //            .setMessage(msg)
    //            .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
    //                public void onClick(DialogInterface dialog, int id) {
    //                    setConsentGiven();
    //                }
    //            })
    //            .setNegativeButton("Decline", new DialogInterface.OnClickListener() {
    //                public void onClick(DialogInterface dialog, int id) { }
    //            })
    //            .create();

    //    return d;
    //}

}
