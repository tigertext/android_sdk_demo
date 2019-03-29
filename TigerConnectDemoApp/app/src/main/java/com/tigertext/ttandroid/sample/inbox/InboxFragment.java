package com.tigertext.ttandroid.sample.inbox;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.account.listener.LogoutListener;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.application.TigerConnectApplication;
import com.tigertext.ttandroid.sample.bottomsheet.BottomSheetOptions;
import com.tigertext.ttandroid.sample.bottomsheet.BottomSheetOptionsAdapter;
import com.tigertext.ttandroid.sample.conversation.ConversationFragment;
import com.tigertext.ttandroid.sample.conversation.viewmodel.ConversationViewModel;
import com.tigertext.ttandroid.sample.faboptions.NewForumFragment;
import com.tigertext.ttandroid.sample.faboptions.NewGroupFragment;
import com.tigertext.ttandroid.sample.inbox.viewmodel.InboxViewModel;
import com.tigertext.ttandroid.sample.login.LoginFragment;

import java.util.ArrayList;
import java.util.Arrays;

import timber.log.Timber;

public class InboxFragment extends Fragment implements InboxAdapter.InboxItemClickListener {

    private static final String NEW_FORUM = "New Forum";
    private static final String NEW_GROUP = "New Group";

    private static final int NEW_FORUM_INDEX = 0;
    private static final int NEW_GROUP_INDEX = 1;

    private RecyclerView recyclerInbox;
    private InboxAdapter inboxAdapter;
    private ConversationViewModel conversationViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Timber.d("InboxContactsFragment onCreateView");
        View rootView = inflater.inflate(R.layout.inbox_fragment, container, false);
        recyclerInbox = rootView.findViewById(R.id.recycler_inbox);
        inboxAdapter = new InboxAdapter(this);
        setupRecyclerView();
        Button logoutButton = rootView.findViewById(R.id.logout_button);
        logoutButton.setOnClickListener(v -> onLogoutClicked());
        FloatingActionButton fab = rootView.findViewById(R.id.fab);
        fab.setOnClickListener(view -> openInboxOptions());
        return rootView;
    }

    private void openInboxOptions() {
        BottomSheetOptions bottomSheetOptions = new BottomSheetOptions();
        bottomSheetOptions.setArguments(getFABOptions());
        bottomSheetOptions.setOnItemSelected(new BottomSheetOptionsAdapter.BottomDialogItemClick() {
            @Override
            public void onItemSelected(View view, int position) {
                switch(position) {
                    case NEW_FORUM_INDEX:
                        goToFragment(new NewForumFragment(), "Forum Fragment");
                        break;
                    case NEW_GROUP_INDEX:
                        goToFragment(new NewGroupFragment(), "New Group Fragment");
                        break;
                }
            }

            @Override
            public void onNothingSelected() {
            }
        });

        if (getFragmentManager() != null) {
            bottomSheetOptions.show(getFragmentManager(), BottomSheetOptions.TAG);
        }
    }

    private Bundle getFABOptions() {
        ArrayList<String> fabOptions = new ArrayList<>(Arrays.asList(
                NEW_FORUM,
                NEW_GROUP));
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("itemOptions", fabOptions);
        return bundle;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Timber.d("InboxContactsFragment onActivityCreated");
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
    }

    private void setupViewModel() {
        InboxViewModel inboxViewModel = ViewModelProviders.of(this).get(InboxViewModel.class);
        inboxViewModel.updateRosterEntries();
        inboxViewModel.getRosterEntries().observe(this, rosterEntries -> {
            if (rosterEntries == null || rosterEntries.size() == 0) {
                Toast.makeText(getContext(), "There are no roster entries in this organization!", Toast.LENGTH_LONG).show();
            }

            inboxAdapter.updateRosterEntries(rosterEntries);
        });

        conversationViewModel = ViewModelProviders.of(getActivity()).get(ConversationViewModel.class);
        conversationViewModel.init();
    }

    private void setupRecyclerView() {
        recyclerInbox.setAdapter(inboxAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerInbox.setLayoutManager(linearLayoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerInbox.getContext(),
                linearLayoutManager.getOrientation());
        recyclerInbox.addItemDecoration(dividerItemDecoration);
    }

    private void goToFragment(Fragment fragment, String tag) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.in, R.anim.out, R.anim.pop_in, R.anim.pop_out);
        ft.replace(R.id.fragment_container, fragment, tag).commit();
    }

    private void onLogoutClicked() {
        TT.getInstance().getAccountManager().logout(new LogoutListener() {
            @Override
            public void onLoggedOut() {
                //Handle logout
                Timber.d("onLoggedOut");
                goToFragment(new LoginFragment(), "Inbox Fragment");
            }

            @Override
            public void onLogoutError(Throwable throwable) {
                //Handle logout error
                Timber.e(throwable, "Logout Error");
                Toast.makeText(getContext(), "Logout Failed", Toast.LENGTH_LONG).show();
            }
        });

        // Terminate our Real Time Events Service when user logs out
        TigerConnectApplication.getApp().terminateRealTimeEventsService();
    }

    @Override
    public void onInboxItemClicked(RosterEntry rosterEntry) {
        Timber.d("onInboxItemClicked");
        conversationViewModel.selectConversation(rosterEntry);
        goToConversation();
    }

    private void goToConversation() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.setCustomAnimations(R.anim.in, R.anim.out, R.anim.pop_in, R.anim.pop_out);
        ft.replace(R.id.fragment_container, new ConversationFragment(), "Conversation Fragment").addToBackStack(null).commit();
    }
}
