package com.oderommanager.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a ROM file tracked by the app.
 * Covers both standard ROMs (renamed/art scraped) and ROM hacks (header modified).
 */
@Entity(tableName = "rom_entries")
data class RomEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // File identity
    val fileName: String,           // current filename on SD card
    val originalFileName: String,   // filename before any rename
    val sdCardPath: String,         // full URI path on SD card
    val fileSizeBytes: Long,
    val md5Hash: String,            // computed at scan time

    // Game metadata
    val displayName: String,        // cleaned display name (e.g. "Pokemon FireRed Version")
    val systemType: SystemType,     // GBA, GB, GBC, NES, etc.

    // GBA header fields (GBA only)
    val originalGameCode: String? = null,   // 4-letter code from header at 0xAC
    val assignedGameCode: String? = null,   // our custom code (0XXX) if hack
    val isRomHack: Boolean = false,

    // Art status
    val artworkPath: String? = null,        // path on SD card to placed BMP
    val hasArtwork: Boolean = false,

    // Scraper metadata
    val scraperGameId: Long? = null,        // ScreenScraper game ID
    val scraperMatchMethod: String? = null, // "hash", "filename", "manual"

    // Timestamps
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
