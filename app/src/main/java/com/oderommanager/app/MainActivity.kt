package com.oderommanager.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.oderommanager.app.data.repository.SettingsRepository
import com.oderommanager.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepo: SettingsRepository

    // SAF: request SD card tree access
    private val sdCardPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission across reboots
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            settingsRepo.saveSdCardUri(uri)
            Toast.makeText(this, "SD card connected!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepo = SettingsRepository(this)

        setupNavigation()
        checkStoragePermissions()

        // First launch: prompt for SD card if not already set
        if (!settingsRepo.isSdCardConfigured()) {
            promptForSdCard()
        }
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.bottomNavigation

        // Simple fragment transaction navigation
        navView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment("home")
                R.id.nav_library -> showFragment("library")
                R.id.nav_hacks -> showFragment("hacks")
                R.id.nav_backups -> showFragment("backups")
                R.id.nav_settings -> showFragment("settings")
            }
            true
        }

        // Default fragment
        showFragment("home")
    }

    private fun showFragment(tag: String) {
        val fragment = when (tag) {
            "home" -> ui.home.HomeFragment.newInstance()
            "library" -> ui.library.LibraryFragment.newInstance()
            "hacks" -> ui.hackworkflow.HackListFragment.newInstance()
            "backups" -> ui.backuplog.BackupLogFragment.newInstance()
            "settings" -> ui.settings.SettingsFragment.newInstance()
            else -> ui.home.HomeFragment.newInstance()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun checkStoragePermissions() {
        // Android 11+: request MANAGE_EXTERNAL_STORAGE for backup folder access
        if (!Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    fun promptForSdCard() {
        Toast.makeText(this, "Select your SD card root folder", Toast.LENGTH_LONG).show()
        sdCardPickerLauncher.launch(null)
    }

    companion object {
        const val REQUEST_SD_CARD = 1001
    }
}
