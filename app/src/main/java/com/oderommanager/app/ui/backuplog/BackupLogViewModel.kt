package com.oderommanager.app.ui.backuplog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oderommanager.app.data.model.BackupLogEntry
import com.oderommanager.app.data.model.BackupStatus
import com.oderommanager.app.data.repository.RomRepository
import com.oderommanager.app.databinding.ItemBackupLogBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── ViewModel ────────────────────────────────────────────────────────────────

class BackupLogViewModel : ViewModel() {

    private lateinit var repository: RomRepository

    val allEntries get() = repository.allBackupLogs

    private val _opState = MutableLiveData<OpState>(OpState.Idle)
    val opState: LiveData<OpState> = _opState

    fun initialize(context: Context) {
        repository = RomRepository(context)
    }

    fun confirmWorked(logId: Long) {
        viewModelScope.launch {
            _opState.value = OpState.Loading
            repository.confirmHackWorked(logId)
            _opState.value = OpState.Done("Confirmed! You can now delete the backup.")
        }
    }

    fun revertHack(logId: Long) {
        viewModelScope.launch {
            _opState.value = OpState.Loading
            when (val result = repository.revertHack(logId)) {
                is RomRepository.RevertResult.Success ->
                    _opState.value = OpState.Done("ROM restored to original.")
                is RomRepository.RevertResult.Error ->
                    _opState.value = OpState.Done("Revert failed: ${result.message}")
            }
        }
    }

    fun deleteBackup(logId: Long) {
        viewModelScope.launch {
            _opState.value = OpState.Loading
            val deleted = repository.deleteBackupFile(logId)
            _opState.value = OpState.Done(
                if (deleted) "Backup deleted." else "Could not delete backup file."
            )
        }
    }

    sealed class OpState {
        object Idle : OpState()
        object Loading : OpState()
        data class Done(val message: String) : OpState()
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class BackupLogAdapter(
    private val onEntryClick: (BackupLogEntry) -> Unit
) : ListAdapter<BackupLogEntry, BackupLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    inner class ViewHolder(val binding: ItemBackupLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BackupLogEntry) {
            binding.tvGameName.text = entry.displayName
            binding.tvCodeChange.text = "${entry.originalGameCode} → ${entry.newGameCode}"
            binding.tvDate.text = dateFormat.format(Date(entry.dateModified))
            binding.tvFileName.text = entry.originalFileName

            val (statusLabel, statusColor) = when (entry.status) {
                BackupStatus.PENDING -> "⏳ Pending confirmation" to
                        android.R.color.holo_orange_light
                BackupStatus.CONFIRMED -> "✓ Confirmed working" to
                        android.R.color.holo_green_light
                BackupStatus.REVERTED -> "↩ Reverted" to
                        android.R.color.darker_gray
                BackupStatus.RESTORED -> "↩ Restored" to
                        android.R.color.darker_gray
            }
            binding.tvStatus.text = statusLabel
            binding.tvStatus.setTextColor(
                binding.root.context.getColor(statusColor)
            )

            binding.root.setOnClickListener { onEntryClick(entry) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBackupLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BackupLogEntry>() {
            override fun areItemsTheSame(a: BackupLogEntry, b: BackupLogEntry) = a.id == b.id
            override fun areContentsTheSame(a: BackupLogEntry, b: BackupLogEntry) = a == b
        }
    }
}
