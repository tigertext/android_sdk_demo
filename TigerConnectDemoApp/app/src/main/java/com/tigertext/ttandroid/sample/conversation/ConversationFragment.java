package com.tigertext.ttandroid.sample.conversation;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.account.listener.GenericAPICallStatusListener;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.exceptions.TTException;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.pubsub.TTEvent;
import com.tigertext.ttandroid.pubsub.TTPubSub;
import com.tigertext.ttandroid.sample.R;
import com.tigertext.ttandroid.sample.bottomsheet.BottomSheetOptions;
import com.tigertext.ttandroid.sample.bottomsheet.BottomSheetOptionsAdapter;
import com.tigertext.ttandroid.sample.conversation.viewmodel.ConversationViewModel;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;
import com.tigertext.ttandroid.settings.SettingType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;


public class ConversationFragment extends Fragment implements ConversationAdapter.ConversationOnClickListener, TTPubSub.Listener {

    private static final int PAGE_SIZE = 101;
    private static final String ORGANIZATION_ID =  SharedPrefs.getInstance().getString(SharedPrefs.ORGANIZATION_ID, Organization.CONSUMER_ORG_ID);

    private static final String RESEND = "Resend";
    private static final String RECALL = "Recall";
    private static final String FORWARD = "Forward";
    private static final String RESEND_AS_PRIORITY_MESSAGE = "Resend as Priority Message";

    private static final int RESEND_INDEX = 0;
    private static final int RECALL_INDEX = 1;
    private static final int FORWARD_INDEX = 2;
    private static final int RESEND_AS_PRIORITY_MESSAGE_INDEX = 3;

    private static final String[] listeners = new String[]{
            TTEvent.MESSAGE_FAILED,
            TTEvent.MESSAGE_ADDED,
            TTEvent.MESSAGE_SENT,
            TTEvent.MESSAGE_STATUS_RECEIVED,
            TTEvent.MESSAGES_REMOVED,
            TTEvent.MESSAGES_UPDATED,
            TTEvent.MESSAGE_UPDATED,
            TTEvent.MESSAGE_STATUS_RECEIVED
    };


