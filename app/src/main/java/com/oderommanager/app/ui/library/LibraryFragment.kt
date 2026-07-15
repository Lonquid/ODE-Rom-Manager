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
import com.oderommanager.app.data.repository.SettingsRepository
import com.oderommanager.app.databinding.FragmentLibraryBinding
import com.oderommanager.app.util.SdCardScanner

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())

        val adapter = RomLibraryAdapter(
            onRenameClick = { rom -> showRenameDialog(rom) },
            onScrapeArtClick = { rom -> viewModel.scrapeArtworkForRom(rom) },
            onThumbnailClick = { rom -> showArtConfirmDialog(rom) }
        )
        binding.recyclerRoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRoms.adapter = adapter

        // Folder chips
        viewModel.folderNames.observe(viewLifecycleOwner) { folders ->
            binding.chipGroupFolders.removeAllViews()
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
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    binding.btnBatchRename.isEnabled = true
                    binding.btnBatchArt.isEnabled = true
                }
            }
        }

        // Route to hack workflow if user wants to replace art
        viewModel.replaceArtFor.observe(viewLifecycleOwner) { romId ->
            if (romId != null) {
                val dialog = com.oderommanager.app.ui.hackworkflow.HackWorkflowDialog
                    .newInstance(romId)
                dialog.show(parentFragmentManager, "replace_art")
                viewModel.clearReplaceArtRequest()
            }
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterBySearch(newText ?: "")
                return true
            }
        })

        binding.btnBatchRename.setOnClickListener {
            val folder = viewModel.currentFolder.value
            val scope = if (folder != null) "folder \"$folder\"" else "all games"
            AlertDialog.Builder(requireContext())
                .setTitle("Batch Rename")
                .setMessage("Auto-rename all ROMs in $scope?\nStrips region tags, keeps translation credits.")
                .setPositiveButton("Rename All") { _, _ -> viewModel.batchRename() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnBatchArt.setOnClickListener {
            val folder = viewModel.currentFolder.value
            val scope = if (folder != null) "folder \"$folder\"" else "all GBA games"
            AlertDialog.Builder(requireContext())
                .setTitle("Batch Get Art")
                .setMessage("Download artwork for GBA ROMs in $scope that don't have art yet?")
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

    private fun showArtConfirmDialog(rom: RomEntry) {
        val settings = SettingsRepository(requireContext()).getSettings()
        val sdUri = SettingsRepository(requireContext()).getSdCardUri()

        // Try to get the actual BMP URI from the SD card
        val gameCode = rom.assignedGameCode
            ?: rom.originalGameCode?.trim()?.uppercase()
            ?: return

        val artUri = if (sdUri != null) {
            SdCardScanner.getArtworkUri(
                requireContext(), sdUri,
                settings.firmwareType.imgsRelativePath, gameCode
            )?.toString() ?: rom.artworkPath
        } else {
            rom.artworkPath
        } ?: return

        val dialog = ArtConfirmDialog.newInstance(
            romId = rom.id,
            artUri = artUri,
            gameName = rom.displayName,
            gameCode = gameCode,
            isVerified = rom.artVerified
        )
        dialog.show(childFragmentManager, "art_confirm")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = LibraryFragment()
    }
}
