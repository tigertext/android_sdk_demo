package com.tigertext.ttandroid.sample.inbox.viewmodel;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.pubsub.TTEvent;
import com.tigertext.ttandroid.pubsub.TTPubSub;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import timber.log.Timber;

public class InboxViewModel extends ViewModel implements TTPubSub.Listener {

    private static final String[] rosterListeners = new String[]{
            TTEvent.ROSTER_ENTRY_REMOVED,
            TTEvent.ROSTER_ENTRY_UPDATED,
            TTEvent.ROSTER_ENTRY_ADDED
    };

    private InboxRepository inboxRepository = new InboxRepository();

    public InboxViewModel() {
        Timber.d("InboxViewModel constructor called");
        // Register for updates for Roster Entries in Pub Sub
        TT.getInstance().getTTPubSub().addListeners(this, rosterListeners);
    }

    @Override
    protected void onCleared() {
        //Unsubscribe from PubSubs when this component gets cleared
        TT.getInstance().getTTPubSub().removeListeners(this, rosterListeners);
    }

    public LiveData<List<RosterEntry>> getRosterEntries() {
        return inboxRepository.getRosterEntriesLiveData();
    }

    public void updateRosterEntries() {
        inboxRepository.updateRosterEntries(getRandomOrganizationID());
    }

    /**
     * This method gets a random organization ID from a map of organizations that a user can be in,
     * and puts it in SharedPrefs
     * @return random organization ID to use
     */
    private String getRandomOrganizationID() {
        Random random = new Random();
        List<String> organizationIDs = new ArrayList<>(TT.getInstance().getOrganizationManager().getOrganizations().keySet());
        if (organizationIDs.size() > 0) {
            String randomOrganizationId = organizationIDs.get(random.nextInt(organizationIDs.size()));
            SharedPrefs.getInstance().putString(SharedPrefs.ORGANIZATION_ID, randomOrganizationId);
        }

        return SharedPrefs.getInstance().getString(SharedPrefs.ORGANIZATION_ID, Organization.CONSUMER_ORG_ID);
    }

    //This method will be called asynchronously every time the SDK fires an event related to the actions you registered on your PubSub
    @Override
    public void onEventReceived(@NonNull String event, @Nullable Object o) {
        if (getRosterEntries() == null || getRosterEntries().getValue() == null) return;
        switch (event) {
            case TTEvent.ROSTER_ENTRY_REMOVED: {
                final RosterEntry rosterEntry = (RosterEntry) o;

                // Check to see if roster entry is not null, and if the CURRENT organization ID
                // is the same as the one being passed down

                // If roster entry removed, find this roster entry in the adapter and remove it accordingly in the view
                if (getRosterEntries().getValue().contains(rosterEntry)) {

                }
            }
            break;
            case TTEvent.ROSTER_ENTRY_UPDATED: {
                final RosterEntry rosterEntry = (RosterEntry) o;

                // Check to see if roster entry is not null, and if the CURRENT organization ID
                // is the same as the one being passed down

                // If roster entry updated, find this roster entry in the adapter and update it accordingly in the view

            }
            break;
            case TTEvent.ROSTER_ENTRY_ADDED: {
                final RosterEntry rosterEntry = (RosterEntry) o;

                // Check to see if roster entry is not null, and if the CURRENT organization ID
                // is the same as the one being passed down

                // If roster entry add, find this roster entry in the adapter and add it accordingly in the view

            }
            break;
        }
    }
}
