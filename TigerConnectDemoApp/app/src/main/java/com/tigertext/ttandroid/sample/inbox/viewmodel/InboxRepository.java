package com.tigertext.ttandroid.sample.inbox.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.widget.Toast;

import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.org.Organization;
import com.tigertext.ttandroid.sample.utils.SharedPrefs;

import java.util.List;

import timber.log.Timber;

class InboxRepository {

    private MutableLiveData<List<RosterEntry>> rosterEntriesLiveData;

    LiveData<List<RosterEntry>> getRosterEntriesLiveData() {
        return rosterEntriesLiveData;
    }

    void updateRosterEntries(String randomOrganizationID) {
        if (rosterEntriesLiveData != null) return;

        rosterEntriesLiveData = new MutableLiveData<>();

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
