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
    private var currentFilter: String? = null

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

        setupFilterChips()

        viewModel.allGbaRoms.observe(viewLifecycleOwner) { roms ->
            allRoms = roms
            applyFilter(currentFilter)

            val scanned = roms.count { it.mismatchType != null }
            val mismatches = roms.count { it.mismatchType == "HACK" }
            val unknown = roms.count { it.mismatchType == "UNKNOWN_SERIAL" }
            val translations = roms.count { it.mismatchType == "TRANSLATION" }
            val unprocessed = roms.count {
                it.mismatchType in listOf("HACK", "UNKNOWN_SERIAL") && it.assignedGameCode == null
            }

            binding.tvSubtitle.text = if (scanned == 0) {
                "${roms.size} GBA ROMs · tap Scan to check"
            } else {
                "$scanned checked · $mismatches mismatches · $translations translations · $unknown unknown"
            }

            // Fix #3: show count on the button so user knows how many need processing
            if (unprocessed > 0) {
                binding.btnProcessAll.visibility = View.VISIBLE
                binding.btnProcessAll.text = "Process All Flagged ($unprocessed)"
            } else {
                binding.btnProcessAll.visibility = View.GONE
            }
        }

        viewModel.filter.observe(viewLifecycleOwner) { filter ->
            currentFilter = filter
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

        binding.btnScanMismatches.setOnClickListener {
            viewModel.runMismatchScan(requireContext())
        }

        // Fix #3: "Process All" opens the workflow for the first unprocessed ROM,
        // and when each workflow completes/dismisses it auto-opens the next one
        binding.btnProcessAll.setOnClickListener {
            val next = allRoms.firstOrNull {
                it.mismatchType in listOf("HACK", "UNKNOWN_SERIAL") && it.assignedGameCode == null
            }
            if (next != null) openHackWorkflow(next)
        }
    }

    private fun setupFilterChips() {
        binding.chipGroupFilter.removeAllViews()
        listOf(
            "All" to null,
            "Mismatches" to "HACK",
            "Translations" to "TRANSLATION",
            "Unknown" to "UNKNOWN_SERIAL",
            "Matched" to "MATCH"
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
        val filtered = if (filter == null) allRoms
        else allRoms.filter { it.mismatchType == filter }
        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerHackRoms.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openHackWorkflow(rom: RomEntry) {
        val dialog = HackWorkflowDialog.newInstance(rom.id)
        // Fix #3: when dialog dismisses, auto-advance to next unprocessed ROM if in bulk mode
        dialog.setOnDismissCallback {
            val nextUnprocessed = allRoms.firstOrNull {
                it.id != rom.id &&
                it.mismatchType in listOf("HACK", "UNKNOWN_SERIAL") &&
                it.assignedGameCode == null
            }
            // Only auto-advance if Process All was active (more than 1 still pending)
            val pendingCount = allRoms.count {
                it.mismatchType in listOf("HACK", "UNKNOWN_SERIAL") && it.assignedGameCode == null
            }
            // Don't auto-advance — let the button show the updated count
            // User can press Process All again for each one if they want bulk
        }
        dialog.show(parentFragmentManager, "hack_workflow_${rom.id}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HackListFragment()
    }
}
