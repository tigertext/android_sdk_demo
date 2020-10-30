package com.tigertext.ttandroid.sample.voip.details.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.tigertext.ttandroid.Entity
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.UserPatientMetadata
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.databinding.ViewVoipParticipantDetailBinding
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import com.tigertext.ttandroid.search.SearchResult
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipDetailsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val binding = ViewVoipParticipantDetailBinding.bind(itemView)

    private val you by lazy { itemView.context.getString(R.string.you) }
    private val patientString by lazy { itemView.context.getString(R.string.patient) }

    fun bind(presenter: TwilioVideoPresenter, userId: String) {
        val isCurrentUser = userId == presenter.currentUserId
        val user = presenter.participantMap[userId]

        val subName = when {
            isCurrentUser -> you
            (user as? User)?.userPatientMetadata != null -> getPatientSubName((user as? User)?.userPatientMetadata!!)
            else -> null
        }
        bindDisplayName(user?.displayName ?: "", subName)

        if (user != null && !user.avatarUrl.isNullOrBlank()) {
            Picasso.get().load(user.avatarUrl).into(binding.voipAvatar)
        } else {
            Picasso.get().cancelRequest(binding.voipAvatar)
        }

        val state = VoipStatusBinder.getState(presenter, userId)
        val disconnectReason = presenter.disconnectedReasonMap[userId]
        VoipStatusBinder.bindStatus(binding.participantStatus, state, disconnectReason, user)
        binding.roleTagName.visibility = View.GONE

        val inviteButtonState = user?.let { VoipStatusBinder.getInviteButtonState(presenter, it) }
                ?: ButtonState.GONE
        binding.voipCall.visibility = if (inviteButtonState == ButtonState.GONE) View.GONE else View.VISIBLE
        binding.voipCall.isEnabled = inviteButtonState == ButtonState.CLICKABLE

        binding.voipCall.setOnClickListener {
            presenter.inviteParticipant(userId, null, patientIdName = (user as? User)?.userPatientMetadata?.let { if (it.isPatientContact) "pc_id" else "p_id" })
        }
    }

    fun bindSearch(presenter: TwilioVideoPresenter, searchResult: SearchResult, userId: String) {
        val isCurrentUser = userId == presenter.currentUserId

        val subName = when {
            isCurrentUser -> you
            searchResult.entity.userPatientMetadata != null -> getPatientSubName(searchResult.entity.userPatientMetadata!!)
            else -> null
        }
        bindDisplayName(searchResult.entity.displayName, subName)
        if (searchResult.entity.avatarUrl.isNullOrBlank()) Picasso.get().cancelRequest(binding.voipAvatar)
        else Picasso.get().load(searchResult.entity.avatarUrl).into(binding.voipAvatar)

        val state = VoipStatusBinder.getState(presenter, userId)
        if (searchResult.entity.isRole) {
            binding.roleTagName.text = searchResult.entity.roleTag?.tagName
            binding.roleTagName.visibility = if (searchResult.entity.roleTag?.isEmpty != true) View.VISIBLE else View.GONE
            val ownerName = searchResult.entity.roleOwners?.firstOrNull()?.displayName
            binding.participantStatus.text = itemView.context.getString(R.string.on_duty_user, ownerName)
            binding.participantStatus.visibility = if (ownerName.isNullOrEmpty()) View.GONE else View.VISIBLE
        } else {
            val disconnectReason = presenter.disconnectedReasonMap[userId]
            VoipStatusBinder.bindStatus(binding.participantStatus, state, disconnectReason, searchResult.entity)
            binding.roleTagName.visibility = View.GONE
        }

        val inviteButtonState = VoipStatusBinder.getInviteButtonState(presenter, searchResult.entity)
        binding.voipCall.visibility = if (inviteButtonState == ButtonState.GONE) View.GONE else View.VISIBLE
        binding.voipCall.isEnabled = inviteButtonState == ButtonState.CLICKABLE

        binding.voipCall.setOnClickListener {
            val roleId = if (searchResult.entity.isRole) searchResult.entity.token else null
            presenter.inviteParticipant(userId, roleId)
        }
    }

    private fun bindDisplayName(displayName: String, subName: String?) {
        binding.participantName.text = if (!subName.isNullOrEmpty()) {
            itemView.context.getString(R.string.join_string_parenthesis_string, displayName, subName)
        } else {
            displayName
        }
    }

    private fun getPatientSubName(userPatientMetadata: UserPatientMetadata) = if (userPatientMetadata.isPatientContact) userPatientMetadata.relationName else patientString

}
