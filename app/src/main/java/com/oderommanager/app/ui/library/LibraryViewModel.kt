package com.oderommanager.app.ui.library

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.repository.RomRepository
import com.oderommanager.app.util.RomNameUtil
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private lateinit var repository: RomRepository
    private var allRomsList: List<RomEntry> = emptyList()
    private var currentSearch = ""

    private val _currentFolder = MutableLiveData<String?>(null)
    val currentFolder: LiveData<String?> = _currentFolder

    private val _filteredRoms = MutableLiveData<List<RomEntry>>()
    val filteredRoms: LiveData<List<RomEntry>> = _filteredRoms

    private val _folderNames = MutableLiveData<List<String>>()
    val folderNames: LiveData<List<String>> = _folderNames

    private val _operationState = MutableLiveData<OpState>(OpState.Idle)
    val operationState: LiveData<OpState> = _operationState

    // Signal to open hack workflow for art replacement
    private val _replaceArtFor = MutableLiveData<Long?>(null)
    val replaceArtFor: LiveData<Long?> = _replaceArtFor

    fun initialize(context: Context) {
        repository = RomRepository(context)
        repository.allRoms.observeForever { roms ->
            allRomsList = roms
            applyFilters()
        }
        repository.allFolderNames.observeForever { folders ->
            _folderNames.value = folders
        }
    }

    fun filterByFolder(folder: String?) {
        _currentFolder.value = folder
        applyFilters()
    }

    fun filterBySearch(query: String) {
        currentSearch = query
        applyFilters()
    }

    private fun applyFilters() {
        var result = allRomsList
        _currentFolder.value?.let { folder ->
            result = result.filter { it.folderName == folder }
        }
        if (currentSearch.isNotBlank()) {
            result = result.filter {
                it.displayName.contains(currentSearch, ignoreCase = true) ||
                        it.fileName.contains(currentSearch, ignoreCase = true)
            }
        }
        _filteredRoms.value = result
    }

    fun renameRom(rom: RomEntry, newName: String) {
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Renaming...")
            when (val r = repository.renameRom(rom, newName)) {
                is RomRepository.RenameResult.Success ->
                    _operationState.value = OpState.Done("Renamed to ${r.newFileName}")
                is RomRepository.RenameResult.Error ->
                    _operationState.value = OpState.Done("Error: ${r.message}")
            }
        }
    }

    fun batchRename() {
        viewModelScope.launch {
            val roms = _filteredRoms.value ?: return@launch
            var success = 0; var failed = 0; var skipped = 0
            roms.forEachIndexed { i, rom ->
                _operationState.value = OpState.Loading("Renaming ${i + 1}/${roms.size}...")
                val suggested = RomNameUtil.cleanName(rom.fileName)
                if (suggested == rom.displayName) { skipped++; return@forEachIndexed }
                when (repository.renameRom(rom, suggested)) {
                    is RomRepository.RenameResult.Success -> success++
                    else -> failed++
                }
            }
            _operationState.value = OpState.Done(
                "Done: $success renamed, $skipped already clean, $failed errors"
            )
        }
    }

    fun scrapeArtworkForRom(rom: RomEntry) {
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Scraping art for ${rom.displayName}...")
            when (val r = repository.scrapeArtwork(rom)) {
                is RomRepository.ArtworkResult.Success ->
                    _operationState.value = OpState.Done("Found: ${r.gameName} (${r.matchMethod})")
                is RomRepository.ArtworkResult.NotFound ->
                    _operationState.value = OpState.Done("Not found on ScreenScraper")
                is RomRepository.ArtworkResult.NoArtAvailable ->
                    _operationState.value = OpState.Done("Game found but no art available")
                is RomRepository.ArtworkResult.Error ->
                    _operationState.value = OpState.Done("Error: ${r.message}")
            }
        }
    }

    fun batchScrapeArt() {
        viewModelScope.launch {
            val roms = (_filteredRoms.value ?: return@launch)
                .filter { it.systemType.name == "GBA" && !it.hasArtwork }
            if (roms.isEmpty()) {
                _operationState.value = OpState.Done("All GBA ROMs here already have art!")
                return@launch
            }
            var success = 0; var notFound = 0; var failed = 0
            roms.forEachIndexed { i, rom ->
                _operationState.value =
                    OpState.Loading("Getting art ${i + 1}/${roms.size}: ${rom.displayName}")
                when (repository.scrapeArtwork(rom)) {
                    is RomRepository.ArtworkResult.Success -> success++
                    is RomRepository.ArtworkResult.NotFound,
                    is RomRepository.ArtworkResult.NoArtAvailable -> notFound++
                    else -> failed++
                }
            }
            _operationState.value = OpState.Done(
                "Art done: $success found, $notFound not found, $failed errors"
            )
        }
    }

    fun verifyArt(romId: Long) {
        viewModelScope.launch {
            val rom = allRomsList.firstOrNull { it.id == romId } ?: return@launch
            repository.verifyArt(rom)
        }
    }

    fun requestReplaceArt(romId: Long) {
        _replaceArtFor.value = romId
    }

    fun clearReplaceArtRequest() {
        _replaceArtFor.value = null
    }

    sealed class OpState {
        object Idle : OpState()
        data class Loading(val message: String) : OpState()
        data class Done(val message: String) : OpState()
    }
}
