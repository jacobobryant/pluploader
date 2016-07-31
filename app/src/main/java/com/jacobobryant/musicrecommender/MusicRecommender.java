package com.jacobobryant.musicrecommender;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;

@ReportsCrashes(
        formUri = "http://192.34.57.201:8080/crash_report/acra-musicrecommender",
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
		mode = ReportingInteractionMode.TOAST,
		resToastText = R.string.crash_toast_text
        )
public class MusicRecommender extends Application {
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
        Log.d(MainActivity.TAG, "ACRA.init()");
		ACRA.init(this);
	}
}
