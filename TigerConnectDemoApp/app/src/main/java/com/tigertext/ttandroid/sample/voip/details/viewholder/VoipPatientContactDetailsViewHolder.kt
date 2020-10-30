package com.tigertext.voip.details.viewholder

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.databinding.RowVoipDetailPatientContactBinding
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import com.tigertext.ttandroid.sample.voip.details.viewholder.VoipStatusBinder
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipPatientContactDetailsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val binding = RowVoipDetailPatientContactBinding.bind(view)

    fun bindContent(presenter: TwilioVideoPresenter, user: User) {
        val metadata = user.userPatientMetadata!!

        binding.contactRelation.text = metadata.relationName
        binding.displayName.text = user.displayName

        binding.displayName.setTextColor(ContextCompat.getColor(itemView.context, if (metadata.smsOptedOut) R.color.patient_opt_disabled_color else R.color.new_title_gray))

        val state = VoipStatusBinder.getState(presenter, user.id)
        VoipStatusBinder.bindStatus(binding.status, state, presenter.disconnectedReasonMap[user.id], user)

        val inviteButtonState = VoipStatusBinder.getInviteButtonState(presenter, user)
        binding.invite.visibility = if (inviteButtonState == ButtonState.GONE) View.GONE else View.VISIBLE
        binding.invite.isEnabled = inviteButtonState == ButtonState.CLICKABLE

        binding.invite.setOnClickListener { presenter.inviteParticipant(user.id, null, "pc_id") }
    }

}