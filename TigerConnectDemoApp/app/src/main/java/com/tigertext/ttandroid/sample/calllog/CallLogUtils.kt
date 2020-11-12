package com.tigertext.ttandroid.sample.calllog

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import com.tigertext.ttandroid.Group
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.calllog.CallLogEntry
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.PatientUtils
import com.tigertext.ttandroid.sse.BangHandler

object CallLogUtils {

    fun isOutgoingCall(callLogEntry: CallLogEntry): Boolean {
        return callLogEntry.caller.id == TT.getInstance().accountManager.userId
    }

    fun isMissedCall(callLogEntry: CallLogEntry): Boolean {
        return when (callLogEntry.type) {
            BangHandler.EVENT_MISSED_PROXY_CALL, BangHandler.EVENT_MISSED_VOIP_CALL -> true
            else -> false
        }
    }

    fun isVoipCall(callLogEntry: CallLogEntry): Boolean {
        return when (callLogEntry.type) {
            BangHandler.EVENT_ENDED_VOIP_CALL, BangHandler.EVENT_MISSED_VOIP_CALL -> true
            else -> false
        }
    }

    fun getCallFeatureString(context: Context, callLogEntry: CallLogEntry): String {
        return if (isVoipCall(callLogEntry))
            context.getString(if (callLogEntry.isVideo) R.string.video else R.string.call)
        else context.getString(R.string.mobile)
    }

    fun getCallStatusText(context: Context, callLogEntry: CallLogEntry): String {
        val isMissedCall = isMissedCall(callLogEntry)
        val isOutgoingCall = isOutgoingCall(callLogEntry)
        val flipCallerAndTarget = callLogEntry.target.id == TT.getInstance().accountManager.userId
        val target = if (flipCallerAndTarget) callLogEntry.caller else callLogEntry.target

        var callTypeId = if (isOutgoingCall) R.string.outgoing else if (isMissedCall) R.string.missed else R.string.incoming
        if (!isMissedCall && target is Group && !target.isTypeRole && !PatientUtils.isPatientP2p(target)) callTypeId = R.string.group
        val callTypeString = context.getString(callTypeId)
        val callOrVideo = getCallFeatureString(context, callLogEntry)
        val callHeader = context.getString(R.string.join_string_space_string, callTypeString, callOrVideo)
        val date = DateUtils.formatDateTime(context, callLogEntry.timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)

        return context.getString(R.string.join_string_space_string, callHeader, date)
    }

    fun getCallDurationString(resources: Resources, isOutgoingCall: Boolean, isMissedCall: Boolean, durationSeconds: Int): String? {
        return if (isOutgoingCall || !isMissedCall) "$durationSeconds seconds"
        else null
    }

}