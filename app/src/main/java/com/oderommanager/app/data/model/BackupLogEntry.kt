package com.oderommanager.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted log of every ROM hack header modification.
 * Intentionally survives SD card removal — works from local DB only.
 */
@Entity(tableName = "backup_log")
data class BackupLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Game identity
    val displayName: String,
    val originalFileName: String,

    // Header change
    val originalGameCode: String,    // what was in the ROM before
    val newGameCode: String,         // the 0XXX code we wrote in

    // File locations
    val sdCardPath: String,          // URI path on SD card (for restore)
    val backupFilePath: String,      // absolute path in /storage/emulated/0/ODE Rom Manager/backups/

    // Artwork
    val artworkSdPath: String?,      // where the BMP was placed on SD card

    // Status lifecycle
    val status: BackupStatus = BackupStatus.PENDING,
    val dateModified: Long = System.currentTimeMillis(),
    val dateConfirmed: Long? = null
)

enum class BackupStatus {
    PENDING,    // header written, user hasn't confirmed yet
    CONFIRMED,  // user said it worked — backup can be deleted
    REVERTED,   // user said it didn't work — original restored
    RESTORED    // restore completed successfully
}
