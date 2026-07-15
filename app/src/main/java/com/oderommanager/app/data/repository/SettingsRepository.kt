package com.oderommanager.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.oderommanager.app.data.model.AppSettings
import com.oderommanager.app.data.model.ArtworkRegion
import com.oderommanager.app.data.model.FirmwareType

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ode_settings", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings = AppSettings(
        sdCardUriString = prefs.getString(KEY_SD_CARD_URI, null),
        ssUsername = prefs.getString(KEY_SS_USERNAME, "") ?: "",
        ssPassword = prefs.getString(KEY_SS_PASSWORD, "") ?: "",
        firmwareType = FirmwareType.valueOf(
            prefs.getString(KEY_FIRMWARE_TYPE, FirmwareType.SIMPLE_DE.name)
                ?: FirmwareType.SIMPLE_DE.name
        ),
        artworkRegion = ArtworkRegion.valueOf(
            prefs.getString(KEY_ARTWORK_REGION, ArtworkRegion.USA.name)
                ?: ArtworkRegion.USA.name
        ),
        autoScanOnLaunch = prefs.getBoolean(KEY_AUTO_SCAN, true)
    )

    fun getSdCardUri(): Uri? {
        val uriStr = prefs.getString(KEY_SD_CARD_URI, null) ?: return null
        return Uri.parse(uriStr)
    }

    fun saveSdCardUri(uri: Uri) {
        prefs.edit().putString(KEY_SD_CARD_URI, uri.toString()).apply()
    }

    fun saveScreenScraperCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_SS_USERNAME, username)
            .putString(KEY_SS_PASSWORD, password)
            .apply()
    }

    fun saveFirmwareType(type: FirmwareType) {
        prefs.edit().putString(KEY_FIRMWARE_TYPE, type.name).apply()
    }

    fun saveArtworkRegion(region: ArtworkRegion) {
        prefs.edit().putString(KEY_ARTWORK_REGION, region.name).apply()
    }

    fun saveAutoScan(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCAN, enabled).apply()
    }

    fun isSdCardConfigured(): Boolean = getSdCardUri() != null

    fun isScreenScraperConfigured(): Boolean {
        val settings = getSettings()
        return settings.ssUsername.isNotBlank() && settings.ssPassword.isNotBlank()
    }

    companion object {
        private const val KEY_SD_CARD_URI = "sd_card_uri"
        private const val KEY_SS_USERNAME = "ss_username"
        private const val KEY_SS_PASSWORD = "ss_password"
        private const val KEY_FIRMWARE_TYPE = "firmware_type"
        private const val KEY_ARTWORK_REGION = "artwork_region"
        private const val KEY_AUTO_SCAN = "auto_scan"
    }
}
