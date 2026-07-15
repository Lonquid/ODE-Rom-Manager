package com.oderommanager.app.ui.hackworkflow

import android.content.Context
import androidx.lifecycle.*
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.data.repository.RomRepository

class HackListViewModel : ViewModel() {

    private lateinit var repository: RomRepository

    val hackCandidates get() = repository.hackCandidates

    private val _openWorkflowFor = MutableLiveData<RomEntry?>(null)
    val openWorkflowFor: LiveData<RomEntry?> = _openWorkflowFor

    fun initialize(context: Context) {
        repository = RomRepository(context)
    }

    fun clearWorkflowRequest() {
        _openWorkflowFor.value = null
    }
}
