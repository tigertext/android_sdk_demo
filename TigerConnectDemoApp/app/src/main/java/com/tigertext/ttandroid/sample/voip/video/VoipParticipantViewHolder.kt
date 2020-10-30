package com.tigertext.voip.video

import androidx.recyclerview.widget.RecyclerView
import com.tigertext.ttandroid.sample.voip.video.VoipParticipantView
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipParticipantViewHolder(private val voipParticipantView: VoipParticipantView) : RecyclerView.ViewHolder(voipParticipantView) {

    fun bind(twilioVideoPresenter: TwilioVideoPresenter, userId: String) {
        voipParticipantView.bind(twilioVideoPresenter, userId)
    }

}
