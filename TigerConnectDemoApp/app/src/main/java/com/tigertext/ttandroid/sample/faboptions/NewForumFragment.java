package com.tigertext.ttandroid.sample.faboptions;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.inbox.InboxFragment;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;

public class NewForumFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.forum_fragment, container, false);
        EditText forumName = view.findViewById(R.id.group_name_edit_text);
        EditText forumDescription = view.findViewById(R.id.message_edit_text);
        Button nextButton = view.findViewById(R.id.next_button);
        nextButton.setOnClickListener(v -> createForum(forumName.getText().toString(), forumDescription.getText().toString()));
        return view;
    }

    /**
     * This is how you create a forum
     * @param forumName the name of the forum
     * @param forumDescription the description of the forum
     */
    private void createForum(String forumName, String forumDescription) {
        String organizationID = SharedPrefs.getInstance().getString(SharedPrefs.ORGANIZATION_ID, "");
        // Call this method to notify our Roster Manager to create a forum in our server
        TT.getInstance().getRosterManager().createRoom(forumName, organizationID, null, forumDescription, null);
        Snackbar.make(getView(), "Your Forum " + forumName + " was created!", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
        goToInbox();
    }

    private void goToInbox() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.in, R.anim.out, R.anim.pop_in, R.anim.pop_out);
        ft.replace(R.id.fragment_container, new InboxFragment(), "Inbox Fragment").commit();
    }
}
