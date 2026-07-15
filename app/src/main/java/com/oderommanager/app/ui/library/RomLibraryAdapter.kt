package com.oderommanager.app.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oderommanager.app.R
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.ItemRomLibraryBinding

class RomLibraryAdapter(
    private val onRenameClick: (RomEntry) -> Unit,
    private val onScrapeArtClick: (RomEntry) -> Unit,
    private val onBulkSelectToggle: (RomEntry, Boolean) -> Unit
) : ListAdapter<RomEntry, RomLibraryAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var bulkMode = false
    private val selected = mutableSetOf<Long>()

    fun setBulkMode(enabled: Boolean) {
        bulkMode = enabled
        if (!enabled) selected.clear()
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemRomLibraryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rom: RomEntry) {
            binding.tvRomName.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvSystem.text = rom.systemType.displayName

            // Status indicators
            binding.ivArtStatus.setImageResource(
                if (rom.hasArtwork) R.drawable.ic_art_check else R.drawable.ic_art_missing
            )
            binding.chipHack.visibility = if (rom.isRomHack) View.VISIBLE else View.GONE

            // Art/rename action buttons (hide in bulk mode)
            binding.btnRename.visibility = if (!bulkMode) View.VISIBLE else View.GONE
            binding.btnScrapeArt.visibility =
                if (!bulkMode && rom.systemType.name == "GBA") View.VISIBLE else View.GONE

            // Bulk checkbox
            binding.checkbox.visibility = if (bulkMode) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = rom.id in selected

            binding.btnRename.setOnClickListener { onRenameClick(rom) }
            binding.btnScrapeArt.setOnClickListener { onScrapeArtClick(rom) }

            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(rom.id) else selected.remove(rom.id)
                onBulkSelectToggle(rom, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRomLibraryBinding.inflate(
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