    private EditText messageEditText;
    private CheckBox priorityCheckBox;
    private RecyclerView recyclerConversation;
    private TextView conversationName;
    private ConversationAdapter conversationAdapter;
    LinearLayoutManager linearLayoutManager;
    private RosterEntry mRosterEntry;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_fragment, container, false);
        recyclerConversation = rootView.findViewById(R.id.recycler_conversation);
        conversationName = rootView.findViewById(R.id.conversation_name);
        priorityCheckBox = rootView.findViewById(R.id.priority_checkbox);
        setupRecyclerView();
        messageEditText = rootView.findViewById(R.id.message_edit_text);
        Button autoForwardButton = rootView.findViewById(R.id.auto_forward_button);
        autoForwardButton.setOnClickListener(v -> autoForwardToThisUser());
        Button sendButton = rootView.findViewById(R.id.send_button);
        sendButton.setOnClickListener(v -> sendMessage(messageEditText.getText().toString()));
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupViewModel();
        TTPubSub.getInstance().addListeners(this, listeners);
    }

    @Override
    public void onResume() {
        super.onResume();

        // If the adapter is not null and the item count is not 0, then we know messages have been loaded.
        // mark any messages as read in case the Activity is going from onResume due to the phone being in sleep state
        // or app being backgrounded.
        if (conversationAdapter != null && conversationAdapter.getItemCount() != 0) {
            // This is how you mark a conversation as read
            TT.getInstance().getConversationManager().markConversationAsRead(mRosterEntry);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // This is how you mark a conversation as read
            TT.getInstance().getConversationManager().markConversationAsRead(mRosterEntry);
        }
    }

    private void setupRecyclerView() {
        conversationAdapter = new ConversationAdapter(this);
        recyclerConversation.setAdapter(conversationAdapter);
        linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerConversation.setLayoutManager(linearLayoutManager);
    }

    private void setupViewModel() {
        ConversationViewModel conversationViewModel = ViewModelProviders.of(getActivity()).get(ConversationViewModel.class);
        conversationViewModel.init();
        mRosterEntry = conversationViewModel.getSelectedRosterEntry().getValue();
        conversationViewModel.getSelectedRosterEntry().observe(this, rosterEntry -> {
            if (rosterEntry == null) return;

            mRosterEntry = rosterEntry;
            conversationName.setText(rosterEntry.getDisplayName());
        });

        conversationViewModel.updateMessages(mRosterEntry, PAGE_SIZE, null);
        conversationViewModel.getMessages().observe(this, messages -> {
            if (messages == null) return;

            Timber.d("Messages size: %s", messages.size());
            conversationAdapter.updateMessages(messages);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        TTPubSub.getInstance().removeListeners(this, listeners);
    }


    /**
     *  This is how you auto forward your messages to this user
     */
    private void autoForwardToThisUser() {
        Timber.d("autoForwardToThisUser");
        Map<SettingType, Object> modifiedSettingsMap = new ArrayMap<>(1);
        modifiedSettingsMap.put(SettingType.DND_AUTO_FORWARD_RECEIVER, mRosterEntry.getId());
        // Call this method to notify our Organization Manager to modify the organization settings
        // and set this user as the recipient of our auto forwarded messages
        TT.getInstance().getOrganizationManager().modifyOrganizationSettings(mRosterEntry.getOrgId(), modifiedSettingsMap, new GetUpdateOrgSettingsListener(this));
    }

    /**
     * This is how you send a message in a conversation
     * @param messageToSend the message that is to be sent
     */
    private void sendMessage(String messageToSend) {
        int priority = 0;
        if (priorityCheckBox.isChecked()) {
            Timber.d("Priority is checked!");
            priority = 1;
            priorityCheckBox.setChecked(false);
        }
        int ttl = 0;
        boolean dor = false;
        try {
            ttl = (int) TT.getInstance().getSettingsManager().get(ORGANIZATION_ID, SettingType.TTL);
            dor = (boolean) TT.getInstance().getSettingsManager().get(ORGANIZATION_ID, SettingType.DOR);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Message messageForSend = null;
        try {
            // Generate the Message Object to be sent
            messageForSend = Message.messageForSend(messageToSend, priority,
                    mRosterEntry, ttl, null, null, dor);
        } catch (TTException e) {
            e.printStackTrace();
        }
        if (messageForSend != null) {
            // Notify our Conversation Manager of this message being sent
            TT.getInstance().getConversationManager().sendMessage(messageForSend);
            Timber.d("Message sent!");
        }
        messageEditText.getText().clear();
    }

    @Override
    public void onConversationLongClick(Message message) {
        Timber.d("onConversationLongClick");
        BottomSheetOptions bottomSheetOptions = new BottomSheetOptions();
        bottomSheetOptions.setArguments(getMessageOptions());
        bottomSheetOptions.setOnItemSelected(new BottomSheetOptionsAdapter.BottomDialogItemClick() {
            @Override
            public void onItemSelected(View view, int position) {
                switch (position) {
                    case RESEND_INDEX:
                        resendMessage(message);
                        break;
                    case RECALL_INDEX:
                        recallMessage(message);
                        break;
                    case FORWARD_INDEX:
                        forwardMessage(message);
                        break;
                    case RESEND_AS_PRIORITY_MESSAGE_INDEX:
                        resendAsPriority(message);
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

    private void resendMessage(Message message) {
        Timber.d("Resend Message: %s", message.getBody());
        // Notify our Conversation Manager that this message needs to be resent
        TT.getInstance().getConversationManager().resendMessage(message.getMessageId());
        conversationAdapter.addMessage(message);
        scrollToBottom();
    }

    private void recallMessage(Message message) {
        Timber.d("Recall Message");
        // Notify our Conversation Manager that this message needs to be recalled
        TT.getInstance().getConversationManager().recallMessage(message.getMessageId());
    }

    private void forwardMessage(Message message) {
        Timber.d("Forward Message");
        // Notify our Conversation Manager that this message is to be forwarded
        TT.getInstance().getConversationManager().forwardMessage(message.getMessageId(), message);
    }

    private void resendAsPriority(Message message) {
        Timber.d("Resend as Priority Message");
        message.setPriority(1);
        // Notify our Conversation Manager that this message will be resent with priority
        resendMessage(message);
    }

    private Bundle getMessageOptions() {
        ArrayList<String> messageOptions = new ArrayList<>(Arrays.asList(
                RESEND,
                RECALL,
                FORWARD,
                RESEND_AS_PRIORITY_MESSAGE));
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("itemOptions", messageOptions);
        return bundle;
    }

    @Override
    public void onEventReceived(@NonNull String event, @Nullable Object o) {
        if (getActivity() == null) {
            return;
        }

        switch (event) {
            case TTEvent.MESSAGE_ADDED: {
                /**
                 *  It may also be useful to check if the fragment is alive as well!
                 */
                // Confirm that this new message is for this conversation and organization,
                // if not, return early
                if (!isEventForRosterEntryOrg(o)) return;

                Timber.d("Message Added");
                Message message = (Message) o;

                /**
                 onEventReceived is done on a background thread, so make sure to publish your logic
                 on the UI thread
                 */
                getActivity().runOnUiThread(() -> {
                    if (getUserVisibleHint()) {
                        // This is how you mark a conversation as read
                        TT.getInstance().getConversationManager().markConversationAsRead(mRosterEntry);
                    }
                    conversationAdapter.addMessage(message);
                    scrollToBottom();
                });
            }
            break;
            case TTEvent.MESSAGE_UPDATED: {
                if (!isEventForRosterEntryOrg(o)) return;
                Timber.d("Message Updated");
            }
            break;
            case TTEvent.MESSAGES_REMOVED: {
                Timber.d("Messages Removed");
                List<Message> messages = (List<Message>) o;
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    conversationAdapter.removeMessages(messages);
                });
            }
            break;
            case TTEvent.MESSAGE_STATUS_RECEIVED: {
                // This event represents a message status being marked as delivered/read/etc.
                Timber.d("Message Status Received!");
                Bundle b = (Bundle) o;
                if (!isEventForRosterEntryOrg(b)) return;

                String statusString = b.getString(com.tigertext.ttandroid.constant.TTConstants.STATUS);
                Message.Status status = null;
                if (statusString != null) {
                    status = Message.Status.valueOf(statusString.toUpperCase());
                }
                if (status == Message.Status.DELIVERED && mRosterEntry.isGroup()) {
                    /***
                     * If status is Delivered and its a group, do nothing
                     */
                    return;
                }

                final Message m = b.getParcelable(com.tigertext.ttandroid.constant.TTConstants.MESSAGE);
                if (m != null && mRosterEntry.getId().equals(m.getRosterId())) {
                    getActivity().runOnUiThread(() -> {
                        conversationAdapter.markMessageAsRead(m);
                    });
                }
            }
            break;
        }
    }

    private boolean isEventForRosterEntryOrg(Object obj) {
        if (obj instanceof Bundle) {
            Bundle bundle = (Bundle) obj;
            Message message = bundle.getParcelable(TTEvent.EXTRA_MESSAGE);
            return isEventOrgSameAsEntryOrg(message != null ? message.getRecipientOrgId() : null);
        }

        if (obj instanceof Message) {
            Message message = (Message) obj;
            return isEventOrgSameAsEntryOrg(message.getRecipientOrgId());
        }
        return false;
    }

    private boolean isEventOrgSameAsEntryOrg(String organizationID) {
        if (organizationID == null) return false;

        String rosterEntryOrganization = mRosterEntry != null ?
                mRosterEntry.getOrgId() : "";
        return rosterEntryOrganization.equals(organizationID);
    }

    private void scrollToBottom() {
        int position = conversationAdapter.getItemCount() - 1;
        if (position > 0) {
            linearLayoutManager.scrollToPosition(position);
        }
    }

    private static class GetUpdateOrgSettingsListener implements GenericAPICallStatusListener {
        WeakReference<ConversationFragment> mFragmentWR;

        GetUpdateOrgSettingsListener(ConversationFragment fragment) {
            mFragmentWR = new WeakReference<>(fragment);
        }

        @Override
        public void onSuccess() {
            ConversationFragment fragment = mFragmentWR.get();
            Toast.makeText(fragment.getContext(), "Successfully modified org settings for Autoforwarding", Toast.LENGTH_LONG).show();
        }

        @Override
        public void onError(Throwable error) {
            ConversationFragment fragment = mFragmentWR.get();
            Toast.makeText(fragment.getContext(), "Error modifying org settings", Toast.LENGTH_LONG).show();
        }
    }
}
