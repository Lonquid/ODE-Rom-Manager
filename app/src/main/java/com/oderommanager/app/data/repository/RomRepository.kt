package com.oderommanager.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import com.oderommanager.app.data.db.AppDatabase
import com.oderommanager.app.data.model.*
import com.oderommanager.app.util.*
import com.oderommanager.app.util.TheGamesDbApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RomRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val romDao = db.romEntryDao()
    private val backupDao = db.backupLogDao()
    private val settingsRepo = SettingsRepository(context)

    val allRoms = romDao.getAllRoms()
    val allFolderNames = romDao.getAllFolderNames()
    val allBackupLogs = backupDao.getAllEntries()
    val pendingBackupCount = backupDao.getPendingCount()
    val allGbaRoms = romDao.getAllGbaRoms()

    fun getRomsByFolder(folder: String): LiveData<List<RomEntry>> =
        romDao.getRomsByFolder(folder)

    // ── Scanning ──────────────────────────────────────────────────────────────

    suspend fun scanSdCard(): ScanResult = withContext(Dispatchers.IO) {
        val sdUri = settingsRepo.getSdCardUri()
            ?: return@withContext ScanResult.Error("No SD card configured")
        val settings = settingsRepo.getSettings()

        // Load existing artwork codes
        val existingArtCodes = SdCardScanner.scanExistingArtwork(
            context, sdUri, settings.firmwareType.imgsRelativePath
        )

        // Load verified log from SD card root
        val verifiedEntries = VerifiedLogUtil.readVerifiedEntries(context, sdUri)

        val scanned = SdCardScanner.scanForRoms(context, sdUri)
        var newCount = 0; var updatedCount = 0

        for (file in scanned) {
            val md5 = GbaHeaderUtil.computeMd5(context, file.uri) ?: continue
            val existing = romDao.findByHash(md5)

            val header = if (file.systemType == SystemType.GBA)
                GbaHeaderUtil.readHeader(context, file.uri) else null

            val headerMismatch = if (header != null && header.isValid) {
                val headerTitle = header.gameTitle.trim().uppercase()
                val fileNameBase = file.name.substringBeforeLast('.').uppercase()
                headerTitle.isNotBlank() &&
                        !fileNameBase.contains(headerTitle) &&
                        !headerTitle.contains(fileNameBase.take(6))
            } else false

            val gameCode = header?.gameCode?.trim()?.uppercase() ?: ""
            val hasExistingArt = gameCode.isNotBlank() && existingArtCodes.contains(gameCode)

            // Check verified log — if this code is verified, honour it
            val isVerified = verifiedEntries[gameCode]?.verified ?: false

            if (existing == null) {
                val entry = RomEntry(
                    fileName = file.name,
                    originalFileName = file.name,
                    sdCardPath = file.uri.toString(),
                    sdCardFolderPath = file.folderUri.toString(),
                    folderName = file.folderName,
                    fileSizeBytes = file.sizeBytes,
                    md5Hash = md5,
                    displayName = RomNameUtil.cleanName(file.name),
                    systemType = file.systemType,
                    headerGameTitle = header?.gameTitle?.trim(),
                    originalGameCode = header?.gameCode?.trim(),
                    isRomHack = headerMismatch,
                    headerMismatch = headerMismatch,
                    hasArtwork = hasExistingArt,
                    artVerified = isVerified,
                    artworkPath = if (hasExistingArt) "IMGS/$gameCode" else null
                )
                romDao.insertOrUpdate(entry)
                newCount++
            } else {
                romDao.update(existing.copy(
                    sdCardFolderPath = file.folderUri.toString(),
                    folderName = file.folderName,
                    headerGameTitle = header?.gameTitle?.trim() ?: existing.headerGameTitle,
                    headerMismatch = headerMismatch,
                    isRomHack = headerMismatch || existing.isRomHack,
                    hasArtwork = hasExistingArt || existing.hasArtwork,
                    artVerified = isVerified || existing.artVerified,
                    dateModified = System.currentTimeMillis()
                ))
                updatedCount++
            }
        }
        ScanResult.Success(newCount, updatedCount, scanned.size)
    }

    // ── Art verification ──────────────────────────────────────────────────────

    suspend fun verifyArt(romEntry: RomEntry): Boolean = withContext(Dispatchers.IO) {
        val sdUri = settingsRepo.getSdCardUri() ?: return@withContext false
        val gameCode = romEntry.assignedGameCode
            ?: romEntry.originalGameCode?.trim()?.uppercase()
            ?: return@withContext false

        // Write to SD card log
        VerifiedLogUtil.writeVerifiedEntry(
            context, sdUri, gameCode, true, romEntry.displayName
        )
        // Update DB
        romDao.setArtVerified(romEntry.id, true)
        true
    }

    suspend fun unverifyArt(romEntry: RomEntry): Boolean = withContext(Dispatchers.IO) {
        val sdUri = settingsRepo.getSdCardUri() ?: return@withContext false
        val gameCode = romEntry.assignedGameCode
            ?: romEntry.originalGameCode?.trim()?.uppercase()
            ?: return@withContext false
        VerifiedLogUtil.writeVerifiedEntry(
            context, sdUri, gameCode, false, romEntry.displayName
        )
        romDao.setArtVerified(romEntry.id, false)
        true
    }

    // ── Artwork ───────────────────────────────────────────────────────────────

    suspend fun scrapeArtwork(romEntry: RomEntry): ArtworkResult = withContext(Dispatchers.IO) {
        val settings = settingsRepo.getSettings()
        val api = ScreenScraperApi(settings.ssUsername, settings.ssPassword)
        val sdUri = settingsRepo.getSdCardUri()
            ?: return@withContext ArtworkResult.Error("No SD card configured")

        // Try ScreenScraper first (most accurate — matches by hash)
        val gameInfo = api.scrapeByHash(
            romEntry.md5Hash, romEntry.fileSizeBytes,
            region = settings.artworkRegion.ssRegionCode
        ) ?: api.scrapeByFilename(
            romEntry.fileName, region = settings.artworkRegion.ssRegionCode
        )

        val artUrl: String?
        if (gameInfo?.boxArtUrl != null) {
            artUrl = gameInfo.boxArtUrl
        } else {
            // Fallback to TheGamesDB (free, no login required)
            val tgdbResult = TheGamesDbApi.searchByName(romEntry.displayName)
            artUrl = tgdbResult?.boxArtUrl
        }

        if (artUrl == null) {
            return@withContext if (gameInfo != null) ArtworkResult.NoArtAvailable
            else ArtworkResult.NotFound
        }

        val tempFile = File(context.cacheDir, "temp_art_${System.currentTimeMillis()}.jpg")
        if (!api.downloadImage(artUrl, tempFile))
            return@withContext ArtworkResult.Error("Failed to download artwork")

        val gameCode = romEntry.assignedGameCode
            ?: romEntry.originalGameCode?.trim()?.uppercase()
            ?: return@withContext ArtworkResult.Error("No game code available")

        val bmpFile = File(context.cacheDir, "$gameCode.bmp")
        val converted = BmpConverter.convertToBmp(context, Uri.fromFile(tempFile), bmpFile)
        tempFile.delete()
        if (!converted) return@withContext ArtworkResult.Error("BMP conversion failed")

        val bmpBytes = bmpFile.readBytes()
        bmpFile.delete()

        val artUri = SdCardScanner.writeBmpToImgs(
            context, sdUri, settings.firmwareType.imgsRelativePath, gameCode, bmpBytes
        ) ?: return@withContext ArtworkResult.Error("Failed to write BMP to SD card")

        romDao.update(romEntry.copy(
            hasArtwork = true,
            artworkPath = artUri.toString(),
            artVerified = true,  // scraper-downloaded art is auto-verified
            scraperGameId = gameInfo.gameId,
            scraperMatchMethod = gameInfo.matchMethod,
            dateModified = System.currentTimeMillis()
        ))
        ArtworkResult.Success(gameInfo.gameName, gameInfo.matchMethod)
    }

    // ── Renaming ──────────────────────────────────────────────────────────────

    suspend fun renameRom(romEntry: RomEntry, newDisplayName: String): RenameResult =
        withContext(Dispatchers.IO) {
            val ext = romEntry.fileName.substringAfterLast('.')
            val newFileName = RomNameUtil.toFileName(newDisplayName, ext)
            val docFile = DocumentFile.fromSingleUri(context, Uri.parse(romEntry.sdCardPath))
                ?: return@withContext RenameResult.Error("File not found")
            if (!docFile.renameTo(newFileName))
                return@withContext RenameResult.Error("Rename failed")
            romDao.update(romEntry.copy(
                fileName = newFileName,
                displayName = newDisplayName,
                dateModified = System.currentTimeMillis()
            ))
            RenameResult.Success(newFileName)
        }

    // ── Hack workflow ─────────────────────────────────────────────────────────

    suspend fun generateUniqueCode(): String = withContext(Dispatchers.IO) {
        val settings = settingsRepo.getSettings()
        val sdUri = settingsRepo.getSdCardUri()
        val api = ScreenScraperApi(settings.ssUsername, settings.ssPassword)
        val usedInImgs = if (sdUri != null) GameCodeGenerator.scanUsedCodesInImgs(
            context, sdUri, settings.firmwareType.imgsRelativePath
        ) else emptySet()
        var attempts = 0
        while (attempts < 100) {
            val candidate = GameCodeGenerator.generateCandidate()
            val inDb = backupDao.findByGameCode(candidate) != null ||
                    romDao.findByAssignedCode(candidate) != null
            if (inDb || candidate in usedInImgs) { attempts++; continue }
            if (settings.ssUsername.isNotBlank() && api.isGameCodeKnown(candidate)) {
                attempts++; continue
            }
            return@withContext candidate
        }
        GameCodeGenerator.generateCandidate()
    }

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

        val backupDir = File(settings.backupFolderPath)
        backupDir.mkdirs()
        val backupFile = File(backupDir, romEntry.originalFileName)
        try {
            context.contentResolver.openInputStream(romUri)?.use { input ->
                backupFile.outputStream().use { input.copyTo(it) }
            } ?: return@withContext HackResult.Error("Could not read ROM for backup")
        } catch (e: Exception) {
            return@withContext HackResult.Error("Backup failed: ${e.message}")
        }

        if (!GbaHeaderUtil.writeGameCode(context, romUri, newGameCode)) {
            backupFile.delete()
            return@withContext HackResult.Error("Failed to write game code to ROM")
        }

        val artUri = SdCardScanner.writeBmpToImgs(
            context, sdUri, settings.firmwareType.imgsRelativePath,
            newGameCode, artworkBmpBytes
        )

        // Write to verified log since we just set up the art intentionally
        VerifiedLogUtil.writeVerifiedEntry(context, sdUri, newGameCode, true, newDisplayName)

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
        romDao.update(romEntry.copy(
            displayName = newDisplayName,
            assignedGameCode = newGameCode,
            isRomHack = true,
            headerMismatch = false,
            hasArtwork = artUri != null,
            artVerified = true,
            artworkPath = artUri?.toString(),
            dateModified = System.currentTimeMillis()
        ))
        HackResult.Success(logId, backupFile.absolutePath)
    }

    suspend fun confirmHackWorked(logId: Long) = withContext(Dispatchers.IO) {
        backupDao.updateStatus(logId, BackupStatus.CONFIRMED)
    }

    suspend fun revertHack(logId: Long): RevertResult = withContext(Dispatchers.IO) {
        val logEntry = backupDao.getById(logId)
            ?: return@withContext RevertResult.Error("Log entry not found")
        val backupFile = File(logEntry.backupFilePath)
        if (!backupFile.exists())
            return@withContext RevertResult.Error("Backup file not found")
        val sdUri = Uri.parse(logEntry.sdCardPath)
        try {
            backupFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(sdUri, "wt")?.use { input.copyTo(it) }
                    ?: return@withContext RevertResult.Error("Cannot write to SD card")
            }
        } catch (e: Exception) {
            return@withContext RevertResult.Error("Restore failed: ${e.message}")
        }
        backupDao.updateStatus(logId, BackupStatus.RESTORED)
        RevertResult.Success
    }

    suspend fun deleteBackupFile(logId: Long): Boolean = withContext(Dispatchers.IO) {
        val logEntry = backupDao.getById(logId) ?: return@withContext false
        val deleted = File(logEntry.backupFilePath).delete()
        if (deleted) backupDao.updateStatus(logId, BackupStatus.CONFIRMED)
        deleted
    }

    // ── Result types ──────────────────────────────────────────────────────────

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
    data class BulkResult(val success: Int, val notFound: Int, val failed: Int, val total: Int)
}
