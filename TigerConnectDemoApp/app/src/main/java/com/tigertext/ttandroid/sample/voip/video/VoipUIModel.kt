package com.tigertext.voip.video

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Constraints
import androidx.gridlayout.widget.GridLayout
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.views.CircleButton
import com.tigertext.ttandroid.sample.voip.video.VoipParticipantView
import com.tigertext.ttandroid.sample.voip.VoIPCallActivity
import com.tigertext.ttandroid.sample.voip.VoIPManager
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import kotlinx.android.synthetic.main.activity_voip_call.*

/**
 * Created by martincazares on 3/23/18.
 */
object VoipUIModel {

    private const val TRANSITION_DURATION = 0L

    /**
     * Does initial UI Setup of icons and buttons
     */
    fun setupIcons(voIPCallActivity: VoIPCallActivity) {
        voIPCallActivity.speakerCall.setOnClickListener {
            voIPCallActivity.speakerCall.toggleState()
            VoIPManager.setSpeakerphone(voIPCallActivity.speakerCall.active)
        }
        voIPCallActivity.declineCall.setOnClickListener {
            voIPCallActivity.presenter?.let { it.endCall(if (it.isStarted) DisconnectReason.LOCAL_ENDED else DisconnectReason.LOCAL_REJECTED) }
        }

        voIPCallActivity.switchCamera.setOnClickListener {
            voIPCallActivity.videoCall.toggleState()
        }
        voIPCallActivity.muteCall.setOnClickListener {
            voIPCallActivity.presenter?.isMuted = voIPCallActivity.presenter?.isMuted?.not() ?: return@setOnClickListener
        }
        voIPCallActivity.videoCall.setOnClickListener {
            voIPCallActivity.presenter?.isVideoEnabled = voIPCallActivity.presenter?.isVideoEnabled?.not() ?: return@setOnClickListener
        }
    }

    /**
     * Transitions views for started video call
     */
    fun setupVideoState(voIPCallActivity: VoIPCallActivity) {
        voIPCallActivity.videoShadow.visibility = View.GONE

        //Move all the views out of the way for Video...
        voIPCallActivity.acceptCall.visibility = View.GONE
        voIPCallActivity.sendMessageOrBoostVolume.visibility = View.GONE
        voIPCallActivity.speakerCall.visibility = View.GONE

        handleVideoInCallTransition(voIPCallActivity)
    }

    /**
     * Moves all the views out of the way for Video
     */
    fun updateViewsBlockingVideo(voIPCallActivity: VoIPCallActivity, visibility: Int) {
        val imagesInTheWayOfVideo = mutableListOf<View>(voIPCallActivity.avatarImage, voIPCallActivity.callState, voIPCallActivity.callViaTT,
                voIPCallActivity.displayName, voIPCallActivity.tagNameTextView)
        imagesInTheWayOfVideo.forEach { it.visibility = visibility }
    }

    /**
     * Update buttons visibiltiy, making sure not to change GONE buttons status
     */
    fun updateButtonVisibility(voIPCallActivity: VoIPCallActivity, visible: Boolean) {
        val voipCallRootView = voIPCallActivity.voipCallRootView

        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        for (i in 0 until voipCallRootView.childCount) {
            val view = voipCallRootView.getChildAt(i)
            if (view is CircleButton && view.visibility != View.GONE) {
                view.visibility = visibility
            }
        }
    }

