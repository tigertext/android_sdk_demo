package com.tigertext.ttandroid.sample.voip

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tigertext.ttandroid.sample.R
import kotlinx.android.synthetic.main.bottom_sheet_select_list.view.*
import kotlinx.android.synthetic.main.bottom_sheet_select_list_item.view.*


/**
 * Created by martincazares on 3/27/18.
 */
class BottomSheetSelectionDialog : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "BottomSheetSelectionDialog"
        const val TITLE = "title"
        const val ARRAY_OF_LABELS = "array.of.labels"
        const val ARRAY_OF_ICONS = "array.of.icons"
        const val ARRAY_OF_IDS = "array.of.ids"
        private const val NUMBER_OF_ARRAYS = 3

        fun getArguments(labels: List<String>, icons: List<Int>, ids: List<Int>): Bundle {
            return getArguments(labels, icons, ids, "")
        }

        fun getArguments(labels: List<String>, icons: List<Int>, ids: List<Int>, title: String): Bundle {
            val bundle = Bundle()
            bundle.putStringArrayList(ARRAY_OF_LABELS, labels as? ArrayList ?: ArrayList(labels))
            bundle.putIntegerArrayList(ARRAY_OF_ICONS, icons as? ArrayList ?: ArrayList(icons))
            bundle.putIntegerArrayList(ARRAY_OF_IDS, ids as? ArrayList ?: ArrayList(ids))
            bundle.putString(TITLE, title)
            return bundle
        }
    }

    private var thereWasAnElementSelected = false
    private var itemSelectedListener: BottomDialogItemClick? = null
    private val itemsAdapter = BottomDialogItemsAdapter()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(R.id.design_bottom_sheet)!!
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.skipCollapsed = true
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED)
        }

        return bottomSheetDialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!thereWasAnElementSelected) {
            itemSelectedListener?.onNothingSelected()
        }
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        val rootView = View.inflate(context, R.layout.bottom_sheet_select_list, null)
        dialog.setContentView(rootView)
        setupDialogItems(rootView)
    }

    private fun setupDialogItems(rootView: View) {
        rootView.bottomSheetSelectItems.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        itemsAdapter.setListener(object : BottomDialogItemClick {
            override fun onNothingSelected() {
                itemSelectedListener?.onNothingSelected()
            }

            override fun onItemSelected(view: View, position: Int, item: BottomDialogItem) {
                if (isStateSaved) return
                thereWasAnElementSelected = true
                itemSelectedListener?.onItemSelected(view, position, item)
                dismiss()
            }
        })
        rootView.bottomSheetSelectItems.adapter = itemsAdapter

        val arguments = arguments
        if (arguments != null && !arguments.isEmpty) {
            val labels = arguments.getStringArrayList(ARRAY_OF_LABELS)
            val icons = arguments.getIntegerArrayList(ARRAY_OF_ICONS)
            val ids = arguments.getIntegerArrayList(ARRAY_OF_IDS)
            val title = arguments.getString(TITLE)
            if (!title.isNullOrEmpty()) {
                rootView.title.visibility = View.VISIBLE
                rootView.title.text = title
            }
            val total = labels!!.size + icons!!.size + ids!!.size

            if (invalidArraySize(total, labels) || invalidArraySize(total, icons) || invalidArraySize(total, ids)) {
                throw IllegalStateException("Make sure you have the same amount of labels and icons...")
            }

            itemsAdapter.addAllItems((0 until labels.size).map { BottomDialogItem(labels[it], icons[it], ids[it]) })
        }
    }

    private fun invalidArraySize(total: Int, array: ArrayList<*>): Boolean {
        return array.size * NUMBER_OF_ARRAYS != total
    }

    fun setOnItemSelected(itemSelectedListener: BottomDialogItemClick?) {
        this.itemSelectedListener = itemSelectedListener
    }

    data class BottomDialogItem(val label: String, @DrawableRes val iconResourceId: Int, val id: Int)

    private class BottomDialogItemsAdapter : RecyclerView.Adapter<BottomDialogItemsViewHolder>() {
        private var listener: BottomDialogItemClick? = null
        private val items = mutableListOf<BottomDialogItem>()
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BottomDialogItemsViewHolder {
            return object : BottomDialogItemsViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.bottom_sheet_select_list_item, parent, false)) {
                override fun onClick(view: View) {
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return

                    listener?.onItemSelected(view, position, items[position])
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: BottomDialogItemsViewHolder, position: Int) {
            holder.bindView(items[position])
        }

        fun addAllItems(items: List<BottomDialogItem>) {
            this.items.clear()
            this.items.addAll(items)
            notifyDataSetChanged()
        }

        fun setListener(listener: BottomDialogItemClick) {
            this.listener = listener
        }
    }

    interface BottomDialogItemClick {
        fun onItemSelected(view: View, position: Int, item: BottomDialogItem)
        fun onNothingSelected()
    }

    private abstract class BottomDialogItemsViewHolder(item: View) : RecyclerView.ViewHolder(item), View.OnClickListener {
        fun bindView(item: BottomDialogItem) {
            if (!itemView.hasOnClickListeners()) itemView.setOnClickListener(this)
            itemView.bottomSheetSelectItemIcon.setImageResource(item.iconResourceId)
            itemView.bottomSheetSelectItemLabel.text = item.label
        }
    }
}