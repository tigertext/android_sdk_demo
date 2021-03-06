package com.tigertext.ttandroid.sample.inbox.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;

import java.util.List;

import timber.log.Timber;

class InboxRepository {

    private MutableLiveData<List<RosterEntry>> rosterEntriesLiveData = new MutableLiveData<>();;

    LiveData<List<RosterEntry>> getRosterEntriesLiveData() {
        return rosterEntriesLiveData;
    }

    void updateRosterEntries(String randomOrganizationID) {
        // This is how you get all the inbox entries for a specific Organization
        // Checks to see if Inbox Entries was fetched recently. If they were, grab from database.
        // Else, make network request to fetch new roster entries.
        TT.getInstance().getRosterManager().getInboxEntries(randomOrganizationID, rosterEntries -> {
            Timber.d("Received List of Inbox Entries size: %s", rosterEntries.size());
            // Post the roster entries value into the MutableLiveData for all observers listening
            rosterEntriesLiveData.postValue(rosterEntries);
        });
    }
}
