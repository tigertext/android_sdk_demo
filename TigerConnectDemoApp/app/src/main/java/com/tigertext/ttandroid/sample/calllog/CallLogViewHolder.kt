package com.tigertext.ttandroid.sample.calllog

import android.graphics.Color
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.tigertext.ttandroid.Group
import com.tigertext.ttandroid.Role
import com.tigertext.ttandroid.api.TT
import com.tigertext.ttandroid.calllog.CallLogEntry
import com.tigertext.ttandroid.entity.Entity2
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.utils.PatientUtils
import com.tigertext.ttandroid.sample.utils.TTCallUtils
import com.tigertext.ttandroid.sample.utils.TTIntentUtils
import com.tigertext.ttandroid.sample.utils.ui.ButtonState
import kotlinx.android.synthetic.main.view_call_log_entry.view.*

class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), CallLogAdapter.ICallLogViewHolder {

    private val missedCallTextColor = Color.RED
    private val headerColor = Color.BLACK
    private val textColor = ContextCompat.getColor(itemView.context, R.color.attachment_preview_text)

    override fun bindContent(callLogEntry: CallLogEntry) {
        val context = itemView.context
        val isOutgoingCall = CallLogUtils.isOutgoingCall(callLogEntry)
        val isMissedCall = CallLogUtils.isMissedCall(callLogEntry)

        val flipCallerAndTarget = callLogEntry.target.id == TT.getInstance().accountManager.userId

        val target = if (flipCallerAndTarget) callLogEntry.caller else callLogEntry.target
        val proxyTarget = if (flipCallerAndTarget) callLogEntry.proxyCaller else callLogEntry.proxyTarget

        itemView.display_name.text = target.displayName
        if (target.avatarUrl.isNullOrEmpty()) Picasso.get().cancelRequest(itemView.avatar_view)
        else Picasso.get().load(target.avatarUrl).into(itemView.avatar_view)

        if (TTCallUtils.getCallButtonState(target, context) == ButtonState.CLICKABLE) {
            itemView.call_button.isEnabled = true
            itemView.call_button.alpha = 1f
            itemView.call_button.setOnClickListener { startCall(target) }
        } else {
            itemView.call_button.isEnabled = false
            itemView.call_button.alpha = .30f
        }

        if ((target is Group && !PatientUtils.isPatientP2p(target)) || proxyTarget is Role) {
            itemView.caller_name.visibility = View.VISIBLE
            itemView.caller_name.text = context.getString(R.string.caller_text, callLogEntry.caller.displayName)
        } else {
            itemView.caller_name.visibility = View.GONE
        }

        val resourceId = if (isOutgoingCall) R.drawable.ic_call_outgoing else if (isMissedCall) R.drawable.ic_call_missed else R.drawable.ic_call_incoming
        itemView.call_status.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(itemView.context, resourceId), null, null, null)

        if (resourceId == R.drawable.ic_call_missed) {
            itemView.display_name.setTextColor(missedCallTextColor)
            itemView.caller_name.setTextColor(missedCallTextColor)
            itemView.call_status.setTextColor(missedCallTextColor)
        } else {
            itemView.display_name.setTextColor(headerColor)
            itemView.caller_name.setTextColor(textColor)
            itemView.call_status.setTextColor(textColor)
        }

        itemView.call_duration.text = CallLogUtils.getCallDurationString(itemView.resources, isOutgoingCall, isMissedCall, callLogEntry.duration)
        itemView.call_duration.visibility = if (itemView.call_duration.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        itemView.call_status.text = CallLogUtils.getCallStatusText(context, callLogEntry)
    }

    override fun bindPosition(isTop: Boolean, isBottom: Boolean) {
        itemView.row_divider.visibility = if (isBottom) View.INVISIBLE else View.VISIBLE
    }

    private fun startCall(entity: Entity2) {
        val callOptions = TTCallUtils.getCallOptions(entity, itemView.context)
        TTIntentUtils.getCallIntent(itemView.context, entity, callOptions)?.let { itemView.context.startActivity(it) }
    }

}
