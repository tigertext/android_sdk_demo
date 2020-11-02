package com.tigertext.ttandroid.sample.voip.details.viewholder

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.databinding.RowVoipDetailPatientBinding
import com.tigertext.ttandroid.sample.utils.PatientUtils
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipPatientDetailsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val binding = RowVoipDetailPatientBinding.bind(view)

    fun bindContent(presenter: TwilioVideoPresenter, user: User) {
        if (user.avatarUrl.isNullOrEmpty()) {
            Picasso.get().cancelRequest(binding.avatar)
        } else {
            Picasso.get().load(user.avatarUrl).into(binding.avatar)
        }
        binding.displayName.text = user.displayName
//        binding.description.text = PatientUtils.getPatientInfoString(itemView.context, user.userPatientMetadata)

        binding.displayName.setTextColor(ContextCompat.getColor(itemView.context, if (user.userPatientMetadata?.smsOptedOut == true) R.color.patient_opt_disabled_color else R.color.new_title_gray))

        val state = VoipStatusBinder.getState(presenter, user.id)
        VoipStatusBinder.bindStatus(binding.status, state, presenter.disconnectedReasonMap[user.id], user)

        val inviteButtonState = VoipStatusBinder.getInviteButtonState(presenter, user)
        binding.invite.visibility = if (inviteButtonState == ButtonState.GONE) View.GONE else View.VISIBLE
        binding.invite.isEnabled = inviteButtonState == ButtonState.CLICKABLE

        binding.invite.setOnClickListener { presenter.inviteParticipant(user.id, null, "p_id") }
    }

}