package com.oderommanager.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rom_entries")
data class RomEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val fileName: String,
    val originalFileName: String,
    val sdCardPath: String,
    val sdCardFolderPath: String,
    val folderName: String,
    val fileSizeBytes: Long,
    val md5Hash: String,

    val displayName: String,
    val systemType: SystemType,

    // GBA header fields
    val headerGameTitle: String? = null,
    val originalGameCode: String? = null,
    val assignedGameCode: String? = null,
    val isRomHack: Boolean = false,
    val headerMismatch: Boolean = false,

    // Artwork
    val artworkPath: String? = null,    // URI or path to the BMP on SD card
    val hasArtwork: Boolean = false,

    // Verification state
    // artVerified = true: user confirmed art is correct, no question mark
    // artVerified = false + hasArtwork = true: show thumbnail with "?" overlay
    val artVerified: Boolean = false,

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
