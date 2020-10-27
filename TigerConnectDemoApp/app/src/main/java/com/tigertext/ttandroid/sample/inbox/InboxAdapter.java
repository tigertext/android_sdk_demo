package com.tigertext.ttandroid.sample.inbox;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.sample.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.InboxViewHolder> {

    private final List<RosterEntry> rosterEntryList = new ArrayList<>();

    private InboxItemClickListener inboxItemClickListener;

    InboxAdapter(@NonNull InboxItemClickListener inboxItemClickListener) {
        this.inboxItemClickListener = inboxItemClickListener;
    }

    @NonNull
    @Override
    public InboxViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new InboxViewHolder(LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.inbox_cell, viewGroup, false)) {
            @Override
            public void onItemClick(View itemView) {
                final int position = this.getAdapterPosition();
                inboxItemClickListener.onInboxItemClicked(rosterEntryList.get(position));
            }
        };
    }

    @Override
    public void onBindViewHolder(@NonNull InboxViewHolder inboxViewHolder, int i) {
        inboxViewHolder.setUp(rosterEntryList.get(i));
    }

    @Override
    public int getItemCount() {
        return rosterEntryList.size();
    }

    void updateRosterEntries(List<RosterEntry> rosterEntries) {
        rosterEntryList.clear();
        rosterEntryList.addAll(rosterEntries);
        notifyDataSetChanged();
    }

    abstract class InboxViewHolder extends RecyclerView.ViewHolder {

        private final ImageView avatar;
        private final TextView title;
        private final TextView subtext;

        InboxViewHolder(@NonNull View itemView) {
            super(itemView);

            itemView.setOnClickListener(v -> {
                onItemClick(itemView);
            });

            avatar = itemView.findViewById(R.id.avatar);
            title = itemView.findViewById(R.id.title);
            subtext = itemView.findViewById(R.id.subtext);
        }
        public void setUp(final RosterEntry rosterEntry) {
            loadImage(avatar, rosterEntry);
            title.setText(rosterEntry.getDisplayName());
            String latestMessage = rosterEntry.getLatestMessage() == null ? "" : rosterEntry.getLatestMessage().getBody();
            subtext.setText(latestMessage);
        }

        private void loadImage(ImageView avatar, RosterEntry rosterEntry) {
            Picasso.get()
                    .load(Uri.parse(rosterEntry.getAvatarUrl()))
                    .into(avatar);
        }

        public abstract void onItemClick(View itemView);
    }

    public interface InboxItemClickListener {
        void onInboxItemClicked(RosterEntry rosterEntry);
    }
}
