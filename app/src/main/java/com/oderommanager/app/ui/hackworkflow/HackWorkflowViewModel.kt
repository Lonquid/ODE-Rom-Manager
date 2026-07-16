package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.oderommanager.app.data.db.AppDatabase
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.repository.RomRepository
import com.oderommanager.app.data.repository.SettingsRepository
import com.oderommanager.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

data class ImageCandidate(
    val url: String,
    val source: String,
    val label: String
)

class HackWorkflowViewModel : ViewModel() {

    private lateinit var repository: RomRepository
    private lateinit var db: AppDatabase

    val romEntry = MutableLiveData<RomEntry?>()

    private val _currentStep = MutableLiveData(Step.REVIEW_HEADER)
    val currentStep: LiveData<Step> = _currentStep

    private val _existingArtworkPath = MutableLiveData<String?>()
    val existingArtworkPath: LiveData<String?> = _existingArtworkPath

    private val _generatedCode = MutableLiveData<String>()
    val generatedCode: LiveData<String> = _generatedCode

    private val _imageCandidates = MutableLiveData<List<ImageCandidate>>(emptyList())
    val imageCandidates: LiveData<List<ImageCandidate>> = _imageCandidates

    private val _searchStatus = MutableLiveData("Searching...")
    val searchStatus: LiveData<String> = _searchStatus

    val selectedImageUri = MutableLiveData<Uri?>()
    val selectedSource = MutableLiveData<String?>()

    private val _btnApplyEnabled = MutableLiveData(false)
    val btnApplyEnabled: LiveData<Boolean> = _btnApplyEnabled

    private val _operationState = MutableLiveData<OpState>(OpState.Idle)
    val operationState: LiveData<OpState> = _operationState

    private var confirmedName: String = ""
    private var convertedBmpBytes: ByteArray? = null

    // Shared HTTP client for candidate checks
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun initialize(context: Context, romId: Long) {
        repository = RomRepository(context)
        db = AppDatabase.getInstance(context)
        viewModelScope.launch {
            val rom = db.romEntryDao().getById(romId)
            romEntry.value = rom
            confirmedName = rom?.displayName ?: ""
        }
    }

