package com.tigertext.ttandroid.sample.conversation.viewholder;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.sample.R;

public abstract class OutgoingMessageHolder extends RecyclerView.ViewHolder {

    private final TextView messageText;
    private final TextView readStatusText;

    protected OutgoingMessageHolder(View itemView) {
        super(itemView);
        itemView.setOnLongClickListener(v -> onLongClick(itemView));
        messageText = itemView.findViewById(R.id.outgoing_message_text);
        readStatusText = itemView.findViewById(R.id.message_status);
    }

    public void setUp(Message message) {
        messageText.setText(message.getBody());
        readStatusText.setText(message.getStatus().name());
    }
    public abstract boolean onLongClick(View itemView);
}
