package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.db.AppDatabase
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.util.GbaSerialDatabase
import kotlinx.coroutines.launch

class HackListViewModel : ViewModel() {

    private lateinit var db: AppDatabase

    val allGbaRoms get() = db.romEntryDao().getAllGbaRoms()

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private val _openWorkflowFor = MutableLiveData<RomEntry?>(null)
    val openWorkflowFor: LiveData<RomEntry?> = _openWorkflowFor

    // Current filter: null=all, "HACK"=mismatches only, "UNKNOWN_SERIAL"=unknown only
    private val _filter = MutableLiveData<String?>(null)
    val filter: LiveData<String?> = _filter

    fun initialize(context: Context) {
        db = AppDatabase.getInstance(context)
    }

    /**
     * Run the mismatch scan against the bundled No-Intro database.
     * Processes all GBA ROMs and updates their mismatchType in the DB.
     */
    fun runMismatchScan(context: Context) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning("Loading No-Intro database...")

            // Pre-load the serial DB
            val serialDb = GbaSerialDatabase.load(context)
            if (serialDb.isEmpty()) {
                _scanState.value = ScanState.Done("Failed to load No-Intro database")
                return@launch
            }

            val allRoms = db.romEntryDao().getAllGbaRoms().value ?: run {
                // Force a fresh query since LiveData.value may be null
                _scanState.value = ScanState.Done("No GBA ROMs found — scan your SD card first")
                return@launch
            }

            var mismatches = 0; var unknown = 0; var matches = 0; var translations = 0

            allRoms.forEachIndexed { index, rom ->
                if (index % 5 == 0) {
                    _scanState.value = ScanState.Scanning(
                        "Scanning ${index + 1}/${allRoms.size}..."
                    )
                }

                val code = (rom.assignedGameCode ?: rom.originalGameCode)
                    ?.trim()?.uppercase()

                if (code.isNullOrBlank() || code == "????") {
                    db.romEntryDao().setMismatchResult(rom.id, "UNKNOWN_SERIAL", null)
                    unknown++
                    return@forEachIndexed
                }

                // Check if it's a translation (T-En etc in filename)
                val isTranslation = rom.fileName.contains(Regex("\\(T-[A-Za-z]{2,3}", RegexOption.IGNORE_CASE))

                val result = GbaSerialDatabase.checkMismatch(context, code, rom.fileName)

                when (result) {
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
                "Scan complete: $matches matched, $mismatches mismatches, " +
                        "$translations translations, $unknown unknown serials"
            )
        }
    }

    fun setFilter(filter: String?) {
        _filter.value = filter
    }

    fun openWorkflow(rom: RomEntry) {
        _openWorkflowFor.value = rom
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
