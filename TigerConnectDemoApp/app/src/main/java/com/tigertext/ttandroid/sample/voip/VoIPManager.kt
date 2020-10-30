package com.tigertext.ttandroid.sample.voip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.call.payload.StartCallPayload
import com.tigertext.ttandroid.group.Participant
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.application.TigerConnectApplication
import com.tigertext.ttandroid.sample.roles.RoleUtils
import com.tigertext.ttandroid.settings.SettingType
import com.tigertext.ttandroid.sse.VoipCallHandler
import timber.log.Timber


/**
 * Created by martincazares on 01/11/18.
 */

object VoIPManager {

    //Ideally this key and secret should come from server, to prevent exposure when decompiling...
    private const val VOLUME_NO_SET = Int.MIN_VALUE

    private var previousVolume = VOLUME_NO_SET
    private val audioManager = TigerConnectApplication.getApp().getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isSpeakerphoneOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }

    fun setSpeakerphone(on: Boolean) {
        val wasPreviouslyOnMax = isOnMaxVolume()
        if (wasPreviouslyOnMax) {
            resetToLastVolume()
        }
        audioManager.isSpeakerphoneOn = on
        if (wasPreviouslyOnMax) {
            setMaxVolume()
        }
    }

    fun setMaxVolume() {
        if (isOnMaxVolume()) return

        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0)
    }

    fun resetToLastVolume() {
        //If we never stored the previous volume just ignore the reset...
        if (!isOnMaxVolume()) {
            return
        }

        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previousVolume, 0)
        previousVolume = VOLUME_NO_SET
    }

    fun isOnMaxVolume(): Boolean {
        return previousVolume != VOLUME_NO_SET
    }

    private var isAudioModeConfigured = false
    private var previousAudioMode: Int = 0
    private var previousMicrophoneMute: Boolean = false
    private val audioFocusChangedListener by lazy { AudioManager.OnAudioFocusChangeListener { Timber.d("Twilio, Audio focus Gained") } }
    private val audioFocusRequest: AudioFocusRequest by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(audioFocusChangedListener).build()
        } else {
            throw IllegalStateException("You can't ask for this focus request if the OS is not O+")
        }
    }

    fun configureAudio(enable: Boolean) {
        if (isAudioModeConfigured == enable) return
        isAudioModeConfigured = enable
        with(audioManager) {
            if (enable) {
                previousAudioMode = audioManager.mode
                // Request audio focus before making any device switch
                gainAudioFocus()
                /*
                 * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
                 * to be in this mode when playout and/or recording starts for the best
                 * possible VoIP performance. Some devices have difficulties with
                 * speaker mode if this is not set.
                 */
                mode = AudioManager.MODE_IN_COMMUNICATION
                /*
                 * Always disable microphone mute during a WebRTC call.
                 */
                previousMicrophoneMute = isMicrophoneMute
                isMicrophoneMute = false
            } else {
                mode = previousAudioMode
                abandonAudioFocus()
                isMicrophoneMute = previousMicrophoneMute
            }
        }
    }

    private fun gainAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(audioFocusRequest)
        } else {
            audioManager.requestAudioFocus(audioFocusChangedListener,
                    AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        } else {
            audioManager.abandonAudioFocus(audioFocusChangedListener)
        }
    }

    fun getRingMediaPlayer(context: Context): MediaPlayer {
        val mediaPlayer: MediaPlayer
        //Set the stream to go internal as first option (can be overriden by enabling speaker)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer = MediaPlayer.create(context, R.raw.ring,
                    AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
                            .build(), audioManager.generateAudioSessionId())
        } else {
            val afd = context.resources.openRawResourceFd(R.raw.ring)
            mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
            mediaPlayer.prepare()
            afd.close()
        }
        mediaPlayer.isLooping = true
        return mediaPlayer
    }

    fun close() {
    }

    /**
     * localId is meant to be a locally generated call id, needed by iOS
     */
    data class CallInfo(var displayName: String?, val orgId: String, var recipientIds: MutableSet<String>,
                        var callId: String?, var isOutgoing: Boolean = false, var callType: String, val groupId: String?,
                        var localId: String?, var roleIds: MutableMap<String, MutableSet<String>>?, var callerId: String?, val phoneNumber: String? = "")

    fun convertCallDataToCallInfo(voipCallData: VoipCallHandler.VoipCallData): CallInfo {
        val startCallPayload = voipCallData.payload as StartCallPayload
        val callerId = startCallPayload.callerId
        val recipientId = startCallPayload.recipientId
        val isOutgoing = callerId == TT.getInstance().accountManager.userId
        val callType = if (startCallPayload.isVideo) VoIPCallActivity.CALL_TYPE_VIDEO else VoIPCallActivity.CALL_TYPE_AUDIO

        val userId = if (isOutgoing) recipientId else callerId
        val recipientIds = (startCallPayload.recipientIds)?.toMutableSet() ?: ArraySet()
        userId?.let { recipientIds.add(it)}
        recipientIds.remove(TT.getInstance().accountManager.userId)

        val roleIds = startCallPayload.userToRoleIds

        return CallInfo(
                displayName = startCallPayload.name,
                orgId = startCallPayload.orgId,
                recipientIds = recipientIds,
                callId = voipCallData.roomName,
                isOutgoing = isOutgoing,
                callType = callType,
                groupId = startCallPayload.groupId,
                localId = startCallPayload.callId ?: voipCallData.roomName,
                roleIds = roleIds,
                callerId = callerId,
        )
    }

    /**
     * Generates the initial payload necessary for the TwilioVideoPresenter
     */
    fun generateVideoJsonPayload(callInfo: CallInfo, groupId: String?, userIds: Collection<String>, roleIds: MutableMap<String, MutableSet<String>>, isInvite: Boolean, patientId: String?,
                                 participantMap: Map<String, Participant>, connectedParticipants: Set<String>):StartCallPayload {
        val context = TigerConnectApplication.getApp()

        //Prepare headers and payload info...
        val usersDisplayName by lazy { TT.getInstance().settingsManager.get(SettingType.DISPLAY_NAME) as String }

        val currentUserId = TT.getInstance().accountManager.userId
        val recipientIds = userIds.filter { it != currentUserId }

        val recipientId = recipientIds.firstOrNull()
        val myRoleId = getFirstRoleId(roleIds, currentUserId)

        val payloadDisplayName: String?
        if (!groupId.isNullOrEmpty()) {
            payloadDisplayName = callInfo.displayName
        } else if (!myRoleId.isNullOrEmpty() && RoleUtils.isMyRole(myRoleId, callInfo.orgId)) {//If is my role call then append role name in the DN
            val role = TT.getInstance().userManager.getRoleLocally(myRoleId, callInfo.orgId)
            var userRoleName = VoIPCallActivity.getRoleAndUserDisplayName(role, usersDisplayName)
            if (isInvite || userIds.size > 2) {
                userRoleName = context.getString(R.string.name_group_call, userRoleName)
            }
            payloadDisplayName = userRoleName
        } else {
            payloadDisplayName = if (isInvite || userIds.size > 2) {
                context.getString(R.string.name_group_call, usersDisplayName)
            } else {
                usersDisplayName
            }
        }

        // for iOS
        var addedPatientId: String? = null
        var patientContactId: String? = null
        if (!patientId.isNullOrEmpty()) {
            for (participant in participantMap.values) {
                (participant as? User)?.userPatientMetadata?.let {
                    if (it.isPatientContact) patientContactId = participant.id else addedPatientId = participant.id
                }
            }
        }

       return StartCallPayload(
               orgId = callInfo.orgId,
               callId = callInfo.localId,
               name = payloadDisplayName,
               callerId = TT.getInstance().accountManager.userId,
               identity = TT.getInstance().accountManager.userId,
               recipientId = recipientId,
               recipientIds = recipientIds.toSet(),
               userToRoleIds = if (roleIds.isNotEmpty()) roleIds else null,
               isVideo = callInfo.callType == VoIPCallActivity.CALL_TYPE_VIDEO,
               date = System.currentTimeMillis(),
               groupId = groupId,
               connectedParticipants = connectedParticipants,
               networkType = if (!patientId.isNullOrEmpty()) "patient" else "provider",
               patientId = addedPatientId,
               patientContactId = patientContactId
       )
    }

    fun convertCallInfoToExtras(callInfo: CallInfo, extras: Bundle) {
        extras.putString(VoIPCallActivity.EXTRA_DISPLAY_NAME, callInfo.displayName)
        extras.putString(VoIPCallActivity.EXTRA_ORGANIZATION_ID, callInfo.orgId)
        extras.putStringArray(VoIPCallActivity.EXTRA_RECIPIENT_IDS, callInfo.recipientIds.toTypedArray())
        extras.putString(VoIPCallActivity.EXTRA_CALL_ID, callInfo.callId)
        extras.putBoolean(VoIPCallActivity.EXTRA_IS_OUTGOING_CALL, callInfo.isOutgoing)
        extras.putString(VoIPCallActivity.EXTRA_CALL_TYPE, callInfo.callType)
        extras.putString(VoIPCallActivity.EXTRA_GROUP_ID, callInfo.groupId)
        extras.putString(VoIPCallActivity.EXTRA_LOCAL_ID, callInfo.localId)
        callInfo.roleIds?.let { extras.putBundle(VoIPCallActivity.EXTRA_ROLE_IDS, convertMappedSetToBundle(it)) }
        extras.putString(VoIPCallActivity.EXTRA_CALLER_ID, callInfo.callerId)
    }

    fun getCallInfoFromExtras(extras: Bundle): CallInfo {
        return CallInfo(
                displayName = extras.getString(VoIPCallActivity.EXTRA_DISPLAY_NAME),
                orgId = extras.getString(VoIPCallActivity.EXTRA_ORGANIZATION_ID)!!,
                recipientIds = extras.getStringArray(VoIPCallActivity.EXTRA_RECIPIENT_IDS)!!.toMutableSet(),
                callId = extras.getString(VoIPCallActivity.EXTRA_CALL_ID),
                isOutgoing = extras.getBoolean(VoIPCallActivity.EXTRA_IS_OUTGOING_CALL),
                callType = extras.getString(VoIPCallActivity.EXTRA_CALL_TYPE)!!,
                groupId = extras.getString(VoIPCallActivity.EXTRA_GROUP_ID),
                localId = extras.getString(VoIPCallActivity.EXTRA_LOCAL_ID),
                roleIds = extras.getBundle(VoIPCallActivity.EXTRA_ROLE_IDS)?.let { convertBundleToMappedSet(it) },
                callerId = extras.getString(VoIPCallActivity.EXTRA_CALLER_ID),
                phoneNumber = extras.getString(VoIPCallActivity.EXTRA_PHONE_NUMBER)
        )
    }

    fun getFirstRoleId(roleIds: MutableMap<String, MutableSet<String>>?, userId: String): String? {
        return roleIds?.get(userId)?.firstOrNull { it != userId }
    }

    private fun convertBundleToMappedSet(bundle: Bundle): MutableMap<String, MutableSet<String>> {
        val map = ArrayMap<String, MutableSet<String>>()
        for (key in bundle.keySet()) {
            map[key] = ArraySet(bundle.getStringArrayList(key))
        }
        return map
    }

    fun convertMappedSetToBundle(map: Map<String, Set<String>>): Bundle {
        val bundle = Bundle()
        for ((key, value) in map) {
            bundle.putStringArrayList(key, ArrayList(value))
        }
        return bundle
    }
}