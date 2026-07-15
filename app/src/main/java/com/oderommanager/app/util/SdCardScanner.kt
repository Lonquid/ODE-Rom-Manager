package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType

object SdCardScanner {

    private val ROM_EXTENSIONS = SystemType.values()
        .flatMap { it.extensions }
        .toSet()

    private val SKIP_FOLDERS = setOf(
        "imgs", "cheat", "system", "saver", "patch", "rts", "backup",
        "cheats", "saves", "states", "screenshots", "bios"
    )

    data class ScannedFile(
        val uri: Uri,
        val folderUri: Uri,
        val folderName: String,
        val name: String,
        val sizeBytes: Long,
        val systemType: SystemType
    )

    /**
     * Scan all ROM files. Respects folder structure — each file knows its parent folder.
     */
    fun scanForRoms(context: Context, sdCardUri: Uri): List<ScannedFile> {
        val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return emptyList()
        val results = mutableListOf<ScannedFile>()
        scanDirectory(root, root.name ?: "SD Card", root.uri, results)
        return results
    }

    private fun scanDirectory(
        dir: DocumentFile,
        folderName: String,
        folderUri: Uri,
        results: MutableList<ScannedFile>
    ) {
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                val name = file.name?.lowercase() ?: continue
                if (name in SKIP_FOLDERS) continue
                // Recurse — child files get this folder's name as their folder
                scanDirectory(file, file.name ?: name, file.uri, results)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in ROM_EXTENSIONS) continue
                val system = SystemType.fromExtension(ext)
                if (system == SystemType.UNKNOWN) continue
                results.add(
                    ScannedFile(
                        uri = file.uri,
                        folderUri = folderUri,
                        folderName = folderName,
                        name = name,
                        sizeBytes = file.length(),
                        systemType = system
                    )
                )
            }
        }
    }

    /**
     * Fix #1: Scan the IMGS folder and return all game codes that already have artwork.
     * Returns a Set of 4-letter game codes (uppercase).
     */
    fun scanExistingArtwork(
        context: Context,
        sdCardUri: Uri,
        firmwareImgsPath: String
    ): Set<String> {
        val found = mutableSetOf<String>()
        return try {
            val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return found
            var imgsFolder: DocumentFile = root
            for (segment in firmwareImgsPath.split("/")) {
                imgsFolder = imgsFolder.findFile(segment) ?: return found
            }
            // Walk /IMGS/X/X/XXXX.bmp
            for (firstDir in imgsFolder.listFiles()) {
                if (!firstDir.isDirectory) continue
                for (secondDir in firstDir.listFiles()) {
                    if (!secondDir.isDirectory) continue
                    for (bmpFile in secondDir.listFiles()) {
                        val name = bmpFile.name ?: continue
                        if (name.endsWith(".bmp", ignoreCase = true)) {
                            found.add(name.substringBeforeLast('.').uppercase())
                        }
                    }
                }
            }
            found
        } catch (e: Exception) {
            found
        }
    }

    fun detectFirmwareType(context: Context, sdCardUri: Uri): FirmwareDetectionResult {
        val root = DocumentFile.fromTreeUri(context, sdCardUri)
            ?: return FirmwareDetectionResult.UNKNOWN
        val systemFolder = root.findFile("SYSTEM")
        if (systemFolder != null && systemFolder.isDirectory) {
            if (systemFolder.findFile("IMGS") != null)
                return FirmwareDetectionResult.SIMPLE_DE
        }
        if (root.findFile("IMGS") != null) return FirmwareDetectionResult.STOCK
        return FirmwareDetectionResult.UNKNOWN
    }

    enum class FirmwareDetectionResult { SIMPLE_DE, STOCK, UNKNOWN }

    fun getOrCreateImgsSubfolder(
        context: Context,
        sdCardUri: Uri,
        imgsRelativePath: String,
        gameCode: String
    ): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, sdCardUri) ?: return null
        val segments = imgsRelativePath.split("/") +
                listOf(gameCode[0].toString(), gameCode[1].toString())
        for (segment in segments) {
            current = current.findFile(segment)
                ?: current.createDirectory(segment)
                ?: return null
        }
        return current
    }

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
