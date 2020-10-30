package com.tigertext.ttandroid.sample.voip.video

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.squareup.picasso.Picasso
import com.tigertext.ttandroid.Entity
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.databinding.ViewVoipParticipantBinding
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import com.tigertext.voip.video.VoipUIModel
import com.twilio.video.*
import timber.log.Timber

class VoipParticipantView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewVoipParticipantBinding.inflate(LayoutInflater.from(context), this)

    private val you by lazy { context.getString(R.string.you) }

    private val ringingAnimation by lazy {
        val pulseRingAnimation = AnimationUtils.loadAnimation(context, R.anim.pulse_ring_scale)
        pulseRingAnimation.duration = ANIMATION_DURATION.toLong()
        pulseRingAnimation.repeatMode = Animation.RESTART

        val alphaAnimation = AlphaAnimation(1f, 0f)
        alphaAnimation.duration = ANIMATION_DURATION.toLong()
        alphaAnimation.repeatMode = Animation.RESTART
        alphaAnimation.repeatCount = Animation.INFINITE

        val animationSet = AnimationSet(false)
        animationSet.addAnimation(pulseRingAnimation)
        animationSet.addAnimation(alphaAnimation)
        animationSet
    }

    private var videoTrack: VideoTrack? = null
        set(value) {
            if (field != value) {
                try {
                    field?.removeRenderer(binding.callerVideo.root as VideoRenderer)
                } catch (e: IllegalStateException) {
                    // Try catch to prevent crashes when unbinding a video track that has been released, Twilio doesn't expose isReleased()
                    Timber.e("Error unbinding video")
                }
                field = value
                value?.addRenderer(binding.callerVideo.root as VideoRenderer)
                binding.callerVideo.root.visibility = if (value != null) View.VISIBLE else View.GONE
            }
        }

    fun bind(twilioVideoPresenter: TwilioVideoPresenter, userId: String) {
        val user = twilioVideoPresenter.participantMap[userId]
        val isCurrentUser = twilioVideoPresenter.currentUserId == userId
        val userDisplayName = when {
            isCurrentUser -> you
            (user as? User)?.userPatientMetadata != null -> {
                val metadata = (user as? User)?.userPatientMetadata!!
                val patientType = if (metadata.isPatientContact) metadata.relationName else context.getString(R.string.patient)
                context.getString(R.string.join_string_parenthesis_string, user.displayName, patientType)
            }
            else -> user?.displayName
        }

        if (user != null) {
            Picasso.get().load(user.avatarUrl).into(binding.callerAvatar)
        } else {
            Picasso.get().cancelRequest(binding.callerAvatar)
        }

        if ((user as? User)?.userPatientMetadata != null) {
            binding.root.background = ContextCompat.getDrawable(context, R.drawable.voip_participant_background)
        } else binding.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))

        val roleIds = twilioVideoPresenter.userIdToRoleIds[userId]?.filter { it != userId }
        val roles = twilioVideoPresenter.participantMap.filterKeys { roleIds?.contains(it) == true }.values
        val size = roles.size
        when {
            size >= 2 -> {
                binding.callerName.text = resources.getQuantityString(R.plurals.assigned_roles, size, size)
                binding.callerSubname.text = context.getString(R.string.string_parenthesis, userDisplayName)
                binding.callerSubname.visibility = View.VISIBLE
            }
            size == 1 -> {
                val role = roles.first() as? Role
                binding.callerName.text = role?.displayName
                val userNameParenthesis = context.getString(R.string.string_parenthesis, userDisplayName)
                binding.callerSubname.text = if (role?.roleTag?.isEmpty != true) "${role?.roleTag?.tagName}\n$userNameParenthesis" else userNameParenthesis
                binding.callerSubname.visibility = View.VISIBLE
            }
            else -> {
                binding.callerName.text = userDisplayName
                binding.callerSubname.visibility = View.GONE
            }
        }

        val participant = twilioVideoPresenter.userIdToCallerMap[userId]
        binding.callerState.visibility = if (participant == null) View.VISIBLE else View.GONE
        setRingingAnimation(participant == null)

        videoTrack = getVideoTrack(participant)
        val mirror = isCurrentUser && twilioVideoPresenter.mirrorVideo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            (binding.callerVideo.root as VideoTextureView).mirror = mirror
        } else {
            (binding.callerVideo.root as VideoView).mirror = mirror
        }

        binding.callerMute.visibility = if (isMuted(participant)) View.VISIBLE else View.GONE

        binding.dominantSpeakerBorder.visibility = if (twilioVideoPresenter.groupViewType != VoipUIModel.GROUP_VIEW_TYPE_P2P
                && userId == twilioVideoPresenter.dominantSpeakerId) View.VISIBLE else View.GONE
    }

    fun unbind() {
        videoTrack = null
    }

    private fun setRingingAnimation(show: Boolean) {
        if (show) {
            binding.pulseRing.visibility = View.VISIBLE
            binding.pulseRing.startAnimation(ringingAnimation)
        } else {
            binding.pulseRing.visibility = View.INVISIBLE
            binding.pulseRing.clearAnimation()
        }
    }

    companion object {

        private const val ANIMATION_DURATION = 834

        fun getVideoTrack(participant: Participant?): VideoTrack? {
            if (participant != null) {
                for (videoTrack in participant.videoTracks) {
                    if (videoTrack.isTrackEnabled) {
                        return videoTrack.videoTrack
                    }
                }
            }

            return null
        }

        fun isMuted(participant: Participant?): Boolean {
            if (participant != null) {
                for (audioTrack in participant.audioTracks) {
                    if (audioTrack.isTrackEnabled) {
                        return false
                    }
                }
                return true
            }

            return false
        }
    }

}
