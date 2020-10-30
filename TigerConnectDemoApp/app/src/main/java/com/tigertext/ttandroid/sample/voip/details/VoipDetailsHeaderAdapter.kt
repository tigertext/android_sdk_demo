package com.tigertext.ttandroid.sample.voip.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.details.viewholder.VoipDetailsHeaderViewHolder

class VoipDetailsHeaderAdapter(private val clickListener: View.OnClickListener) : RecyclerView.Adapter<VoipDetailsHeaderViewHolder>() {

    var header: String? = null
        set(value) {
            if (field != value) {
                val oldField = field
                field = value
                when {
                    !oldField.isNullOrEmpty() && !value.isNullOrEmpty() -> notifyItemChanged(0)
                    oldField.isNullOrEmpty() && !value.isNullOrEmpty() -> notifyItemInserted(0)
                    !oldField.isNullOrEmpty() && value.isNullOrEmpty() -> notifyItemRemoved(0)
                }
            }
        }

    var showButton: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!header.isNullOrEmpty()) notifyItemChanged(0)
            }
        }

    override fun getItemCount() = if (header != null) 1 else 0

    override fun getItemViewType(position: Int) = R.layout.view_voip_details_header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoipDetailsHeaderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return VoipDetailsHeaderViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoipDetailsHeaderViewHolder, position: Int) {
        holder.onBind(header!!, showButton, clickListener)
    }
}