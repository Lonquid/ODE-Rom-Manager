package com.oderommanager.app.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oderommanager.app.data.repository.RomRepository
import com.oderommanager.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private lateinit var romRepository: RomRepository
    private lateinit var settingsRepository: SettingsRepository

    private val _sdCardStatus = MutableLiveData<String>()
    val sdCardStatus: LiveData<String> = _sdCardStatus

    private val _totalRoms = MutableLiveData(0)
    val totalRoms: LiveData<Int> = _totalRoms

    private val _romsWithArt = MutableLiveData(0)
    val romsWithArt: LiveData<Int> = _romsWithArt

    private val _romHacks = MutableLiveData(0)
    val romHacks: LiveData<Int> = _romHacks

    private val _pendingBackups = MutableLiveData(0)
    val pendingBackups: LiveData<Int> = _pendingBackups

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    fun initialize(context: Context) {
        romRepository = RomRepository(context)
        settingsRepository = SettingsRepository(context)

        updateSdCardStatus()

        romRepository.allRoms.observeForever { roms ->
            _totalRoms.value = roms.size
            _romsWithArt.value = roms.count { it.hasArtwork }
            _romHacks.value = roms.count { it.isRomHack }
        }

        romRepository.pendingBackupCount.observeForever { count ->
            _pendingBackups.value = count ?: 0
        }
    }

    private fun updateSdCardStatus() {
        val uri = settingsRepository.getSdCardUri()
        _sdCardStatus.value = if (uri != null) {
            "✓ SD card connected"
        } else {
            "⚠ No SD card configured — tap to connect"
        }
    }

    fun scanSdCard() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            when (val result = romRepository.scanSdCard()) {
                is RomRepository.ScanResult.Success -> {
                    _scanState.value = ScanState.Done(
                        "Scan complete: ${result.newCount} new, " +
                                "${result.totalFound} total ROMs found"
                    )
                }
                is RomRepository.ScanResult.Error -> {
                    _scanState.value = ScanState.Done("Error: ${result.message}")
                }
            }
        }
    }

    sealed class ScanState {
        object Idle : ScanState()
        object Scanning : ScanState()
        data class Done(val message: String) : ScanState()
    }
}
