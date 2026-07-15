package com.oderommanager.app.ui.settings

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.oderommanager.app.MainActivity
import com.oderommanager.app.data.model.ArtworkRegion
import com.oderommanager.app.data.model.FirmwareType
import com.oderommanager.app.data.repository.SettingsRepository
import com.oderommanager.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var settingsRepo: SettingsRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsRepo = SettingsRepository(requireContext())

        loadCurrentSettings()

        // SD card
        binding.btnChangeSdCard.setOnClickListener {
            (requireActivity() as MainActivity).promptForSdCard()
        }

        // ScreenScraper credentials
        binding.btnSaveCredentials.setOnClickListener {
            val username = binding.etSsUsername.text.toString().trim()
            val password = binding.etSsPassword.text.toString().trim()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Please enter both username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            settingsRepo.saveScreenScraperCredentials(username, password)
            Toast.makeText(requireContext(), "Credentials saved", Toast.LENGTH_SHORT).show()
        }

        // Firmware type
        binding.radioStock.setOnClickListener {
            settingsRepo.saveFirmwareType(FirmwareType.STOCK)
        }
        binding.radioSimpleDe.setOnClickListener {
            settingsRepo.saveFirmwareType(FirmwareType.SIMPLE_DE)
        }

        // Region
        binding.radioUsa.setOnClickListener { settingsRepo.saveArtworkRegion(ArtworkRegion.USA) }
        binding.radioEurope.setOnClickListener { settingsRepo.saveArtworkRegion(ArtworkRegion.EUROPE) }
        binding.radioJapan.setOnClickListener { settingsRepo.saveArtworkRegion(ArtworkRegion.JAPAN) }

        // Auto-scan
        binding.switchAutoScan.setOnCheckedChangeListener { _, checked ->
            settingsRepo.saveAutoScan(checked)
        }
    }

    private fun loadCurrentSettings() {
        val settings = settingsRepo.getSettings()

        val sdUri = settingsRepo.getSdCardUri()
        binding.tvSdCardPath.text = if (sdUri != null) {
            "Connected: ${sdUri.path?.substringAfterLast("/") ?: sdUri.toString()}"
        } else {
            "Not configured"
        }

        binding.etSsUsername.setText(settings.ssUsername)
        binding.etSsPassword.setText(settings.ssPassword)

        when (settings.firmwareType) {
            FirmwareType.STOCK -> binding.radioStock.isChecked = true
            FirmwareType.SIMPLE_DE -> binding.radioSimpleDe.isChecked = true
        }

        when (settings.artworkRegion) {
            ArtworkRegion.USA -> binding.radioUsa.isChecked = true
            ArtworkRegion.EUROPE -> binding.radioEurope.isChecked = true
            ArtworkRegion.JAPAN -> binding.radioJapan.isChecked = true
            else -> binding.radioUsa.isChecked = true
        }

        binding.switchAutoScan.isChecked = settings.autoScanOnLaunch
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = SettingsFragment()
    }
}
