package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.model.SystemType
import com.oderommanager.app.data.repository.RomRepository
import kotlinx.coroutines.launch

class HackListViewModel : ViewModel() {

    private lateinit var repository: RomRepository

    private val _gbaRoms = MutableLiveData<List<RomEntry>>()
    val gbaRoms: LiveData<List<RomEntry>> = _gbaRoms

    // Queue for bulk processing — items added by user, processed one-at-a-time
    private val _hackQueue = MutableLiveData<List<RomEntry>>(emptyList())
    val hackQueue: LiveData<List<RomEntry>> = _hackQueue

    private val _openWorkflowFor = MutableLiveData<RomEntry?>(null)
    val openWorkflowFor: LiveData<RomEntry?> = _openWorkflowFor

    fun initialize(context: Context) {
        repository = RomRepository(context)
        repository.allRoms.observeForever { roms ->
            // Only GBA ROMs can have header modification
            _gbaRoms.value = roms.filter { it.systemType == SystemType.GBA }
                .sortedWith(compareByDescending<RomEntry> { it.isRomHack }
                    .thenBy { it.displayName })
        }
    }

    fun addToQueue(rom: RomEntry) {
        val current = _hackQueue.value?.toMutableList() ?: mutableListOf()
        if (current.none { it.id == rom.id }) {
            current.add(rom)
            _hackQueue.value = current
        }
    }

    fun removeFromQueue(rom: RomEntry) {
        val current = _hackQueue.value?.toMutableList() ?: mutableListOf()
        current.removeAll { it.id == rom.id }
        _hackQueue.value = current
    }

    fun startBulkQueue() {
        val queue = _hackQueue.value ?: return
        if (queue.isNotEmpty()) {
            _openWorkflowFor.value = queue.first()
        }
    }

    fun advanceQueue() {
        val current = _hackQueue.value?.toMutableList() ?: return
        if (current.isNotEmpty()) {
            current.removeAt(0)
            _hackQueue.value = current
            if (current.isNotEmpty()) {
                _openWorkflowFor.value = current.first()
            }
        }
    }

    fun clearWorkflowRequest() {
        _openWorkflowFor.value = null
    }
}
