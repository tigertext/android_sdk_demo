package com.tigertext.ttandroid.sample.fcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.tigertext.ttandroid.gcm.TTGcm;

import timber.log.Timber;

public class FcmBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (TTGcm.ACTION_DISPLAY_ALERT.equals(action)) {
            displayAlert(context, intent);
        }
    }

    private void displayAlert(Context context, Intent intent) {
        Timber.v("displayAlert");
        Toast.makeText(context, intent.getExtras().getString("alert"), Toast.LENGTH_SHORT).show();
//        Handler handler = new Handler();
//        handler.post(() -> {
//            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
//                    .setSmallIcon(R.drawable.notification_icon)
//                    .setContentTitle(textTitle)
//                    .setContentText(textContent)
//                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
//        });
    }

}
