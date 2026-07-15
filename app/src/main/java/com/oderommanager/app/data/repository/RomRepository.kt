package com.oderommanager.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oderommanager.app.data.db.AppDatabase
import com.oderommanager.app.data.model.*
import com.oderommanager.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RomRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val romDao = db.romEntryDao()
    private val backupDao = db.backupLogDao()
    private val settingsRepo = SettingsRepository(context)

    // LiveData for UI observation
    val allRoms = romDao.getAllRoms()
    val allBackupLogs = backupDao.getAllEntries()
    val pendingBackupCount = backupDao.getPendingCount()

    // ─── Scanning ─────────────────────────────────────────────────────────────

    /**
     * Scan the SD card and update the ROM database.
     * Returns count of newly discovered ROMs.
     */
    suspend fun scanSdCard(): ScanResult = withContext(Dispatchers.IO) {
        val sdUri = settingsRepo.getSdCardUri()
            ?: return@withContext ScanResult.Error("No SD card configured")

        val scanned = SdCardScanner.scanForRoms(context, sdUri)
        var newCount = 0
        var updatedCount = 0

        for (file in scanned) {
            val existing = romDao.findByHash(
                GbaHeaderUtil.computeMd5(context, file.uri) ?: continue
            )
            if (existing == null) {
                val md5 = GbaHeaderUtil.computeMd5(context, file.uri) ?: continue
                val header = if (file.systemType == SystemType.GBA) {
                    GbaHeaderUtil.readHeader(context, file.uri)
                } else null

                val entry = RomEntry(
                    fileName = file.name,
                    originalFileName = file.name,
                    sdCardPath = file.uri.toString(),
                    fileSizeBytes = file.sizeBytes,
                    md5Hash = md5,
                    displayName = RomNameUtil.cleanName(file.name),
                    systemType = file.systemType,
                    originalGameCode = header?.gameCode,
                    isRomHack = RomNameUtil.looksLikeHack(file.name)
                )
                romDao.insertOrUpdate(entry)
                newCount++
            } else {
                updatedCount++
            }
        }
        ScanResult.Success(newCount, updatedCount, scanned.size)
    }

    // ─── Artwork ──────────────────────────────────────────────────────────────

    /**
     * Scrape and place artwork for a single GBA ROM.
     */
    suspend fun scrapeArtwork(romEntry: RomEntry): ArtworkResult = withContext(Dispatchers.IO) {
        val settings = settingsRepo.getSettings()
        val api = ScreenScraperApi(settings.ssUsername, settings.ssPassword)
        val sdUri = settingsRepo.getSdCardUri()
            ?: return@withContext ArtworkResult.Error("No SD card configured")

        // Step 1: find game on ScreenScraper
        val gameInfo = api.scrapeByHash(
            romEntry.md5Hash,
            romEntry.fileSizeBytes,
            region = settings.artworkRegion.ssRegionCode
        ) ?: api.scrapeByFilename(
            romEntry.fileName,
            region = settings.artworkRegion.ssRegionCode
        ) ?: return@withContext ArtworkResult.NotFound

        // Step 2: download box art to temp file
        val artUrl = gameInfo.boxArtUrl
            ?: return@withContext ArtworkResult.NoArtAvailable

        val tempFile = File(context.cacheDir, "temp_art_${System.currentTimeMillis()}.jpg")
        if (!api.downloadImage(artUrl, tempFile)) {
            return@withContext ArtworkResult.Error("Failed to download artwork")
        }

        // Step 3: determine game code (use assigned hack code or original)
        val gameCode = romEntry.assignedGameCode
            ?: romEntry.originalGameCode
            ?: return@withContext ArtworkResult.Error("No game code available")

        // Step 4: convert to BMP
        val bmpFile = File(context.cacheDir, "$gameCode.bmp")
        val converted = BmpConverter.convertToBmp(context, Uri.fromFile(tempFile), bmpFile)
        tempFile.delete()

        if (!converted) return@withContext ArtworkResult.Error("BMP conversion failed")

        // Step 5: write to SD card IMGS folder
        val bmpBytes = bmpFile.readBytes()
        bmpFile.delete()

        val artUri = SdCardScanner.writeBmpToImgs(
            context, sdUri,
            settings.firmwareType.imgsRelativePath,
            gameCode,
            bmpBytes
        ) ?: return@withContext ArtworkResult.Error("Failed to write BMP to SD card")

        // Step 6: update DB
        val updated = romEntry.copy(
            hasArtwork = true,
            artworkPath = artUri.toString(),
            scraperGameId = gameInfo.gameId,
            scraperMatchMethod = gameInfo.matchMethod,
            dateModified = System.currentTimeMillis()
        )
        romDao.update(updated)

        ArtworkResult.Success(gameInfo.gameName, gameInfo.matchMethod)
    }

    // ─── ROM Renaming ─────────────────────────────────────────────────────────

    /**
     * Rename a ROM file on the SD card and update the database.
     */
    suspend fun renameRom(romEntry: RomEntry, newDisplayName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val sdUri = settingsRepo.getSdCardUri()
                ?: return@withContext RenameResult.Error("No SD card configured")

            val ext = romEntry.fileName.substringAfterLast('.')
            val newFileName = RomNameUtil.toFileName(newDisplayName, ext)

            // Find the file on SD card
            val docFile = DocumentFile.fromSingleUri(
                context, Uri.parse(romEntry.sdCardPath)
            ) ?: return@withContext RenameResult.Error("File not found on SD card")

            val renamed = docFile.renameTo(newFileName)
            if (!renamed) return@withContext RenameResult.Error("Rename failed (SD card write error)")

            // Update DB
            val updated = romEntry.copy(
                fileName = newFileName,
                displayName = newDisplayName,
                dateModified = System.currentTimeMillis()
            )
            romDao.update(updated)
            RenameResult.Success(newFileName)
        }

    // ─── ROM Hack Header Workflow ─────────────────────────────────────────────

    /**
     * Generate a unique 0XXX game code not used in DB, IMGS folder, or ScreenScraper.
     */
    suspend fun generateUniqueCode(): String = withContext(Dispatchers.IO) {
        val settings = settingsRepo.getSettings()
        val sdUri = settingsRepo.getSdCardUri()
        val api = ScreenScraperApi(settings.ssUsername, settings.ssPassword)

        // Pre-scan IMGS folder for used codes
        val usedInImgs = if (sdUri != null) {
            GameCodeGenerator.scanUsedCodesInImgs(
                context, sdUri, settings.firmwareType.imgsRelativePath
            )
        } else emptySet()

        var attempts = 0
        while (attempts < 100) {
            val candidate = GameCodeGenerator.generateCandidate()

            // Check 1: local DB
            val inDb = backupDao.findByGameCode(candidate) != null ||
                    romDao.findByAssignedCode(candidate) != null
            if (inDb) { attempts++; continue }

            // Check 2: IMGS folder
            if (candidate in usedInImgs) { attempts++; continue }

            // Check 3: ScreenScraper (only if credentials configured)
            if (settings.ssUsername.isNotBlank()) {
                val onSS = api.isGameCodeKnown(candidate)
                if (onSS) { attempts++; continue }
            }

            return@withContext candidate
        }
        // Extremely unlikely to reach here, but fallback
        GameCodeGenerator.generateCandidate()
    }

    /**
     * Execute the ROM hack header modification:
     * 1. Copy original ROM to backup location
     * 2. Write new game code to ROM header on SD card
     * 3. Place artwork BMP in IMGS folder
     * 4. Log everything to BackupLog DB
     */
    suspend fun applyHackHeaderModification(
        romEntry: RomEntry,
        newDisplayName: String,
        newGameCode: String,
        artworkBmpBytes: ByteArray
    ): HackResult = withContext(Dispatchers.IO) {
        val settings = settingsRepo.getSettings()
        val sdUri = settingsRepo.getSdCardUri()
            ?: return@withContext HackResult.Error("No SD card configured")

        val romUri = Uri.parse(romEntry.sdCardPath)

        // Step 1: Backup the ROM to phone storage
        val backupDir = File(settings.backupFolderPath)
        backupDir.mkdirs()
        val backupFile = File(backupDir, romEntry.originalFileName)

        try {
            context.contentResolver.openInputStream(romUri)?.use { input ->
                backupFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext HackResult.Error("Could not read ROM for backup")
        } catch (e: Exception) {
            return@withContext HackResult.Error("Backup failed: ${e.message}")
        }

        // Step 2: Write new game code to ROM on SD card
        val writeSuccess = GbaHeaderUtil.writeGameCode(context, romUri, newGameCode)
        if (!writeSuccess) {
            backupFile.delete()  // clean up backup if write failed
            return@withContext HackResult.Error("Failed to write game code to ROM")
        }

        // Step 3: Place artwork BMP in IMGS folder
        val artUri = SdCardScanner.writeBmpToImgs(
            context, sdUri,
            settings.firmwareType.imgsRelativePath,
            newGameCode,
            artworkBmpBytes
        )

        // Step 4: Log to BackupLog DB
        val logEntry = BackupLogEntry(
            displayName = newDisplayName,
            originalFileName = romEntry.originalFileName,
            originalGameCode = romEntry.originalGameCode ?: "????",
            newGameCode = newGameCode,
            sdCardPath = romEntry.sdCardPath,
            backupFilePath = backupFile.absolutePath,
            artworkSdPath = artUri?.toString(),
            status = BackupStatus.PENDING,
            dateModified = System.currentTimeMillis()
        )
        val logId = backupDao.insert(logEntry)

        // Step 5: Update ROM entry in DB
        val updatedRom = romEntry.copy(
            displayName = newDisplayName,
            assignedGameCode = newGameCode,
            isRomHack = true,
            hasArtwork = artUri != null,
            artworkPath = artUri?.toString(),
            dateModified = System.currentTimeMillis()
        )
        romDao.update(updatedRom)

        HackResult.Success(logId, backupFile.absolutePath)
    }

    /**
     * Confirm that a header modification worked.
     * Marks the backup log entry as CONFIRMED.
     */
    suspend fun confirmHackWorked(logId: Long) = withContext(Dispatchers.IO) {
        backupDao.updateStatus(logId, BackupStatus.CONFIRMED)
    }

    /**
     * Revert a header modification — restores original ROM from backup.
     */
    suspend fun revertHack(logId: Long): RevertResult = withContext(Dispatchers.IO) {
        val logEntry = backupDao.getById(logId)
            ?: return@withContext RevertResult.Error("Log entry not found")

        val backupFile = File(logEntry.backupFilePath)
        if (!backupFile.exists()) {
            return@withContext RevertResult.Error("Backup file not found at ${logEntry.backupFilePath}")
        }

        val sdUri = Uri.parse(logEntry.sdCardPath)
        try {
            backupFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(sdUri, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: return@withContext RevertResult.Error("Cannot write to SD card — is it inserted?")
            }
        } catch (e: Exception) {
            return@withContext RevertResult.Error("Restore failed: ${e.message}")
        }

        backupDao.updateStatus(logId, BackupStatus.RESTORED)
        RevertResult.Success
    }

    /**
     * Delete a backup file from phone storage after confirmed.
     */
    suspend fun deleteBackupFile(logId: Long): Boolean = withContext(Dispatchers.IO) {
        val logEntry = backupDao.getById(logId) ?: return@withContext false
        val file = File(logEntry.backupFilePath)
        val deleted = file.delete()
        if (deleted) {
            backupDao.updateStatus(logId, BackupStatus.CONFIRMED)
        }
        deleted
    }

    // ─── Result types ─────────────────────────────────────────────────────────

    sealed class ScanResult {
        data class Success(val newCount: Int, val updatedCount: Int, val totalFound: Int) : ScanResult()
        data class Error(val message: String) : ScanResult()
    }

    sealed class ArtworkResult {
        data class Success(val gameName: String, val matchMethod: String) : ArtworkResult()
        object NotFound : ArtworkResult()
        object NoArtAvailable : ArtworkResult()
        data class Error(val message: String) : ArtworkResult()
    }

    sealed class RenameResult {
        data class Success(val newFileName: String) : RenameResult()
        data class Error(val message: String) : RenameResult()
    }

    sealed class HackResult {
        data class Success(val logId: Long, val backupPath: String) : HackResult()
        data class Error(val message: String) : HackResult()
    }

    sealed class RevertResult {
        object Success : RevertResult()
        data class Error(val message: String) : RevertResult()
    }
}
