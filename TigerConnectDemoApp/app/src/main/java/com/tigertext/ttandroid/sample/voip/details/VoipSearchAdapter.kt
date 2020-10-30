package com.tigertext.ttandroid.sample.voip.details

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.recyclerview.BindListener
import com.tigertext.ttandroid.search.SearchResult
import com.tigertext.ttandroid.sample.voip.details.viewholder.VoipDetailsViewHolder
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipSearchAdapter(val presenter: TwilioVideoPresenter, private val bindListener: BindListener) : RecyclerView.Adapter<VoipDetailsViewHolder>() {

    private val searchResults = ArrayList<SearchResult>()

    fun clear() {
        val size = searchResults.size
        if (size > 0) {
            searchResults.clear()
            notifyItemRangeRemoved(0, size)
        }
    }

    fun addSearchResults(results: List<SearchResult>) {
        val originalSize = searchResults.size
        searchResults.addAll(results)
        notifyItemRangeInserted(originalSize, results.size)
    }

    override fun getItemViewType(position: Int) = R.layout.view_voip_participant_detail

    override fun getItemCount(): Int {
        return searchResults.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoipDetailsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return VoipDetailsViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoipDetailsViewHolder, position: Int) {
        val result = searchResults[position]
        val userId: String = if (result.entity.isRole) { result.entity.roleOwners?.firstOrNull()?.id ?: result.entity.token } else result.entity.token

        holder.bindSearch(presenter, result, userId)
        bindListener.onBind(position)
    }

}
