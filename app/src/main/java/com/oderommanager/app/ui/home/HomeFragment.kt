package com.oderommanager.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.oderommanager.app.MainActivity
import com.oderommanager.app.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // SD card status
        viewModel.sdCardStatus.observe(viewLifecycleOwner) { status ->
            binding.tvSdStatus.text = status
            binding.cardSdStatus.setCardBackgroundColor(
                if (status.startsWith("✓"))
                    requireContext().getColor(android.R.color.holo_green_light)
                else
                    requireContext().getColor(android.R.color.holo_orange_light)
            )
        }

        // Stats
        viewModel.totalRoms.observe(viewLifecycleOwner) {
            binding.tvTotalRoms.text = it.toString()
        }
        viewModel.romsWithArt.observe(viewLifecycleOwner) {
            binding.tvRomsWithArt.text = it.toString()
        }
        viewModel.romHacks.observe(viewLifecycleOwner) {
            binding.tvRomHacks.text = it.toString()
        }
        viewModel.pendingBackups.observe(viewLifecycleOwner) {
            binding.tvPendingBackups.text = it.toString()
        }

        // Scan button
        binding.btnScanSdCard.setOnClickListener {
            viewModel.scanSdCard()
        }

        // Scan progress
        viewModel.scanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HomeViewModel.ScanState.Idle -> {
                    binding.progressScan.visibility = View.GONE
                    binding.btnScanSdCard.isEnabled = true
                    binding.tvScanResult.visibility = View.GONE
                }
                is HomeViewModel.ScanState.Scanning -> {
                    binding.progressScan.visibility = View.VISIBLE
                    binding.btnScanSdCard.isEnabled = false
                    binding.tvScanResult.visibility = View.GONE
                }
                is HomeViewModel.ScanState.Done -> {
                    binding.progressScan.visibility = View.GONE
                    binding.btnScanSdCard.isEnabled = true
                    binding.tvScanResult.visibility = View.VISIBLE
                    binding.tvScanResult.text = state.message
                }
            }
        }

        // Connect SD card button
        binding.btnConnectSdCard.setOnClickListener {
            (requireActivity() as MainActivity).promptForSdCard()
        }

        viewModel.initialize(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
