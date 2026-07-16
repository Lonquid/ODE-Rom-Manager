package com.oderommanager.app.util

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fetches box art from the libretro thumbnails server.
 * URL format: https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/{name}.png
 *
 * The game name must match the No-Intro naming convention exactly.
 * Certain characters must be replaced with underscores: & * / : ` < > ? \ |
 *
 * No API key required — completely free public server.
 */
object LibretroThumbnailApi {

    private const val BASE_URL = "https://thumbnails.libretro.com"
    private const val GBA_SYSTEM = "Nintendo%20-%20Game%20Boy%20Advance"
    private const val BOX_ART_TYPE = "Named_Boxarts"

    private val REPLACE_CHARS = setOf('&', '*', '/', ':', '`', '<', '>', '?', '\\', '|')

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Build the libretro thumbnail URL for a given No-Intro game name.
     */
    fun buildUrl(noIntroName: String): String {
        val safeName = noIntroName.map { if (it in REPLACE_CHARS) '_' else it }.joinToString("")
        val encoded = URLEncoder.encode(safeName, "UTF-8").replace("+", "%20")
        return "$BASE_URL/$GBA_SYSTEM/$BOX_ART_TYPE/$encoded.png"
    }

    /**
     * Check if a thumbnail exists and download it.
     * Returns true if downloaded successfully.
     */
    fun downloadThumbnail(noIntroName: String, outputFile: File): Boolean {
        return try {
            val url = buildUrl(noIntroName)
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false
            val bytes = response.body?.bytes() ?: return false
            if (bytes.size < 100) return false // too small = error page
            outputFile.writeBytes(bytes)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Just get the URL without downloading — for preview in the UI.
     */
    fun getThumbnailUrl(noIntroName: String): String = buildUrl(noIntroName)
}
