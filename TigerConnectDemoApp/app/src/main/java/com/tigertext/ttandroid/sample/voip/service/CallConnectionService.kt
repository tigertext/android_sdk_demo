package com.tigertext.ttandroid.sample.voip.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.*
import androidx.annotation.RequiresApi
import com.tigertext.ttandroid.sample.notification.TCNotificationManager
import com.tigertext.ttandroid.sample.utils.TTIntentUtils
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.states.CallPresenterManager
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class CallConnectionService: ConnectionService() {

    override fun onCreate() {
        super.onCreate()
        Timber.v("onCreate")
        callConnectionService = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.v("onDestroy")
        callConnectionService = null
    }

    override fun onCreateOutgoingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        Timber.v("onCreateOutgoingConnection")

        val outgoingExtras = request.extras
        val callInfo = VoIPManager.getCallInfoFromExtras(outgoingExtras)

        return createNewCallConnection(outgoingExtras, callInfo)
    }

    override fun onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest) {
        Timber.e("onCreateOutgoingConnectionFailed")

        val outgoingExtras = request.extras
        val callInfo = VoIPManager.getCallInfoFromExtras(outgoingExtras)

        CallPresenterManager.getPresenter(callInfo.callId)?.endCall(DisconnectReason.LOCAL_ERROR)
    }

    override fun onCreateIncomingConnection(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest): Connection {
        Timber.v("onCreateIncomingConnection")

        val incomingExtras = request.extras.getParcelable<Bundle>(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)!!
        val callInfo = VoIPManager.getCallInfoFromExtras(incomingExtras)

        return createNewCallConnection(incomingExtras, callInfo)
    }

    override fun onCreateIncomingConnectionFailed(connectionManagerPhoneAccount: PhoneAccountHandle?, request: ConnectionRequest) {
        Timber.e("onCreateIncomingConnectionFailed")

        val incomingExtras = request.extras.getParcelable<Bundle>(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)!!
        val callInfo = VoIPManager.getCallInfoFromExtras(incomingExtras)

        CallPresenterManager.getPresenter(callInfo.callId)?.endCall(DisconnectReason.LOCAL_BUSY)
    }

    private fun createNewCallConnection(extras: Bundle?, callInfo: VoIPManager.CallInfo): Connection {
        val presenter = CallPresenterManager.getPresenter(callInfo.callId) ?: return Connection.createCanceledConnection()

        val connection = CallConnection(applicationContext, callInfo)
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.audioModeIsVoip = true
        connection.extras = extras
        connection.setCallerDisplayName(callInfo.displayName, TelecomManager.PRESENTATION_ALLOWED)

        connection.connectionCapabilities = connection.connectionCapabilities.or(Connection.CAPABILITY_MUTE)
        if (presenter.supportsOnHold) {
            connection.connectionCapabilities = connection.connectionCapabilities.or(Connection.CAPABILITY_HOLD).or(Connection.CAPABILITY_SUPPORT_HOLD)
        }

        presenter.onRegisterConnection(connection)

        return connection
    }


    companion object {

        var callConnectionService: CallConnectionService? = null

    }


    class CallConnection(val context: Context, val callInfo: VoIPManager.CallInfo): android.telecom.Connection() {

        override fun onShowIncomingCallUi() {
            Timber.v("callId %s onShowIncomingCallUi", callInfo.callId)
            val presenter = CallPresenterManager.getPresenter(callInfo.callId) ?: return
            TCNotificationManager.INSTANCE.showIncomingCallNotification(callInfo, presenter)
        }

        override fun onStateChanged(state: Int) {
            Timber.v("callId %s onStateChanged %d", callInfo.callId, state)
        }

        override fun onCallAudioStateChanged(state: CallAudioState?) {
            Timber.v("callId %s onCallAudioStateChanged: %s", callInfo.callId, state?.toString())
        }

        override fun onAnswer() {
            Timber.v("callId %s onAnswer", callInfo.callId)
            val intent = TTIntentUtils.getVoipCallIntent(context, callInfo)
            intent.putExtra(VoIPCallActivity.EXTRA_IS_ANSWERED, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        override fun onHold() {
            Timber.v("callId %s onHold", callInfo.callId)
            CallPresenterManager.getPresenter(callInfo.callId)?.onHold = true
        }

        override fun onUnhold() {
            Timber.v("callId %s onUnhold", callInfo.callId)
            CallPresenterManager.getPresenter(callInfo.callId)?.onHold = false
        }

        override fun onReject() {
            Timber.v("callId %s onReject", callInfo.callId)
            CallPresenterManager.endCall(callInfo, true, DisconnectReason.LOCAL_REJECTED)
        }

        override fun onDisconnect() {
            Timber.v("callId %s onDisconnect", callInfo.callId)
            CallPresenterManager.endCall(callInfo, true, DisconnectReason.LOCAL_ENDED)
        }

        override fun onAbort() {
            Timber.v("callId %s onAbort", callInfo.callId)
            CallPresenterManager.endCall(callInfo, true, DisconnectReason.LOCAL_ENDED)
        }

    }

}