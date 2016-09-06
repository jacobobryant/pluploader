package com.jacobobryant.musicrecommender;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public class C {
    public static final String TAG = "PlaylistUploader";
    public static final String SERVER = "https://192.34.57.201";
    //public static final String SERVER = "http://192.168.10.174:5666";

    public static boolean isStoragePermissionGranted(Context c) {
        if (Build.VERSION.SDK_INT >= 23) {
            return (c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED);
        } else {
            return true;
        }
    }
}
