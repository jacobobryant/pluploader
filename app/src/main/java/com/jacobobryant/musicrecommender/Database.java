package com.jacobobryant.musicrecommender;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
    public static final int VERSION = 1;
    public static final String FILE = "recommendations.db";

    public Database(Context context) {
        super(context, FILE, null, VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE recommendations (" +
                   "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                   "playlist_name TEXT, " +
                   "title TEXT, " +
                   "artist TEXT, " +
                   "album TEXT, " +
                   "score REAL)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE recommendations");
        onCreate(db);
    }
}
