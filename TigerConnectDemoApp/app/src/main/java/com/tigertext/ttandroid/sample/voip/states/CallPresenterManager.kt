package com.tigertext.ttandroid.sample.voip.states

import android.os.Build
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.call.payload.EndCallPayload
import com.tigertext.ttandroid.http.models.request.call.EndVoipCallRequest
import com.tigertext.ttandroid.sample.notification.TCNotificationManager
import com.tigertext.ttandroid.sse.VoipCallHandler
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.service.CallConnectionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

object CallPresenterManager {

    private val presenters: MutableMap<String, CallPresenter> = ArrayMap()
    var activePresenter: CallPresenter? = null
        set(value) {
            if (field != value) {
                field?.onHold = true
                field = value
            }
            field?.onHold = false
        }

    @JvmStatic
    fun registerPresenter(callId: String, callPresenter: CallPresenter) {
        presenters[callId] = callPresenter
    }

    @JvmStatic
    fun getPresenter(callId: String?) = presenters[callId]

    @JvmStatic
    fun unregisterPresenter(callId: String) {
        val presenter = presenters.remove(callId)

        if (presenter != null && activePresenter == presenter) {
            activePresenter = null
        }
    }

    @JvmStatic
    fun hasOngoingCalls(): Boolean {
        return presenters.isNotEmpty()
    }

    /**
     * Clean up and end the call for the given call info. This will delegate to the presenter for the call if one exists
     * [sendStatusUpdate] The call was ended by the current user and a status must be sent to the server
     */
    @MainThread
    @JvmStatic
    fun endCall(callInfo: VoIPManager.CallInfo, sendStatusUpdate: Boolean, disconnectReason: Int) {
        val callId = callInfo.callId ?: return
        val presenter = presenters[callId]
        if (presenter != null) {
            presenter.endCall(disconnectReason)
        } else {
            TCNotificationManager.INSTANCE.removeOngoingCallNotification(callId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CallConnectionService.callConnectionService?.allConnections?.forEach {
                    if (it is CallConnectionService.CallConnection && it.callInfo.callId == callId) {
                        it.setDisconnected(DisconnectReason.convertDisconnectReasonToDisconnectCause(disconnectReason))
                        it.destroy()
                    }
                }
            }

            if (sendStatusUpdate && (callInfo.callType == VoIPCallActivity.CALL_TYPE_AUDIO
                            || callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO)) {
                val reason = DisconnectReason.convertDisconnectReasonToTTReason(disconnectReason)
                        ?: return
                val localId = callInfo.localId ?: callId
                val payload = EndCallPayload(TT.getInstance().accountManager.userId)

                if (disconnectReason == DisconnectReason.REMOTE_ENDED ||
                        disconnectReason == DisconnectReason.LOCAL_TIMEOUT) {
                    TCNotificationManager.INSTANCE.showMissedVoIPCallNotification(callInfo, null)
                }

                GlobalScope.launch(Dispatchers.IO) {
                    val response = kotlin.runCatching { TT.getInstance().callManager.endVoipCall(callId, EndVoipCallRequest(reason, localId, payload)) }
                    response.exceptionOrNull()?.let { e -> Timber.e(e, "endVoipCall failure") } ?: Timber.d("endVoipCall success")
                }
            }
        }
    }

    @MainThread
    @JvmStatic
    fun onUserLeftCall(callId: String, userId: String, disconnectReason: Int) {
        if (userId == TT.getInstance().accountManager.userId) {
            onUserAnsweredElsewhere(callId, DisconnectReason.REMOTE_LOCAL_ENDED_ELSEWHERE)
            return
        }
        val presenter = presenters[callId]
        if (presenter is TwilioVideoPresenter) {
            presenter.removeParticipant(userId, disconnectReason)
        } else {
            Timber.w("onUserLeftCall ignored callId: %s", callId)
        }
    }

    @MainThread
    @JvmStatic
    fun onUnknownUserLeftCall(callId: String, disconnectReason: Int) {
        val presenter = presenters[callId]
        if (presenter is TwilioVideoPresenter) {
            if (presenter.userIds.size <= 2) {
                presenter.endCall(disconnectReason)
            }
        } else {
            Timber.w("onUnknownUserLeftCall ignored callId: %s", callId)
        }
    }

    @MainThread
    @JvmStatic
    @JvmOverloads
    fun onUserAnsweredElsewhere(callId: String, disconnectReason: Int = DisconnectReason.REMOTE_ANSWERED_ELSEWHERE) {
        val presenter = presenters[callId]
        if (presenter != null) {
            presenter.endCall(disconnectReason)
        } else {
            TCNotificationManager.INSTANCE.removeOngoingCallNotification(callId)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CallConnectionService.callConnectionService?.allConnections?.forEach {
                    if (it is CallConnectionService.CallConnection && it.callInfo.callId == callId) {
                        it.setDisconnected(DisconnectReason.convertDisconnectReasonToDisconnectCause(disconnectReason))
                        it.destroy()
                    }
                }
            }
        }
    }

    @MainThread
    @JvmStatic
    fun onCallUpdate(voipCallData: VoipCallHandler.VoipCallData) {
        val presenter = presenters[voipCallData.roomName]
        if (presenter is TwilioVideoPresenter) {
            presenter.onCallUpdate(voipCallData)
        } else {
            Timber.w("onCallUpdate ignored callId: %s", voipCallData.roomName)
        }
    }


}