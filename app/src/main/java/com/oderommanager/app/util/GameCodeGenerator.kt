package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Generates unique 4-character game codes for ROM hacks.
 *
 * Format: "0" + 3 uppercase alphanumeric characters (A-Z, 0-9)
 * This prefix "0" is never used by retail GBA games (which use A-Z only),
 * making all codes generated here immediately identifiable.
 *
 * Examples: 0KFR, 0PXB, 0QZT
 *
 * Uniqueness is checked against THREE sources:
 *  1. Local app database (previously assigned codes)
 *  2. IMGS folder on SD card (scanned BMP filenames)
 *  3. ScreenScraper API (to catch any edge cases)
 */
object GameCodeGenerator {

    private const val PREFIX = "0"
    private val CHARSET = ('A'..'Z') + ('0'..'9')

    /**
     * Generate a random candidate code (not yet validated).
     */
    fun generateCandidate(): String {
        return PREFIX + (1..3).map { CHARSET.random() }.joinToString("")
    }

    /**
     * Check if a code is already in use by scanning the IMGS folder on SD card.
     * Returns true if the code is taken.
     */
    fun isCodeInImgsFolder(
        context: Context,
        sdCardUri: Uri,
        firmwareImgsPath: String,  // e.g. "SYSTEM/IMGS" or "IMGS"
        code: String
    ): Boolean {
        return try {
            val sdRoot = DocumentFile.fromTreeUri(context, sdCardUri) ?: return false

            // Navigate to IMGS folder
            val imgsFolder = firmwareImgsPath.split("/").fold(sdRoot) { current, segment ->
                current?.findFile(segment)
            } ?: return false

            // Navigate to [code[0]] / [code[1]]
            val firstLetter = imgsFolder.findFile(code[0].toString()) ?: return false
            val secondLetter = firstLetter.findFile(code[1].toString()) ?: return false

            // Check for XXXX.bmp
            secondLetter.findFile("$code.bmp") != null
        } catch (e: Exception) {
            false  // if we can't check, assume it's free (will catch with DB check)
        }
    }

    /**
     * Scan the entire IMGS folder and return all used codes.
     * Useful for bulk pre-loading the "taken" set before generating many codes.
     */
    fun scanUsedCodesInImgs(
        context: Context,
        sdCardUri: Uri,
        firmwareImgsPath: String
    ): Set<String> {
        val used = mutableSetOf<String>()
        return try {
            val sdRoot = DocumentFile.fromTreeUri(context, sdCardUri) ?: return used

            val imgsFolder = firmwareImgsPath.split("/").fold(sdRoot) { current, segment ->
                current?.findFile(segment)
            } ?: return used

            // Iterate first-letter directories
            imgsFolder.listFiles().forEach { firstDir ->
                if (firstDir.isDirectory) {
                    firstDir.listFiles().forEach { secondDir ->
                        if (secondDir.isDirectory) {
                            secondDir.listFiles().forEach { bmpFile ->
                                val name = bmpFile.name
                                if (name != null && name.endsWith(".bmp", ignoreCase = true)) {
                                    used.add(name.substringBeforeLast('.').uppercase())
                                }
                            }
                        }
                    }
                }
            }
            used
        } catch (e: Exception) {
            used
        }
    }
}
