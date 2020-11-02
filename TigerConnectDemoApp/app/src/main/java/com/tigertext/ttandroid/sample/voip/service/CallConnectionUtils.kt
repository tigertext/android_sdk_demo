package com.tigertext.ttandroid.sample.voip.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.annotation.RequiresApi
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.VoIPManager
import timber.log.Timber
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
object CallConnectionUtils {

    private const val PHONE_ACCOUNT_ID_VOIP = "TigerConnect.Sample.VoIP"

    fun getPhoneAccountHandle(context: Context) = PhoneAccountHandle(ComponentName(context, CallConnectionService::class.java), PHONE_ACCOUNT_ID_VOIP)

    fun registerPhoneAccount(context: Context) {
        val handle = getPhoneAccountHandle(context)

        val phoneAccount = PhoneAccount.Builder(handle, context.getString(R.string.app_name))
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED or
                        PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                        PhoneAccount.CAPABILITY_VIDEO_CALLING)
                .build()

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        telecomManager.registerPhoneAccount(phoneAccount)
    }

    /**
     * @return true if successful, false if not successful
     * @note this should be called AFTER [com.tigertext.voip.states.CallPresenterManager] so that the presenter can receive the new connection if any
     */
    fun addOutgoingCall(context: Context, callInfo: VoIPManager.CallInfo): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        // Generate random sip uri to get poor telecom implementations to shut up about invalid number
        val uri = Uri.fromParts("sip", UUID.randomUUID().toString(), null)

        val passedExtras = Bundle()
        VoIPManager.convertCallInfoToExtras(callInfo, passedExtras)

        val outgoingExtras = Bundle()
        outgoingExtras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, passedExtras)
        outgoingExtras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getPhoneAccountHandle(context))

        return try {
            telecomManager.placeCall(uri, outgoingExtras)
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Error adding outgoing call to Telecom manager, is outgoing call allowed: %b isRegistered: %b", telecomManager.isOutgoingCallPermitted(getPhoneAccountHandle(context)), telecomManager.getPhoneAccount(getPhoneAccountHandle(context)) != null)
            false
        }
    }

    /**
     * @return true if successful, false if not successful
     * @note this should be called AFTER [com.tigertext.voip.states.CallPresenterManager] so that the presenter can receive the new connection if any
     */
    fun addIncomingCall(context: Context, callInfo: VoIPManager.CallInfo): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val passedExras = Bundle()
        VoIPManager.convertCallInfoToExtras(callInfo, passedExras)

        val incomingExtras = Bundle()
        incomingExtras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, passedExras)

        return try {
            telecomManager.addNewIncomingCall(getPhoneAccountHandle(context), incomingExtras)
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Error adding incoming call to Telecom manager, is incoming call allowed: %b isRegistered: %b", telecomManager.isIncomingCallPermitted(getPhoneAccountHandle(context)), telecomManager.getPhoneAccount(getPhoneAccountHandle(context)) != null)
            false
        }
    }

}
