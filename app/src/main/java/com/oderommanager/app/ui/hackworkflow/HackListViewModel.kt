package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.db.AppDatabase
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.util.GbaSerialDatabase
import kotlinx.coroutines.launch

class HackListViewModel : ViewModel() {

    private lateinit var db: AppDatabase

    // LiveData for the RecyclerView — filtered by current chip selection
    val allGbaRoms get() = db.romEntryDao().getAllGbaRoms()

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private val _openWorkflowFor = MutableLiveData<RomEntry?>(null)
    val openWorkflowFor: LiveData<RomEntry?> = _openWorkflowFor

    private val _filter = MutableLiveData<String?>(null)
    val filter: LiveData<String?> = _filter

    fun initialize(context: Context) {
        db = AppDatabase.getInstance(context)
    }

    fun runMismatchScan(context: Context) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning("Loading No-Intro database...")

            val serialDb = GbaSerialDatabase.load(context)
            if (serialDb.isEmpty()) {
                _scanState.value = ScanState.Done("Failed to load database — check app installation")
                return@launch
            }

            // Fix #3: use direct suspend query instead of LiveData.value (which is null)
            val allRoms = db.romEntryDao().getAllGbaRomsDirect()

            if (allRoms.isEmpty()) {
                _scanState.value = ScanState.Done("No GBA ROMs found — scan your SD card from the Home tab first")
                return@launch
            }

            _scanState.value = ScanState.Scanning("Scanning ${allRoms.size} GBA ROMs...")

            var mismatches = 0; var unknown = 0; var matches = 0; var translations = 0

            allRoms.forEachIndexed { index, rom ->
                if (index % 10 == 0) {
                    _scanState.value = ScanState.Scanning(
                        "Checking ${index + 1}/${allRoms.size}: ${rom.displayName}"
                    )
                }

                val code = (rom.assignedGameCode ?: rom.originalGameCode)
                    ?.trim()?.uppercase()

                // No valid code — can't identify
                if (code.isNullOrBlank() || code == "????" || code.length < 4) {
                    db.romEntryDao().setMismatchResult(rom.id, "UNKNOWN_SERIAL", null)
                    unknown++
                    return@forEachIndexed
                }

                // Translation detection by filename
                val isTranslation = rom.fileName.contains(
                    Regex("\\(T-[A-Za-z]{2,3}", RegexOption.IGNORE_CASE)
                )

                when (val result = GbaSerialDatabase.checkMismatch(context, code, rom.fileName)) {
                    is GbaSerialDatabase.MismatchResult.Match -> {
                        db.romEntryDao().setMismatchResult(rom.id, "MATCH", result.info.name)
                        matches++
                    }
                    is GbaSerialDatabase.MismatchResult.Mismatch -> {
                        val type = if (isTranslation) "TRANSLATION" else "HACK"
                        db.romEntryDao().setMismatchResult(rom.id, type, result.officialInfo.name)
                        if (type == "HACK") mismatches++ else translations++
                    }
                    is GbaSerialDatabase.MismatchResult.UnknownSerial -> {
                        val type = if (isTranslation) "TRANSLATION" else "UNKNOWN_SERIAL"
                        db.romEntryDao().setMismatchResult(rom.id, type, null)
                        if (type == "UNKNOWN_SERIAL") unknown++ else translations++
                    }
                }
            }

            _scanState.value = ScanState.Done(
                "Scan complete — ${allRoms.size} ROMs checked\n" +
                "$matches matched · $mismatches mismatches · " +
                "$translations translations · $unknown unknown"
            )
        }
    }

    fun setFilter(filter: String?) {
        _filter.value = filter
    }

    fun clearWorkflowRequest() {
        _openWorkflowFor.value = null
    }

    sealed class ScanState {
        object Idle : ScanState()
        data class Scanning(val message: String) : ScanState()
        data class Done(val message: String) : ScanState()
    }
}
