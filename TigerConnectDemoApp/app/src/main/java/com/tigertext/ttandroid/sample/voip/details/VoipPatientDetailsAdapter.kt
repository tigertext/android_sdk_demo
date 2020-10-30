package com.tigertext.ttandroid.sample.voip.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tigertext.ttandroid.User
import com.tigertext.ttandroid.sample.R
import com.tigertext.voip.details.viewholder.VoipPatientContactDetailsViewHolder
import com.tigertext.ttandroid.sample.voip.details.viewholder.VoipPatientDetailsViewHolder
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipPatientDetailsAdapter(val presenter: TwilioVideoPresenter) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val patients = ArrayList<User>()

    fun add(newPatients: List<User>) {
        if (newPatients.isEmpty()) return
        val originalSize = patients.size
        patients.addAll(newPatients)
        notifyItemRangeInserted(originalSize, newPatients.size)
    }

    fun setAll(user: User) {
        clear()
        add(listOf(user))
        user.userPatientMetadata?.patientContacts?.let { add(it) }
    }

    fun clear() {
        val size = patients.size
        if (size > 0) {
            patients.clear()
            notifyItemRangeRemoved(0, size)
        }
    }

    fun rebind(userId: String) {
        val index = patients.indexOfFirst { it.id == userId }
        if (index >= 0) notifyItemChanged(index)
    }


    override fun getItemViewType(position: Int) = if (patients[position].userPatientMetadata?.isPatientContact == true)
        R.layout.row_voip_detail_patient_contact else R.layout.row_voip_detail_patient

    override fun getItemCount() = patients.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return if (viewType == R.layout.row_voip_detail_patient_contact) {
            VoipPatientContactDetailsViewHolder(view)
        } else {
            VoipPatientDetailsViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is VoipPatientContactDetailsViewHolder) {
            holder.bindContent(presenter, patients[position])
        } else if (holder is VoipPatientDetailsViewHolder) {
            holder.bindContent(presenter, patients[position])
        }
    }

}