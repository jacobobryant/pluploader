package com.jacobobryant.musicrecommender;

import android.util.Log;

import com.android.volley.NetworkResponse;
import com.android.volley.Response;
import com.android.volley.VolleyError;

public class VolleyErrorHandler implements Response.ErrorListener {

    @Override
    public void onErrorResponse(VolleyError error) {
        NetworkResponse response = error.networkResponse;
        if (response != null) {
            if (BuildConfig.DEBUG) Log.e(C.TAG, response.statusCode + " " + response.data);
        }
        if (BuildConfig.DEBUG) Log.e(C.TAG, "VolleyError", error);
    }
}
