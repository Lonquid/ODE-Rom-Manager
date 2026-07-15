package com.oderommanager.app.ui.library

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.oderommanager.app.R
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.ItemRomLibraryBinding

class RomLibraryAdapter(
    private val onRenameClick: (RomEntry) -> Unit,
    private val onScrapeArtClick: (RomEntry) -> Unit,
    private val onThumbnailClick: (RomEntry) -> Unit,
    // Adapter needs the resolved BMP URIs — provided by the fragment after SD card lookup
    private val getArtUri: (RomEntry) -> Uri?
) : ListAdapter<RomEntry, RomLibraryAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemRomLibraryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rom: RomEntry) {
            binding.tvRomName.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvSystem.text = rom.systemType.displayName

            binding.chipHack.visibility =
                if (rom.headerMismatch || rom.isRomHack) View.VISIBLE else View.GONE
            binding.btnScrapeArt.visibility =
                if (rom.systemType.name == "GBA") View.VISIBLE else View.GONE

            val resolvedUri = if (rom.hasArtwork) getArtUri(rom) else null

            if (resolvedUri != null) {
                binding.ivThumbnail.visibility = View.VISIBLE
                binding.ivArtPlaceholder.visibility = View.GONE

                Glide.with(binding.root.context)
                    .load(resolvedUri)
                    .placeholder(R.drawable.ic_art_missing)
                    .error(R.drawable.ic_art_missing)
                    .into(binding.ivThumbnail)

                val needsVerification = !rom.artVerified &&
                        (rom.headerMismatch || rom.isRomHack)
                binding.tvQuestionBadge.visibility =
                    if (needsVerification) View.VISIBLE else View.GONE

                binding.frameThumbnail.setOnClickListener { onThumbnailClick(rom) }
            } else {
                binding.ivThumbnail.visibility = View.GONE
                binding.ivArtPlaceholder.visibility = View.VISIBLE
                binding.tvQuestionBadge.visibility = View.GONE
                binding.frameThumbnail.setOnClickListener(null)
            }

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
