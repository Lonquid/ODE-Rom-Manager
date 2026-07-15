package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Reads and writes GBA ROM headers.
 *
 * GBA Header layout (first 192 bytes):
 *   0x000: 4 bytes  - Entry point (ARM branch opcode)
 *   0x004: 156 bytes - Nintendo Logo
 *   0x0A0: 12 bytes - Game Title (uppercase ASCII)
 *   0x0AC: 4 bytes  - Game Code (4-letter serial, e.g. "BPRE")
 *   0x0B0: 2 bytes  - Maker Code
 *   0x0B2: 1 byte   - Fixed value (must be 0x96)
 *   0x0B3: 1 byte   - Main unit code
 *   0x0B4: 1 byte   - Device type
 *   0x0B5: 7 bytes  - Reserved
 *   0x0BC: 1 byte   - Software version
 *   0x0BD: 1 byte   - Complement check (header checksum)
 *   0x0BE: 2 bytes  - Reserved
 */
object GbaHeaderUtil {

    const val GAME_CODE_OFFSET = 0xAC
    const val GAME_TITLE_OFFSET = 0xA0
    const val GAME_TITLE_LENGTH = 12
    const val GAME_CODE_LENGTH = 4
    const val CHECKSUM_OFFSET = 0xBD
    const val HEADER_SIZE = 192

    // Nintendo logo bytes - used to verify this is a valid GBA ROM
    private val NINTENDO_LOGO_START = byteArrayOf(
        0x24.toByte(), 0xFF.toByte(), 0xAE.toByte(), 0x51.toByte()
    )

    data class GbaHeader(
        val gameTitle: String,
        val gameCode: String,
        val makerCode: String,
        val softwareVersion: Int,
        val checksum: Int,
        val isValid: Boolean
    )

    /**
     * Read GBA header from a file on internal storage.
     */
    fun readHeader(file: File): GbaHeader? {
        if (!file.exists() || file.length() < HEADER_SIZE) return null
        return try {
            val bytes = ByteArray(HEADER_SIZE)
            FileInputStream(file).use { it.read(bytes) }
            parseHeader(bytes)
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Read GBA header via SAF URI (SD card file).
     */
    fun readHeader(context: Context, uri: Uri): GbaHeader? {
        return try {
            val bytes = ByteArray(HEADER_SIZE)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read(bytes)
            }
            parseHeader(bytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHeader(bytes: ByteArray): GbaHeader {
        // Check Nintendo logo signature
        val isValid = bytes[4] == NINTENDO_LOGO_START[0] &&
                bytes[5] == NINTENDO_LOGO_START[1] &&
                bytes[6] == NINTENDO_LOGO_START[2] &&
                bytes[7] == NINTENDO_LOGO_START[3]

        val gameTitle = String(bytes, GAME_TITLE_OFFSET, GAME_TITLE_LENGTH)
            .trimEnd('\u0000').trim()

        val gameCode = String(bytes, GAME_CODE_OFFSET, GAME_CODE_LENGTH)
            .trimEnd('\u0000').trim()

        val makerCode = String(bytes, 0xB0, 2).trimEnd('\u0000').trim()
        val softwareVersion = bytes[0xBC].toInt() and 0xFF
        val checksum = bytes[CHECKSUM_OFFSET].toInt() and 0xFF

        return GbaHeader(gameTitle, gameCode, makerCode, softwareVersion, checksum, isValid)
    }

    /**
     * Calculate the complement checksum over bytes 0xA0–0xBC.
     * Formula: chk = 0; for i in 0xA0..0xBC: chk -= bytes[i]; chk = (chk - 0x19) and 0xFF
     */
    fun calculateChecksum(headerBytes: ByteArray): Byte {
        var chk = 0
        for (i in 0xA0..0xBC) {
            chk -= (headerBytes[i].toInt() and 0xFF)
        }
        chk = (chk - 0x19) and 0xFF
        return chk.toByte()
    }

    /**
     * Write a new 4-letter game code into a GBA ROM file (SAF URI on SD card).
     * Also recalculates the header checksum at 0xBD.
     * Returns true if successful.
     */
    fun writeGameCode(context: Context, uri: Uri, newCode: String): Boolean {
        require(newCode.length == 4) { "Game code must be exactly 4 characters" }
        return try {
            // Read full file into memory (GBA ROMs max 32MB, manageable)
            val fullBytes = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: return false

            if (fullBytes.size < HEADER_SIZE) return false

            // Write new game code at 0xAC
            val codeBytes = newCode.toByteArray(Charsets.US_ASCII)
            for (i in 0..3) {
                fullBytes[GAME_CODE_OFFSET + i] = codeBytes[i]
            }

            // Recalculate and write checksum at 0xBD
            fullBytes[CHECKSUM_OFFSET] = calculateChecksum(fullBytes)

            // Write back to SD card
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(fullBytes)
            } ?: return false

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compute MD5 hash of a file (used for ScreenScraper lookup).
     */
    fun computeMd5(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Compute MD5 via SAF URI (copies to temp, hashes, deletes temp).
     */
    fun computeMd5(context: Context, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
