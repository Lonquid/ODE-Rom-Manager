package com.oderommanager.app.ui.hackworkflow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.ItemHackRomBinding

class HackRomAdapter(
    private val onItemClick: (RomEntry) -> Unit
) : ListAdapter<RomEntry, HackRomAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemHackRomBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rom: RomEntry) {
            binding.tvRomName.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvGameCode.text = rom.originalGameCode ?: "(none)"

            // Show assigned code if already processed
            if (rom.assignedGameCode != null) {
                binding.tvAssignedCode.text = "✓ Assigned new code: ${rom.assignedGameCode}"
                binding.tvAssignedCode.visibility = View.VISIBLE
                binding.btnProcess.visibility = View.GONE
            } else {
                binding.tvAssignedCode.visibility = View.GONE
            }

            // Show official name from No-Intro scan if available
            if (!rom.officialName.isNullOrBlank() && rom.mismatchType != "MATCH") {
                binding.rowOfficialName.visibility = View.VISIBLE
                binding.tvOfficialName.text = rom.officialName
                binding.btnProcess.visibility = View.VISIBLE
            } else if (rom.mismatchType == "UNKNOWN_SERIAL") {
                binding.rowOfficialName.visibility = View.VISIBLE
                binding.tvOfficialName.text = "(not in retail database — homebrew/hack)"
                binding.btnProcess.visibility = if (rom.assignedGameCode == null) View.VISIBLE else View.GONE
            } else {
                binding.rowOfficialName.visibility = View.GONE
                binding.btnProcess.visibility = View.GONE
            }

            // Status chip
            val (chipText, chipColor) = when (rom.mismatchType) {
                "HACK" -> "⚠ Mismatch" to "#E65100"
                "UNKNOWN_SERIAL" -> "? Unknown" to "#6A1B9A"
                "TRANSLATION" -> "T Translation" to "#1565C0"
                "MATCH" -> "✓ Match" to "#2E7D32"
                null -> "— Not scanned" to "#757575"
                else -> rom.mismatchType to "#757575"
            }
            binding.chipStatus.text = chipText

            binding.btnProcess.setOnClickListener { onItemClick(rom) }
            binding.root.setOnClickListener { onItemClick(rom) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHackRomBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RomEntry>() {
            override fun areItemsTheSame(a: RomEntry, b: RomEntry) = a.id == b.id
            override fun areContentsTheSame(a: RomEntry, b: RomEntry) = a == b
        }
    }
}
