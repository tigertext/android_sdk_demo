package com.tigertext.ttandroid.sample.voip

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY
import android.util.Rational
import android.view.View
import android.view.WindowManager.LayoutParams.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.application.TigerConnectApplication
import com.tigertext.ttandroid.sample.databinding.ActivityVoipCallBinding
import com.tigertext.ttandroid.sample.notification.TCNotificationManager
import com.tigertext.ttandroid.sample.voip.states.CallPresenter
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import com.tigertext.ttandroid.sample.voip.states.CallPresenterManager
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason
import com.tigertext.voip.video.VoipUIModel
import com.twilio.video.VideoRenderer
import kotlinx.android.synthetic.main.activity_voip_call.*
import timber.log.Timber
import java.util.*

/**
 * Created by martincazares on 1/9/18.
 */
class VoIPCallActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var viewBinding: ActivityVoipCallBinding
    var presenter: CallPresenter? = null
    private lateinit var callInfo: VoIPManager.CallInfo
    private var isOutgoingCall = false


    /**
     * True if the incoming call should be picked up immediately, false if the user should be prompted to answer
     */
    private var isAnswered = false

    // Proximity
    private var isProximityEnabled = false
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private lateinit var proximity: Sensor
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private val hasProximitySensor by lazy { packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(FLAG_SHOW_WHEN_LOCKED)
        }
        //Make sure we ignore "fat clicks" and keep screen on, so the user doesn't have to unlock after done calling...
        window.addFlags(FLAG_IGNORE_CHEEK_PRESSES)
        window.addFlags(FLAG_KEEP_SCREEN_ON)

        viewBinding = ActivityVoipCallBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        setupInitState()

        viewBinding.participantRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        (viewBinding.participantRecyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    }

    override fun onStart() {
        super.onStart()
        if (presenter?.proximityEnabledLiveData?.value == true) {
            startScreenDimLogic()
        }

        // Automatically set this ongoing call as the active call if needed
        if (presenter?.isStarted == true && presenter?.isEnded == false) {
            CallPresenterManager.activePresenter = presenter
        }
    }

    private fun startScreenDimLogic() {
        if (isProximityEnabled) return
        isProximityEnabled = true

        //If the device can handle the proximity for us let it do it...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            if (proximityWakeLock == null) {
                proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, PROXIMITY_SENSOR_TAG)
            }
            proximityWakeLock?.acquire()
        } else if (hasProximitySensor) {
            proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
            window.addFlags(FLAG_FULLSCREEN)
        }
    }

    private fun stopScreenDimLogic() {
        if (!isProximityEnabled) return
        isProximityEnabled = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release(RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        } else if (hasProximitySensor) {
            sensorManager.unregisterListener(this)
        }//else just use ignore cheek press
    }

    private fun setUserData() {
        updateDisplayName()
        updateAvatar()
    }

    private fun updateDisplayName() {
        displayName.text = getDisplayName(this, callInfo, presenter)
    }

    private fun updateAvatar() {
        presenter?.rosterEntryLiveData?.value?.let {
            if (it.avatarUrl.isNullOrEmpty()) {
                Picasso.get().cancelRequest(avatarImage)
            } else {
                Picasso.get().load(it.avatarUrl).into(avatarImage)
            }
            return
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)

        //If there's a change in the isAnswered extra check if we should checkPermissions to follow the start call flow
        //This case happens when you open manually this activity, press home and then click answer on notification
        //Since the activity is already created and is a single task, the OS just fires the onNewIntent event...
        val newIsAnswered = intent.getBooleanExtra(EXTRA_IS_ANSWERED, false)
        if (newIsAnswered != isAnswered) {
            isAnswered = newIsAnswered
        }

        if (newIsAnswered) {
            checkPermissions()
        }
    }

    private fun setupInitState() {
        intent ?: return
        val extras = intent.extras ?: return

        isAnswered = intent.getBooleanExtra(EXTRA_IS_ANSWERED, false)
        isOutgoingCall = intent.getBooleanExtra(EXTRA_IS_OUTGOING_CALL, false)
        callInfo = VoIPManager.getCallInfoFromExtras(extras)

        setUserData()

        if (presenter == null) {
            val callId = callInfo.callId
            if (!callId.isNullOrEmpty()) {
                presenter = CallPresenterManager.getPresenter(callId)
                presenter?.let {
                    if (it.activity != this) {
                        it.activity?.presenter = null
                        it.activity?.finish()
                        it.activity = this
                    }
                }
            }

            if (presenter == null) {
                when (callInfo.callType) {
                    CALL_TYPE_AUDIO, CALL_TYPE_VIDEO -> {
                        if (isOutgoingCall && callInfo.localId == null) {
                            callInfo.localId = UUID.randomUUID().toString()
                        }
                        presenter = TwilioVideoPresenter(this, callInfo)
                    }
//                    CALL_TYPE_SIP -> {
//                        presenter = TwilioVoicePresenter(this, callInfo)
//                    }
                    else -> Timber.e("Unknown call type, %s", callInfo.callType)
                }
            } else {
                Timber.w("Presenter for call id %s already exists", callId)
            }

            presenter?.initUiState()
            setupObservers()
        }

        if (isOutgoingCall || isAnswered) {
            checkPermissions()
        } else if (presenter?.isStarted != true) {
            acceptCall.visibility = View.VISIBLE
            acceptCall.setOnClickListener {
                isAnswered = true
                checkPermissions()
            }
        }
    }

    private fun onConnected() {
        statusPill.visibility = View.GONE
    }

    private fun onReconnected() {
        statusPill.background?.mutate()?.setColorFilter(resources.getColor(R.color.inbox_read_status), PorterDuff.Mode.SRC_IN)
        statusPill.visibility = View.VISIBLE
        statusPill.setText(R.string.reconnected)
    }

    private fun onReconnecting() {
        statusPill.background?.mutate()?.setColorFilter(resources.getColor(R.color.reconnecting), PorterDuff.Mode.SRC_IN)
        statusPill.visibility = View.VISIBLE
        statusPill.setText(R.string.reconnecting)
    }

    private fun setupObservers() {
        presenter?.callTypeTextLiveData?.observe(this, Observer { callViaTT.text = it })
        presenter?.proximityEnabledLiveData?.observe(this, Observer {
            if (it == true) {
                startScreenDimLogic()
            } else {
                stopScreenDimLogic()
            }
        })



        presenter?.callStatusLiveData?.observe(this, Observer {


            when (it) {
                RECONNECTING -> onReconnecting()
                RECONNECTED -> onReconnected()
                else -> onConnected()
            }
        })


        presenter?.callStateLiveData?.observe(this, Observer { callState.text = it })
        presenter?.callStateColorLiveData?.observe(this, {
            callState.setTextColor(ContextCompat.getColor(this, it
                    ?: R.color.attachment_preview_text))
        })
        presenter?.hideCallInfoLiveData?.observe(this, Observer {
            val isEnabled = it == true
            caller_grid.visibility = if (isEnabled) View.VISIBLE else View.GONE

            VoipUIModel.updateViewsBlockingVideo(this, if (isEnabled) View.GONE else View.VISIBLE)
            if (presenter?.localVideoTrackLiveData?.value != null) {
                val isFullscreen = !isEnabled
                VoipUIModel.animateLocalVideoFullScreen(this, isFullscreen)
                val textColor = ContextCompat.getColor(this, if (isFullscreen) R.color.white else R.color.view_component_text_view)
                displayName.setTextColor(textColor)
                tagNameTextView.setTextColor(textColor)
            }
            if (!isEnabled && presenter?.otherRoleLiveData?.value == null) {
                tagNameTextView.visibility = View.GONE
            }
        })
        presenter?.hideButtonsLiveData?.observe(this, Observer {
            var isEnabled = it == true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
                isEnabled = true
            }
            VoipUIModel.updateButtonVisibility(this, !isEnabled)
        })
        presenter?.localVideoTrackLiveData?.observe(this, Observer {
            val isEnabled = it != null
            it?.addRenderer(localUserVideo as VideoRenderer)
            localUserVideo.visibility = if (isEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) View.INVISIBLE else View.VISIBLE
            } else View.GONE
            if (isEnabled) {
                val isFullscreen = presenter?.hideCallInfoLiveData?.value != true
                VoipUIModel.animateLocalVideoFullScreen(this, isFullscreen)
                val textColor = ContextCompat.getColor(this, if (isFullscreen) R.color.white else R.color.view_component_text_view)
                displayName.setTextColor(textColor)
                tagNameTextView.setTextColor(textColor)
            } else {
                videoShadow.visibility = View.GONE
                val textColor = ContextCompat.getColor(this, R.color.view_component_text_view)
                displayName.setTextColor(textColor)
                tagNameTextView.setTextColor(textColor)
            }
        })
        presenter?.otherRoleLiveData?.observe(this, Observer {
            val presenter = presenter ?: return@Observer
            val visibility = if (it == null || presenter.hideCallInfoLiveData?.value == true) View.GONE else View.VISIBLE
            updateDisplayName()
            tagNameTextView.visibility = if (it == null || it.roleTag.isEmpty) View.GONE else visibility
            tagNameTextView.text = it?.roleTag?.tagName
        })
        presenter?.rosterEntryLiveData?.observe(this, Observer {
            updateDisplayName()
            updateAvatar()
        })
    }

    private fun checkPermissions() {
        if (presenter?.isStarted == true) return
        // Stop ringing because the user has already accepted the call
        TCNotificationManager.INSTANCE.removeOngoingCallNotification(callInfo.callId)

        //After the UI is setup Look for permissions for the actions...
        val microphonePermission = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(this, microphonePermission, 1)
    }

    private fun permissionGranted() {
        presenter?.startCall()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissions.isEmpty() || grantResults.isEmpty()) return

        for (permission in grantResults) {
            if (permission == PackageManager.PERMISSION_DENIED) {
                presenter?.endCall(DisconnectReason.LOCAL_REJECTED)
            }
        }

        permissionGranted()
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && callInfo.callType == CALL_TYPE_VIDEO) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(1, 1)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        val isVisible = !isInPictureInPictureMode && presenter?.hideButtonsLiveData?.value != true
        VoipUIModel.updateButtonVisibility(this, isVisible)

        if (isInPictureInPictureMode) {
            if (localUserVideo.visibility == View.VISIBLE) localUserVideo.visibility = View.INVISIBLE
        } else {
            if (localUserVideo.visibility == View.INVISIBLE) localUserVideo.visibility = View.VISIBLE
        }
    }

    override fun onBackPressed() {
        if (presenter == null || presenter?.isEnded == true) {
            super.onBackPressed()
        } else {
            moveTaskToBack(true)
        }
    }

    override fun onStop() {
        super.onStop()
        stopScreenDimLogic()
    }

    override fun onDestroy() {
        super.onDestroy()
        val presenter = presenter ?: return
        if (presenter.isStarted && !presenter.isEnded && presenter is TwilioVideoPresenter) {
            presenter.clearVideoRenderers()
        }
        if (presenter.activity == this) {
            presenter.activity = null
        }
        this.presenter = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(sensorEvent: SensorEvent?) {
        val event = sensorEvent ?: return
        val halfMaximum = event.sensor.maximumRange / 2
        val visibility = if (event.values[0] < halfMaximum) View.VISIBLE else View.GONE
        if (screenDimmer.visibility != visibility) {
            screenDimmer.visibility = visibility
        }
    }

    companion object {
        const val EXTRA_DISPLAY_NAME = "display.name.extra"
        const val EXTRA_PHONE_NUMBER = "phone.number.extra"
        const val EXTRA_ORGANIZATION_ID = "org.id.extra"
        const val EXTRA_RECIPIENT_IDS = "participants_tokens"
        const val EXTRA_GROUP_ID = "group_id"
        const val EXTRA_ROLE_IDS = "role_tokens"
        const val EXTRA_LOCAL_ID = "local.id"
        const val EXTRA_CALLER_ID = "caller_id"

        private const val PROXIMITY_SENSOR_TAG = "TigerConnect:VoIPCallActivity_proximity"

        const val EXTRA_CALL_TYPE = "call_type"
        const val CALL_TYPE_AUDIO = "audio"
        const val CALL_TYPE_VIDEO = "video"
        const val CALL_TYPE_CHOOSE = "choose" // Either audio, video, or C2C
        const val CALL_TYPE_SIP = "sip"

        const val EXTRA_IS_OUTGOING_CALL = "is.outgoing.call.extra"
        const val EXTRA_CALL_ID = "call.id.extra"
        const val EXTRA_IS_ANSWERED = "redirect.to.answer.extra"

        const val EXTRA_VIEW_COMPONENT_CALLBACK = "extra.view.component.callback"
        const val EXTRA_MESSAGE_ID = "extra.message.id"

        const val ALREADY_CONNECTED = 0
        const val RECONNECTING = 1
        const val RECONNECTED = 2
        private fun getDisconnectReasonString(context: Context, disconnectReason: Int): String? {
            return when (disconnectReason) {
                DisconnectReason.REMOTE_ENDED -> context.getString(R.string.voip_call_ended_hung_up)
                DisconnectReason.REMOTE_ERROR -> context.getString(R.string.voip_call_ended_failed)
                DisconnectReason.REMOTE_DECLINED -> context.getString(R.string.user_is_busy)
                DisconnectReason.REMOTE_UNANSWERED -> context.getString(R.string.voip_call_ended_cancelled)
                DisconnectReason.REMOTE_UNKNOWN -> context.getString(R.string.voip_call_ended_hung_up)
                DisconnectReason.LOCAL_TIMEOUT -> context.getString(R.string.voip_call_ended_timeout)
                DisconnectReason.LOCAL_ERROR -> context.getString(R.string.voip_call_ended_failed)
                DisconnectReason.REMOTE_ANSWERED_ELSEWHERE -> context.getString(R.string.voip_call_ended_transfered)
                DisconnectReason.REMOTE_LOCAL_ENDED_ELSEWHERE -> context.getString(R.string.voip_call_ended_elsewhere)
                else -> null
            }
        }

        fun showDisconnectReasonAndFinish(activity: VoIPCallActivity?, disconnectReason: Int) {
            val reason = getDisconnectReasonString(TigerConnectApplication.getApp(), disconnectReason)
            if (!reason.isNullOrEmpty()) {
                if (activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    Snackbar.make(activity.voipCallRootView, reason, Snackbar.LENGTH_SHORT).addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            activity.finish()
                        }
                    }).show()
                } else {
                    Toast.makeText(TigerConnectApplication.getApp(), reason, Toast.LENGTH_SHORT).show()
                    activity?.finish()
                }
            } else {
                activity?.finish()
            }
        }

        fun getDisplayName(context: Context, callInfo: VoIPManager.CallInfo, presenter: CallPresenter?): String {
            val name = presenter?.rosterEntryLiveData?.value?.displayName ?: callInfo.displayName
            ?: ""
            val role = presenter?.otherRoleLiveData?.value
            val displayText = getRoleAndUserDisplayName(role, name)

            return if (callInfo.groupId.isNullOrEmpty() && (presenter as? TwilioVideoPresenter)?.userIds?.size ?: 1 > 2) {
                context.getString(R.string.name_group_call, displayText)
            } else {
                displayText
            }
        }

        fun getRoleAndUserDisplayName(role: Role?, userDisplayName: String): String {
            return if (role != null) {
                String.format("%s (%s)", role.displayName, userDisplayName)
            } else {
                userDisplayName
            }
        }
    }
}