    private fun handleVideoInCallTransition(voIPCallActivity: VoIPCallActivity) {
        //Create the transition's animation...
        val autoTransition = AutoTransition()
        autoTransition.duration = TRANSITION_DURATION

        //Apply transition animation to the views...
        TransitionManager.beginDelayedTransition(voIPCallActivity.voipCallRootView, autoTransition)
        val applyConstraintSet = ConstraintSet()
        applyConstraintSet.clone(voIPCallActivity.voipCallRootView)

        //Manipulate menu icon to animate smoothly with other buttons...
        if (voIPCallActivity.muteCall.visibility != View.VISIBLE) { applyConstraintSet.setVisibility(R.id.muteCall, View.VISIBLE) }

        //Make required video views visible...
        if (voIPCallActivity.videoCall.visibility != View.VISIBLE) { applyConstraintSet.setVisibility(R.id.videoCall, View.VISIBLE) }
        if (voIPCallActivity.switchCamera.visibility != View.VISIBLE) { applyConstraintSet.setVisibility(R.id.switchCamera, View.VISIBLE) }

        applyConstraintSet.applyTo(voIPCallActivity.voipCallRootView)

        //Video by default needs speakers on...
        if (!VoIPManager.isSpeakerphoneOn()) {
            VoIPManager.setSpeakerphone(true)
        }

    }

    /**
     * Transitions views for started audio call
     */
    fun callingTransition(voIPCallActivity: VoIPCallActivity) {
        //Create the transition's animation...
        val autoTransition = AutoTransition()
        autoTransition.duration = TRANSITION_DURATION
        autoTransition.interpolator = AccelerateDecelerateInterpolator()

        //Apply transition animation to the views...
        TransitionManager.beginDelayedTransition(voIPCallActivity.voipCallRootView, autoTransition)
        val applyConstraintSet = ConstraintSet()
        applyConstraintSet.clone(voIPCallActivity.voipCallRootView)
        applyConstraintSet.setVisibility(R.id.muteCall, View.VISIBLE)
        applyConstraintSet.setVisibility(R.id.speakerCall, View.VISIBLE)
        applyConstraintSet.setVisibility(R.id.acceptCall, View.GONE)
        applyConstraintSet.setVisibility(R.id.sendMessageOrBoostVolume, View.VISIBLE)
        applyConstraintSet.applyTo(voIPCallActivity.voipCallRootView)
    }

    fun setupBoostVolumeButton(voIPCallActivity: VoIPCallActivity) {
        //Change the message icon to boost volume since we are calling now...
        voIPCallActivity.sendMessageOrBoostVolume.setIcon(R.drawable.boost_volume)
        voIPCallActivity.sendMessageOrBoostVolume.setOnClickListener {
            voIPCallActivity.sendMessageOrBoostVolume.toggleState()
            if (voIPCallActivity.sendMessageOrBoostVolume.active) {
                VoIPManager.setMaxVolume()
            }else {
                VoIPManager.resetToLastVolume()
            }
        }
        if (VoIPManager.isOnMaxVolume()) {
            voIPCallActivity.sendMessageOrBoostVolume.active = true
        }
    }

    /**
     * Sets views for not accepted incoming call
     */
    fun setIncomingCallInitialState(voIPCallActivity: VoIPCallActivity) {
        voIPCallActivity.muteCall.visibility = View.GONE
        voIPCallActivity.speakerCall.visibility = View.GONE
        voIPCallActivity.acceptCall.visibility = View.VISIBLE
        voIPCallActivity.callState.visibility = View.VISIBLE
        voIPCallActivity.sendMessageOrBoostVolume.active = false
    }

