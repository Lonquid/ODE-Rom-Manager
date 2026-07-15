package com.oderommanager.app.data.model

/**
 * App-wide settings stored in SharedPreferences.
 * Not a Room entity — accessed via SettingsRepository.
 */
data class AppSettings(
    val sdCardUriString: String? = null,        // SAF URI granted by user
    val ssUsername: String = "",                 // ScreenScraper username
    val ssPassword: String = "",                 // ScreenScraper password
    val firmwareType: FirmwareType = FirmwareType.SIMPLE_DE,
    val artworkRegion: ArtworkRegion = ArtworkRegion.USA,
    val autoScanOnLaunch: Boolean = true,
    val backupFolderPath: String = "/storage/emulated/0/ODE Rom Manager/backups"
)

enum class FirmwareType(val displayName: String, val imgsRelativePath: String) {
    STOCK("Stock Kernel", "IMGS"),
    SIMPLE_DE("SimpleDE / Custom", "SYSTEM/IMGS")
}

enum class ArtworkRegion(val displayName: String, val ssRegionCode: String) {
    USA("USA", "us"),
    EUROPE("Europe", "eu"),
    JAPAN("Japan", "jp"),
    WORLD("World", "wor")
}
