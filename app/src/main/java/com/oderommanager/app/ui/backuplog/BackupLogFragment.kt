package com.oderommanager.app.ui.backuplog

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.oderommanager.app.data.model.BackupLogEntry
import com.oderommanager.app.data.model.BackupStatus
import com.oderommanager.app.databinding.FragmentBackupLogBinding

class BackupLogFragment : Fragment() {

    private var _binding: FragmentBackupLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: BackupLogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())

        val adapter = BackupLogAdapter(
            onEntryClick = { entry -> showEntryOptions(entry) }
        )
        binding.recyclerBackupLog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBackupLog.adapter = adapter

        viewModel.allEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            binding.tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            val pending = entries.count { it.status == BackupStatus.PENDING }
            binding.tvSummary.text = "${entries.size} total modifications · $pending pending confirmation"
        }

        viewModel.opState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is BackupLogViewModel.OpState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is BackupLogViewModel.OpState.Done -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                else -> binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showEntryOptions(entry: BackupLogEntry) {
        when (entry.status) {
            BackupStatus.PENDING -> showPendingOptions(entry)
            BackupStatus.CONFIRMED -> showConfirmedOptions(entry)
            BackupStatus.REVERTED, BackupStatus.RESTORED -> showFinalStateInfo(entry)
        }
    }

    private fun showPendingOptions(entry: BackupLogEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.displayName)
            .setMessage(
                "Code change: ${entry.originalGameCode} → ${entry.newGameCode}\n" +
                        "Modified: ${java.text.SimpleDateFormat("MMM d, yyyy").format(entry.dateModified)}\n\n" +
                        "Did the change work correctly on your EZ Flash?"
            )
            .setPositiveButton("✓ Yes, it worked!") { _, _ ->
                viewModel.confirmWorked(entry.id)
            }
            .setNegativeButton("✗ No, revert it") { _, _ ->
                confirmRevert(entry)
            }
            .setNeutralButton("Not yet", null)
            .show()
    }

    private fun confirmRevert(entry: BackupLogEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Revert ${entry.displayName}?")
            .setMessage(
                "This will:\n" +
                        "• Restore the original ROM on your SD card\n" +
                        "• Revert header code back to: ${entry.originalGameCode}\n\n" +
                        "Make sure your SD card is inserted."
            )
            .setPositiveButton("Revert") { _, _ ->
                viewModel.revertHack(entry.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmedOptions(entry: BackupLogEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.displayName)
            .setMessage(
                "✓ Change confirmed working!\n\n" +
                        "Backup file is still stored at:\n${entry.backupFilePath}\n\n" +
                        "You can delete it to free up space."
            )
            .setPositiveButton("Delete Backup") { _, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete backup?")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteBackup(entry.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Keep It", null)
            .show()
    }

    private fun showFinalStateInfo(entry: BackupLogEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(entry.displayName)
            .setMessage(
                "Status: ${entry.status.name}\n" +
                        "Original code: ${entry.originalGameCode}\n" +
                        "Assigned code: ${entry.newGameCode}\n" +
                        "Date: ${java.text.SimpleDateFormat("MMM d, yyyy").format(entry.dateModified)}"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = BackupLogFragment()
    }
}
