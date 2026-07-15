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

    val romEntry = MutableLiveData<RomEntry?>()

    private val _currentStep = MutableLiveData(Step.REVIEW_HEADER)
    val currentStep: LiveData<Step> = _currentStep

    private val _existingArtworkPath = MutableLiveData<String?>()
    val existingArtworkPath: LiveData<String?> = _existingArtworkPath

    private val _generatedCode = MutableLiveData<String>()
    val generatedCode: LiveData<String> = _generatedCode

    val selectedImageUri = MutableLiveData<Uri?>()

    private val _operationState = MutableLiveData<OpState>(OpState.Idle)
    val operationState: LiveData<OpState> = _operationState

    private var confirmedName: String = ""
    private var convertedBmpBytes: ByteArray? = null

    fun initialize(context: Context, romId: Long) {
        repository = RomRepository(context)
        viewModelScope.launch {
            // Fix #4: load rom and go straight to header review step
            val rom = repository.allRoms.value?.firstOrNull { it.id == romId }
                ?: repository.allRoms.value?.firstOrNull()
            romEntry.value = rom
            confirmedName = rom?.displayName ?: ""
        }
    }

    // Step 1: User reviews header info and confirms or edits the name
    fun confirmHeaderAndName(name: String) {
        confirmedName = name
        val rom = romEntry.value ?: return

        viewModelScope.launch {
            _operationState.value = OpState.Loading("Checking game code...")

            val gameCode = rom.assignedGameCode ?: rom.originalGameCode?.trim()?.uppercase()

            if (!gameCode.isNullOrBlank() && rom.hasArtwork && rom.artworkPath != null) {
                // Art already exists for this code — show it for confirmation
                _existingArtworkPath.value = rom.artworkPath
                _currentStep.value = Step.CONFIRM_EXISTING_ART
            } else {
                // No art yet — generate a new code and go to image picking
                val newCode = repository.generateUniqueCode()
                _generatedCode.value = newCode
                _currentStep.value = Step.PICK_NEW_ART
            }
            _operationState.value = OpState.Idle
        }
    }

    fun confirmExistingArt() {
        _operationState.value = OpState.Success(
            "✓ Artwork already in place for ${romEntry.value?.displayName}\nNo changes needed."
        )
    }

    fun rejectExistingArt() {
        viewModelScope.launch {
            val newCode = repository.generateUniqueCode()
            _generatedCode.value = newCode
            _currentStep.value = Step.PICK_NEW_ART
        }
    }

    fun onImageSelected(context: Context, uri: Uri) {
        selectedImageUri.value = uri
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Converting image to EZ Flash BMP format...")
            val tempFile = File(context.cacheDir, "preview_bmp_${System.currentTimeMillis()}.bmp")
            if (BmpConverter.convertToBmp(context, uri, tempFile)) {
                convertedBmpBytes = tempFile.readBytes()
                tempFile.delete()
                _operationState.value = OpState.Idle
            } else {
                _operationState.value = OpState.Error("Could not convert image")
            }
        }
    }

    fun applyHackModification(context: Context, displayName: String) {
        val rom = romEntry.value ?: return
        val newCode = _generatedCode.value ?: return
        val bmpBytes = convertedBmpBytes ?: run {
            _operationState.value = OpState.Error("No image converted yet")
            return
        }
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Backing up ROM and writing new header...")
            when (val result = repository.applyHackHeaderModification(rom, displayName, newCode, bmpBytes)) {
                is RomRepository.HackResult.Success -> {
                    _operationState.value = OpState.Success(
                        "✓ Done!\n\nNew code: $newCode\nBackup at:\n${result.backupPath}\n\n" +
                                "Test it on your EZ Flash, then go to the Backups tab to confirm or revert."
                    )
                }
                is RomRepository.HackResult.Error ->
                    _operationState.value = OpState.Error(result.message)
            }
        }
    }

    enum class Step {
        REVIEW_HEADER,          // Fix #4: show header info first
        CONFIRM_EXISTING_ART,
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
