package com.tigertext.ttandroid.sample.conversation;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.conversation.viewholder.IncomingMessageHolder;
import com.tigertext.ttandroid.sample.conversation.viewholder.OutgoingMessageHolder;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class ConversationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int OUTGOING_MESSAGE = R.layout.outgoing_message;
    private static final int INCOMING_MESSAGE = R.layout.incoming_message;

    private final List<Message> messageList = new ArrayList<>();

    private ConversationOnClickListener conversationOnClickListener;

    ConversationAdapter(ConversationOnClickListener conversationOnClickListener) {
        this.conversationOnClickListener = conversationOnClickListener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        switch (viewType) {
            case OUTGOING_MESSAGE:
                return new OutgoingMessageHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(OUTGOING_MESSAGE, viewGroup, false)) {
                    @Override
                    public boolean onLongClick(View itemView) {
                        final int position = this.getAdapterPosition();
                        conversationOnClickListener.onConversationLongClick(messageList.get(position));
                        return false;
                    }
                };
            case INCOMING_MESSAGE:
                return new IncomingMessageHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(INCOMING_MESSAGE, viewGroup, false)) {
                    @Override
                    public boolean onLongClick(View itemView) {
                        final int position = this.getAdapterPosition();
                        conversationOnClickListener.onConversationLongClick(messageList.get(position));
                        return false;
                    }
                };
        }

        throw new IllegalStateException("Unexpected view type: " + viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
        if (viewHolder instanceof OutgoingMessageHolder) {
            ((OutgoingMessageHolder) viewHolder).setUp(messageList.get(i));
        } else if (viewHolder instanceof IncomingMessageHolder) {
            ((IncomingMessageHolder) viewHolder).setUp(messageList.get(i));
        }
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        return message.isMine() ? OUTGOING_MESSAGE : INCOMING_MESSAGE;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    void updateMessages(List<Message> messages) {
        messageList.clear();
        messageList.addAll(messages);
        notifyDataSetChanged();
    }

    void removeMessages(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            if (messageList.remove(messages.get(i))) {
                notifyDataSetChanged();
            }
        }
    }

    void addMessage(Message message) {
        messageList.add(message);
        notifyItemChanged(messageList.size() - 1);
    }

    void markMessageAsRead(Message m) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            if (m.getBody().equals(messageList.get(i).getBody())) {
                Timber.d("Message found and marking as read");
                messageList.set(i, m);
                notifyItemChanged(i);
            }
        }
    }

    public interface ConversationOnClickListener {
        void onConversationLongClick(Message message);
    }
}