    fun animateLocalVideoFullScreen(voIPCallActivity: VoIPCallActivity, isFullscreen: Boolean) {
        //Create the transition's animation...
        val autoTransition = AutoTransition()
        autoTransition.duration = 0
        autoTransition.interpolator = AccelerateDecelerateInterpolator()

        //Apply transition animation to the views...
        TransitionManager.beginDelayedTransition(voIPCallActivity.voipCallRootView, autoTransition)
        val applyConstraintSet = ConstraintSet()
        applyConstraintSet.clone(voIPCallActivity.voipCallRootView)

        applyConstraintSet.clear(R.id.localUserVideo)
        applyConstraintSet.setVisibility(R.id.localUserVideo, voIPCallActivity.localUserVideo.visibility)
        if (isFullscreen) {
            applyConstraintSet.constrainWidth(R.id.localUserVideo, Constraints.LayoutParams.MATCH_PARENT)
            applyConstraintSet.constrainHeight(R.id.localUserVideo, Constraints.LayoutParams.MATCH_PARENT)
        } else {
            applyConstraintSet.constrainWidth(R.id.localUserVideo, voIPCallActivity.resources.getDimensionPixelSize(R.dimen.local_user_video_width))
            applyConstraintSet.constrainHeight(R.id.localUserVideo, voIPCallActivity.resources.getDimensionPixelSize(R.dimen.local_user_video_height))
            val margin = voIPCallActivity.resources.getDimensionPixelOffset(R.dimen.voip_common_horizontal_margin)
            applyConstraintSet.connect(R.id.localUserVideo, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, margin)
            applyConstraintSet.connect(R.id.localUserVideo, ConstraintSet.RIGHT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, margin)
        }
        applyConstraintSet.setVisibility(R.id.videoShadow, if (isFullscreen) View.VISIBLE else View.GONE)

        applyConstraintSet.applyTo(voIPCallActivity.voipCallRootView)
    }

    fun resetGridParams(activity: VoIPCallActivity, numCallers: Int) {
        val grid = activity.caller_grid as GridLayout
        var count = 0
        for (i in 0 until grid.childCount) {
            val item = grid.getChildAt(i)
            if (item.visibility == View.GONE) continue

            val size = if (numCallers == 2 || (count == 0 && numCallers % 2 == 1)) 2 else 1
            val params = item.layoutParams as GridLayout.LayoutParams
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, size, 1f)
            item.layoutParams = params

            count++
        }
    }

    private fun animateRecyclerView(voIPCallActivity: VoIPCallActivity, visibility: Int) {
        voIPCallActivity.participant_recycler_view.visibility = visibility
    }

    private fun unbindAllGridViewsButFirst(activity: VoIPCallActivity) {
        val grid = activity.caller_grid as GridLayout
        for (i in 1 until grid.childCount) {
            val child = grid.getChildAt(i)
            child.visibility = View.GONE
            (child as VoipParticipantView).unbind()
        }
    }

    const val GROUP_VIEW_TYPE_P2P = 0
    const val GROUP_VIEW_TYPE_GRID = 1
    const val GROUP_VIEW_TYPE_LIST = 2

    fun setGroupViewType(activity: VoIPCallActivity, videoPresenter: TwilioVideoPresenter, viewType: Int, numCallers: Int) {
        when (viewType) {
            GROUP_VIEW_TYPE_P2P -> {
                if (activity.participant_recycler_view.adapter != null) {
                    activity.participant_recycler_view.adapter = null
                    animateRecyclerView(activity, View.GONE)
                }
                unbindAllGridViewsButFirst(activity)
                val lastIndex = videoPresenter.userIds.lastIndex
                if (lastIndex >= 0) {
                    videoPresenter.rebindParticipant(activity, videoPresenter.userIds[lastIndex], lastIndex, true)
                }
                resetGridParams(activity, 1)
            }
            GROUP_VIEW_TYPE_GRID -> {
                if (activity.participant_recycler_view.adapter != null) {
                    activity.participant_recycler_view.adapter = null
                    animateRecyclerView(activity, View.GONE)
                }
                for (index in videoPresenter.userIds.lastIndex downTo 0) {
                    videoPresenter.rebindParticipant(activity, videoPresenter.userIds[index], index, true)
                }
                resetGridParams(activity, numCallers)
            }
            GROUP_VIEW_TYPE_LIST -> {
                if (activity.participant_recycler_view.adapter == null) {
                    activity.participant_recycler_view.adapter = videoPresenter.voipParticipantAdapter
                    animateRecyclerView(activity, View.VISIBLE)
                }
                unbindAllGridViewsButFirst(activity)
                val lastIndex = videoPresenter.userIds.lastIndex
                if (lastIndex >= 0) {
                    videoPresenter.rebindParticipant(activity, videoPresenter.userIds[lastIndex], lastIndex, true)
                }
                resetGridParams(activity, 1)
            }
        }
    }
}