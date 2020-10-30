package com.tigertext.ttandroid.sample.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.states.CallPresenter
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

/**
 * Created by martincazares on 1/17/18.
 */
class OnGoingCallNotificationManager(private val context: Context) {

    fun buildAndShowOnGoingCallNotification(callInfo: VoIPManager.CallInfo, callPresenter: CallPresenter) {
        val notificationBuilder = getCommonOngoingCallNotificationBuilder(callInfo, callPresenter, callPresenter.isStarted)

        val contentText = when {
            callPresenter.onHold -> context.getString(R.string.on_hold)
            else -> {
                var callType = context.getString(if (callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO) R.string.video_call else R.string.voice_call)
                val isPatientCall = (callPresenter as? TwilioVideoPresenter)?.isPatientCall() == true
                if (isPatientCall) callType = "${context.getString(R.string.patient)} $callType"
                context.getString(if (callPresenter.isStarted) R.string.ongoing_audio_call else R.string.incoming_audio_call, callType)
            }
        }
        notificationBuilder.setContentText(contentText)

        if (!callPresenter.isStarted) {
            val pendingIntent = NotificationIntentUtils.getOpenVoipCallActivityIntent(context, callInfo)
            notificationBuilder.setFullScreenIntent(pendingIntent, true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .addAction(R.drawable.ic_call_decline_notification,
                            context.getString(R.string.decline),
                            NotificationIntentUtils.getCallDeclineIntent(context, callInfo, false))
                    .addAction(R.drawable.ic_call_notification,
                            context.getString(R.string.answer), NotificationIntentUtils.getCallAnswerIntent(context, callInfo))
        } else {
            notificationBuilder.addAction(R.drawable.ic_call_decline_notification,
                    context.getString(R.string.end_call),
                    NotificationIntentUtils.getCallDeclineIntent(context, callInfo, true))
        }

        if (callPresenter.establishedTime != 0L) {
            notificationBuilder.setUsesChronometer(callPresenter.isEstablished)
            notificationBuilder.setWhen(callPresenter.establishedTime)
        }

        notificationBuilder.setSmallIcon(R.drawable.notification_icon)
//        val rosterEntry = callPresenter.rosterEntryLiveData?.value
//        if (rosterEntry != null) {
//            TCNotificationManager.INSTANCE.setRosterEntryAsLargeIcon(notificationBuilder, rosterEntry)
//        } else {
//            TCNotificationManager.INSTANCE.setRosterEntryAsLargeIcon(notificationBuilder, callInfo.displayName, null, !callInfo.groupId.isNullOrEmpty())
//        }

        val notification = notificationBuilder.build()
        if (!callPresenter.isStarted) {
            notification.flags = notification.flags.or(NotificationCompat.FLAG_INSISTENT)
        }

        TCNotificationManager.INSTANCE.notifyNotificationManager(notification, callInfo.callId, NOTIFICATION_ID, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun initNotificationChannels(notificationManager: NotificationManager, channelGroupId: String?) {
        val channel = NotificationChannel(CHANNEL_ID, "Ongoing Calls", NotificationManager.IMPORTANCE_HIGH)
        val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setLegacyStreamType(AudioManager.STREAM_RING)
                .build()
        channel.setSound(Settings.System.DEFAULT_RINGTONE_URI, audioAttributes)
        channel.vibrationPattern = VIBRATE_PATTERN
        channel.group = channelGroupId
        notificationManager.createNotificationChannel(channel)
    }

    @WorkerThread
    private fun getCommonOngoingCallNotificationBuilder(callInfo: VoIPManager.CallInfo, callPresenter: CallPresenter, silent: Boolean): NotificationCompat.Builder {
        val isKitKatOrLower = Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT

        val pendingIntent = if (callPresenter.isStarted) {
            NotificationIntentUtils.getCallAnswerIntent(context, callInfo)
        } else {
            NotificationIntentUtils.getOpenVoipCallActivityIntent(context, callInfo)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentIntent(pendingIntent)
                .setContentTitle(VoIPCallActivity.getDisplayName(context, callInfo, callPresenter))
                .setAutoCancel(false)
                .setOngoing(true)
                .setColorized(true)
                .setWhen(System.currentTimeMillis())
                .setCategory(NotificationCompat.CATEGORY_CALL)

        if (!silent) {
            builder.setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioManager.STREAM_RING)
                    .setVibrate(VIBRATE_PATTERN)
        }

        return builder
    }

    private companion object {
        private val VIBRATE_PATTERN = longArrayOf(0, 500, 200, 250, 200)
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID ="calls_channel"
        private const val GROUP_KEY = "group_key"
    }
}