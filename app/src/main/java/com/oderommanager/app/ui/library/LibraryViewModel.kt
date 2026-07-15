package com.oderommanager.app.ui.library

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType
import com.oderommanager.app.data.repository.RomRepository
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {

    private lateinit var repository: RomRepository

    private var allRomsList: List<RomEntry> = emptyList()
    private var currentSearch = ""
    private var currentSystemFilter: SystemType? = null
    private val bulkSelected = mutableSetOf<Long>()

    private val _filteredRoms = MutableLiveData<List<RomEntry>>()
    val filteredRoms: LiveData<List<RomEntry>> = _filteredRoms

    private val _bulkSelectionActive = MutableLiveData(false)
    val bulkSelectionActive: LiveData<Boolean> = _bulkSelectionActive

    private val _operationState = MutableLiveData<OpState>(OpState.Idle)
    val operationState: LiveData<OpState> = _operationState

    fun initialize(context: Context) {
        repository = RomRepository(context)
        repository.allRoms.observeForever { roms ->
            allRomsList = roms
            applyFilters()
        }
    }

    fun filterRoms(query: String) {
        currentSearch = query
        applyFilters()
    }

    fun filterBySystem(systemName: String?) {
        currentSystemFilter = systemName?.let { SystemType.valueOf(it) }
        applyFilters()
    }

    private fun applyFilters() {
        var result = allRomsList
        if (currentSearch.isNotBlank()) {
            result = result.filter {
                it.displayName.contains(currentSearch, ignoreCase = true) ||
                        it.fileName.contains(currentSearch, ignoreCase = true)
            }
        }
        currentSystemFilter?.let { system ->
            result = result.filter { it.systemType == system }
        }
        _filteredRoms.value = result
    }

    fun toggleBulkMode() {
        val active = _bulkSelectionActive.value ?: false
        _bulkSelectionActive.value = !active
        if (active) bulkSelected.clear()
    }

    fun toggleBulkSelect(rom: RomEntry, selected: Boolean) {
        if (selected) bulkSelected.add(rom.id) else bulkSelected.remove(rom.id)
    }

    fun selectAll() {
        bulkSelected.addAll(allRomsList.map { it.id })
        applyFilters()
    }

    fun clearBulkSelection() {
        bulkSelected.clear()
        _bulkSelectionActive.value = false
    }

    fun renameRom(rom: RomEntry, newName: String) {
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Renaming ${rom.displayName}...")
            when (val result = repository.renameRom(rom, newName)) {
                is RomRepository.RenameResult.Success ->
                    _operationState.value = OpState.Done("Renamed to ${result.newFileName}")
                is RomRepository.RenameResult.Error ->
                    _operationState.value = OpState.Done("Error: ${result.message}")
            }
        }
    }

    fun bulkRename() {
        val selected = allRomsList.filter { it.id in bulkSelected }
        viewModelScope.launch {
            var success = 0
            var failed = 0
            selected.forEachIndexed { index, rom ->
                _operationState.value = OpState.Loading(
                    "Renaming ${index + 1}/${selected.size}: ${rom.displayName}"
                )
                val suggestedName = com.oderommanager.app.util.RomNameUtil.cleanName(rom.fileName)
                when (repository.renameRom(rom, suggestedName)) {
                    is RomRepository.RenameResult.Success -> success++
                    is RomRepository.RenameResult.Error -> failed++
                }
            }
            _operationState.value = OpState.Done("Renamed $success ROMs. $failed failed.")
            clearBulkSelection()
        }
    }

    fun scrapeArtworkForRom(rom: RomEntry) {
        viewModelScope.launch {
            _operationState.value = OpState.Loading("Scraping art for ${rom.displayName}...")
            when (val result = repository.scrapeArtwork(rom)) {
                is RomRepository.ArtworkResult.Success ->
                    _operationState.value = OpState.Done(
                        "Art found for ${result.gameName} (via ${result.matchMethod})"
                    )
                is RomRepository.ArtworkResult.NotFound ->
                    _operationState.value = OpState.Done("Not found on ScreenScraper")
                is RomRepository.ArtworkResult.NoArtAvailable ->
                    _operationState.value = OpState.Done("Game found but no art available")
                is RomRepository.ArtworkResult.Error ->
                    _operationState.value = OpState.Done("Error: ${result.message}")
            }
        }
    }

    fun bulkScrapeArt() {
        val selected = allRomsList.filter { it.id in bulkSelected && !it.hasArtwork }
        viewModelScope.launch {
            var success = 0
            var notFound = 0
            var failed = 0
            selected.forEachIndexed { index, rom ->
                _operationState.value = OpState.Loading(
                    "Scraping art ${index + 1}/${selected.size}: ${rom.displayName}"
                )
                when (repository.scrapeArtwork(rom)) {
                    is RomRepository.ArtworkResult.Success -> success++
                    is RomRepository.ArtworkResult.NotFound -> notFound++
                    is RomRepository.ArtworkResult.NoArtAvailable -> notFound++
                    is RomRepository.ArtworkResult.Error -> failed++
                }
            }
            _operationState.value = OpState.Done(
                "Art scrape done: $success found, $notFound not found, $failed errors"
            )
            clearBulkSelection()
        }
    }

    sealed class OpState {
        object Idle : OpState()
        data class Loading(val message: String) : OpState()
        data class Done(val message: String) : OpState()
    }
}
