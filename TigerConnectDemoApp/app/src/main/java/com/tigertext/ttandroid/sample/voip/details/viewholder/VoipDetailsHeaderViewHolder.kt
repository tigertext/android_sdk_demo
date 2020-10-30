package com.tigertext.ttandroid.sample.voip.details.viewholder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.tigertext.ttandroid.sample.databinding.ViewVoipDetailsHeaderBinding

class VoipDetailsHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val binding = ViewVoipDetailsHeaderBinding.bind(view)

    fun onBind(text: String, showButton: Boolean, clickListener: View.OnClickListener) {
        binding.headerText.text = text
        binding.headerButton.visibility = if (showButton) View.VISIBLE else View.GONE
        binding.headerButton.setOnClickListener(clickListener)
    }

}