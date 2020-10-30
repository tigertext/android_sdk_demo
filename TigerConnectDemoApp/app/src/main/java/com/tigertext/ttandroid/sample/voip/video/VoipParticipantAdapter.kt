package com.tigertext.ttandroid.sample.voip.video

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import com.tigertext.voip.video.VoipParticipantViewHolder

class VoipParticipantAdapter(val presenter: TwilioVideoPresenter) : RecyclerView.Adapter<VoipParticipantViewHolder>() {

    override fun getItemCount() = presenter.userIds.size

    override fun getItemViewType(position: Int) = R.layout.view_voip_participant_tile

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoipParticipantViewHolder {
        val participantView = LayoutInflater.from(parent.context).inflate(viewType, parent, false) as VoipParticipantView
        return VoipParticipantViewHolder(participantView)
    }

    override fun onBindViewHolder(holder: VoipParticipantViewHolder, position: Int) {
        val userId = presenter.userIds[position]
        holder.bind(presenter, userId)
        holder.itemView.setOnClickListener {
            val index = presenter.userIds.indexOf(userId)
            if (index < presenter.userIds.lastIndex && presenter.currentUserId != userId) {
                val lastIndex = presenter.userIds.lastIndex
                presenter.userIds.removeAt(index)
                presenter.userIds.add(lastIndex, userId)
                notifyItemMoved(index, lastIndex)
            }
        }
    }

}
