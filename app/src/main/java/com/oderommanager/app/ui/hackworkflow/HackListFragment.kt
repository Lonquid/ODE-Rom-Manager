package com.oderommanager.app.ui.hackworkflow

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.FragmentHackListBinding

class HackListFragment : Fragment() {

    private var _binding: FragmentHackListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HackListViewModel by viewModels()
    private lateinit var adapter: HackRomAdapter
    private var allRoms: List<RomEntry> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHackListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())

        adapter = HackRomAdapter { rom -> openHackWorkflow(rom) }
        binding.recyclerHackRoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHackRoms.adapter = adapter

        // Build filter chips
        setupFilterChips()

        viewModel.allGbaRoms.observe(viewLifecycleOwner) { roms ->
            allRoms = roms
            applyFilter(viewModel.filter.value)
            val scanned = roms.count { it.mismatchType != null }
            val mismatches = roms.count { it.mismatchType == "HACK" }
            val unknown = roms.count { it.mismatchType == "UNKNOWN_SERIAL" }
            val translations = roms.count { it.mismatchType == "TRANSLATION" }

            binding.tvSubtitle.text = if (scanned == 0) {
                "${roms.size} GBA ROMs · tap Scan to check"
            } else {
                "$scanned scanned · $mismatches mismatches · $translations translations · $unknown unknown"
            }

            binding.btnProcessAll.visibility =
                if (mismatches > 0 || unknown > 0) View.VISIBLE else View.GONE
        }

        viewModel.filter.observe(viewLifecycleOwner) { filter ->
            applyFilter(filter)
        }

        viewModel.scanState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HackListViewModel.ScanState.Scanning -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvProgress.text = state.message
                    binding.tvProgress.visibility = View.VISIBLE
                    binding.btnScanMismatches.isEnabled = false
                }
                is HackListViewModel.ScanState.Done -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    binding.btnScanMismatches.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    binding.btnScanMismatches.isEnabled = true
                }
            }
        }

        viewModel.openWorkflowFor.observe(viewLifecycleOwner) { rom ->
            if (rom != null) {
                openHackWorkflow(rom)
                viewModel.clearWorkflowRequest()
            }
        }

        binding.btnScanMismatches.setOnClickListener {
            viewModel.runMismatchScan(requireContext())
        }

        binding.btnProcessAll.setOnClickListener {
            val unprocessed = allRoms.firstOrNull {
                it.mismatchType in listOf("HACK", "UNKNOWN_SERIAL") && it.assignedGameCode == null
            }
            if (unprocessed != null) openHackWorkflow(unprocessed)
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.removeAllViews()

        listOf(
            "All" to null,
            "Mismatches" to "HACK",
            "Translations" to "TRANSLATION",
            "Unknown" to "UNKNOWN_SERIAL",
            "Matches" to "MATCH"
        ).forEach { (label, filter) ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                isChecked = filter == null
                setOnClickListener { viewModel.setFilter(filter) }
            }
            binding.chipGroupFilter.addView(chip)
        }
    }

    private fun applyFilter(filter: String?) {
        val filtered = when (filter) {
            null -> allRoms
            else -> allRoms.filter { it.mismatchType == filter }
        }
        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerHackRoms.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openHackWorkflow(rom: RomEntry) {
        HackWorkflowDialog.newInstance(rom.id).show(parentFragmentManager, "hack_workflow")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HackListFragment()
    }
}
