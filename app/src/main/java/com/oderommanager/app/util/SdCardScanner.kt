package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType

/**
 * Scans the SD card for ROM files using the Storage Access Framework.
 * Requires the user to have granted tree URI access to the SD card root.
 */
object SdCardScanner {

    // All ROM extensions we care about
    private val ROM_EXTENSIONS = SystemType.values()
        .flatMap { it.extensions }
        .toSet()

    data class ScannedFile(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val systemType: SystemType
    )

    /**
     * Recursively scan all ROM files under the given SD card root URI.
     * Skips system folders (IMGS, CHEAT, SYSTEM, SAVER, PATCH, RTS, BACKUP).
     */
    fun scanForRoms(context: Context, sdCardUri: Uri): List<ScannedFile> {
        val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return emptyList()
        val results = mutableListOf<ScannedFile>()
        scanDirectory(root, results)
        return results
    }

    private val SKIP_FOLDERS = setOf(
        "imgs", "cheat", "system", "saver", "patch", "rts", "backup",
        "cheats", "saves", "states", "screenshots"
    )

    private fun scanDirectory(dir: DocumentFile, results: MutableList<ScannedFile>) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                // Skip system/metadata folders
                val folderName = file.name?.lowercase() ?: continue
                if (folderName in SKIP_FOLDERS) continue
                scanDirectory(file, results)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in ROM_EXTENSIONS) continue
                val system = SystemType.fromExtension(ext)
                if (system == SystemType.UNKNOWN) continue

                results.add(
                    ScannedFile(
                        uri = file.uri,
                        name = name,
                        sizeBytes = file.length(),
                        systemType = system
                    )
                )
            }
        }
    }

    /**
     * Check if the SD card has the expected EZ Flash structure (SimpleDE).
     * Returns true if /SYSTEM/IMGS/ exists (SimpleDE),
     * false if /IMGS/ exists (stock),
     * null if neither found.
     */
    fun detectFirmwareType(context: Context, sdCardUri: Uri): FirmwareDetectionResult {
        val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return FirmwareDetectionResult.UNKNOWN
        val systemFolder = root.findFile("SYSTEM")
        if (systemFolder != null && systemFolder.isDirectory) {
            val imgsInSystem = systemFolder.findFile("IMGS")
            if (imgsInSystem != null) return FirmwareDetectionResult.SIMPLE_DE
        }
        val imgsAtRoot = root.findFile("IMGS")
        if (imgsAtRoot != null && imgsAtRoot.isDirectory) return FirmwareDetectionResult.STOCK
        return FirmwareDetectionResult.UNKNOWN
    }

    enum class FirmwareDetectionResult { SIMPLE_DE, STOCK, UNKNOWN }

    /**
     * Find or create the IMGS subfolder for a given game code on the SD card.
     * Path: [sdRoot]/[imgsPath]/[code[0]]/[code[1]]/
     */
    fun getOrCreateImgsSubfolder(
        context: Context,
        sdCardUri: Uri,
        imgsRelativePath: String,
        gameCode: String
    ): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, sdCardUri) ?: return null

        // Navigate/create each path segment
        val segments = imgsRelativePath.split("/") +
                listOf(gameCode[0].toString(), gameCode[1].toString())

        for (segment in segments) {
            current = current.findFile(segment)
                ?: current.createDirectory(segment)
                ?: return null
        }
        return current
    }

    /**
     * Write a BMP file into the correct IMGS subfolder for the given game code.
     * Returns the URI of the created file, or null on failure.
     */
    fun writeBmpToImgs(
        context: Context,
        sdCardUri: Uri,
        imgsRelativePath: String,
        gameCode: String,
        bmpBytes: ByteArray
    ): Uri? {
        val folder = getOrCreateImgsSubfolder(context, sdCardUri, imgsRelativePath, gameCode)
            ?: return null

        val bmpName = "$gameCode.bmp"
        // Delete existing if present
        folder.findFile(bmpName)?.delete()

        val newFile = folder.createFile("image/bmp", bmpName) ?: return null
        return try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                out.write(bmpBytes)
            }
            newFile.uri
        } catch (e: Exception) {
            null
        }
    }
}
