package com.tigertext.ttandroid.sample.fcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.tigertext.ttandroid.sse.PushNotificationHandler;

import timber.log.Timber;

public class SSEBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (PushNotificationHandler.ACTION_DISPLAY_ALERT.equals(intent.getAction())) {
            displayAlert(context, intent);
        }
    }

    private void displayAlert(Context context, Intent intent) {
        Timber.v("displayAlert");
        Toast.makeText(context, intent.getExtras().getString("alert"), Toast.LENGTH_SHORT).show();
    }
}
