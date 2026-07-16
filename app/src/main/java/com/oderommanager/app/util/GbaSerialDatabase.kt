package com.oderommanager.app.util

import android.content.Context
import org.json.JSONObject

/**
 * Lookup table of all 2,759 retail GBA game serial codes -> official names.
 * Sourced from the No-Intro database (libretro-database).
 * Bundled as an app asset — no network calls, instant lookup, works offline.
 */
object GbaSerialDatabase {

    data class GameInfo(
        val name: String,       // cleaned display name e.g. "Pokemon FireRed Version"
        val fullName: String    // full No-Intro name e.g. "Pokemon FireRed Version (USA)"
    )

    private var db: Map<String, GameInfo>? = null

    /**
     * Load the database from assets (cached after first load).
     */
    fun load(context: Context): Map<String, GameInfo> {
        db?.let { return it }
        return try {
            val json = context.assets.open("gba_serials.json")
                .bufferedReader().readText()
            val obj = JSONObject(json)
            val result = mutableMapOf<String, GameInfo>()
            for (key in obj.keys()) {
                val entry = obj.getJSONObject(key)
                result[key.uppercase()] = GameInfo(
                    name = entry.getString("name"),
                    fullName = entry.getString("fullName")
                )
            }
            db = result
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Look up a 4-letter serial code. Returns null if not in the retail database
     * (meaning it's a ROM hack, homebrew, or unknown).
     */
    fun lookup(context: Context, serialCode: String): GameInfo? {
        return load(context)[serialCode.uppercase()]
    }

    /**
     * Check if a serial code belongs to a known retail game.
     */
    fun isRetailGame(context: Context, serialCode: String): Boolean {
        return lookup(context, serialCode) != null
    }

    /**
     * Compare a filename against the official name for a serial code.
     * Returns a MismatchResult describing the relationship.
     *
     * This is the core of the mismatch detection — replaces the old
     * broken take(6) string comparison.
     */
    fun checkMismatch(context: Context, serialCode: String, fileName: String): MismatchResult {
        val info = lookup(context, serialCode)
            ?: return MismatchResult.UnknownSerial(serialCode)

        val cleanFile = RomNameUtil.cleanName(fileName).lowercase().trim()
        val officialName = info.name.lowercase().trim()

        // Exact match after cleaning
        if (cleanFile == officialName) return MismatchResult.Match(info)

        // Significant word overlap check — split into meaningful words,
        // require >50% of the official name's words to appear in the filename
        val officialWords = officialName
            .split(Regex("\\s+|-"))
            .filter { it.length > 2 }  // skip short words like "a", "of", "the"
            .toSet()
        val fileWords = cleanFile
            .split(Regex("\\s+|-"))
            .filter { it.length > 2 }
            .toSet()

        if (officialWords.isEmpty()) return MismatchResult.Match(info)

        val overlap = officialWords.intersect(fileWords).size.toFloat()
        val ratio = overlap / officialWords.size

        return if (ratio >= 0.6f) {
            // Enough words match — likely same game (e.g. regional variant)
            MismatchResult.Match(info)
        } else {
            // Real mismatch — serial says one game, filename says another
            MismatchResult.Mismatch(
                officialInfo = info,
                fileName = fileName,
                overlapRatio = ratio
            )
        }
    }

    sealed class MismatchResult {
        data class Match(val info: GameInfo) : MismatchResult()
        data class Mismatch(
            val officialInfo: GameInfo,  // what the serial code says this game is
            val fileName: String,         // what the filename says
            val overlapRatio: Float       // 0.0 = completely different, 1.0 = same
        ) : MismatchResult()
        data class UnknownSerial(val code: String) : MismatchResult()  // not a retail game
    }
}
