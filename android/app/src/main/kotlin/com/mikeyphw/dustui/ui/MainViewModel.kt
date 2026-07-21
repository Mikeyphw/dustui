package com.mikeyphw.dustui.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.scan.ScanPreset
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: ScanRepository) : ViewModel() {
    val presets = ScanPreset.defaults
    val latestSession: StateFlow<ScanSessionEntity?> = repository.observeLatestSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val entries: StateFlow<List<ScanEntryEntity>> = latestSession
        .flatMapLatest { session -> session?.let { repository.observeEntries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rootState = MutableStateFlow<RootUiState>(RootUiState.Checking)
    val rootState: StateFlow<RootUiState> = _rootState
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init { refreshRoot() }

    fun refreshRoot() {
        _rootState.value = RootUiState.Checking
        viewModelScope.launch {
            _rootState.value = if (repository.rootAvailable()) RootUiState.Available else RootUiState.Unavailable
        }
    }

    fun startPreset(preset: ScanPreset) = startRequest(runCatching(preset::request))

    fun startCustom(path: String) = startRequest(
        runCatching { ScanRequest(rootPath = path.trim()).validated() },
    )

    private fun startRequest(result: Result<ScanRequest>) {
        if (_rootState.value != RootUiState.Available) {
            _message.value = "Root access is required for Phase 1 scans"
            return
        }
        result.onSuccess {
            _message.value = null
            repository.startScan(it)
        }.onFailure { _message.value = it.message }
    }

    fun cancelActive() {
        latestSession.value?.id?.let(repository::cancelScan)
    }

    fun clearMessage() { _message.value = null }
}

sealed interface RootUiState {
    data object Checking : RootUiState
    data object Available : RootUiState
    data object Unavailable : RootUiState
}

class MainViewModelFactory(private val repository: ScanRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
}
