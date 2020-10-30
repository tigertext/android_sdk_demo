package com.tigertext.ttandroid.sample.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.RemoteInput;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.gcm.TTGcm;
import com.tigertext.ttandroid.sample.utils.TTIntentUtils;
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity;
import com.tigertext.ttandroid.sample.voip.VoIPManager;
import com.tigertext.ttandroid.sse.PushNotificationHandler;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by carydobeck on 4/10/17.
 */

public class NotificationIntentUtils {

    private static final int DISMISS_REQUEST_CODE = 0x1;
    private static final int MUTE_ALL_REQUEST_CODE = 0x3;
    private static final int OPEN_INBOX_REQUEST_CODE = 0x4;
    private static final int RETRY_REGISTRATION_REQUEST_CODE = 0x5;
    private static final int DISMISS_ROLE_REQUEST_CODE = 0x6;
    private static final int OPEN_MY_ROLES_REQUEST_CODE = 0x7;
    private static final int DISMISS_ALL_MISSED_CALLS_REQUEST_CODE = 0x8;
    private static final int DISMISS_ALL_PRIORITY_REQUEST_CODE = 0x9;
    private static final int OPEN_CLINICAL_ALERTS_REQUEST_CODE = 0xA;
    private static final int OPEN_VOIP_ACTIVITY_REQUEST_CODE = 0xB;
    private static final int DECLINE_CALL_REQUEST_CODE = 0xC;
    private static final int ANSWER_CALL_REQUEST_CODE = 0xD;
    private static final int DISMISS_ALL_PATIENT_CARE_REQUEST_CODE = 0xE;

    ///////////////////////////////// Get PendingIntents /////////////////////////////////

    static PendingIntent getOpenVoipCallActivityIntent(Context context, VoIPManager.CallInfo call) {
        Intent voipActivityIntent = TTIntentUtils.getVoipCallIntent(context, call);
        return PendingIntent.getActivity(context, OPEN_VOIP_ACTIVITY_REQUEST_CODE + call.getCallId().hashCode(), voipActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent getCallDeclineIntent(Context context, VoIPManager.CallInfo callInfo, boolean isCallStarted) {
        Intent declineCallIntent = new Intent(context, NotificationBroadcastReceiver.class);
        declineCallIntent.setAction(NotificationBroadcastReceiver.DECLINE_CALL_ACTION);
        Bundle extras = new Bundle();
        VoIPManager.INSTANCE.convertCallInfoToExtras(callInfo, extras);
        declineCallIntent.putExtras(extras);
        declineCallIntent.putExtra(NotificationBroadcastReceiver.EXTRA_IS_CALL_STARTED, isCallStarted);
        return PendingIntent.getBroadcast(context, DECLINE_CALL_REQUEST_CODE + callInfo.getCallId().hashCode(), declineCallIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent getCallAnswerIntent(Context context, VoIPManager.CallInfo call) {
        Intent relaunchCallIntent = TTIntentUtils.getVoipCallIntent(context, call);
        relaunchCallIntent.putExtra(VoIPCallActivity.EXTRA_IS_ANSWERED, true);
        return PendingIntent.getActivity(context, ANSWER_CALL_REQUEST_CODE + call.getCallId().hashCode(), relaunchCallIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    ///////////////////////////////// Get from Intents /////////////////////////////////

    static String getRemoteInputTextFromIntent(Intent intent, String extraName) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence charSequence = remoteInput.getCharSequence(extraName);
            return charSequence != null ? charSequence.toString() : null;
        }
        return null;
    }

    static boolean hasSoundInfo(@Nullable Intent intent) {
        return intent != null && intent.hasExtra(TTGcm.NOTIFICATION_FLAG) && intent.hasExtra(TTGcm.VIBRATE_FLAG) && intent.hasExtra(TTGcm.RINGTONE);
    }

    /**
     * Get the org id from the intent, regardless of whether the intent came from {@link TTGcm} or from {@link PushNotificationHandler}
     *
     * @param intent The notification intent, either from {@link TTGcm} or from {@link PushNotificationHandler}
     * @return The organization id for the notification, or null if none was found.
     */
    @Nullable
    static String getOrganizationId(@Nullable Intent intent) {
        if (intent == null) return null;
        String orgId = intent.getStringExtra(TTGcm.ORG);
        if (TextUtils.isEmpty(orgId)) {
            orgId = intent.getStringExtra(PushNotificationHandler.ORGANIZATION);
        }
        return orgId;
    }

    ///////////////////////////////// Parcelable Utils /////////////////////////////////

    /**
     * Inserts a list of RosterEntries into an intent as just ids, org ids, and roster types for minimal
     * impact.
     */
    static void insertRosterEntriesAsIdsAndTypes(Intent intent, List<RosterEntry> rosterEntries) {
        ArrayList<String> rosterIds = new ArrayList<>(rosterEntries.size());
        ArrayList<String> orgIds = new ArrayList<>(rosterEntries.size());
        ArrayList<String> rosterTypes = new ArrayList<>(rosterEntries.size());
        for (RosterEntry rosterEntry : rosterEntries) {
            rosterIds.add(rosterEntry.getId());
            orgIds.add(rosterEntry.getOrgId());
            rosterTypes.add(rosterEntry.getType());
        }
        intent.putStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ROSTER_IDS, rosterIds);
        intent.putStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ORGANIZATION_IDS, orgIds);
        intent.putStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ROSTER_TYPES, rosterTypes);
    }

    /**
     * Creates a list of entries that only contain roster id, org id and type from the intent
     *
     * @return A list of roster entries, or null if something was wrong.
     * @see #insertRosterEntriesAsIdsAndTypes(Intent, List) To generate extras in the intent
     */
    @Nullable
    static List<RosterEntry> getDummyRosterEntriesFromIntentIdsAndTypes(Intent intent) {
        ArrayList<String> rosterIds = intent.getStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ROSTER_IDS);
        ArrayList<String> orgIds = intent.getStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ORGANIZATION_IDS);
        ArrayList<String> rosterTypes = intent.getStringArrayListExtra(NotificationBroadcastReceiver.EXTRA_ROSTER_TYPES);

        if (rosterIds == null || orgIds == null || rosterTypes == null) {
            return null;
        }

        List<RosterEntry> dummyEntries = new ArrayList<>(rosterIds.size());
        for (int i = 0, n = rosterIds.size(); i < n; i++) {
            RosterEntry dummyEntry = RosterEntry.getRosterEntry(rosterIds.get(i))
                    .setRosterOrgId(orgIds.get(i))
                    .setRosterType(rosterTypes.get(i));
            dummyEntries.add(dummyEntry);
        }

        return dummyEntries;
    }

}
