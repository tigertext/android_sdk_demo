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

import java.util.Arrays;
import java.util.List;

public class NewGroupFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.group_fragment, container, false);
        EditText groupName = view.findViewById(R.id.group_name_edit_text);
        EditText groupUsers = view.findViewById(R.id.users_edit_text);
        Button createGroupButton = view.findViewById(R.id.create_group_button);
        createGroupButton.setOnClickListener(v -> createGroup(groupName.getText().toString(), getUsers(groupUsers.getText().toString())));
        return view;
    }

    private List<String> getUsers(String users) {
        String[] split = users.split(",");
        return Arrays.asList(split);
    }

    /**
     * This is how you create a group
     * @param groupName the name of the group
     * @param users a list of users to be added into the group
     */
    private void createGroup(String groupName, List<String> users) {
        String organizationID = SharedPrefs.getInstance().getString(SharedPrefs.ORGANIZATION_ID, "");
        // Call this method to notify our Roster Manager to create a group in our server
        TT.getInstance().getRosterManager().createGroup(users, organizationID, groupName, null);
        Snackbar.make(getView(), "Your group " + groupName + " was created!", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
        goToInbox();
    }

    private void goToInbox() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.in, R.anim.out, R.anim.pop_in, R.anim.pop_out);
        ft.replace(R.id.fragment_container, new InboxFragment(), "Inbox Fragment").commit();
    }

}
