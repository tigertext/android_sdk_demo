package com.tigertext.ttandroid.sample.fcm;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.gcm.TTGcm;
import com.tigertext.ttandroid.sample.application.TigerConnectApplication;

import timber.log.Timber;

public class FcmMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(final RemoteMessage message) {
        if (!TT.getInstance().getAccountManager().isLoggedIn()) {
            Timber.e("Received FCM while logged out, ignoring message");
            return;
        }

        if (message.getNotification() != null) {
            Timber.d("Message Notification Body: %s", message.getNotification().getBody());
        }

        if (message.getData().size() > 0) {
            Timber.d("Message data: %s", message.getData());
        }

        Timber.d("Remote message received via FCM");
        // Notify the TT SDK that a FCM was received.
        TTGcm.onMessageReceived(message.getFrom(), message.getData());
        /* If hibernation mode is not on, then only wake up the SSE */
        if (!TT.getInstance().getAccountManager().isHibernationModeOn()) {
            // If app is open or BG Service is enabled, and FCM is received, means that app is not connected to SSE, hence, connect SSE.
            if (TigerConnectApplication.getApp().isAppOpen()) {
                Timber.d("FCM received, awaking SSE");
                try {
                    TT.getInstance().startService(true);
                } catch (IllegalStateException e) {
                    Timber.d(e, "Unable to start service, FCM might not be high priority");
                }
            }
        }
    }

    @Override
    public void onDeletedMessages() {
        if (!TT.getInstance().getAccountManager().isLoggedIn()) {
            Timber.e("onDeletedMessages while logged out");
            return;
        }

        /* If hibernation mode is not on, then only wake up the SSE */
        if (!TT.getInstance().getAccountManager().isHibernationModeOn()) {
            // If app is open or BG Service is enabled, and FCM is received, means that app is not connected to SSE, hence, connect SSE.
            if (TigerConnectApplication.getApp().isAppOpen()) {
                Timber.d("FCM onDeletedMessages, starting SSE");
                try {
                    TT.getInstance().startService(false);
                } catch (IllegalStateException e) {
                    Timber.d(e, "Unable to start service, Fallback to manual download");
                    boolean isSuccess = TTGcm.downloadPendingEvents();
                    Timber.d("Manual pending event download success: %b", isSuccess);
                }
            } else {
                // Not allowed to start service, but we know we're missing updates. Try to fetch latest
                boolean isSuccess = TTGcm.downloadPendingEvents();
                Timber.d("Manual pending event download success: %b", isSuccess);
            }
        }
    }
}

