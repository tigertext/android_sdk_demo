package com.tigertext.ttandroid.sample.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.tigertext.ttandroid.sample.application.TigerConnectApplication;

public class SharedPrefs {

    private static final String SHARED_PREFERENCES = "shared_preferences";

    public static final String ORGANIZATION_ID = "organization_id";

    private static SharedPrefs instance;
    private SharedPreferences sharedPrefs;
    private SharedPreferences.Editor sharedPrefsEditor;

    private SharedPrefs(final Context context) {
        sharedPrefs = context.getApplicationContext().getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
        sharedPrefsEditor = sharedPrefs.edit();
    }

    public static SharedPrefs getInstance() {
        if (instance == null) {
            instance = new SharedPrefs(TigerConnectApplication.getApp());
        }

        return instance;
    }

    public String getString(final String key, String defaultValue) {
        return sharedPrefs.getString(key, defaultValue);
    }

    public void putString(final String key, final String value) {
        sharedPrefsEditor.putString(key, value).apply();
    }
}
