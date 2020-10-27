package com.tigertext.ttandroid.sample.conversation.viewholder;

import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.User;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.sample.R;

public abstract class IncomingMessageHolder extends RecyclerView.ViewHolder {

    private final ImageView avatar;
    private final TextView name;
    private final TextView messageText;

    protected IncomingMessageHolder(View itemView) {
        super(itemView);
        itemView.setOnLongClickListener(v -> onLongClick(itemView));

        avatar = itemView.findViewById(R.id.outgoing_message_avatar);
        name = itemView.findViewById(R.id.message_sender);
        messageText = itemView.findViewById(R.id.message_text);
    }

    public void setUp(Message message) {
        if (message == null) return;

        // This is how you get the User from a message
        User user = (User) TT.getInstance().getUserManager().getParticipantLocally(message.getSenderId(), message.getSenderOrgId());
        if (user != null) {
            name.setText(user.getDisplayName());
            loadImage(avatar, user);
        }
        messageText.setText(message.getBody());
    }

    private void loadImage(ImageView avatar, User user) {
        Picasso.get()
                .load(Uri.parse(user.getAvatarUrl()))
                .into(avatar);
    }

    public abstract boolean onLongClick(View itemView);

}
