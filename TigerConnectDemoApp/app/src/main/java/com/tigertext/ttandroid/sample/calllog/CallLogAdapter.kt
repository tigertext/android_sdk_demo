package com.tigertext.ttandroid.sample.calllog

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.tigertext.ttandroid.calllog.CallLogEntry
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.recyclerview.BindListener

class CallLogAdapter() : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var listener: BindListener? = null

    private val callLogEntries = ArrayList<CallLogEntry>()

    fun addEntries(newEntries: List<CallLogEntry>) {
        if (newEntries.isEmpty()) return

        if (callLogEntries.isNotEmpty()) {
            notifyItemChanged(callLogEntries.lastIndex, CORNERS_CHANGED)
        }
        val startIndex = callLogEntries.size
        callLogEntries.addAll(newEntries)
        notifyItemRangeInserted(startIndex, newEntries.size)
    }

    fun clear() {
        val size = callLogEntries.size
        if (size > 0) {
            callLogEntries.clear()
            notifyItemRangeRemoved(0, size)
        }
    }

    override fun getItemCount() = callLogEntries.size

    override fun getItemViewType(position: Int) = R.layout.view_call_log_entry

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            (viewHolder as ICallLogViewHolder).bindContent(callLogEntries[position])
        }
        (viewHolder as ICallLogViewHolder).bindPosition(position == 0, position == callLogEntries.lastIndex)

        listener?.onBind(position)
    }

    interface ICallLogViewHolder {
        fun bindContent(callLogEntry: CallLogEntry)
        fun bindPosition(isTop: Boolean, isBottom: Boolean)
    }

    companion object {

        private val CORNERS_CHANGED = Any()

    }

}