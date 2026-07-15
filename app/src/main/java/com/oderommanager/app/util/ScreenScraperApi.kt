package com.oderommanager.app.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ScreenScraper.fr API v2 client.
 *
 * API endpoint: https://www.screenscraper.fr/api2/
 * Authentication: devid + devpassword (our app dev credentials) + user login
 *
 * GBA system ID on ScreenScraper: 12
 */
class ScreenScraperApi(
    private val ssUsername: String,
    private val ssPassword: String
) {
    // Dev credentials for this app — required by SS API terms
    // In a real release these would be registered. Using placeholder for v1.
    private val devId = "ODERomManager"
    private val devPassword = "ODERomManagerDev"
    private val softwareName = "ODERomManager"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val gson = Gson()

    data class SsGameInfo(
        val gameId: Long,
        val gameName: String,
        val systemName: String,
        val releaseDate: String?,
        val boxArtUrl: String?,
        val screenshotUrl: String?,
        val matchMethod: String  // "hash" or "filename"
    )

    /**
     * Look up a game by MD5 hash + file size. Most accurate method.
     * System ID 12 = Game Boy Advance.
     */
    suspend fun scrapeByHash(
        md5: String,
        fileSizeBytes: Long,
        systemId: Int = 12,
        region: String = "us"
    ): SsGameInfo? {
        val url = buildUrl(
            "jeuInfos.php",
            mapOf(
                "md5" to md5,
                "romtaille" to fileSizeBytes.toString(),
                "systemeid" to systemId.toString(),
                "romregion" to region
            )
        )
        return fetchGameInfo(url, "hash")
    }

    /**
     * Look up a game by filename. Fallback if hash doesn't match.
     */
    suspend fun scrapeByFilename(
        filename: String,
        systemId: Int = 12,
        region: String = "us"
    ): SsGameInfo? {
        val url = buildUrl(
            "jeuInfos.php",
            mapOf(
                "romnom" to filename,
                "systemeid" to systemId.toString(),
                "romregion" to region
            )
        )
        return fetchGameInfo(url, "filename")
    }

    /**
     * Search for a game by name (for manual ROM hack art search).
     * Returns a list of candidates.
     */
    suspend fun searchByName(
        query: String,
        systemId: Int = 12
    ): List<SsGameInfo> {
        val url = buildUrl(
            "jeuRecherche.php",
            mapOf(
                "recherche" to query,
                "systemeid" to systemId.toString()
            )
        )
        return fetchSearchResults(url)
    }

    /**
     * Check if a game code is registered in ScreenScraper.
     * Used for uniqueness validation before assigning a hack code.
     */
    suspend fun isGameCodeKnown(gameCode: String, systemId: Int = 12): Boolean {
        return scrapeByFilename("$gameCode.gba", systemId) != null
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>): String {
        val base = "https://www.screenscraper.fr/api2/$endpoint"
        val authParams = mutableMapOf(
            "devid" to devId,
            "devpassword" to devPassword,
            "softname" to softwareName,
            "output" to "json"
        )
        if (ssUsername.isNotBlank()) {
            authParams["ssid"] = ssUsername
            authParams["sspassword"] = ssPassword
        }
        val allParams = authParams + params
        val queryString = allParams.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$base?$queryString"
    }

    private fun fetchGameInfo(url: String, matchMethod: String): SsGameInfo? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val response2 = json.getAsJsonObject("response") ?: return null
            val jeu = response2.getAsJsonObject("jeu") ?: return null

            val gameId = jeu.get("id")?.asLong ?: return null
            val noms = jeu.getAsJsonArray("noms")
            val gameName = noms?.firstOrNull {
                it.asJsonObject.get("region")?.asString == "us"
            }?.asJsonObject?.get("text")?.asString
                ?: noms?.firstOrNull()?.asJsonObject?.get("text")?.asString
                ?: return null

            val systemName = jeu.getAsJsonObject("systeme")
                ?.get("text")?.asString ?: ""

            val releaseDate = jeu.getAsJsonArray("dates")
                ?.firstOrNull()?.asJsonObject?.get("text")?.asString

            // Find box art URL (prefer USA, fallback to first available)
            val medias = jeu.getAsJsonArray("medias")
            val boxArtUrl = findMediaUrl(medias, "box-2D", listOf("us", "eu", "wor"))
            val screenshotUrl = findMediaUrl(medias, "ss", listOf("us", "eu", "wor"))

            SsGameInfo(gameId, gameName, systemName, releaseDate, boxArtUrl, screenshotUrl, matchMethod)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchSearchResults(url: String): List<SsGameInfo> {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val jeux = json.getAsJsonObject("response")
                ?.getAsJsonArray("jeux") ?: return emptyList()

            jeux.mapNotNull { element ->
                val jeu = element.asJsonObject
                val gameId = jeu.get("id")?.asLong ?: return@mapNotNull null
                val name = jeu.get("nom")?.asString ?: return@mapNotNull null
                SsGameInfo(gameId, name, "", null, null, null, "search")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findMediaUrl(
        medias: com.google.gson.JsonArray?,
        mediaType: String,
        regionPriority: List<String>
    ): String? {
        if (medias == null) return null
        val candidates = medias.filter {
            it.asJsonObject.get("type")?.asString == mediaType
        }
        for (region in regionPriority) {
            val match = candidates.firstOrNull {
                it.asJsonObject.get("region")?.asString == region
            }
            if (match != null) return match.asJsonObject.get("url")?.asString
        }
        return candidates.firstOrNull()?.asJsonObject?.get("url")?.asString
    }

    /**
     * Download an image from a URL and save to a local File.
     */
    fun downloadImage(url: String, outputFile: java.io.File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val bytes = response.body?.bytes() ?: return false
            outputFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }
}
