package com.oderommanager.app.ui.library

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.FragmentLibraryBinding

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var adapter: RomLibraryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())

        adapter = RomLibraryAdapter(
            onRenameClick = { rom -> showRenameDialog(rom) },
            onScrapeArtClick = { rom -> scrapeArtForRom(rom) },
            onBulkSelectToggle = { rom, selected -> viewModel.toggleBulkSelect(rom, selected) }
        )

        binding.recyclerRoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRoms.adapter = adapter

        viewModel.filteredRoms.observe(viewLifecycleOwner) { roms ->
            adapter.submitList(roms)
            binding.tvEmptyState.visibility = if (roms.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.bulkSelectionActive.observe(viewLifecycleOwner) { active ->
            binding.toolbarBulkActions.visibility = if (active) View.VISIBLE else View.GONE
            adapter.setBulkMode(active)
        }

        viewModel.operationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LibraryViewModel.OpState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvProgress.text = state.message
                    binding.tvProgress.visibility = View.VISIBLE
                }
                is LibraryViewModel.OpState.Done -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                is LibraryViewModel.OpState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                }
            }
        }

        // Search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterRoms(newText ?: "")
                return true
            }
        })

        // System filter chips
        binding.chipAll.setOnClickListener { viewModel.filterBySystem(null) }
        binding.chipGba.setOnClickListener { viewModel.filterBySystem("GBA") }
        binding.chipGb.setOnClickListener { viewModel.filterBySystem("GB") }
        binding.chipGbc.setOnClickListener { viewModel.filterBySystem("GBC") }

        // Bulk actions
        binding.btnBulkRename.setOnClickListener { viewModel.bulkRename() }
        binding.btnBulkScrapeArt.setOnClickListener { viewModel.bulkScrapeArt() }
        binding.btnBulkCancel.setOnClickListener { viewModel.clearBulkSelection() }
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }

        // FAB for bulk mode toggle
        binding.fabBulkMode.setOnClickListener {
            viewModel.toggleBulkMode()
        }
    }

    private fun showRenameDialog(rom: RomEntry) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(rom.displayName)
            selectAll()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Rename ROM")
            .setMessage("Current file: ${rom.fileName}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) {
                    viewModel.renameRom(rom, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun scrapeArtForRom(rom: RomEntry) {
        viewModel.scrapeArtworkForRom(rom)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = LibraryFragment()
    }
}
