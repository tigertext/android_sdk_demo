package com.tigertext.ttandroid.sample.voip.details

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tigertext.ttandroid.sample.R
import com.tigertext.ttandroid.sample.databinding.FragmentVoipDetailsBinding
import com.tigertext.ttandroid.sample.databinding.ViewSearchToolbarBinding
import com.tigertext.ttandroid.sample.utils.TTUtils
import com.tigertext.ttandroid.sample.voip.states.TwilioVideoPresenter

class VoipDetailsFragment : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "VoipDetailsFragment"
        const val CALL_ID = "call_id"
        const val STATE = "state"

        fun getArguments(messageId: String, state: State): Bundle {
            val bundle = Bundle()
            bundle.putString(CALL_ID, messageId)
            bundle.putSerializable(STATE, state)
            return bundle
        }
    }

    enum class State {
        VIEW, INVITE_SEARCH, INVITE_PATIENTS
    }

    private val voipDetailsViewModel by viewModels<VoipDetailsViewModel>()
    private lateinit var binding: FragmentVoipDetailsBinding

    private lateinit var addParticipantItem: MenuItem

    private var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : BottomSheetDialog(requireContext(), theme) {
            override fun onBackPressed() {
                if (voipDetailsViewModel.selectedState.value != State.VIEW) {
                    voipDetailsViewModel.selectedState.value = State.VIEW
                } else {
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setStyle(DialogFragment.STYLE_NORMAL, R.style.ThemeOverlay_TC_BottomSheetDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentVoipDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.banner.text = getString(R.string.patient_group_call_limit, TwilioVideoPresenter.PARTICIPANTS_PATIENTS_MAX)
        setupToolbar()

        val layoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = layoutManager
        (binding.recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (voipDetailsViewModel.selectedState.value != State.VIEW && dy > 0) {
                    TTUtils.hideKeyboard(recyclerView.context, recyclerView.windowToken)
                }
            }
        })

        dialog?.setOnShowListener {
            val frameLayout = dialog!!.findViewById<FrameLayout>(R.id.design_bottom_sheet)
            frameLayout.layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT
            frameLayout.layoutParams = frameLayout.layoutParams
            bottomSheetBehavior = BottomSheetBehavior.from(frameLayout)
            bottomSheetBehavior?.skipCollapsed = true
        }
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        setupViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (isStateSaved) return@setNavigationOnClickListener
            dismiss()
        }
        DrawableCompat.setTintList(binding.toolbar.navigationIcon!!, ContextCompat.getColorStateList(binding.toolbar.context, R.color.icon_tint_gray))
        addParticipantItem = binding.toolbar.menu.findItem(R.id.add_participant)

        addParticipantItem.setActionView(R.layout.view_search_toolbar)
        val actionViewBinding = ViewSearchToolbarBinding.bind(addParticipantItem.actionView)
        actionViewBinding.clearButton.setOnClickListener { actionViewBinding.searchEditText.text = null }

        actionViewBinding.searchEditText.doAfterTextChanged {
            actionViewBinding.clearButton.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE
            voipDetailsViewModel.voipSearchAdapterViewModel.searchString = it?.toString()
                    ?: ""
        }
        addParticipantItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                actionViewBinding.searchEditText.requestFocus()

                actionViewBinding.root.doOnPreDraw { TTUtils.showKeyboard(it.context, actionViewBinding.searchEditText) }
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                if (voipDetailsViewModel.selectedState.value == State.VIEW) voipDetailsViewModel.selectedState.value = State.INVITE_SEARCH
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                TTUtils.hideKeyboard(context, actionViewBinding.searchEditText.windowToken)
                voipDetailsViewModel.selectedState.value = State.VIEW
                return true
            }
        })
    }

    private fun setupViewModel() {
        val callId = arguments?.getString(CALL_ID)
        voipDetailsViewModel.setPresenterByCallId(callId)

        if (voipDetailsViewModel.twilioVideoPresenter == null) {
            dismissAllowingStateLoss()
            return
        }

        binding.recyclerView.adapter = voipDetailsViewModel.concatAdapter

        voipDetailsViewModel.voipSearchAdapterViewModel.isLoading.observe(viewLifecycleOwner, {
            val isLoading = it == true
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })
        voipDetailsViewModel.closeLiveData.observe(viewLifecycleOwner, {
            val close = it == true
            if (close) dismiss()
        })

        voipDetailsViewModel.twilioVideoPresenter?.patientId?.observe(viewLifecycleOwner, {
            updateToolbar()
        })
        voipDetailsViewModel.isInviteAllowed.observe(viewLifecycleOwner, {
            updateToolbar()
        })
        voipDetailsViewModel.memberCount.observe(viewLifecycleOwner, {
            binding.toolbar.title = getString(R.string.participants_count, it ?: 0)
        })

        updateToolbar()

        voipDetailsViewModel.selectedState.observe(viewLifecycleOwner, {
            it ?: return@observe
            when (it) {
                State.VIEW -> {
                    if (addParticipantItem.isActionViewExpanded) addParticipantItem.collapseActionView()
                    binding.recyclerView.swapAdapter(voipDetailsViewModel.concatAdapter, true)
                }
                State.INVITE_SEARCH -> {
                    if (!addParticipantItem.isActionViewExpanded) addParticipantItem.expandActionView()
                    binding.recyclerView.swapAdapter(voipDetailsViewModel.searchConcatAdapter, true)
                }
                State.INVITE_PATIENTS -> {
                    if (!addParticipantItem.isActionViewExpanded) addParticipantItem.expandActionView()
                    binding.recyclerView.swapAdapter(voipDetailsViewModel.patientSearchConcatAdapter, true)
                }
            }
        })
    }

    private fun updateToolbar() {
        val isEnabled = voipDetailsViewModel.isInviteAllowed.value == true
        val isPatientCall = voipDetailsViewModel.twilioVideoPresenter?.isPatientCall() == true
        addParticipantItem.isVisible = isEnabled && !isPatientCall
        addParticipantItem.isEnabled = isEnabled && !isPatientCall
        if (!isEnabled) {
            addParticipantItem.collapseActionView()
        }

        binding.banner.visibility = if (isPatientCall && voipDetailsViewModel.twilioVideoPresenter?.isGroupCallFull() == true)
            View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerView.adapter = null
    }

}
