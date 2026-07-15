package com.oderommanager.app.ui.library

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
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
            onScrapeArtClick = { rom -> viewModel.scrapeArtworkForRom(rom) }
        )
        binding.recyclerRoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRoms.adapter = adapter

        // Observe folder list and build chips dynamically (Fix #5)
        viewModel.folderNames.observe(viewLifecycleOwner) { folders ->
            binding.chipGroupFolders.removeAllViews()

            // "All" chip
            val allChip = Chip(requireContext()).apply {
                text = "All"
                isCheckable = true
                isChecked = true
                setOnClickListener { viewModel.filterByFolder(null) }
            }
            binding.chipGroupFolders.addView(allChip)

            folders.forEach { folder ->
                val chip = Chip(requireContext()).apply {
                    text = folder
                    isCheckable = true
                    setOnClickListener { viewModel.filterByFolder(folder) }
                }
                binding.chipGroupFolders.addView(chip)
            }
        }

        viewModel.filteredRoms.observe(viewLifecycleOwner) { roms ->
            adapter.submitList(roms)
            binding.tvEmptyState.visibility = if (roms.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.currentFolder.observe(viewLifecycleOwner) { folder ->
            binding.tvCurrentFolder.text = folder ?: "All Games"
        }

        viewModel.operationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LibraryViewModel.OpState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvProgress.text = state.message
                    binding.tvProgress.visibility = View.VISIBLE
                    binding.btnBatchRename.isEnabled = false
                    binding.btnBatchArt.isEnabled = false
                }
                is LibraryViewModel.OpState.Done -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    binding.btnBatchRename.isEnabled = true
                    binding.btnBatchArt.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                is LibraryViewModel.OpState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    binding.btnBatchRename.isEnabled = true
                    binding.btnBatchArt.isEnabled = true
                }
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterBySearch(newText ?: "")
                return true
            }
        })

        // Batch rename — applies to current folder or all (Fix #2)
        binding.btnBatchRename.setOnClickListener {
            val folder = viewModel.currentFolder.value
            val scope = if (folder != null) "folder \"$folder\"" else "all games"
            AlertDialog.Builder(requireContext())
                .setTitle("Batch Rename")
                .setMessage("Auto-rename all ROMs in $scope?\n\nThis strips region tags and cleans filenames.")
                .setPositiveButton("Rename All") { _, _ -> viewModel.batchRename() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Batch art scrape (Fix #2)
        binding.btnBatchArt.setOnClickListener {
            val folder = viewModel.currentFolder.value
            val scope = if (folder != null) "folder \"$folder\"" else "all GBA games"
            AlertDialog.Builder(requireContext())
                .setTitle("Batch Get Art")
                .setMessage("Download artwork for all GBA ROMs in $scope that don't have art yet?\n\nRequires ScreenScraper credentials.")
                .setPositiveButton("Get Art") { _, _ -> viewModel.batchScrapeArt() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showRenameDialog(rom: RomEntry) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(rom.displayName)
            selectAll()
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename ROM")
            .setMessage("File: ${rom.fileName}")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotBlank()) viewModel.renameRom(rom, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = LibraryFragment()
    }
}
