package com.tigertext.ttandroid.sample.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.tigertext.ttandroid.sample.voip.VoIPManager;
import com.tigertext.ttandroid.sample.voip.states.CallPresenterManager;
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason;

/**
 * Created by carydobeck on 2/5/16.
 */
public class NotificationBroadcastReceiver extends BroadcastReceiver {

    public static final String EXTRA_ROSTER_IDS = "rosterIds";
    public static final String EXTRA_ORGANIZATION_IDS = "organizationIds";
    public static final String EXTRA_ROSTER_TYPES = "rosterTypes";

    public static final String DECLINE_CALL_ACTION = "decline.call.action";
    public static final String EXTRA_IS_CALL_STARTED = "is_call_started";

    public NotificationBroadcastReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case DECLINE_CALL_ACTION: {
                VoIPManager.CallInfo callInfo = VoIPManager.INSTANCE.getCallInfoFromExtras(intent.getExtras());
                boolean isCallStarted = intent.getBooleanExtra(EXTRA_IS_CALL_STARTED, false);
                int reason = isCallStarted ? DisconnectReason.LOCAL_ENDED : DisconnectReason.LOCAL_REJECTED;

                CallPresenterManager.endCall(callInfo, true, reason);
            }
            break;
        }
    }
}
