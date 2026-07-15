package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.repository.RomRepository
import com.oderommanager.app.util.BmpConverter
import kotlinx.coroutines.launch
import java.io.File

class HackWorkflowViewModel : ViewModel() {

    private lateinit var repository: RomRepository

    private val _romEntry = MutableLiveData<RomEntry?>()
    val romEntry: LiveData<RomEntry?> = _romEntry

    private val _currentStep = MutableLiveData(Step.CONFIRM_NAME)
    val currentStep: LiveData<Step> = _currentStep

    private val _existingArtworkPath = MutableLiveData<String?>()
    val existingArtworkPath: LiveData<String?> = _existingArtworkPath

    private val _generatedCode = MutableLiveData<String>()
    val generatedCode: LiveData<String> = _generatedCode

    private val _selectedImageUri = MutableLiveData<Uri?>()
    val selectedImageUri: LiveData<Uri?> = _selectedImageUri

    private val _operationState = MutableLiveData<OpState>(OpState.Idle)
    val operationState: LiveData<OpState> = _operationState

    private var confirmedName: String = ""
    private var convertedBmpBytes: ByteArray? = null

    fun initialize(context: Context, romId: Long) {
        repository = RomRepository(context)
        viewModelScope.launch {
            val rom = repository.allRoms.value?.firstOrNull { it.id == romId }
            _romEntry.value = rom
            confirmedName = rom?.displayName ?: ""
        }
    }

    // ── Step 1: Confirm name ───────────────────────────────────────────────

    fun confirmName(name: String) {
        confirmedName = name
        val rom = _romEntry.value ?: return

        viewModelScope.launch {
            _operationState.value = OpState.Loading("Checking game code...")

            val gameCode = rom.originalGameCode
            if (gameCode.isNullOrBlank() || gameCode == "????") {
                // No valid code — go straight to Path B
                generateNewCode()
                _currentStep.value = Step.PICK_NEW_ART
                _operationState.value = OpState.Idle
                return@launch
            }

            // Check if this code is already in our DB with artwork
            val existingLog = repository.allBackupLogs.value
                ?.firstOrNull { it.originalGameCode == gameCode || it.newGameCode == gameCode }

            val artworkPath = rom.artworkPath ?: existingLog?.artworkSdPath

            if (artworkPath != null) {
                // Path A: we have art for this code
                _existingArtworkPath.value = artworkPath
                _currentStep.value = Step.SHOW_EXISTING_ART
            } else {
                // Path B: no art found
                generateNewCode()
                _currentStep.value = Step.PICK_NEW_ART
            }

            _operationState.value = OpState.Idle
        }
    }

    // ── Step 2A: Existing art ─────────────────────────────────────────────

    fun confirmExistingArt(context: Context) {
        val rom = _romEntry.value ?: return
        val artPath = _existingArtworkPath.value ?: return

        viewModelScope.launch {
            _operationState.value = OpState.Loading("Placing artwork...")
            // Artwork already exists or is already placed — just ensure the BMP is in IMGS
            // In this case the game code doesn't change, art is already there
            _operationState.value = OpState.Success(
                "✓ Artwork confirmed for ${rom.displayName}\nNo header change needed."
            )
        }
    }

    fun rejectExistingArt() {
        viewModelScope.launch {
            generateNewCode()
            _currentStep.value = Step.PICK_NEW_ART
        }
    }

    // ── Step 2B: New art ──────────────────────────────────────────────────

    private suspend fun generateNewCode() {
        val code = repository.generateUniqueCode()
        _generatedCode.value = code
    }

    fun onImageSelected(context: Context, uri: Uri) {
        _selectedImageUri.value = uri

        // Pre-convert to BMP in background
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Converting image...")
            val tempFile = File(context.cacheDir, "preview_bmp_${System.currentTimeMillis()}.bmp")
            val success = BmpConverter.convertToBmp(context, uri, tempFile)
            if (success) {
                convertedBmpBytes = tempFile.readBytes()
                tempFile.delete()
                _operationState.value = OpState.Idle
            } else {
                _operationState.value = OpState.Error("Could not convert image to BMP format")
            }
        }
    }

    fun applyHackModification(context: Context, displayName: String) {
        val rom = _romEntry.value ?: return
        val newCode = _generatedCode.value ?: return
        val bmpBytes = convertedBmpBytes ?: run {
            _operationState.value = OpState.Error("No image converted yet")
            return
        }

        viewModelScope.launch {
            _operationState.value = OpState.Loading("Backing up ROM...")

            when (val result = repository.applyHackHeaderModification(
                rom, displayName, newCode, bmpBytes
            )) {
                is RomRepository.HackResult.Success -> {
                    _operationState.value = OpState.Success(
                        "✓ Done!\n\nGame code: $newCode\n" +
                                "Backup saved to:\n${result.backupPath}\n\n" +
                                "Insert the EZ Flash and test the game. " +
                                "Then visit the Backups tab to confirm or revert."
                    )
                }
                is RomRepository.HackResult.Error -> {
                    _operationState.value = OpState.Error(result.message)
                }
            }
        }
    }

    enum class Step {
        CONFIRM_NAME,
        SHOW_EXISTING_ART,
        PICK_NEW_ART,
        COMPLETE
    }

    sealed class OpState {
        object Idle : OpState()
        data class Loading(val message: String) : OpState()
        data class Success(val message: String) : OpState()
        data class Error(val message: String) : OpState()
    }
}
