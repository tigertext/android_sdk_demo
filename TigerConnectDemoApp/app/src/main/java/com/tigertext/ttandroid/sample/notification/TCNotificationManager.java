package com.tigertext.ttandroid.sample.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.call.payload.EndCallPayload;
import com.tigertext.ttandroid.call.payload.StartCallPayload;
import com.tigertext.ttandroid.entity.Entity2;
import com.tigertext.ttandroid.pubsub.TTEvent;
import com.tigertext.ttandroid.pubsub.TTPubSub;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.utils.TTUtils;
import com.tigertext.ttandroid.sample.voip.service.CallConnectionUtils;
import com.tigertext.ttandroid.sample.voip.states.CallPresenter;
import com.tigertext.ttandroid.sample.voip.VoIPManager;
import com.tigertext.ttandroid.sample.voip.states.CallPresenterManager;
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason;
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter;
import com.tigertext.ttandroid.sse.VoipCallHandler;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public enum TCNotificationManager implements TTPubSub.Listener {
    INSTANCE;

    private static final String CHANNEL_ID_MESSAGES = "unread_messages";
    private static final int MESSAGES_NOTIFICATION_ID = 0;

    private static final String[] listeners = {
            TTEvent.MESSAGES_INCREMENTED_UNREAD_COUNT,
            TTEvent.VOIP_CALL,
    };

    private OnGoingCallNotificationManager onGoingCallNotificationManager;

    private NotificationManager notificationManager;
    private Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public void init(Context context) {
        mContext = context;
        TT.getInstance().getTTPubSub().addListeners(TCNotificationManager.this, listeners);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        initNotificationChannel();

        onGoingCallNotificationManager = new OnGoingCallNotificationManager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            onGoingCallNotificationManager.initNotificationChannels(notificationManager, null);
        }
    }

    protected void runOnMainHandler(Runnable runnable) {
        TTUtils.runOrPostToHandler(mMainHandler, runnable);
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
            case TTEvent.VOIP_CALL:
                final VoipCallHandler.VoipCallData voipCall = (VoipCallHandler.VoipCallData) o;
                switch (voipCall.getType()) {
                    case incoming_call:
                        StartCallPayload startCallPayload = (StartCallPayload) voipCall.getPayload();
                        long timestamp = startCallPayload.getDate();
                        if (System.currentTimeMillis() > timestamp + TimeUnit.MINUTES.toMillis(1)) {
                            Timber.v("Incoming call time must have already expired");
                            return;
                        }
                        final VoIPManager.CallInfo convertedInfo = VoIPManager.INSTANCE.convertCallDataToCallInfo(voipCall);

                        runOnMainHandler(() -> {
                            CallPresenter presenter = CallPresenterManager.getPresenter(convertedInfo.getCallId());
                            if (presenter == null) { // Create/register presenter now so we can keep track of future call updates
                                presenter = new TwilioVideoPresenter(null, convertedInfo);
                            }

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                                    !CallConnectionUtils.INSTANCE.addIncomingCall(mContext, convertedInfo)) {
                                showIncomingCallNotification(convertedInfo, presenter);
                            }
                        });
                        break;
                    case call_ended: {
                        final String callId = voipCall.getRoomName();
                        EndCallPayload endCallPayload = (EndCallPayload) voipCall.getPayload();
                        final String userId = endCallPayload.getUserId();
                        // If this is missing userId, then this update likely came from an older client
                        if (TextUtils.isEmpty(userId)) {
                            Timber.w("userId is empty, might cause incorrect behavior %s", voipCall.getPayload());
                            final int reason = DisconnectReason.INSTANCE.convertTTReasonToRemoteDisconnectReason(voipCall.getReason());
                            runOnMainHandler(() -> CallPresenterManager.onUnknownUserLeftCall(callId, reason));
                        } else {
                            final int reason = DisconnectReason.INSTANCE.convertTTReasonToRemoteDisconnectReason(voipCall.getReason());
                            runOnMainHandler(() -> CallPresenterManager.onUserLeftCall(callId, userId, reason));
                        }
                        break;
                    }
                    case call_answered:
                        final String callId = voipCall.getRoomName();
                        runOnMainHandler(() -> CallPresenterManager.onUserAnsweredElsewhere(callId));
                        break;
                    case call_state:
                        runOnMainHandler(() -> CallPresenterManager.onCallUpdate(voipCall));
                        break;
                    default:
                        throw new IllegalArgumentException("onEventReceived.voipCall - No case for " + voipCall.getType());
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

    /**
     * Posts a notification with a given tag and notification id, updating/replacing in place if one with the same tag and id exists
     */
    void notifyNotificationManager(Notification notification, @androidx.annotation.Nullable String tag, int notificationId, boolean forceOverrideSilentMode) {
        notificationManager.notify(tag, notificationId, notification);
    }

    public void removeOngoingCallNotification(final String callId) {
        runOnMainHandler(() -> notificationManager.cancel(callId, 123));
    }

    public void showIncomingCallNotification(final VoIPManager.CallInfo callInfo, CallPresenter callPresenter) {
        runOnMainHandler(() -> onGoingCallNotificationManager.buildAndShowOnGoingCallNotification(callInfo, callPresenter));
    }

    public void updateCallNotification(final VoIPManager.CallInfo callInfo, CallPresenter callPresenter) {
        if (!callPresenter.isStarted()) return;
        if (TextUtils.isEmpty(callInfo.getCallId())) return;
        runOnMainHandler(() -> onGoingCallNotificationManager.buildAndShowOnGoingCallNotification(callInfo, callPresenter));
    }

    public void showMissedVoIPCallNotification(final VoIPManager.CallInfo callInfo, @androidx.annotation.Nullable final Entity2 entity) {
        // Show missed notification
    }
}
