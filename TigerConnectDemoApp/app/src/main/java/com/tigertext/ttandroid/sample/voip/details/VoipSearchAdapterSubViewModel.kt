package com.tigertext.ttandroid.sample.voip.details

import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.sample.utils.recyclerview.AdapterSubViewModel
import com.tigertext.ttandroid.sample.utils.recyclerview.BindListener
import com.tigertext.ttandroid.search.ReturnField2
import com.tigertext.ttandroid.search.SearchQuery
import com.tigertext.ttandroid.user.OrgCapability
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class VoipSearchAdapterSubViewModel(private val twilioVideoPresenter: TwilioVideoPresenter) : AdapterSubViewModel() {

    private val bindListener = object : BindListener {
        override fun onBind(position: Int) {
            if (position + LOAD_MORE_THRESHOLD >= adapter.itemCount) {
                loadPage()
            }
        }
    }

    val adapter: VoipSearchAdapter = VoipSearchAdapter(twilioVideoPresenter, bindListener)

    private val refreshRunnable = Runnable(::refresh)

    var searchString: String = ""
        set(value) {
            if (field != value) {
                field = value
                scheduleRefresh()
            }
        }
    var continuation: String? = null
    private var isFullyLoaded = false

    override fun startUp(): Boolean {
        if (super.startUp()) {
            refresh()
            return true
        }
        return false
    }

    override fun shutDown(): Boolean {
        if (super.shutDown()) {
            searchString = ""
            reset()
            return true
        }
        return false
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshRunnable)

        if (isAlive) {
            handler.postDelayed(refreshRunnable, DELAY_REFRESH)
        }
    }

    private fun reset() {
        continuation = null
        isFullyLoaded = false
        isLoading.value = false
        adapter.clear()
        // Remove any delayed refreshes because we are refreshing now
        handler.removeCallbacks(refreshRunnable)
    }

    private fun refresh() {
        reset()
        loadPage()
    }

    private fun loadPage() {
        val currentOrgId = twilioVideoPresenter.getCallInfo().orgId
        if (currentOrgId.isEmpty()) return
        if (isFullyLoaded) return
        if (!isAlive) return
        if (isLoading.value == true) return

        isLoading.value = true
        val currentSearch = searchString

        val searchQueryBuilder = SearchQuery.Builder()
                .filteringFieldsOROperation(currentSearch, ReturnField2.DISPLAY_NAME)
                .sort(listOf(ReturnField2.DISPLAY_NAME))
                .resultsFormat(SearchQuery.ResultsFormat.ENTITIES)
                .directory(listOf(currentOrgId))
                .continuation(continuation)

        if (twilioVideoPresenter.isPatientCall()) {
            searchQueryBuilder.type(listOf(SearchQuery.Type.ACCOUNT))
                    .enabledOrgCapabilities(setOf(OrgCapability.pf_group_video_call))
        } else {
            searchQueryBuilder.type(listOf(SearchQuery.Type.ACCOUNT, SearchQuery.Type.ROLE))
        }

        adapterScope.launch {
            try {
                val resultSet = withContext(Dispatchers.IO) { TT.getInstance().searchManager.search(searchQueryBuilder.build()) }
                if (currentSearch != searchString) return@launch

                val currentUserId = twilioVideoPresenter.currentUserId
                val iterator = resultSet.results.iterator()
                while (iterator.hasNext()) {
                    val result = iterator.next()
                    if (result.entity.isRole) {
                        if (result.entity.roleOwners?.firstOrNull { it.id != currentUserId } == null) {
                            iterator.remove()
                        }
                    } else if (result.entity.token == currentUserId) {
                        iterator.remove()
                    }
                }

                continuation = resultSet.continuation
                isFullyLoaded = continuation.isNullOrEmpty()
                adapter.addSearchResults(resultSet.results)
                emptyState.value = adapter.itemCount == 0
            } catch (e: Exception) {
                Timber.e(e, "Error getting search results")
                error.value = e
            }

            isLoading.value = false
        }
    }

    private companion object {
        private const val DELAY_REFRESH = 250L
        private const val LOAD_MORE_THRESHOLD = 10
    }

}