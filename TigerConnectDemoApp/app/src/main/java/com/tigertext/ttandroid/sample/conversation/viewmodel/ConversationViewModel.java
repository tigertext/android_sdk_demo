package com.tigertext.ttandroid.sample.conversation.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.pubsub.TTEvent;
import com.tigertext.ttandroid.pubsub.TTPubSub;

import java.util.Collection;
import java.util.List;

import timber.log.Timber;

public class ConversationViewModel extends ViewModel implements TTPubSub.Listener {
    private MutableLiveData<RosterEntry> selectedRosterEntry;
    private MutableLiveData<Organization> organization;

    private ConversationRepository conversationRepository = new ConversationRepository();

    private String currentOrganizationId;

    private static final String[] listeners = new String[]{
            TTEvent.ORGS_UPDATED,
            TTEvent.ROSTER_ENTRY_UPDATED,
            TTEvent.MESSAGE_ADDED,
            TTEvent.MESSAGES_REMOVED
    };

    public ConversationViewModel() {
        // Subscribe to PubSubs when component gets created
        TT.getInstance().getTTPubSub().addListeners(this, listeners);
    }

    public void init() {
        if (selectedRosterEntry != null & organization != null) return;

        selectedRosterEntry = new MutableLiveData<>();
        organization = new MutableLiveData<>();
    }

    @Override
    protected void onCleared() {
        // Unsubscribe from PubSubs when this component gets cleared
        TT.getInstance().getTTPubSub().removeListeners(this, listeners);
    }

    public MutableLiveData<RosterEntry> getSelectedRosterEntry() {
        return selectedRosterEntry;
    }

    public MutableLiveData<Organization> getOrganization() {
        return organization;
    }

    public LiveData<List<Message>> getMessages() {
        return conversationRepository.getMessages();
    }

    public void updateMessages(RosterEntry rosterEntry, int pageSize, Message topMessage) {
        conversationRepository.updateMessagesByPage(rosterEntry, pageSize, topMessage);
    }

    @Override
    public void onEventReceived(@NonNull String event, @Nullable Object o) {
        switch (event) {
            case TTEvent.ORGS_UPDATED: {
                final Collection<Organization> orgs = (Collection<Organization>) o;
                if (orgs == null) return;

                for (Organization org : orgs) {
                    if (org.getToken().equals(currentOrganizationId)) {
                        organization.postValue(org);
                    }
                }
            }
            break;
            case TTEvent.ROSTER_ENTRY_UPDATED: {
                final RosterEntry re = (RosterEntry) o;
                if (re == null) return;
                if (re.equals(selectedRosterEntry.getValue())) {
                    selectedRosterEntry.postValue(re);
                }
            }
            break;
        }
    }

    public String getCurrentOrganizationId() {
        return currentOrganizationId;
    }

    public void setCurrentOrganizationId(String currentOrganizationId) {
        this.currentOrganizationId = currentOrganizationId;
    }

    public void selectConversation(RosterEntry rosterEntry) {
        selectedRosterEntry.setValue(rosterEntry);
    }
}
