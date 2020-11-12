package com.tigertext.ttandroid.sample.calllog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tigertext.ttandroid.sample.databinding.FragmentCallLogBinding

class CallLogFragment : Fragment() {

    private lateinit var binding: FragmentCallLogBinding

    private val adapterViewModel = CallLogAdapterSubViewModel(CallLogAdapterSubViewModel.NETWORK_PROVIDER)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCallLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.adapter = adapterViewModel.adapter
        binding.swipeRefresh.setOnRefreshListener { adapterViewModel.refresh() }

        adapterViewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefresh.isRefreshing = it
        }

        adapterViewModel.orgId = arguments?.getString("org_id")
        adapterViewModel.startUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapterViewModel.shutDown()
    }

}