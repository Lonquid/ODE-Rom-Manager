package com.oderommanager.app.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages ode_verified.json at the root of the SD card.
 * This file is the source of truth for verified art entries — survives app reinstalls.
 *
 * Format:
 * [
 *   { "gameCode": "BPRE", "verified": true, "displayName": "Pokemon FireRed", "date": "2026-07-15" },
 *   ...
 * ]
 */
object VerifiedLogUtil {

    const val LOG_FILENAME = "ode_verified.json"

    data class VerifiedEntry(
        val gameCode: String,
        val verified: Boolean,
        val displayName: String,
        val date: String
    )

    /**
     * Read all entries from ode_verified.json on the SD card root.
     * Returns empty map if file doesn't exist yet.
     */
    fun readVerifiedEntries(context: Context, sdCardUri: Uri): Map<String, VerifiedEntry> {
        return try {
            val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return emptyMap()
            val logFile = root.findFile(LOG_FILENAME) ?: return emptyMap()
            val text = context.contentResolver.openInputStream(logFile.uri)?.use {
                it.bufferedReader().readText()
            } ?: return emptyMap()

            val entries = mutableMapOf<String, VerifiedEntry>()
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val code = obj.getString("gameCode")
                entries[code] = VerifiedEntry(
                    gameCode = code,
                    verified = obj.optBoolean("verified", false),
                    displayName = obj.optString("displayName", ""),
                    date = obj.optString("date", "")
                )
            }
            entries
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Write or update a single entry in ode_verified.json.
     * Creates the file if it doesn't exist.
     */
    fun writeVerifiedEntry(
        context: Context,
        sdCardUri: Uri,
        gameCode: String,
        verified: Boolean,
        displayName: String
    ): Boolean {
        return try {
            val root = DocumentFile.fromTreeUri(context, sdCardUri) ?: return false

            // Read existing entries
            val existing = readVerifiedEntries(context, sdCardUri).toMutableMap()

            // Update or add
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            existing[gameCode] = VerifiedEntry(gameCode, verified, displayName, today)

            // Serialize back to JSON
            val array = JSONArray()
            for (entry in existing.values) {
                val obj = JSONObject().apply {
                    put("gameCode", entry.gameCode)
                    put("verified", entry.verified)
                    put("displayName", entry.displayName)
                    put("date", entry.date)
                }
                array.put(obj)
            }
            val json = array.toString(2)

            // Write to SD card
            val logFile = root.findFile(LOG_FILENAME)
                ?: root.createFile("application/json", LOG_FILENAME)
                ?: return false

            context.contentResolver.openOutputStream(logFile.uri, "wt")?.use {
                it.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
