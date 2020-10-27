package com.tigertext.ttandroid.sample.bottomsheet;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.tigertext.ttandroid.sample.R;

import java.util.ArrayList;
import java.util.List;

public class BottomSheetOptionsAdapter extends RecyclerView.Adapter<BottomSheetOptionsAdapter.BottomSheetItemHolder> {

    private final List<String> messageOptions = new ArrayList<>();
    private BottomDialogItemClick listener;

    @NonNull
    @Override
    public BottomSheetItemHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        return new BottomSheetItemHolder(LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.conversation_message_option_item, viewGroup, false)) {
            @Override
            public void onItemClick(View itemView) {
                final int position = getAdapterPosition();
                listener.onItemSelected(itemView, position);
            }
        };
    }

    @Override
    public void onBindViewHolder(@NonNull BottomSheetItemHolder bottomSheetItemHolder, int i) {
        bottomSheetItemHolder.setup(messageOptions.get(i));
    }

    void addItemOptions(List<String> options) {
        messageOptions.clear();
        messageOptions.addAll(options);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return messageOptions.size();
    }

    void setListener(BottomDialogItemClick listener) {
        this.listener = listener;
    }

    public interface BottomDialogItemClick {
        void onItemSelected(View view, int position);
        void onNothingSelected();
    }

    abstract class BottomSheetItemHolder extends RecyclerView.ViewHolder {
        private final TextView optionText;

        BottomSheetItemHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setOnClickListener(v -> {
                onItemClick(itemView);
            });

            optionText = itemView.findViewById(R.id.message_option_text);
        }

        void setup(String option) {
            optionText.setText(option);
        }

        public abstract void onItemClick(View itemView);
    }
}
