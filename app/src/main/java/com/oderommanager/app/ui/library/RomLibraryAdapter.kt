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
    private val onScrapeArtClick: (RomEntry) -> Unit
) : ListAdapter<RomEntry, RomLibraryAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemRomLibraryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rom: RomEntry) {
            binding.tvRomName.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvSystem.text = rom.systemType.displayName

            binding.ivArtStatus.setImageResource(
                if (rom.hasArtwork) R.drawable.ic_art_check else R.drawable.ic_art_missing
            )
            binding.chipHack.visibility =
                if (rom.headerMismatch || rom.isRomHack) View.VISIBLE else View.GONE

            binding.btnScrapeArt.visibility =
                if (rom.systemType.name == "GBA") View.VISIBLE else View.GONE

            binding.btnRename.setOnClickListener { onRenameClick(rom) }
            binding.btnScrapeArt.setOnClickListener { onScrapeArtClick(rom) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemRomLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RomEntry>() {
            override fun areItemsTheSame(a: RomEntry, b: RomEntry) = a.id == b.id
            override fun areContentsTheSame(a: RomEntry, b: RomEntry) = a == b
        }
    }
}
