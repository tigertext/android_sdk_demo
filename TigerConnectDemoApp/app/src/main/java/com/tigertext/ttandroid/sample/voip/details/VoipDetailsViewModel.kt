package com.tigertext.ttandroid.sample.voip.details

import android.app.Application
import androidx.lifecycle.*
import androidx.recyclerview.widget.ConcatAdapter
import com.tigertext.ttandroid.RosterEntry
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.group.Participant
import com.tigertext.ttandroid.pubsub.TTEvent
import com.tigertext.ttandroid.pubsub.TTPubSub
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.voip.states.CallPresenterManager
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class VoipDetailsViewModel(app: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(app), TTPubSub.Listener {

    private lateinit var patientAdapter: VoipDetailsAdapter
    private lateinit var providerAdapter: VoipDetailsAdapter
    private val patientHeaderAdapter = VoipDetailsHeaderAdapter { selectedState.value = VoipDetailsFragment.State.INVITE_PATIENTS }
    private val headerAdapter = VoipDetailsHeaderAdapter { selectedState.value = VoipDetailsFragment.State.INVITE_SEARCH }
    val concatAdapter = ConcatAdapter(ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build())

    lateinit var voipSearchAdapterViewModel: VoipSearchAdapterSubViewModel
    private lateinit var patientSearchAdapter: VoipPatientDetailsAdapter
    val searchConcatAdapter = ConcatAdapter(ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build())
    val patientSearchConcatAdapter = ConcatAdapter(ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build())

    var twilioVideoPresenter: TwilioVideoPresenter? = null

    val closeLiveData = MutableLiveData<Boolean>()
    val selectedState = savedStateHandle.getLiveData(VoipDetailsFragment.STATE, VoipDetailsFragment.State.VIEW)
    val isInviteAllowed = MutableLiveData<Boolean>()
    val memberCount = MutableLiveData<Int>()

    private val patientObserver = Observer<String?> {
        if (it.isNullOrEmpty()) return@Observer
        loadPatient()

        headerAdapter.header = app.getString(R.string.providers)
        patientHeaderAdapter.header = app.getString(R.string.patient_and_contacts)
    }

    fun setPresenterByCallId(callId: String?) {
        twilioVideoPresenter = CallPresenterManager.getPresenter(callId) as TwilioVideoPresenter?
        twilioVideoPresenter?.let {
            it.voipDetailsViewModel = this
            providerAdapter = VoipDetailsAdapter(it)
            patientAdapter = VoipDetailsAdapter(it)
            voipSearchAdapterViewModel = VoipSearchAdapterSubViewModel(it)
            for (userId in it.userIds) {
                if (isPatient(twilioVideoPresenter?.participantMap?.get(userId))) {
                    patientAdapter.addOrUpdate(userId)
                } else providerAdapter.addOrUpdate(userId)
            }
            for (userId in it.disconnectedUserIds) {
                if (isPatient(twilioVideoPresenter?.participantMap?.get(userId))) {
                    patientAdapter.addOrUpdate(userId)
                } else providerAdapter.addOrUpdate(userId)
            }
            memberCount.value = it.userIds.size

            concatAdapter.addAdapter(patientHeaderAdapter)
            concatAdapter.addAdapter(patientAdapter)
            concatAdapter.addAdapter(headerAdapter)
            concatAdapter.addAdapter(providerAdapter)

            patientSearchAdapter = VoipPatientDetailsAdapter(it)
            patientSearchConcatAdapter.addAdapter(patientHeaderAdapter)
            patientSearchConcatAdapter.addAdapter(patientSearchAdapter)

            searchConcatAdapter.addAdapter(headerAdapter)
            searchConcatAdapter.addAdapter(voipSearchAdapterViewModel.adapter)

            it.patientId.observeForever(patientObserver)

            updateInviteState()
        }
        if (twilioVideoPresenter == null) {
            closeLiveData.value = true
        }
    }

    init {
        selectedState.observeForever {
            twilioVideoPresenter ?: return@observeForever
            if (it == VoipDetailsFragment.State.INVITE_SEARCH) {
                voipSearchAdapterViewModel.startUp()
            } else {
                voipSearchAdapterViewModel.shutDown()
            }

            if (it == VoipDetailsFragment.State.INVITE_PATIENTS && patientSearchAdapter.itemCount == 0) {
                loadPatient()
            }
            updateHeaders()
        }
        isInviteAllowed.observeForever {
            val inviteAllowed = it == true
            if (!inviteAllowed && selectedState.value != VoipDetailsFragment.State.VIEW) {
                selectedState.value = VoipDetailsFragment.State.VIEW
            }

            updateHeaders()
            concatAdapter.notifyItemRangeChanged(0, concatAdapter.itemCount)
        }

        TT.getInstance().ttPubSub.addListeners(this, *listeners)
    }

    fun onUserChanged(userId: String) {
        if (isPatient(twilioVideoPresenter?.participantMap?.get(userId))) {
            providerAdapter.remove(userId)
            patientAdapter.addOrUpdate(userId)
            patientSearchAdapter.rebind(userId)
        } else {
            providerAdapter.addOrUpdate(userId)
        }
        voipSearchAdapterViewModel.adapter.notifyItemRangeChanged(0, voipSearchAdapterViewModel.adapter.itemCount)

        updateInviteState()
        memberCount.value = twilioVideoPresenter?.userIds?.size ?: 0
    }

    private fun updateHeaders() {
        val showInviteButton = isInviteAllowed.value == true && selectedState.value == VoipDetailsFragment.State.VIEW
        headerAdapter.showButton = showInviteButton
        patientHeaderAdapter.showButton = showInviteButton
    }

    private fun updateInviteState() {
        val inviteAllowed = twilioVideoPresenter?.let { it.isInviteParticipantAllowed() && !it.isGroupCallFull() }
                ?: false
        if (isInviteAllowed.value != inviteAllowed) isInviteAllowed.value = inviteAllowed
    }

    private fun loadPatient() {
        val patientId = twilioVideoPresenter?.patientId?.value ?: return
        val orgId = twilioVideoPresenter?.getCallInfo()?.orgId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { TT.getInstance().userManager.getUserSync(patientId, orgId) }?.let {
                    patientSearchAdapter.setAll(it)
                }
                // Receive USER_UPDATED if successful
                withContext(Dispatchers.IO) { TT.getInstance().userManager.fetchParticipant(patientId, orgId) }
            } catch (e: Exception) {
                Timber.e("Error getting patient")
            }
        }
    }

    override fun onCleared() {
        if (twilioVideoPresenter?.voipDetailsViewModel == this) {
            twilioVideoPresenter?.voipDetailsViewModel = null
        }
        voipSearchAdapterViewModel.shutDown()
        twilioVideoPresenter?.patientId?.removeObserver(patientObserver)
        TT.getInstance().ttPubSub.removeListeners(this, *listeners)
    }

    override fun onEventReceived(type: String, payload: Any?) {
        when (type) {
            TTEvent.USER_UPDATED -> {
                val participant = payload as User
                if (!isPatient(participant)) return
                viewModelScope.launch {
                    if (twilioVideoPresenter?.patientId?.value == participant.id) {
                        patientSearchAdapter.setAll(participant)
                    }
                }
            }
        }
    }

    private companion object {

        fun isPatient(participant: Participant?) = participant?.featureService == RosterEntry.FEATURE_SERVICE_PATIENT_MESSAGING

        val listeners = arrayOf(TTEvent.USER_UPDATED)

    }

}
