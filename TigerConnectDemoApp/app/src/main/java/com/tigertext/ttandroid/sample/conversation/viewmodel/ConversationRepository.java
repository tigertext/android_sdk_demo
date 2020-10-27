package com.tigertext.ttandroid.sample.conversation.viewmodel;


import androidx.lifecycle.MutableLiveData;

import com.tigertext.ttandroid.Message;
import com.tigertext.ttandroid.RosterEntry;
import com.tigertext.ttandroid.api.TT;
import com.tigertext.ttandroid.http.GenericActionListener;

import java.util.List;

import timber.log.Timber;

class ConversationRepository {

    private MutableLiveData<List<Message>> messagesLiveData = new MutableLiveData<>();

    MutableLiveData<List<Message>> getMessages() {
        return messagesLiveData;
    }

    /**
     * This is how you get a List of Messages to display in your conversation
     * @param rosterEntry the Roster Entry of the conversation
     * @param pageSize The number of messages you want for your conversation page
     * @param topMessage The top message
     */
    void updateMessagesByPage(RosterEntry rosterEntry, int pageSize, Message topMessage) {
        /**
            This is how to get a specific conversation for a Roster Entry
         */
        TT.getInstance().getConversationManager().getMessagesByPage(rosterEntry, pageSize, topMessage, new GenericActionListener<List<Message>, Throwable>() {
            @Override
            public void onResult(List<Message> messages) {
                // Post the messages in Live Data for observers to act on, or directly
                // update the adapter/views with these new messages
                messagesLiveData.postValue(messages);
            }

            @Override
            public void onFail(Throwable throwable) {
                Timber.e(throwable, "Failed getting messages by page");
            }
        });
    }
}
