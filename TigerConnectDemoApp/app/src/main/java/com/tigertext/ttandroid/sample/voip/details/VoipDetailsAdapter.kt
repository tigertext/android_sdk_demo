package com.tigertext.ttandroid.sample.voip.details

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.details.viewholder.VoipDetailsViewHolder
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipDetailsAdapter(val presenter: TwilioVideoPresenter) : RecyclerView.Adapter<VoipDetailsViewHolder>() {

    private val userIds = ArrayList<String>()
    private val disconnectedUserIds = ArrayList<String>()

    fun addOrUpdate(userId: String) {
        if (presenter.disconnectedUserIds.contains(userId)) {
            val oldIndex = userIds.indexOf(userId)
            if (oldIndex >= 0) {
                val oldPosition = getUserIdOffset() + oldIndex
                notifyItemChanged(oldPosition)
                userIds.removeAt(oldIndex)
                disconnectedUserIds.add(userId)
                val newPosition = getDisconnectedUserIdOffset() + disconnectedUserIds.lastIndex
                notifyItemMoved(oldPosition, newPosition)
            } else {
                var userIdIndex = disconnectedUserIds.indexOf(userId)
                if (userIdIndex >= 0) {
                    notifyItemChanged(getDisconnectedUserIdOffset() + userIdIndex)
                } else {
                    disconnectedUserIds.add(userId)
                    userIdIndex = disconnectedUserIds.lastIndex
                    notifyItemInserted(getDisconnectedUserIdOffset() + userIdIndex)
                }
            }
        } else {
            val oldIndex = disconnectedUserIds.indexOf(userId)
            if (oldIndex >= 0) {
                val oldPosition = getDisconnectedUserIdOffset() + oldIndex
                notifyItemChanged(oldPosition)
                disconnectedUserIds.removeAt(oldIndex)
                userIds.add(userId)
                val newPosition = getUserIdOffset() + userIds.lastIndex
                notifyItemMoved(oldPosition, newPosition)
            } else {
                var userIdIndex = userIds.indexOf(userId)
                if (userIdIndex >= 0) {
                    notifyItemChanged(getUserIdOffset() + userIdIndex)
                } else {
                    userIds.add(userId)
                    userIdIndex = userIds.lastIndex
                    notifyItemInserted(getUserIdOffset() + userIdIndex)
                }
            }
        }
    }

    fun remove(userId: String): Boolean {
        var oldIndex = userIds.indexOf(userId)
        if (oldIndex >= 0) {
            userIds.removeAt(oldIndex)
            notifyItemRemoved(getUserIdOffset() + oldIndex)
            return true
        }
        oldIndex = disconnectedUserIds.indexOf(userId)
        if (oldIndex >= 0) {
            disconnectedUserIds.removeAt(oldIndex)
            notifyItemRemoved(getDisconnectedUserIdOffset() + oldIndex)
            return true
        }
        return false
    }

    override fun getItemViewType(position: Int) = R.layout.view_voip_participant_detail

    private fun getUserIdOffset() = 0

    private fun getDisconnectedUserIdOffset() = getUserIdOffset() + userIds.size

    override fun getItemCount() = userIds.size + disconnectedUserIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoipDetailsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return VoipDetailsViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoipDetailsViewHolder, position: Int) {
        val isDisconnected = position > userIds.lastIndex
        val userId = if (isDisconnected) disconnectedUserIds[position - userIds.size] else userIds[position]

        holder.bind(presenter, userId)
    }

}
