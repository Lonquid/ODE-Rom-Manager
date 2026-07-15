package com.oderommanager.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rom_entries")
data class RomEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val fileName: String,
    val originalFileName: String,
    val sdCardPath: String,         // URI of the file itself
    val sdCardFolderPath: String,   // URI of the parent folder (for folder-aware browsing)
    val folderName: String,         // human-readable folder name e.g. "GBA", "Pokemon"
    val fileSizeBytes: Long,
    val md5Hash: String,

    val displayName: String,
    val systemType: SystemType,

    // GBA header fields
    val headerGameTitle: String? = null,    // raw title from ROM header bytes 0xA0-0xAB
    val originalGameCode: String? = null,   // 4-letter code from header 0xAC
    val assignedGameCode: String? = null,   // our custom 0XXX code if hack
    val isRomHack: Boolean = false,         // true if header title doesn't match filename
    val headerMismatch: Boolean = false,    // specifically: header title vs filename mismatch

    val artworkPath: String? = null,
    val hasArtwork: Boolean = false,

    val scraperGameId: Long? = null,
    val scraperMatchMethod: String? = null,

    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
)

enum class SystemType(val extensions: List<String>, val displayName: String) {
    GBA(listOf("gba", "agb", "bin"), "Game Boy Advance"),
    GB(listOf("gb"), "Game Boy"),
    GBC(listOf("gbc"), "Game Boy Color"),
    NES(listOf("nes"), "NES"),
    GG(listOf("gg"), "Game Gear"),
    SMS(listOf("sms", "sg"), "Sega Master System"),
    NGP(listOf("ngp", "ngc", "ngpc"), "Neo Geo Pocket"),
    WS(listOf("ws", "wsc"), "WonderSwan"),
    PCE(listOf("pce"), "PC Engine"),
    UNKNOWN(listOf(), "Unknown");

    companion object {
        fun fromExtension(ext: String): SystemType {
            val lower = ext.lowercase()
            return values().firstOrNull { it.extensions.contains(lower) } ?: UNKNOWN
        }
    }
}
