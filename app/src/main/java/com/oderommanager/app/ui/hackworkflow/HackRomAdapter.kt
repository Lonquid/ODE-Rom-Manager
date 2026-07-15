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
            binding.tvGameCode.text = "Code: ${rom.originalGameCode ?: "none"}"
            binding.tvAssignedCode.text = if (rom.assignedGameCode != null) {
                "→ Assigned: ${rom.assignedGameCode}"
            } else ""
            binding.tvAssignedCode.visibility =
                if (rom.assignedGameCode != null) View.VISIBLE else View.GONE

            // Status
            binding.chipHackFlag.visibility = if (rom.isRomHack) View.VISIBLE else View.GONE
            binding.ivArtStatus.setImageResource(
                if (rom.hasArtwork)
                    android.R.drawable.presence_online
                else
                    android.R.drawable.presence_offline
            )

            binding.root.setOnClickListener { onItemClick(rom) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHackRomBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RomEntry>() {
            override fun areItemsTheSame(a: RomEntry, b: RomEntry) = a.id == b.id
            override fun areContentsTheSame(a: RomEntry, b: RomEntry) = a == b
        }
    }
}
