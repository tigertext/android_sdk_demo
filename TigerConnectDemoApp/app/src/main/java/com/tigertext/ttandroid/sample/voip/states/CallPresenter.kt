package com.tigertext.ttandroid.sample.voip.states

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.service.CallConnectionService
import com.twilio.video.LocalVideoTrack

interface CallPresenter {

    fun startCall()

    /**
     * True when the user has accepted the call or initiated the call themselves
     */
    val isStarted: Boolean

    /**
     * Returns true if 2 or more parties are connected (passed the ringing step)
     */
    val isEstablished: Boolean

    /**
     * Returns the established time of the call. This can be after the call has started.
     * This should be 0 until isEstablished is true
     */
    val establishedTime: Long

    /**
     * True when the call is ended, either this user hung up or the call ended on the other side
     */
    val isEnded: Boolean

    var isMuted: Boolean
    var isVideoEnabled: Boolean
    val supportsOnHold: Boolean
    var onHold: Boolean

    fun endCall(disconnectReason: Int)

    var activity: VoIPCallActivity?
    fun initUiState()

    val proximityEnabledLiveData: LiveData<Boolean>
    val callTypeTextLiveData: LiveData<String>
    val callStateLiveData: LiveData<String>
    val callStatusLiveData: LiveData<Int>?
    /**
     * Returns ColorRes of text color, not the value
     */
    val callStateColorLiveData: LiveData<Int>
    val hideCallInfoLiveData: LiveData<Boolean>?
    val hideButtonsLiveData: LiveData<Boolean>?
    val localVideoTrackLiveData: LiveData<LocalVideoTrack?>?
    val otherRoleLiveData: LiveData<Role?>?
    val rosterEntryLiveData: LiveData<RosterEntry?>?

    fun getCallId(): String?
    fun getCallInfo(): VoIPManager.CallInfo

    @RequiresApi(Build.VERSION_CODES.O)
    fun onRegisterConnection(callConnection: CallConnectionService.CallConnection)

}