package com.tigertext.ttandroid.sample.bottomsheet;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.tigertext.ttandroid.sample.R;

import org.jetbrains.annotations.NotNull;

public class BottomSheetOptions extends BottomSheetDialogFragment {

    public static final String TAG = "BottomSheetOptions";
    private BottomSheetOptionsAdapter bottomSheetOptionsAdapter;

    private BottomSheetOptionsAdapter.BottomDialogItemClick listener;

    @NotNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog bottomSheetDialog = super.onCreateDialog(savedInstanceState);
        bottomSheetDialog.setOnShowListener(dialog -> {
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(R.id.design_bottom_sheet);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        return bottomSheetDialog;
    }

    @Override
    public void setupDialog(Dialog dialog, int style) {
        View rootView = View.inflate(getContext(), R.layout.message_option_bottom_sheet, null);
        dialog.setContentView(rootView);
        setupRecyclerView(rootView);
        addDialogItems();
    }

    private void addDialogItems() {
        bottomSheetOptionsAdapter.setListener(new BottomSheetOptionsAdapter.BottomDialogItemClick() {
            @Override
            public void onItemSelected(View view, int position) {
                if (isStateSaved()) return;

                listener.onItemSelected(view, position);
                dismiss();
            }

            @Override
            public void onNothingSelected() {
                listener.onNothingSelected();
            }
        });

        if (getArguments() != null) {
            bottomSheetOptionsAdapter.addItemOptions(getArguments().getStringArrayList("itemOptions"));
        }
    }

    private void setupRecyclerView(View rootView) {
        RecyclerView recyclerMessageOptions = rootView.findViewById(R.id.message_options_recycler_view);
        bottomSheetOptionsAdapter = new BottomSheetOptionsAdapter();
        recyclerMessageOptions.setAdapter(bottomSheetOptionsAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerMessageOptions.setLayoutManager(linearLayoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerMessageOptions.getContext(),
                linearLayoutManager.getOrientation());
        recyclerMessageOptions.addItemDecoration(dividerItemDecoration);
    }

    public void setOnItemSelected(BottomSheetOptionsAdapter.BottomDialogItemClick listener) {
        this.listener = listener;
    }
}
