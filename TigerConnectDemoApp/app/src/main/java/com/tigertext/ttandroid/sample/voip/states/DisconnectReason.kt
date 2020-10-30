package com.tigertext.ttandroid.sample.voip.states

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import android.telecom.DisconnectCause
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.application.TigerConnectApplication
import com.tigertext.ttandroid.sse.VoipCallHandler

/**
 * Based on [android.telecom.DisconnectCause] and [VoipCallHandler.VoipCallData] to create a common
 * ground to report connection status to the telecom and to TC.
 */
object DisconnectReason {


    /**
     * Local user ended a current call
     */
    const val LOCAL_ENDED = 0
    /**
     * Local user rejected an incoming call
     */
    const val LOCAL_REJECTED = 1
    /**
     * Local user experienced an issue
     */
    const val LOCAL_ERROR = 2
    /**
     * Local user is busy (determined by system)
     */
    const val LOCAL_BUSY = 3
    /**
     * Local user waited long enough with no response from remote user
     */
    const val LOCAL_TIMEOUT = 4

    /**
     * Unknown remote reason
     */
    const val REMOTE_UNKNOWN = 10
    /**
     * Remote user ended the call
     */
    const val REMOTE_ENDED = 11
    /**
     * Remote user declined their incoming call
     */
    const val REMOTE_DECLINED = 12
    /**
     * Remote user timed out, remote version of [LOCAL_TIMEOUT]
     */
    const val REMOTE_UNANSWERED = 13
    /**
     * Local user picked up the call on a different remote device
     */
    const val REMOTE_ANSWERED_ELSEWHERE = 14
    /**
     * Local user ended the call on a different remote device
     */
    const val REMOTE_LOCAL_ENDED_ELSEWHERE = 15
    /**
     * Remote user experienced an issue
     */
    const val REMOTE_ERROR = 16

    /**
     * Converts local reasons to a TT reason that must be sent to server, or null if not a local reason
     */
    fun convertDisconnectReasonToTTReason(disconnectReason: Int): VoipCallHandler.VoipCallData.Reason? =
            when (disconnectReason) {
                LOCAL_REJECTED, LOCAL_BUSY -> VoipCallHandler.VoipCallData.Reason.declined
                LOCAL_ENDED, REMOTE_ENDED, REMOTE_DECLINED, REMOTE_ERROR, REMOTE_UNANSWERED -> VoipCallHandler.VoipCallData.Reason.ended
                LOCAL_ERROR -> VoipCallHandler.VoipCallData.Reason.failed
                LOCAL_TIMEOUT -> VoipCallHandler.VoipCallData.Reason.unanswered
                else -> null //answered elsewhere
            }

    /**
     * Converts remote TT reasons to a disconnect reason
     */
    fun convertTTReasonToRemoteDisconnectReason(reason: VoipCallHandler.VoipCallData.Reason?): Int =
            when (reason) {
                VoipCallHandler.VoipCallData.Reason.answered -> REMOTE_ANSWERED_ELSEWHERE
                VoipCallHandler.VoipCallData.Reason.failed -> REMOTE_ERROR
                VoipCallHandler.VoipCallData.Reason.unanswered -> REMOTE_UNANSWERED
                VoipCallHandler.VoipCallData.Reason.remote_ended, VoipCallHandler.VoipCallData.Reason.ended -> REMOTE_ENDED
                VoipCallHandler.VoipCallData.Reason.declined -> REMOTE_DECLINED
                else -> REMOTE_UNKNOWN
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun convertDisconnectReasonToDisconnectCause(disconnectReason: Int) =
            when (disconnectReason) {
                LOCAL_ENDED, LOCAL_REJECTED  -> DisconnectCause(DisconnectCause.LOCAL)
                LOCAL_ERROR, REMOTE_ERROR -> DisconnectCause(DisconnectCause.ERROR)
                LOCAL_BUSY -> DisconnectCause(DisconnectCause.MISSED)
                LOCAL_TIMEOUT -> DisconnectCause(DisconnectCause.MISSED, TigerConnectApplication.getApp().getString(R.string.voip_call_ended_timeout))
                REMOTE_UNKNOWN -> DisconnectCause(DisconnectCause.REMOTE)
                REMOTE_ENDED -> DisconnectCause(DisconnectCause.REMOTE, TigerConnectApplication.getApp().getString(R.string.voip_call_ended_hung_up))
                REMOTE_DECLINED -> DisconnectCause(DisconnectCause.REMOTE, TigerConnectApplication.getApp().getString(R.string.voip_call_ended_cancelled))
                REMOTE_UNANSWERED -> DisconnectCause(DisconnectCause.BUSY)
                REMOTE_ANSWERED_ELSEWHERE -> DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE)
                REMOTE_LOCAL_ENDED_ELSEWHERE -> DisconnectCause(DisconnectCause.ANSWERED_ELSEWHERE)
                else -> DisconnectCause(DisconnectCause.UNKNOWN)
            }

    @StringRes
    fun convertDisconnectReasonToStringRes(disconnectReason: Int) =
            when (disconnectReason) {
                REMOTE_DECLINED -> R.string.name_declined_the_call
                REMOTE_ENDED -> R.string.name_left_the_call
                REMOTE_UNANSWERED -> R.string.name_didnt_answer
                else -> 0
            }

}