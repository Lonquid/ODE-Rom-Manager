package com.oderommanager.app.util

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TheGamesDB.net API — free fallback for artwork when ScreenScraper fails.
 * No login required. Uses the public search endpoint.
 * API docs: https://api.thegamesdb.net/
 */
object TheGamesDbApi {

    // Public API key — TheGamesDB provides this for open use
    private const val API_KEY = "legacy"
    private const val BASE = "https://api.thegamesdb.net/v1"
    // GBA platform ID on TheGamesDB = 4
    private const val GBA_PLATFORM_ID = 4

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class TgdbResult(
        val gameId: Int,
        val gameName: String,
        val boxArtUrl: String?
    )

    /**
     * Search by game name. Returns the best match or null.
     */
    fun searchByName(name: String): TgdbResult? {
        return try {
            val encoded = java.net.URLEncoder.encode(name, "UTF-8")
            val url = "$BASE/Games/ByGameName?apikey=$API_KEY&name=$encoded&fields=players,publishers&include=boxart&filter%5Bplatform%5D=$GBA_PLATFORM_ID"
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            parseSearchResult(body)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSearchResult(json: String): TgdbResult? {
        return try {
            val root = JSONObject(json)
            if (root.getInt("code") != 200) return null
            val games = root.getJSONObject("data").getJSONArray("games")
            if (games.length() == 0) return null
            val game = games.getJSONObject(0)
            val id = game.getInt("id")
            val name = game.getString("game_title")

            // Try to get box art from included data
            val boxArtUrl = try {
                val images = root.getJSONObject("include")
                    .getJSONObject("boxart")
                    .getJSONObject("data")
                    .getJSONArray(id.toString())
                var frontUrl: String? = null
                for (i in 0 until images.length()) {
                    val img = images.getJSONObject(i)
                    if (img.getString("side") == "front") {
                        val base = root.getJSONObject("include")
                            .getJSONObject("boxart")
                            .getString("base_url")
                        frontUrl = base + "large/" + img.getString("filename")
                        break
                    }
                }
                frontUrl
            } catch (e: Exception) {
                null
            }

            TgdbResult(id, name, boxArtUrl)
        } catch (e: Exception) {
            null
        }
    }

    fun downloadImage(url: String, outputFile: File): Boolean {
        return try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) return false
            val bytes = response.body?.bytes() ?: return false
            outputFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }
}