    fun confirmHeaderAndName(name: String) {
        confirmedName = name
        val rom = romEntry.value ?: return
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Checking...")
            val alreadyProcessed = rom.assignedGameCode != null
            val hasArt = rom.hasArtwork && !rom.artworkPath.isNullOrBlank()
            if (alreadyProcessed && hasArt) {
                _existingArtworkPath.value = rom.artworkPath
                _currentStep.value = Step.CONFIRM_EXISTING_ART
            } else {
                val newCode = repository.generateUniqueCode()
                _generatedCode.value = newCode
                _currentStep.value = Step.PICK_IMAGE
            }
            _operationState.value = OpState.Idle
        }
    }

    fun searchForImagesWithContext(context: Context, displayName: String, rom: RomEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = mutableListOf<ImageCandidate>()
            val settings = SettingsRepository(context).getSettings()
            val officialName = rom.officialName ?: displayName

            withContext(Dispatchers.Main) { _searchStatus.value = "Searching all sources..." }

            // Source 1: libretro thumbnails (direct URL check — no API key needed)
            for (name in listOf(officialName, displayName).distinct()) {
                val url = LibretroThumbnailApi.getThumbnailUrl(name)
                try {
                    val resp = httpClient.newCall(
                        okhttp3.Request.Builder().url(url).head().build()
                    ).execute()
                    if (resp.isSuccessful) {
                        candidates.add(ImageCandidate(url, "libretro", "Libretro Boxart"))
                        break
                    }
                } catch (e: Exception) { }
            }

            withContext(Dispatchers.Main) {
                _imageCandidates.value = candidates.toList()
            }

            // Source 2: ScreenScraper by filename
            if (settings.ssUsername.isNotBlank()) {
                try {
                    val api = ScreenScraperApi(settings.ssUsername, settings.ssPassword)
                    val result = api.scrapeByFilename(rom.fileName)
                    if (result?.boxArtUrl != null) {
                        candidates.add(ImageCandidate(
                            result.boxArtUrl, "ScreenScraper", "ScreenScraper: ${result.gameName}"
                        ))
                    }
                } catch (e: Exception) { }
                withContext(Dispatchers.Main) { _imageCandidates.value = candidates.toList() }
            }

            // Source 3: TheGamesDB
            try {
                val tgdb = TheGamesDbApi.searchByName(displayName)
                if (tgdb?.boxArtUrl != null) {
                    candidates.add(ImageCandidate(
                        tgdb.boxArtUrl, "TheGamesDB", "TheGamesDB: ${tgdb.gameName}"
                    ))
                }
            } catch (e: Exception) { }

            withContext(Dispatchers.Main) {
                _imageCandidates.value = candidates.toList()
                _searchStatus.value = if (candidates.isEmpty())
                    "No images found — upload your own below"
                else
                    "${candidates.size} image${if (candidates.size != 1) "s" else ""} found — tap to select"

                // Auto-select first candidate
                if (candidates.isNotEmpty()) {
                    selectCandidate(context, candidates.first())
                }
            }
        }
    }

    fun selectCandidate(context: Context, candidate: ImageCandidate) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _operationState.value = OpState.Loading("Loading ${candidate.source} image...")
            }
            val tempFile = File(context.cacheDir, "candidate_${System.currentTimeMillis()}.jpg")
            try {
                val resp = httpClient.newCall(
                    okhttp3.Request.Builder().url(candidate.url).build()
                ).execute()
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.size > 100) {
                    tempFile.writeBytes(bytes)
                    val uri = Uri.fromFile(tempFile)
                    withContext(Dispatchers.Main) {
                        selectedImageUri.value = uri
                        selectedSource.value = candidate.source
                    }
                    convertImage(context, uri)
                } else {
                    withContext(Dispatchers.Main) {
                        _operationState.value = OpState.Error("Failed to load from ${candidate.source}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _operationState.value = OpState.Error("Download failed: ${e.message}")
                }
            }
        }
    }

    fun onImageSelected(context: Context, uri: Uri) {
        selectedImageUri.value = uri
        selectedSource.value = "Uploaded"
        viewModelScope.launch { convertImage(context, uri) }
    }

    private suspend fun convertImage(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _operationState.value = OpState.Loading("Converting to EZ Flash BMP (120×80)...")
        }
        val tempFile = File(context.cacheDir, "bmp_${System.currentTimeMillis()}.bmp")
        if (BmpConverter.convertToBmp(context, uri, tempFile)) {
            convertedBmpBytes = tempFile.readBytes()
            tempFile.delete()
            withContext(Dispatchers.Main) {
                _operationState.value = OpState.Idle
                _btnApplyEnabled.value = true
            }
        } else {
            withContext(Dispatchers.Main) {
                _operationState.value = OpState.Error("Conversion failed — try a different image")
                _btnApplyEnabled.value = false
            }
        }
    }

    fun confirmExistingArt() {
        _operationState.value = OpState.Success("✓ Artwork confirmed for ${romEntry.value?.displayName}")
    }

    fun rejectExistingArt(context: Context) {
        val rom = romEntry.value ?: return
        viewModelScope.launch {
            val newCode = repository.generateUniqueCode()
            _generatedCode.value = newCode
            _currentStep.value = Step.PICK_IMAGE
            searchForImagesWithContext(context, confirmedName, rom)
        }
    }

    fun applyHackModification(context: Context, displayName: String) {
        val rom = romEntry.value ?: return
        val newCode = _generatedCode.value ?: return
        val bmpBytes = convertedBmpBytes ?: run {
            _operationState.value = OpState.Error("No image selected yet")
            return
        }
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Backing up ROM and writing new header...")
            when (val result = repository.applyHackHeaderModification(rom, displayName, newCode, bmpBytes)) {
                is RomRepository.HackResult.Success ->
                    _operationState.value = OpState.Success(
                        "✓ Done!\n\nCode: $newCode\nBackup: ${result.backupPath}\n\nTest on EZ Flash, then confirm in Backups tab."
                    )
                is RomRepository.HackResult.Error ->
                    _operationState.value = OpState.Error(result.message)
            }
        }
    }

    enum class Step { REVIEW_HEADER, CONFIRM_EXISTING_ART, PICK_IMAGE, COMPLETE }

    sealed class OpState {
        object Idle : OpState()
        data class Loading(val message: String) : OpState()
        data class Success(val message: String) : OpState()
        data class Error(val message: String) : OpState()
    }
}
