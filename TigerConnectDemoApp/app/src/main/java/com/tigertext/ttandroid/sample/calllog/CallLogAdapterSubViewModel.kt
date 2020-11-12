package com.tigertext.ttandroid.sample.calllog

import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.sample.utils.recyclerview.AdapterSubViewModel
import com.tigertext.ttandroid.sample.utils.recyclerview.BindListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class CallLogAdapterSubViewModel(val network: String) : AdapterSubViewModel() {

    val adapter = CallLogAdapter()

    private var nextPage: String? = null
    private var isFullyFetched = false

    private val listener = object : BindListener {
        override fun onBind(position: Int) {
            if (!isFullyFetched && isLoading.value != true && position >= adapter.itemCount - FETCH_NEXT_PAGE_THRESHOLD) {
                startLoad()
            }
        }
    }

    init {
        adapter.listener = listener
    }

    var orgId: String? = null
        set(value) {
            if (field != value) {
                field = value
                reset()
                startLoad()
            }
        }

    override fun startUp(): Boolean {
        return if (super.startUp()) {
            // start up
            startLoad()
            true
        } else false
    }

    override fun shutDown(): Boolean {
        return if (super.shutDown()) {
            // shut down
            reset()
            true
        } else false
    }

    fun refresh() {
        reset()
        startLoad()
    }

    private fun reset() {
        adapter.clear()
        nextPage = null
        isFullyFetched = false
        isLoading.value = false
        emptyState.value = false
        error.value = null
    }

    private fun startLoad() {
        val requestOrgId = orgId ?: return
        if (!isAlive || requestOrgId.isEmpty()) return

        isLoading.value = true

        adapterScope.launch {
            try {
                val callLogPageResult = withContext(Dispatchers.IO) { TT.getInstance().callManager.getCallLog(requestOrgId, nextPage, PAGE_SIZE, network) }
                if (requestOrgId != orgId) return@launch

                isFullyFetched = callLogPageResult.metadata.isFullyFetched
                nextPage = callLogPageResult.metadata.nextPage
                adapter.addEntries(callLogPageResult.callLogEntries)
                if (adapter.itemCount == 0) {
                    emptyState.value = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting call log")
                if (requestOrgId != orgId) return@launch
                error.value = e
            }
            isLoading.value = false
        }
    }

    companion object {

        private const val PAGE_SIZE = 25
        private const val FETCH_NEXT_PAGE_THRESHOLD = 10
        const val NETWORK_PROVIDER = "provider"
        const val NETWORK_PATIENT = "patient"

    }

}