package com.tigertext.ttandroid.sample.utils.recyclerview

import android.os.Handler
import android.os.Looper
import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

open class AdapterSubViewModel {

    lateinit var adapterScope: CoroutineScope

    /**
     * Startup any listeners, should be called when the [AdapterPresenter] should start up, usually after it is created
     * @return true if [AdapterPresenter] wasn't alive before and was started, false if already started
     */
    @CallSuper
    open fun startUp(): Boolean {
        return if (!isAlive) {
            isAlive = true
            adapterScope = MainScope()
            true
        } else false
    }

    /**
     * Cleanup any listeners, should be called when the [AdapterPresenter] is no longer in use.
     * The [AdapterPresenter] may be reused with a subsequent call to [startUp]
     * @return true if [AdapterPresenter] was alive before and was shut down, false if already shut down
     */
    @CallSuper
    open fun shutDown(): Boolean {
        return if (isAlive) {
            isAlive = false
            adapterScope.cancel("AdapterViewModel shut down")
            emptyState.value = false
            isLoading.value = false
            error.value = null
            true
        } else false
    }

    val handler = Handler(Looper.getMainLooper())

    var isAlive: Boolean = false
        private set

    val emptyState = MutableLiveData(false)
    val isLoading = MutableLiveData(false)
    val error = MutableLiveData<Throwable?>()

}