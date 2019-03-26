package com.tigertext.ttandroid.sample.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.pubsub.TTEvent;
import com.tigertext.ttandroid.pubsub.TTPubSub;
import com.tigertext.ttandroid.sample.R;

import java.util.Collection;
import java.util.Map;

import timber.log.Timber;

public enum TCNotificationManager implements TTPubSub.Listener {
    INSTANCE;

    private static final String CHANNEL_ID_MESSAGES = "unread_messages";
    private static final int MESSAGES_NOTIFICATION_ID = 0xBADA55;

    private static final String[] listeners = {
            TTEvent.MESSAGES_INCREMENTED_UNREAD_COUNT
    };

    private NotificationManager notificationManager;
    private Context mContext;

    public void init(Context context) {
        mContext = context;
        TT.getInstance().getTTPubSub().addListeners(TCNotificationManager.this, listeners);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        initNotificationChannel();
    }

    @Override
    public void onEventReceived(@NonNull String event, @Nullable Object o) {
        switch (event) {
            case TTEvent.MESSAGES_INCREMENTED_UNREAD_COUNT:
                Map<RosterEntry, Collection<Message>> rosterMessageMap = (Map<RosterEntry, Collection<Message>>) o;
                if (rosterMessageMap == null) return;

                for (Map.Entry<RosterEntry, Collection<Message>> rosterMessages : rosterMessageMap.entrySet()) {
                    RosterEntry rosterEntry = rosterMessages.getKey();
                    Collection<Message> messages = rosterMessages.getValue();
                    for (Message message : messages) {
                        showNotification(message.getSenderDisplayName(), message.getBody());
                    }
                }
                break;
        }
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.enableLights(true);
        notificationChannel.enableVibration(true);
        notificationChannel.setShowBadge(true);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationChannel.setLightColor(ContextCompat.getColor(mContext, R.color.colorAccent));

        notificationManager.createNotificationChannel(notificationChannel);
    }

    private void showNotification(String senderDisplayName, String body) {
        Timber.d("showNotification sender: %s, body: %s", senderDisplayName, body);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID_MESSAGES)
        .setSmallIcon(R.drawable.notification_icon)
        .setContentTitle(senderDisplayName)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true);

        notificationManager.notify(MESSAGES_NOTIFICATION_ID, builder.build());
    }
}
