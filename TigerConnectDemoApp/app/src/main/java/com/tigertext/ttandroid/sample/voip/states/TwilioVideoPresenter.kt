package com.tigertext.ttandroid.sample.voip.states

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telecom.CallAudioState
import android.util.AndroidException
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import bolts.Continuation
import bolts.Task
import com.google.android.material.snackbar.Snackbar
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.call.payload.*
import com.tigertext.ttandroid.http.models.request.call.*
import com.tigertext.ttandroid.http.models.response.call.StartVoipCallResponse
import com.tigertext.ttandroid.pubsub.TTEvent
import com.tigertext.ttandroid.pubsub.TTPubSub
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.application.TigerConnectApplication
import com.tigertext.ttandroid.sample.notification.TCNotificationManager
import com.tigertext.ttandroid.sample.utils.TTCallUtils
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.video.VoipParticipantView
import com.tigertext.ttandroid.settings.SettingType
import com.tigertext.ttandroid.sse.VoipCallHandler
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.details.VoipDetailsFragment
import com.tigertext.ttandroid.sample.voip.details.VoipDetailsViewModel
import com.tigertext.ttandroid.sample.voip.service.CallConnectionService
import com.tigertext.ttandroid.sample.voip.service.CallConnectionUtils
import com.tigertext.voip.video.DataTrackUtil
import com.tigertext.ttandroid.sample.voip.video.VoipParticipantAdapter
import com.tigertext.voip.video.VoipUIModel
import com.twilio.video.*
import kotlinx.android.synthetic.main.activity_voip_call.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import tvi.webrtc.MediaCodecVideoEncoder
import java.lang.Runnable
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class TwilioVideoPresenter(override var activity: VoIPCallActivity?, private val callInfo: VoIPManager.CallInfo) : CallPresenter, TTPubSub.Listener {

    private var localAudioTrack: LocalAudioTrack? = null
    private var localVideoTrack: LocalVideoTrack? = null
        set(value) {
            if (field != value) {
                field = value
                if (groupViewType == VoipUIModel.GROUP_VIEW_TYPE_P2P) {
                    localVideoTrackLiveData.value = value
                }
                onParticipantChanged(currentUserId)
            }
        }
    private var localDataTrack: LocalDataTrack? = null

    private var room: Room? = null
    private var connection: CallConnectionService.CallConnection? = null

    private var isSetUp = false
    override var isStarted = false
        private set
    override var isEstablished = false // Defined as "Other users have picked up (no longer ringing)"
        private set(value) {
            field = value
            if (value) establishedTime = System.currentTimeMillis()
        }
    override var isEnded = false
        private set

    override val callStatusLiveData = MutableLiveData<Int>()
    private val resetToConnected = Runnable { callStatusLiveData.value = VoIPCallActivity.ALREADY_CONNECTED }

    private val presenterScope = MainScope()
    private val appContext by lazy { TigerConnectApplication.getApp() }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val cameraManager by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        } else {
            throw IllegalAccessException("Can only use CameraManager on L+ devices")
        }
    }
    private var selectedCameraId: String? = null
    private val isFrontCameraAvailable by lazy { CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.FRONT_CAMERA) }
    private val isBackCameraAvailable by lazy { CameraCapturer.isSourceAvailable(CameraCapturer.CameraSource.BACK_CAMERA) }

    override val proximityEnabledLiveData = MutableLiveData(false)
    override val callTypeTextLiveData = MutableLiveData<String>()
    override val callStateLiveData = MutableLiveData<String>()

    override val callStateColorLiveData = MutableLiveData(R.color.attachment_preview_text)
    override val hideCallInfoLiveData = MutableLiveData(false)
    override val hideButtonsLiveData = MutableLiveData(false)
    override val localVideoTrackLiveData = MutableLiveData<LocalVideoTrack?>()
    override val otherRoleLiveData = MutableLiveData<Role?>()
    override val rosterEntryLiveData = MutableLiveData<RosterEntry?>()

    init {
        otherRoleLiveData.observeForever { TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this) }
        rosterEntryLiveData.observeForever {
            TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this)
        }
    }

    val currentUserId: String = TT.getInstance().accountManager.userId
    val userIds: MutableList<String> = ArrayList()
    val disconnectedUserIds: MutableList<String> = ArrayList()
    val disconnectedReasonMap: MutableMap<String, Int> = ArrayMap()
    val userIdToCallerMap: MutableMap<String, Participant> = ArrayMap()
    val participantMap: MutableMap<String, com.tigertext.ttandroid.group.Participant> = ArrayMap()
    val userIdToRoleIds: MutableMap<String, MutableSet<String>> = callInfo.roleIds ?: ArrayMap()

    var dominantSpeakerId: String? = null
        private set(value) {
            if (field != value) {
                val oldField = field
                field = value
                oldField?.let { onParticipantChanged(it) }
                value?.let { onParticipantChanged(it) }
            }
        }

    val patientId = MutableLiveData<String?>()

    private val userTimeoutRunnables = ArrayMap<String, UserTimeOutRunnable>()

    var groupViewType = VoipUIModel.GROUP_VIEW_TYPE_P2P
        private set(value) {
            if (field != value) {
                field = value
                if (localVideoTrack != null) {
                    if (value != VoipUIModel.GROUP_VIEW_TYPE_P2P) {
                        localVideoTrackLiveData.value = null
                    } else {
                        localVideoTrackLiveData.value = localVideoTrack
                    }
                }
                activity?.let {
                    VoipUIModel.setGroupViewType(it, this, value, userIds.size)
                }
            }
        }

    val voipParticipantAdapter = VoipParticipantAdapter(this)
    var voipDetailsViewModel: VoipDetailsViewModel? = null

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            if (toPosition + itemCount == userIds.size) {
                activity?.let {
                    rebindParticipant(it, userIds.last(), userIds.lastIndex, false)
                }
            }
        }
    }

    init {
        voipParticipantAdapter.registerAdapterDataObserver(adapterDataObserver)
    }

    var mirrorVideo = false
        set(value) {
            if (field != value) {
                field = value
                onParticipantChanged(currentUserId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    (activity?.localUserVideo as? VideoTextureView)?.mirror = value
                } else {
                    (activity?.localUserVideo as? VideoView)?.mirror = value
                }
            }
        }

    override var establishedTime = 0L

    private var warning: String? = null
        private set(value) {
            if (field != value) {
                field = value
                if (value.isNullOrEmpty()) {
                    callStateLiveData.value = null
                    callStateColorLiveData.value = R.color.attachment_preview_text
                    isCounterRunning = isEstablished && !isEnded

                } else {
                    isCounterRunning = false
                    callStateColorLiveData.value = R.color.voip_state_warning
                    callStateLiveData.value = value
                }
            }
        }

    private fun updateWarning() {
        when {
            isEnded -> warning = null
            onHold -> warning = appContext.getString(R.string.call_on_hold)
            VoipParticipantView.isMuted(userIdToCallerMap.values.firstOrNull { it.identity != currentUserId }) -> warning = appContext.getString(R.string.call_muted)
            else -> warning = null
        }
    }

    private class SecondsTickRunnable(presenter: TwilioVideoPresenter) : Runnable {
        private val weakPresenter = WeakReference(presenter)

        override fun run() {
            val presenter = weakPresenter.get() ?: return

            val millisInSecond = TimeUnit.SECONDS.toMillis(1L)
            val secondsInMinute = TimeUnit.MINUTES.toSeconds(1L)

            val callTime = (System.currentTimeMillis() - presenter.establishedTime) / millisInSecond
            val minutes = callTime / secondsInMinute
            val seconds = callTime % secondsInMinute

            presenter.callStateLiveData.value = presenter.appContext.getString(R.string.timer_secs_format, minutes, seconds)

            if (presenter.isCounterRunning) {
                presenter.mainHandler.postDelayed(this, millisInSecond)
            }
        }
    }

    private var ringingMediaPlayer: MediaPlayer? = null

    /**
     * Used for both incoming calls before accepting, and outgoing calls, waiting for users to pick up
     */
    private val timeoutRunnable = Runnable { endCall(DisconnectReason.LOCAL_TIMEOUT) }
    private val secondsTickRunnable = SecondsTickRunnable(this)

    var isCounterRunning: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    if (establishedTime == 0L) establishedTime = System.currentTimeMillis()
                    callStateColorLiveData.value = R.color.on_duty_text_color
                    secondsTickRunnable.run()
                } else {
                    callStateColorLiveData.value = R.color.attachment_preview_text
                    mainHandler.removeCallbacks(secondsTickRunnable)
                }
            }
        }

    private fun getCallTypeString(): String {
        var callType = appContext.getString(if (callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO) R.string.video_call_via_tt else R.string.audio_call_via_tigertext)
        if (isPatientCall()) callType = "${appContext.getString(R.string.patient)} $callType"
        return callType
    }

    init {
        callInfo.callId?.let { CallPresenterManager.registerPresenter(it, this) }
        TT.getInstance().ttPubSub.addListeners(this, *listeners)
        callTypeTextLiveData.value = getCallTypeString()

        presenterScope.launch {
            try {
                if (!TT.getInstance().organizationManager.isInitialized) {
                    withContext(Dispatchers.IO) { TT.getInstance().organizationManager.initOrganizationsManager() }
                }
                if (!callInfo.groupId.isNullOrEmpty()) {
                    val group = withContext(Dispatchers.IO) {
                        TT.getInstance().rosterManager.getGroupEntry(callInfo.groupId)
                    }
                    group?.let { group ->
                        rosterEntryLiveData.value = group
                        group.groupPatientInfo?.let { patientId.value = it.patientId }
                    }
                }

                val participantIds = ArraySet<String>()
                participantIds.add(currentUserId)
                participantIds.addAll(callInfo.recipientIds)
                callInfo.roleIds?.forEach { participantIds.addAll(it.value) }

                val participants = withContext(Dispatchers.IO) { TT.getInstance().userManager.getParticipantsSync(participantIds, callInfo.orgId) }

                participantMap.putAll(participants.associateBy { it.id })

                callInfo.roleIds?.let { userIdToRoleIds.putAll(it) }
                addOrUpdateParticipant(currentUserId, 0)
                for (participant in participants) {
                    if (participant is User) {
                        addOrUpdateParticipant(participant.id, 0)
                        participant.userPatientMetadata?.let { patientId.value = if (it.isPatientContact) it.patientId else participant.id }
                    }
                }

                if (callInfo.groupId.isNullOrEmpty()) {
                    val otherUserId = callInfo.callerId
                            ?: callInfo.recipientIds.first()
                    rosterEntryLiveData.value = participantMap[otherUserId] as? User
                    val otherRoleId = VoIPManager.getFirstRoleId(callInfo.roleIds, otherUserId)
                    if (!otherRoleId.isNullOrEmpty()) {
                        otherRoleLiveData.value = participantMap[otherRoleId] as? Role
                    }
                } else if (!callInfo.isOutgoing) {
                    val inviterId = callInfo.callerId
                    if (!inviterId.isNullOrEmpty()) {
                        val displayName = getDisplayNameByUserId(inviterId)
                        val type = appContext.getString(if (callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO) R.string.video_call else R.string.voice_call).toLowerCase(Locale.getDefault())
                        callStateLiveData.value = appContext.getString(R.string.name_wants_you_to_join_a_group_type_call, displayName, type)
                    }
                }
                callTypeTextLiveData.value = getCallTypeString()
            } catch (e: Exception) {
                Timber.e(e, "Error with init load")
            }

            isSetUp = true
            if (!callInfo.isOutgoing) { // 1 Minute to accept call
                mainHandler.postDelayed(timeoutRunnable, TimeUnit.MINUTES.toMillis(1L))
            }

            if (isStarted) {
                isStarted = false
                startCall()
            }
        }
    }

    override fun initUiState() {
        val activity = activity ?: run {
            Timber.e("Activity is null when initUiState is called")
            return
        }

        VoipUIModel.setupIcons(activity)

        if (callInfo.isOutgoing) {
            VoipUIModel.callingTransition(activity)
        }

        if (isStarted) {
            updateUiState()
        }
    }

    override fun getCallId(): String? = callInfo.callId
    override fun getCallInfo(): VoIPManager.CallInfo = callInfo

    override fun startCall() {
        if (isStarted || isEnded) return
        isStarted = true
        mainHandler.removeCallbacks(timeoutRunnable)

        if (!isSetUp) return // Will retry when finished setting up

        CallPresenterManager.activePresenter = this

        VoIPManager.configureAudio(true)
        callStateLiveData.value = appContext.getString(R.string.connecting)
        updateUiState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Camera2Capturer.isSupported(appContext)) {
            cameraManager.registerAvailabilityCallback(availabilityCallback, mainHandler)
        }

        if (callInfo.isOutgoing) {
            startVoipCall()
        } else {
            joinVoipCall()
        }

        onMemberCountChanged()
    }

    private fun startVoipCall() {
        Task.callInBackground {
            val orgId = callInfo.orgId
            val recipients = userIds.filter { it != currentUserId }

            val callPayload = VoIPManager.generateVideoJsonPayload(callInfo, callInfo.groupId, userIds, userIdToRoleIds, false, patientId.value, participantMap, userIdToCallerMap.keys)
            val callContext = callInfo.groupId?.let { StartVoipCallRequest.CallContext(it) }
            val response = if (!callInfo.phoneNumber.isNullOrEmpty()) {
                TT.getInstance().callManager.startQuickVoipCall(StartQuickVoipCallRequest(orgId, setOf(callInfo.phoneNumber), callContext, callPayload))
            } else {
                TT.getInstance().callManager.startVoipCall(StartVoipCallRequest(orgId, recipients, callContext, callPayload))
            }
            return@callInBackground response
        }.continueWith(Continuation<StartVoipCallResponse, Void> { task ->
            if (task.isFaulted) {
                Timber.e(task.error, "Error starting call")
                endCall(DisconnectReason.LOCAL_ERROR)
            } else {
                if (isEnded) return@Continuation null

                val roomName = task.result.roomName
                val accessToken = task.result.accessToken

                task.result.phoneToRecipients?.let {
                    for (userId in it.values) {
                        addOrUpdateParticipant(userId, 0)
                    }
                }

                task.result.disabledParticipants?.let {
                    if (it.isEmpty()) return@let
                    for (userId in it.keys) {
                        removeParticipant(userId, DisconnectReason.REMOTE_ERROR)
                    }
                    presenterScope.launch(Dispatchers.IO) {
                        val callId = callInfo.localId ?: roomName
                        val payload = DisabledParticipantsPayload(it)
                        val response = kotlin.runCatching { TT.getInstance().callManager.updateVoipCall(roomName, UpdateVoipCallRequest(callId, payload)) }
                        response.exceptionOrNull()?.let { e -> Timber.e(e, "Disabled Participants: Update error") } ?: Timber.d("Disabled Participants: Update success")
                    }
                }

                callInfo.callId = roomName

                CallPresenterManager.registerPresenter(roomName, this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    CallConnectionUtils.addOutgoingCall(appContext, callInfo)
                }

                // TODO: Wait for outgoing call to finish?
                connectToRoom(roomName, accessToken, callInfo)
            }
            null
        }, Task.UI_THREAD_EXECUTOR).continueWith({
            if (it.isFaulted) {
                Timber.e(it.error, "Error establishing call")
                endCall(DisconnectReason.LOCAL_ERROR)
            }
            null
        }, Task.UI_THREAD_EXECUTOR)
    }

    private fun joinVoipCall() {
        Task.callInBackground {
            val roomName = callInfo.callId
                    ?: throw IllegalArgumentException("Call id must not be null")
            return@callInBackground TT.getInstance().callManager.joinVoipCall(roomName)
        }.continueWith({
            if (it.isFaulted) {
                Timber.e(it.error, "Error joining call")
                endCall(DisconnectReason.LOCAL_ERROR)
            } else {
                val roomName = callInfo.callId ?: ""
                val callId = callInfo.localId ?: roomName
                val payload = AnswerPayload(currentUserId)

                presenterScope.launch(Dispatchers.IO) {
                    val response = kotlin.runCatching { TT.getInstance().callManager.answerVoipCall(roomName, AnswerVoipCallRequest(callId = callId, payload = payload)) }
                    response.exceptionOrNull()?.let { e -> Timber.e(e, "Answer voip call error") } ?: Timber.d("Answer voip call success")
                }
                connectToRoom(roomName, it.result.accessToken, callInfo)
            }
            null

        }, Task.UI_THREAD_EXECUTOR).continueWith({
            if (it.isFaulted) {
                Timber.e(it.error, "Error answering call")
                endCall(DisconnectReason.LOCAL_ERROR)
            }
            null
        }, Task.UI_THREAD_EXECUTOR)
    }

    override var isMuted: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                localAudioTrack?.enable(!isMuted)
                onParticipantChanged(currentUserId)
                activity?.muteCall?.active = isMuted
            }
        }

    override var isVideoEnabled: Boolean = callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO
        set(value) {
            if (field != value) {
                field = value
                if (field) {
                    createLocalVideoTrack()
                } else {
                    destroyLocalVideoTrack()
                }
                activity?.videoCall?.active = !field
            }
        }

    override val supportsOnHold = true

    override var onHold: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (isEnded) return
                room?.remoteParticipants?.forEach { setRemoteParticipantPlaybackEnabled(it, !value) }
                if (field) {
                    localAudioTrack?.enable(false)
                    destroyLocalVideoTrack()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        connection?.setOnHold()
                    }
                    if (this == CallPresenterManager.activePresenter) CallPresenterManager.activePresenter = null
                } else {
                    localAudioTrack?.enable(!isMuted)
                    if (isVideoEnabled) createLocalVideoTrack()
                    VoIPManager.setSpeakerphone(activity?.speakerCall?.active == true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        connection?.setActive()
                        connection?.setAudioRoute(if (activity?.speakerCall?.active == true) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
                    }
                }
                updateWarning()
                TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this)
            }
        }

    override fun endCall(disconnectReason: Int) {
        // OneShot the end logic
        if (isEnded) return
        isEnded = true

        VoIPManager.configureAudio(false)
        VoIPManager.resetToLastVolume()

        updateWarning()
        isCounterRunning = false
        ringingMediaPlayer?.release()
        ringingMediaPlayer = null
        for (runnable in userTimeoutRunnables.values) {
            mainHandler.removeCallbacks(runnable)
        }
        userTimeoutRunnables.clear()
        VoIPCallActivity.showDisconnectReasonAndFinish(activity, disconnectReason)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            connection?.setDisconnected(DisconnectReason.convertDisconnectReasonToDisconnectCause(disconnectReason))
            connection?.destroy()
        }
        TCNotificationManager.INSTANCE.removeOngoingCallNotification(callInfo.callId)

        room?.disconnect()
        room = null
        localAudioTrack?.release()
        localAudioTrack = null
        localVideoTrack?.release()
        localVideoTrack = null
        localDataTrack?.release()
        localDataTrack = null

        updateHideCallInfo()
        proximityEnabledLiveData.value = false
        voipDetailsViewModel?.closeLiveData?.value = true

        presenterScope.cancel("Call is ended")
        val roomName = callInfo.callId ?: return
        CallPresenterManager.unregisterPresenter(roomName)
        TT.getInstance().ttPubSub.removeListeners(this, *listeners)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Camera2Capturer.isSupported(appContext)) {
            cameraManager.unregisterAvailabilityCallback(availabilityCallback)
        }

        val reason = DisconnectReason.convertDisconnectReasonToTTReason(disconnectReason) ?: return
        val callId = callInfo.localId ?: roomName
        val payload = EndCallPayload(currentUserId)

        if (!isStarted && (disconnectReason == DisconnectReason.REMOTE_ENDED ||
                        disconnectReason == DisconnectReason.LOCAL_TIMEOUT)) {
            TCNotificationManager.INSTANCE.showMissedVoIPCallNotification(callInfo, rosterEntryLiveData.value)
        }

        GlobalScope.launch(Dispatchers.IO) { // Global because presenterScope is already cancelled
            val response = kotlin.runCatching { TT.getInstance().callManager.endVoipCall(roomName, EndVoipCallRequest(reason, callId, payload)) }
            response.exceptionOrNull()?.let { Timber.e(it, "endVoipCall failure") } ?: Timber.d("endVoipCall success")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRegisterConnection(callConnection: CallConnectionService.CallConnection) {
        Timber.v("onRegisterConnection")
        connection = callConnection
        if (isEnded) {
            callConnection.setDisconnected(DisconnectReason.convertDisconnectReasonToDisconnectCause(DisconnectReason.LOCAL_ENDED))
            callConnection.destroy()
        } else if (isEstablished) {
            callConnection.setActive()
        } else if (isStarted) {
            if (callInfo.isOutgoing) {
                callConnection.setDialing()
            } else {
                callConnection.setRinging()
            }
        }
    }

    private fun connectToRoom(roomName: String, accessToken: String, callInfo: VoIPManager.CallInfo) {
        if (isEnded) return

        localAudioTrack = LocalAudioTrack.create(appContext, !isMuted)
        if (isVideoEnabled) createLocalVideoTrack()
        localDataTrack = LocalDataTrack.create(appContext)

        bindLocalVideo()

        val builder = ConnectOptions.Builder(accessToken)
                .roomName(roomName)
                .enableDominantSpeaker(true)
                .enableNetworkQuality(true)
                .networkQualityConfiguration(NetworkQualityConfiguration(
                        NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_MINIMAL,
                        NetworkQualityVerbosity.NETWORK_QUALITY_VERBOSITY_NONE))

        localAudioTrack?.let { builder.audioTracks(listOf(it)) }
        localVideoTrack?.let { builder.videoTracks(listOf(it)) }
        localDataTrack?.let { builder.dataTracks(listOf(it)) }

        room = Video.connect(appContext, builder.build(), roomListener)
        TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this)
    }

    private fun createLocalVideoTrack() {
        if (isEnded || localVideoTrack != null) return

        try {
            var videoCapturer: VideoCapturer? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Camera2Capturer.isSupported(appContext)) {

                if (selectedCameraId.isNullOrEmpty()) {
                    val cameraIdList = cameraManager.cameraIdList
                    try {
                        for (cameraId in cameraIdList) {
                            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                            if (characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT) {
                                selectedCameraId = cameraId
                                mirrorVideo = true
                                break
                            }
                        }
                    } catch (e: AndroidException) {
                        Timber.e(e, "Error finding camera id")
                    }
                    if (selectedCameraId.isNullOrEmpty()) {
                        selectedCameraId = cameraIdList.firstOrNull()
                    }
                }
                if (!selectedCameraId.isNullOrEmpty()) {
                    videoCapturer = Camera2Capturer(appContext, selectedCameraId!!, camera2Listener!!)
                }
            }

            if (videoCapturer == null) {
                videoCapturer = when {
                    isFrontCameraAvailable -> CameraCapturer(appContext, CameraCapturer.CameraSource.FRONT_CAMERA, cameraListener)
                    isBackCameraAvailable -> CameraCapturer(appContext, CameraCapturer.CameraSource.BACK_CAMERA, cameraListener)
                    else -> null
                }
                mirrorVideo = isFrontCameraAvailable
            }

            if (videoCapturer != null) {
                val constraints = VideoConstraints.Builder()
                        .maxFps(VideoConstraints.FPS_30)
                        .maxVideoDimensions(VideoDimensions.HD_1080P_VIDEO_DIMENSIONS)
                if (Build.MODEL.startsWith("Pixel 3")) MediaCodecVideoEncoder.disableVp8HwCodec()

                val track = LocalVideoTrack.create(appContext, true, videoCapturer, constraints.build(), null)
                if (track != null) {
                    room?.localParticipant?.publishTrack(track)
                }
                localVideoTrack = track
            }
        } catch (e: Exception) {
            Timber.e(e, "Couldn't initialize local video track")
        }

        if (localVideoTrack == null) {
            isVideoEnabled = false
        }
    }

    private fun updateUiState() {
        val activity = activity ?: run {
            Timber.e("Activity is null when updateUiState is called")
            return
        }

        activity.acceptCall.visibility = View.GONE

        if (callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO) {
            VoipUIModel.setupVideoState(activity)
        } else {
            VoipUIModel.callingTransition(activity)
        }

        proximityEnabledLiveData.value = callInfo.callType == VoIPCallActivity.CALL_TYPE_AUDIO && !VoIPManager.isSpeakerphoneOn()

        activity.speakerCall.active = callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO || VoIPManager.isSpeakerphoneOn()
        activity.speakerCall.setOnClickListener {
            activity.speakerCall.toggleState()
            VoIPManager.setSpeakerphone(activity.speakerCall.active)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connection?.setAudioRoute(if (activity.speakerCall.active) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
            }
            proximityEnabledLiveData.value = !activity.speakerCall.active
        }
        activity.switchCamera.setOnClickListener {
            activity.switchCamera.toggleState()
            val videoCapturer = localVideoTrack?.videoCapturer
            if (videoCapturer is CameraCapturer) {
                videoCapturer.switchCamera()
            } else if (videoCapturer is Camera2Capturer) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val nextCameraId = getNextCameraId(cameraManager, videoCapturer.cameraId)
                    if (!nextCameraId.isNullOrEmpty()) {
                        selectedCameraId = nextCameraId
                        videoCapturer.switchCamera(nextCameraId)
                    }
                }
            }
        }
        activity.participants.visibility = if (groupViewType != VoipUIModel.GROUP_VIEW_TYPE_P2P || isInviteParticipantAllowed()) getButtonVisibility() else View.GONE
        activity.participants.setOnClickListener { openParticipantsView(activity) }
        if (callInfo.callType == VoIPCallActivity.CALL_TYPE_AUDIO) {
            VoipUIModel.setupBoostVolumeButton(activity)
        } else {
            VoIPManager.setSpeakerphone(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connection?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            }
        }

        activity.muteCall.active = isMuted
        bindLocalVideo()

        VoipUIModel.setGroupViewType(activity, this, groupViewType, userIds.size)

        activity.caller_grid.setOnClickListener {
            mainHandler.removeCallbacks(hideButtonsRunnable)

            if (hideButtonsLiveData.value == true) {
                hideButtonsLiveData.value = false
                mainHandler.postDelayed(hideButtonsRunnable, DELAY_HIDE_BUTTONS)
            } else {
                hideButtonsLiveData.value = true
            }
        }
    }

    private fun openParticipantsView(activity: VoIPCallActivity) {
        if (activity.supportFragmentManager.isStateSaved) return
        val callId = callInfo.callId ?: return

        val fragment = VoipDetailsFragment()
        fragment.arguments = VoipDetailsFragment.getArguments(callId, VoipDetailsFragment.State.VIEW)
        fragment.show(activity.supportFragmentManager, VoipDetailsFragment.TAG)
    }

    private val hideButtonsRunnable = Runnable {
        if (hideCallInfoLiveData.value != true) return@Runnable
        hideButtonsLiveData.value = true
    }

    private fun bindLocalVideo() {
        val activity = activity ?: kotlin.run {
            Timber.e("Activity is null in bindLocalVideo")
            return
        }

        if (!isFrontCameraAvailable || !isBackCameraAvailable) {
            activity.switchCamera.visibility = View.GONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (activity.localUserVideo as VideoTextureView).mirror = mirrorVideo
        } else {
            (activity.localUserVideo as VideoView).mirror = mirrorVideo
        }
        activity.videoCall.active = !isVideoEnabled
    }

    fun isPatientCall() = !patientId.value.isNullOrEmpty()

    fun isInviteParticipantAllowed(): Boolean {
        if (!isStarted || isEnded) return false
        val org = TT.getInstance().organizationManager.getOrganization(callInfo.orgId)
                ?: return false
        val isVideo = callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO

        return if (isPatientCall()) {
            org.settings[if (isVideo) SettingType.PF_GROUP_VIDEO_CALL else SettingType.PF_GROUP_AUDIO_CALL]?.value == true
        } else {
            if (isVideo) {
                TTCallUtils.isCapableOfGroupVoipVideo(org)
            } else {
                TTCallUtils.isCapableOfGroupVoipAudio(org)
            }
        }
    }

    fun isGroupCallFull() = if (isPatientCall()) userIds.size >= PARTICIPANTS_PATIENTS_MAX else userIds.size >= PARTICIPANTS_MAX

    /**
     * @param patientIdName Used for iOS in invite payload, can be either "p_id" for patient, "pc_id" for patient contact, or null for regular user
     */
    fun inviteParticipant(userId: String, roleId: String?, patientIdName: String? = null) {
        val roomName = callInfo.callId ?: return

        if (!isInviteParticipantAllowed() || isGroupCallFull()) {
            val maxParticipants = if (isPatientCall()) PARTICIPANTS_PATIENTS_MAX else PARTICIPANTS_MAX
            Toast.makeText(appContext, appContext.getString(R.string.call_limit_reached, maxParticipants), Toast.LENGTH_SHORT).show()
            return
        }

        presenterScope.launch {
            val payload = VoIPManager.generateVideoJsonPayload(callInfo, null, userIds, userIdToRoleIds, true, patientId.value, participantMap, userIdToCallerMap.keys)
            val addResponse = withContext(Dispatchers.IO) { kotlin.runCatching { TT.getInstance().callManager.addParticipantsToVoipCall(roomName, AddParticipantsToVoipCallRequest(listOf(userId), payload)) } }
            if (addResponse.isSuccess) {
                if (isEnded) return@launch
                addOrUpdateParticipant(userId, 0)
                val callId = callInfo.localId ?: roomName

                roleId?.let {
                    if (!participantMap.containsKey(it)) {
                        getParticipants(setOf(it), callInfo.orgId, 0)
                    }
                    val existingSet = userIdToRoleIds[userId]
                    if (existingSet != null) {
                        existingSet.add(it)
                    } else {
                        userIdToRoleIds[userId] = mutableSetOf(it)
                    }
                }

                val patientId = if (patientIdName == "p_id") userId else null
                val contactId = if (patientIdName == "pc_id") userId else null
                val updatePayload = AddParticipantsPayload(currentUserId, callInfo.localId ?: "", listOf(userId), userIdToRoleIds, patientId, contactId)
                presenterScope.launch(Dispatchers.IO) {
                    val updateResponse = kotlin.runCatching { TT.getInstance().callManager.updateVoipCall(roomName, UpdateVoipCallRequest(callId, updatePayload)) }
                    updateResponse.exceptionOrNull()?.let { Timber.e(it, "updateVoipCall failure") } ?: Timber.d("updateVoipCall success")
                }
            } else {
                Timber.e(addResponse.exceptionOrNull(), "Invite error")
            }
        }
    }

    private fun addOrUpdateParticipant(userId: String, @StringRes stringRes: Int) {
        var index = userIds.indexOf(userId)

        if (index < 0) {
            index = userIds.size
            userIds.add(index, userId)
            onMemberCountChanged()
            voipParticipantAdapter.notifyItemInserted(index)
            disconnectedReasonMap.remove(userId)
            disconnectedUserIds.remove(userId)
            voipDetailsViewModel?.onUserChanged(userId)
            updateUserTimeoutRunnable(userId)
            activity?.let {
                rebindParticipant(it, userId, index, true)
                VoipUIModel.resetGridParams(it, userIds.size)
            }
        } else {
            onParticipantChanged(userId)
        }

        val user = participantMap[userId]
        if (user == null) {
            getParticipants(setOf(userId), callInfo.orgId, stringRes)
        } else if (user is User) {
            showParticipantSnackbar(activity, user, stringRes)
        }
    }

    private fun showParticipantSnackbar(activity: VoIPCallActivity?, user: User, @StringRes stringRes: Int) {
        if (activity == null || stringRes == 0 || isEnded) return
        val displayName = getDisplayNameByUserId(user.id) ?: user.displayName
        val snackbarText = activity.getString(stringRes, displayName)
        Snackbar.make(activity.voipCallRootView, snackbarText, Snackbar.LENGTH_SHORT).show()
    }

    private fun getParticipantViewForIndex(grid: GridLayout, index: Int, size: Int, addIfNeeded: Boolean): VoipParticipantView? {
        val gridIndex = size - 1 - index // Grid is backwards from list
        val gridSize = if (groupViewType == VoipUIModel.GROUP_VIEW_TYPE_GRID) size else 1

        val participantView: VoipParticipantView
        if (gridIndex >= grid.childCount || gridSize > grid.childCount) {
            if (!addIfNeeded) return null
            participantView = VoipParticipantView(grid.context)
            participantView.setBackgroundResource(R.drawable.voip_participant_background)
            val params = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1, 1f))
            params.width = 0
            params.height = 0
            grid.addView(participantView, gridIndex, params)
        } else {
            participantView = grid.getChildAt(gridIndex) as VoipParticipantView
        }
        return participantView
    }

    private fun onParticipantChanged(userId: String) {
        val index = userIds.indexOf(userId)
        if (index >= 0) {
            voipParticipantAdapter.notifyItemChanged(index)
            updateHideCallInfo()
            activity?.let {
                rebindParticipant(it, userId, index, false)
            }
            updateWarning()
        }
        voipDetailsViewModel?.onUserChanged(userId)
    }

    fun rebindParticipant(activity: VoIPCallActivity, userId: String, index: Int, addIfNeeded: Boolean) {
        if (groupViewType != VoipUIModel.GROUP_VIEW_TYPE_GRID && (index != userIds.lastIndex || userId == currentUserId)) return

        if (index >= 0) {
            val grid = activity.caller_grid as GridLayout
            val participantView: VoipParticipantView = getParticipantViewForIndex(grid, index, userIds.size, addIfNeeded)
                    ?: return
            if (addIfNeeded || participantView.visibility == View.VISIBLE) {
                participantView.visibility = View.VISIBLE
                participantView.bind(this, userId)
            }
        }
    }

    fun removeParticipant(userId: String, disconnectReason: Int) {
        if (userId == currentUserId) {
            endCall(disconnectReason)
            return
        }
        val index = userIds.indexOf(userId)
        if (index > -1) {
            userIds.removeAt(index)
            voipParticipantAdapter.notifyItemRemoved(index)
            disconnectedReasonMap[userId] = disconnectReason
            val disconnectedIndex = disconnectedUserIds.indexOf(userId)
            if (disconnectedIndex >= 0) {
                Timber.e("userId: %s was in both lists when removed", userId)
            } else {
                disconnectedUserIds.add(userId)
                voipDetailsViewModel?.onUserChanged(userId)
            }

            if (userIds.size <= 1) {
                endCall(disconnectReason)
            } else {
                (participantMap[userId] as? User)?.let { showParticipantSnackbar(activity, it, DisconnectReason.convertDisconnectReasonToStringRes(disconnectReason)) }
            }

            activity?.let {
                val grid = it.caller_grid as GridLayout
                val participantView: VoipParticipantView? = getParticipantViewForIndex(grid, index, userIds.size + 1, false)
                if (participantView != null && participantView.visibility == View.VISIBLE) {
                    participantView.unbind()
                    participantView.visibility = View.GONE
                    val gridIndex = grid.indexOfChild(participantView)
                    if (gridIndex > 0) {
                        grid.removeView(participantView)
//                    grid.addView(participantView, 0) // Move all gone views to front for reuse
                    } else if (gridIndex == 0 && groupViewType == VoipUIModel.GROUP_VIEW_TYPE_LIST) {
                        rebindParticipant(it, userIds.last(), userIds.lastIndex, true)
                    }
                    VoipUIModel.resetGridParams(it, userIds.size)
                }
            }
            onMemberCountChanged()
        }
    }

    private fun updateHideCallInfo() {
        val hideCallInfo = if (!isStarted || isEnded) {
            false
        } else {
            groupViewType != VoipUIModel.GROUP_VIEW_TYPE_P2P ||
                    userIdToCallerMap.values.firstOrNull { it is RemoteParticipant && VoipParticipantView.getVideoTrack(it) != null } != null
        }
        if (hideCallInfoLiveData.value != hideCallInfo) hideCallInfoLiveData.value = hideCallInfo
        if (!hideCallInfo) {
            if (hideButtonsLiveData.value != hideCallInfo) hideButtonsLiveData.value = hideCallInfo
        } else {
            mainHandler.postDelayed(hideButtonsRunnable, DELAY_HIDE_BUTTONS)
        }
    }

    private fun onMemberCountChanged() {
        groupViewType = when {
            !isStarted -> VoipUIModel.GROUP_VIEW_TYPE_P2P
            userIds.size <= 2 -> VoipUIModel.GROUP_VIEW_TYPE_P2P
            userIds.size >= 7 -> VoipUIModel.GROUP_VIEW_TYPE_LIST
            else -> VoipUIModel.GROUP_VIEW_TYPE_GRID
        }
        updateHideCallInfo()
        activity?.let {
            it.participants.visibility = if (groupViewType != VoipUIModel.GROUP_VIEW_TYPE_P2P || isInviteParticipantAllowed()) getButtonVisibility() else View.GONE
        }

        if (callInfo.groupId.isNullOrEmpty() && !userIds.contains(callInfo.callerId)) {
            val newCallerId = userIds.firstOrNull { it != currentUserId }
            if (!newCallerId.isNullOrEmpty()) {
                callInfo.callerId = newCallerId
                rosterEntryLiveData.value = participantMap[newCallerId] as? User
                otherRoleLiveData.value = participantMap[VoIPManager.getFirstRoleId(userIdToRoleIds, newCallerId)] as? Role
            }
        }
    }

    private fun getButtonVisibility() = if (hideButtonsLiveData.value == true) View.INVISIBLE else View.VISIBLE

    fun clearVideoRenderers() {
        for (participant in userIdToCallerMap.values) {
            for (trackPublication in participant.videoTracks) {
                val videoTrack = trackPublication.videoTrack ?: continue
                for (renderer in videoTrack.renderers) {
                    videoTrack.removeRenderer(renderer)
                }
            }
        }
    }

    fun onCallUpdate(voipCallData: VoipCallHandler.VoipCallData) {
        when ((voipCallData.payload as CallStatePayload).callState) {
            CallStatePayload.State.participants_updated -> {
                val payload = voipCallData.payload as AddParticipantsPayload
                val roleTokens = payload.roleIds
                if (roleTokens != null) {
                    val roleMap = roleTokens
                    val rolesToFetch = ArraySet<String>()
                    for (entry in roleMap.entries) {
                        val existingMap = userIdToRoleIds[entry.key]
                        if (existingMap != null) {
                            existingMap.addAll(entry.value)
                        } else {
                            userIdToRoleIds[entry.key] = entry.value.toMutableSet()
                        }
                        for (roleId in entry.value) {
                            if (!participantMap.containsKey(roleId)) {
                                rolesToFetch.add(roleId)
                            }
                        }
                    }
                    if (rolesToFetch.isNotEmpty()) {
                        getParticipants(rolesToFetch, callInfo.orgId, 0)
                    }
                }
                val newParticipants = payload.newParticipantIds
                for (userId in newParticipants) {
                    addOrUpdateParticipant(userId, 0)
                }
            }
            CallStatePayload.State.disabled_participants -> {
                val disabledPayload = voipCallData.payload as DisabledParticipantsPayload
                for (userId in disabledPayload.disabledParticipants.keys) {
                    removeParticipant(userId, DisconnectReason.REMOTE_ERROR)
                }
            }
        }
    }

    private fun getDisplayNameByUserId(userId: String): String? {
        var displayName = participantMap[userId]?.displayName
        val roleName = participantMap[VoIPManager.getFirstRoleId(userIdToRoleIds, userId)]?.displayName
        if (!displayName.isNullOrEmpty() && !roleName.isNullOrEmpty()) {
            displayName = appContext.getString(R.string.join_string_parenthesis_string, roleName, displayName)
        } else if (!roleName.isNullOrEmpty()) {
            displayName = roleName
        }
        return displayName
    }

    private fun getParticipants(participantIds: Collection<String>, orgId: String, @StringRes stringRes: Int) {
        presenterScope.launch {
            try {
                val participants = withContext(Dispatchers.IO) { TT.getInstance().userManager.getParticipantsSync(participantIds, orgId) }

                participantMap.putAll(participants.associateBy { it.id })

                for (participant in participants) {
                    if (participant is User) {
                        onParticipantChanged(participant.id)
                        showParticipantSnackbar(activity, participant, stringRes)
                        if (rosterEntryLiveData.value == participant) {
                            rosterEntryLiveData.value = participant
                        }
                        participant.userPatientMetadata?.let { patientId.value = if (it.isPatientContact) it.patientId else participant.id }
                    } else if (participant is Role) {
                        for (entry in userIdToRoleIds.entries) {
                            if (entry.value.contains(participant.id)) {
                                onParticipantChanged(entry.key)
                            }
                        }
                        if (otherRoleLiveData.value == participant) {
                            otherRoleLiveData.value = participant
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Track, Error getting users")
            }
        }
    }

    private fun updateUserTimeoutRunnable(userId: String) {
        if (userIdToCallerMap[userId] != null) {
            userTimeoutRunnables.remove(userId)?.let { mainHandler.removeCallbacks(it) }
            return
        }

        val participant = participantMap[userId]
        val ttlMillis = if (participant is User && participant.userPatientMetadata != null) {
            val ttl = TT.getInstance().organizationManager.getOrganization(participant.orgId)?.patientVideoCallTtl?.toLong()
                    ?: 60L
            if (ttl == -1L) { // unlimited
                userTimeoutRunnables.remove(userId)?.let { mainHandler.removeCallbacks(it) }
                return
            } else {
                TimeUnit.SECONDS.toMillis(ttl)
            }
        } else TimeUnit.MINUTES.toMillis(1L)

        var runnable = userTimeoutRunnables[userId]
        if (runnable != null && runnable.ttlMillis == ttlMillis) return

        runnable = UserTimeOutRunnable(this, userId, ttlMillis, runnable?.startTime
                ?: SystemClock.uptimeMillis())
        userTimeoutRunnables[userId] = runnable
        mainHandler.postAtTime(runnable, runnable.startTime + ttlMillis)
    }

    private class UserTimeOutRunnable(presenter: TwilioVideoPresenter, private val userId: String, var ttlMillis: Long, var startTime: Long) : Runnable {

        private var weakVoipCallPresenter = WeakReference(presenter)

        override fun run() {
            val presenter = weakVoipCallPresenter.get() ?: return

            presenter.userTimeoutRunnables.remove(userId)
            if (presenter.userIdToCallerMap[userId] == null) {
                presenter.removeParticipant(userId, DisconnectReason.LOCAL_TIMEOUT)
            } else {
                Timber.w("UserTimeOutRunnable - user already connected, might be missing a remove runnable call")
            }
        }

    }

    private val roomListener = object : Room.Listener {
        override fun onConnected(room: Room) {
            callStatusLiveData.value = VoIPCallActivity.ALREADY_CONNECTED
            Timber.d("onConnected")
            if (isEnded) return

            room.localParticipant?.let {
                it.setListener(localParticipantListener)
                userIdToCallerMap[currentUserId] = it
                addOrUpdateParticipant(currentUserId, 0)
            }

            room.remoteParticipants.forEach {
                it.setListener(participantListener)
                userIdToCallerMap[it.identity] = it
                addOrUpdateParticipant(it.identity, 0)
                setRemoteParticipantPlaybackEnabled(it, !onHold)
            }

            for (userId in userIds) {
                updateUserTimeoutRunnable(userId)
            }

            if (callInfo.isOutgoing && room.remoteParticipants.isEmpty()) {
                callStateLiveData.value = appContext.getString(if (isPatientCall()) R.string.waiting_for_participants_to_join else R.string.ringing)
                ringingMediaPlayer = VoIPManager.getRingMediaPlayer(appContext)
                ringingMediaPlayer?.start()
                if (callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO) {
                    VoIPManager.setSpeakerphone(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        connection?.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                    }
                }
            } else {
                isEstablished = true
                TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this@TwilioVideoPresenter)
                isCounterRunning = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                connection?.setActive()
            }
        }

        override fun onConnectFailure(room: Room, twilioException: TwilioException) {
            Timber.e(twilioException, "onConnectFailure")
            endCall(DisconnectReason.LOCAL_ERROR)
        }

        override fun onDisconnected(room: Room, twilioException: TwilioException?) {
            if (twilioException != null) {
                Timber.e(twilioException, "onDisconnected")
            } else {
                Timber.d("onDisconnected")
            }

            endCall(DisconnectReason.LOCAL_ERROR)
        }

        override fun onRecordingStarted(room: Room) {
            Timber.d("onRecordingStarted")
        }

        override fun onRecordingStopped(room: Room) {
            Timber.d("onRecordingStopped")
        }

        override fun onReconnected(room: Room) {
            Timber.d("onReconnected")
            callStatusLiveData.value = VoIPCallActivity.RECONNECTED
            mainHandler.postDelayed(resetToConnected, 2000)

        }

        override fun onReconnecting(room: Room, twilioException: TwilioException) {
            Timber.d("onReconnecting")
            mainHandler.removeCallbacks(resetToConnected)
            callStatusLiveData.value = VoIPCallActivity.RECONNECTING
        }

        override fun onParticipantConnected(room: Room, remoteParticipant: RemoteParticipant) {
            val userId = remoteParticipant.identity
            Timber.d("onParticipantConnected %s", userId)

            remoteParticipant.setListener(participantListener)
            userIdToCallerMap[userId] = remoteParticipant
            addOrUpdateParticipant(userId, R.string.name_joined_the_call)
            setRemoteParticipantPlaybackEnabled(remoteParticipant, !onHold)
            userTimeoutRunnables.remove(userId)?.let { mainHandler.removeCallbacks(it) }

            if (!isEstablished) {
                isEstablished = true
                TCNotificationManager.INSTANCE.updateCallNotification(callInfo, this@TwilioVideoPresenter)
                ringingMediaPlayer?.release()
                ringingMediaPlayer = null
                isCounterRunning = true
            }
        }

        override fun onParticipantDisconnected(room: Room, remoteParticipant: RemoteParticipant) {
            val userId = remoteParticipant.identity
            Timber.d("onParticipantDisconnected %s", userId)

            userIdToCallerMap.remove(userId)
            removeParticipant(userId, DisconnectReason.REMOTE_ENDED)
        }

        override fun onDominantSpeakerChanged(room: Room, remoteParticipant: RemoteParticipant?) {
            val userId = remoteParticipant?.identity
            Timber.d("onDominantSpeakerChanged %s", userId)
            dominantSpeakerId = userId
        }
    }

    private val participantListener = object : RemoteParticipant.Listener {

        override fun onAudioTrackEnabled(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackEnabled %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackDisabled(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackDisabled %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackPublished(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackPublished %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackUnpublished(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackUnpublished %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackSubscribed(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication, remoteAudioTrack: RemoteAudioTrack) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackSubscribed %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackUnsubscribed(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication, remoteAudioTrack: RemoteAudioTrack) {
            val userId = remoteParticipant.identity
            Timber.v("onAudioTrackUnsubscribed %s", userId)
            onParticipantChanged(userId)
        }

        override fun onAudioTrackSubscriptionFailed(remoteParticipant: RemoteParticipant, remoteAudioTrackPublication: RemoteAudioTrackPublication, twilioException: TwilioException) {
            val userId = remoteParticipant.identity
            Timber.e(twilioException, "onAudioTrackSubscriptionFailed %s", userId)
            onParticipantChanged(userId)
        }

        override fun onVideoTrackEnabled(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onVideoTrackEnabled %s", userId)
            onParticipantChanged(userId)
        }

        override fun onVideoTrackDisabled(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            val userId = remoteParticipant.identity
            Timber.v("onVideoTrackDisabled %s", userId)
            onParticipantChanged(userId)
        }

        override fun onVideoTrackPublished(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            Timber.d("onVideoTrackPublished")
        }

        override fun onVideoTrackUnpublished(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication) {
            Timber.d("onVideoTrackUnpublished")
        }

        override fun onVideoTrackSubscribed(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication, remoteVideoTrack: RemoteVideoTrack) {
            val userId = remoteParticipant.identity
            Timber.v("onVideoTrackSubscribed %s", userId)
            onParticipantChanged(userId)
        }

        override fun onVideoTrackUnsubscribed(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication, remoteVideoTrack: RemoteVideoTrack) {
            val userId = remoteParticipant.identity
            Timber.v("onVideoTrackUnsubscribed %s", userId)
            onParticipantChanged(userId)
        }

        override fun onVideoTrackSubscriptionFailed(remoteParticipant: RemoteParticipant, remoteVideoTrackPublication: RemoteVideoTrackPublication, twilioException: TwilioException) {
            Timber.e(twilioException, "onVideoTrackSubscriptionFailed")
        }

        override fun onDataTrackPublished(remoteParticipant: RemoteParticipant, remoteDataTrackPublication: RemoteDataTrackPublication) {
        }

        override fun onDataTrackUnpublished(remoteParticipant: RemoteParticipant, remoteDataTrackPublication: RemoteDataTrackPublication) {
        }

        override fun onDataTrackSubscribed(remoteParticipant: RemoteParticipant, remoteDataTrackPublication: RemoteDataTrackPublication, remoteDataTrack: RemoteDataTrack) {
            Timber.d("onDataTrackSubscribed")
            remoteDataTrack.setListener(dataTrackListener)
        }

        override fun onDataTrackUnsubscribed(remoteParticipant: RemoteParticipant, remoteDataTrackPublication: RemoteDataTrackPublication, remoteDataTrack: RemoteDataTrack) {
            Timber.d("onDataTrackSubscribed")
        }

        override fun onDataTrackSubscriptionFailed(remoteParticipant: RemoteParticipant, remoteDataTrackPublication: RemoteDataTrackPublication, twilioException: TwilioException) {
        }
    }

    private val localParticipantListener = object : LocalParticipant.Listener {
        override fun onVideoTrackPublicationFailed(localParticipant: LocalParticipant, localVideoTrack: LocalVideoTrack, twilioException: TwilioException) {
            Timber.e("onVideoTrackPublicationFailed")
        }

        override fun onDataTrackPublished(localParticipant: LocalParticipant, localDataTrackPublication: LocalDataTrackPublication) {
            Timber.d("onDataTrackPublished")
        }

        override fun onDataTrackPublicationFailed(localParticipant: LocalParticipant, localDataTrack: LocalDataTrack, twilioException: TwilioException) {
            Timber.e("onDataTrackPublicationFailed")
        }

        override fun onNetworkQualityLevelChanged(localParticipant: LocalParticipant, networkQualityLevel: NetworkQualityLevel) {
            val levelInt = toInt(networkQualityLevel)
            Timber.v("onNetworkQualityLevelChanged: %d", levelInt)
        }

        override fun onAudioTrackPublished(localParticipant: LocalParticipant, localAudioTrackPublication: LocalAudioTrackPublication) {
            Timber.d("onAudioTrackPublished")
        }

        override fun onAudioTrackPublicationFailed(localParticipant: LocalParticipant, localAudioTrack: LocalAudioTrack, twilioException: TwilioException) {
            Timber.e("onAudioTrackPublicationFailed")
        }

        override fun onVideoTrackPublished(localParticipant: LocalParticipant, localVideoTrackPublication: LocalVideoTrackPublication) {
            Timber.d("onVideoTrackPublished")
        }

    }

    private val dataTrackListener = object : RemoteDataTrack.Listener {
        override fun onMessage(remoteDataTrack: RemoteDataTrack, message: String) {
            Timber.d("onMessage: string - %s", message)
            try {
                val json = JSONObject(message)
                val type = json.getString(DataTrackUtil.TYPE)
                val requestType = json.getString(DataTrackUtil.REQUEST_TYPE)
                val payload = json.getJSONObject(DataTrackUtil.PAYLOAD)
                if (type == DataTrackUtil.TYPE_REQUEST && requestType == DataTrackUtil.REQUEST_TYPE_GET_USER_INFO) {
                    val userIds = payload.getJSONArray("userIds")
                    val usersInfo = JSONArray()
                    for (i in 0 until userIds.length()) {
                        val userId = userIds[i]
                        participantMap[userId]?.let { usersInfo.put(DataTrackUtil.convertToUserInfo(it)) }
                    }
                    val responsePayload = JSONObject()
                    responsePayload.put("usersInfo", usersInfo)
                    val dataMessage = JSONObject()
                    dataMessage.put(DataTrackUtil.TYPE, DataTrackUtil.TYPE_RESPONSE)
                            .put(DataTrackUtil.REQUEST_TYPE, DataTrackUtil.REQUEST_TYPE_GET_USER_INFO)
                            .put(DataTrackUtil.PAYLOAD, responsePayload)
                    localDataTrack?.send(dataMessage.toString())
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting json")
            }
        }

        override fun onMessage(remoteDataTrack: RemoteDataTrack, messageBuffer: ByteBuffer) {
            Timber.d("onMessage: byteBuffer")
        }
    }

    fun destroyLocalVideoTrack() {
        localVideoTrack?.let {
            room?.localParticipant?.unpublishTrack(it)
            it.release()
            localVideoTrack = null
        }
    }

    fun recreateLocalVideoTrack() {
        destroyLocalVideoTrack()
        createLocalVideoTrack()
    }

    private val cameraListener = object : CameraCapturer.Listener {
        override fun onError(errorCode: Int) {
            Timber.e("Camera error: %d", errorCode)

            mainHandler.post {
                recreateLocalVideoTrack()
            }
        }

        override fun onFirstFrameAvailable() {
            mainHandler.post {
                onParticipantChanged(currentUserId)
            }
        }

        override fun onCameraSwitched() {
            mainHandler.post {
                val videoCapturer = localVideoTrack?.videoCapturer
                if (videoCapturer is CameraCapturer) {
                    mirrorVideo = videoCapturer.cameraSource == CameraCapturer.CameraSource.FRONT_CAMERA
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val camera2Listener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        object : Camera2Capturer.Listener {
            override fun onError(camera2CapturerException: Camera2Capturer.Exception) {
                Timber.e(camera2CapturerException, "Error with camera2")

                if (camera2CapturerException.code == Camera2Capturer.Exception.UNKNOWN) {
                    destroyLocalVideoTrack() // Expecting an onCameraAvailable callback to retry
                } else {
                    recreateLocalVideoTrack()
                }
            }

            override fun onFirstFrameAvailable() {
                onParticipantChanged(currentUserId)
            }

            override fun onCameraSwitched(newCameraId: String) {
                try {
                    mirrorVideo = cameraManager.getCameraCharacteristics(newCameraId)[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT
                } catch (e: CameraAccessException) {
                    Timber.e(e, "Couldn't get camera lens direction")
                }
            }
        }
    } else null

    private val availabilityCallback: CameraManager.AvailabilityCallback by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            object : CameraManager.AvailabilityCallback() {

                val unavailableCameras: MutableSet<String> = ArraySet<String>()

                private val reconnectVideoRunnable = {
                    removeReconnectRunnable()

                    if (!onHold && isVideoEnabled) {
                        Timber.d("Reconnecting camera")
                        recreateLocalVideoTrack()
                    }
                }

                private fun removeReconnectRunnable() {
                    mainHandler.removeCallbacks(reconnectVideoRunnable)
                }

                override fun onCameraAvailable(cameraId: String) {
                    Timber.d("Camera available: %s", cameraId)
                    if (unavailableCameras.remove(cameraId) && unavailableCameras.isEmpty()) {
                        if (!onHold && isVideoEnabled) {
                            if (selectedCameraId == cameraId && localVideoTrack != null) {
                                Timber.d("Someone else might be accessing the camera")
                            } else {
                                // Wait to see if another camera is about to be used
                                mainHandler.postDelayed(reconnectVideoRunnable, 100)
                                Timber.d("Scheduled camera reconnect")
                            }
                        }
                    }
                }

                override fun onCameraUnavailable(cameraId: String) {
                    Timber.d("Camera unavailable: %s", cameraId)
                    if (unavailableCameras.add(cameraId)) {
                        // Some app (including ours) is using the camera, it's not safe to access the camera
                        removeReconnectRunnable()
                    }
                }
            }
        } else {
            throw IllegalAccessException("Camera Availability is only available on L+")
        }
    }

    override fun onEventReceived(type: String, item: Any?) {
        when (type) {
            TTEvent.ROSTER_ENTRY_UPDATED -> {
                val rosterEntry = item as RosterEntry
                if (callInfo.groupId != rosterEntry.id || callInfo.orgId != rosterEntry.orgId) return
                mainHandler.post {
                    if (isEnded) return@post
                    rosterEntryLiveData.value = rosterEntry
                }
            }
            TTEvent.USER_UPDATED -> {
                val user = item as User
                if (user.orgId != callInfo.orgId) return
                mainHandler.post {
                    if (isEnded) return@post
                    if (participantMap.containsKey(user.id)) {
                        participantMap[user.id] = user
                        onParticipantChanged(user.id)
                    }
                    if (rosterEntryLiveData.value == user) {
                        rosterEntryLiveData.value = user
                    }
                }
            }
            TTEvent.ROLE_UPDATED -> {
                val role = item as Role
                if (role.orgId != callInfo.orgId) return
                mainHandler.post {
                    if (isEnded) return@post
                    if (participantMap.containsKey(role.id)) {
                        participantMap[role.id] = role

                        for (entry in userIdToRoleIds.entries) {
                            if (entry.value.contains(role.id)) {
                                onParticipantChanged(entry.key)
                            }
                        }
                    }
                    if (otherRoleLiveData.value == role) {
                        otherRoleLiveData.value = role
                    }
                }
            }
        }
    }

    companion object {

        const val PARTICIPANTS_MAX = 10
        const val PARTICIPANTS_PATIENTS_MAX = 4
        private const val DELAY_HIDE_BUTTONS = 3000L

        private val listeners = arrayOf(
                TTEvent.ROSTER_ENTRY_UPDATED,
                TTEvent.USER_UPDATED,
                TTEvent.ROLE_UPDATED
        )

        private fun setRemoteParticipantPlaybackEnabled(remoteParticipant: RemoteParticipant?, isEnabled: Boolean) {
            remoteParticipant?.remoteAudioTracks?.forEach { it.remoteAudioTrack?.enablePlayback(isEnabled) }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun getNextCameraId(cameraManager: CameraManager, cameraId: String): String? {
            try {
                val cameraIdList = cameraManager.cameraIdList
                if (cameraIdList.isEmpty()) return null

                val nextIndex = (cameraIdList.indexOf(cameraId) + 1) % cameraIdList.size
                val nextCameraId = cameraIdList[nextIndex]
                return if (nextCameraId != cameraId) {
                    nextCameraId
                } else {
                    null
                }
            } catch (e: AndroidException) { // TODO: Change back to CameraAccessException after min upgraded to 21
                Timber.e(e, "Couldn't get camera id")
                return null
            }
        }

        private fun toInt(networkQualityLevel: NetworkQualityLevel): Int {
            return when (networkQualityLevel) {
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_UNKNOWN -> -1
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ZERO -> 0
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_ONE -> 1
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_TWO -> 2
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_THREE -> 3
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_FOUR -> 4
                NetworkQualityLevel.NETWORK_QUALITY_LEVEL_FIVE -> 5
            }
        }
    }
}