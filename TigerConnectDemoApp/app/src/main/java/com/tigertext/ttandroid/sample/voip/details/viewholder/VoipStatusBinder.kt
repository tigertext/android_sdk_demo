package com.tigertext.ttandroid.sample.voip.details.viewholder

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.entity.Entity2
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import com.tigertext.ttandroid.search.SearchResultEntity
import com.tigertext.ttandroid.sample.voip.states.DisconnectReason
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

object VoipStatusBinder {

    fun getState(presenter: TwilioVideoPresenter, userId: String) = when {
        presenter.disconnectedReasonMap.containsKey(userId) -> STATE_DISCONNECTED
        presenter.userIdToCallerMap.containsKey(userId) -> STATE_CONNECTED
        presenter.userIds.contains(userId) -> STATE_RINGING
        else -> STATE_DISCONNECTED
    }

    fun getInviteButtonState(presenter: TwilioVideoPresenter, entity: Entity2): ButtonState {
        if (presenter.userIds.contains(entity.id)) return ButtonState.GONE

        if (!presenter.isInviteParticipantAllowed()) return ButtonState.GONE
        if (presenter.isGroupCallFull()) return ButtonState.DISABLED

        if (entity is User) {
            if (entity.isDnd) return ButtonState.DISABLED
            if (entity.userPatientMetadata?.smsOptedOut == true) return ButtonState.DISABLED
        } else if (entity is SearchResultEntity) {
            if (entity.dnd) return ButtonState.DISABLED
            if (entity.userPatientMetadata?.smsOptedOut == true) return ButtonState.DISABLED
        }

        if (presenter.disconnectedReasonMap[entity.id] == DisconnectReason.REMOTE_ERROR) return ButtonState.DISABLED

        return ButtonState.CLICKABLE
    }

    fun bindStatus(statusTextView: TextView, state: Int, disconnectReason: Int?, entity: Entity2?) {
        var textColor = R.color.search_faded_gray
        val status = when {
            state == STATE_CONNECTED -> {
                textColor = R.color.switch_selected_color
                R.string.connected
            }
            state == STATE_RINGING -> R.string.ringing
            disconnectReason == DisconnectReason.REMOTE_DECLINED -> R.string.declined_call
            disconnectReason == DisconnectReason.REMOTE_ENDED -> R.string.left_call
            disconnectReason != null -> R.string.unavailable
            (entity is User && entity.isDnd)
                    || (entity is SearchResultEntity && entity.dnd) -> R.string.do_not_disturb
            (entity is User && entity.userPatientMetadata?.smsOptedOut == true)
                    || (entity is SearchResultEntity && entity.userPatientMetadata?.smsOptedOut == true) -> {
                textColor = R.color.patient_opt
                R.string.patient_opted_out_via_sms
            }
            else -> 0
        }

        if (status != 0) {
            statusTextView.setText(status)
            statusTextView.setTextColor(ContextCompat.getColor(statusTextView.context, textColor))
            statusTextView.visibility = View.VISIBLE
        } else {
            statusTextView.visibility = View.GONE
        }
    }

    const val STATE_CONNECTED = 0
    const val STATE_RINGING = 1
    const val STATE_DISCONNECTED = 2

}