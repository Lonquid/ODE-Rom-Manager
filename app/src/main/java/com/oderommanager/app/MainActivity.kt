package com.oderommanager.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.oderommanager.app.data.repository.SettingsRepository
import com.oderommanager.app.databinding.ActivityMainBinding
import com.oderommanager.app.ui.backuplog.BackupLogFragment
import com.oderommanager.app.ui.hackworkflow.HackListFragment
import com.oderommanager.app.ui.home.HomeFragment
import com.oderommanager.app.ui.library.LibraryFragment
import com.oderommanager.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepo: SettingsRepository

    private val sdCardPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
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

        if (!settingsRepo.isSdCardConfigured()) {
            promptForSdCard()
        }
    }

    private fun setupNavigation() {
        val navView: BottomNavigationView = binding.bottomNavigation
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
        showFragment("home")
    }

    private fun showFragment(tag: String) {
        val fragment = when (tag) {
            "home" -> HomeFragment.newInstance()
            "library" -> LibraryFragment.newInstance()
            "hacks" -> HackListFragment.newInstance()
            "backups" -> BackupLogFragment.newInstance()
            "settings" -> SettingsFragment.newInstance()
            else -> HomeFragment.newInstance()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun checkStoragePermissions() {
        if (!Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    fun promptForSdCard() {
        Toast.makeText(this, "Select your SD card root folder", Toast.LENGTH_LONG).show()
        sdCardPickerLauncher.launch(null)
    }
}
