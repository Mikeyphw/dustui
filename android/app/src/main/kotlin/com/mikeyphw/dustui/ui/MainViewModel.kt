package com.mikeyphw.dustui.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mikeyphw.dustui.data.EntryIntelligence
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.data.AppStorageSummary
import com.mikeyphw.dustui.data.SnapshotDelta
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.CleanupOperationEntity
import com.mikeyphw.dustui.data.AdvancedStorageReport
import com.mikeyphw.dustui.data.DuplicateVerificationReport
import com.mikeyphw.dustui.data.AdvancedStorageAnalyzer
import com.mikeyphw.dustui.data.SnapshotPoint
import com.mikeyphw.dustui.data.DeviceCertificationReport
import com.mikeyphw.dustui.data.RootPathInspection
import com.mikeyphw.dustui.data.RootModuleReport
import com.mikeyphw.dustui.data.RootFileSearchResult
import com.mikeyphw.dustui.data.RootFileSearchRequest
import com.mikeyphw.dustui.data.PackageStorageProfile
import com.mikeyphw.dustui.scan.ScanPreset
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: ScanRepository) : ViewModel() {
    val presets = ScanPreset.defaults
    val latestSession: StateFlow<ScanSessionEntity?> = repository.observeLatestSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val allEntries: StateFlow<List<ScanEntryEntity>> = latestSession
        .flatMapLatest { session -> session?.let { repository.observeEntries(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _explorerPath = MutableStateFlow<String?>(null)
    val explorerPath: StateFlow<String?> = _explorerPath
    private val _filter = MutableStateFlow(ExplorerFilter())
    val filter: StateFlow<ExplorerFilter> = _filter
    private val _selectedEntry = MutableStateFlow<ScanEntryEntity?>(null)
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths
    private val _cleanupReview = MutableStateFlow<CleanupReview?>(null)
    val cleanupReview: StateFlow<CleanupReview?> = _cleanupReview
    val cleanupOperations: StateFlow<List<CleanupOperationEntity>> = repository.observeCleanupOperations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedEntry: StateFlow<ScanEntryEntity?> = _selectedEntry
    private val _selectedIntelligence = MutableStateFlow<EntryIntelligence?>(null)
    val selectedIntelligence: StateFlow<EntryIntelligence?> = _selectedIntelligence
    private val _selectedRootInspection = MutableStateFlow<RootPathInspection?>(null)
    val selectedRootInspection: StateFlow<RootPathInspection?> = _selectedRootInspection

    val entries: StateFlow<List<ScanEntryEntity>> = combine(allEntries, latestSession, _explorerPath, _filter) { entries, session, path, filter ->
        val current = path ?: session?.rootPath
        entries.asSequence()
            .filter { current == null || it.parentPath == current }
            .filter { filter.query.isBlank() || it.name.contains(filter.query, true) || it.path.contains(filter.query, true) }
            .filter { when (filter.type) { EntryTypeFilter.ALL -> true; EntryTypeFilter.DIRECTORIES -> it.isDirectory; EntryTypeFilter.FILES -> !it.isDirectory } }
            .filter { it.sizeBytes >= filter.minimumBytes }
            .filter { filter.category == null || AdvancedStorageAnalyzer.fileCategory(it.name) == filter.category }
            .sortedWith(when (filter.sort) {
                EntrySort.SIZE -> compareByDescending<ScanEntryEntity> { it.sizeBytes }.thenBy { it.name.lowercase() }
                EntrySort.NAME -> compareBy<ScanEntryEntity> { it.name.lowercase() }
                EntrySort.MODIFIED -> compareByDescending<ScanEntryEntity> { it.modifiedAt }.thenBy { it.name.lowercase() }
            }).toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rootState = MutableStateFlow<RootUiState>(RootUiState.Checking)
    val rootState: StateFlow<RootUiState> = _rootState
    private val _message = MutableStateFlow<String?>(null)
    private val _appSummaries = MutableStateFlow<List<AppStorageSummary>>(emptyList())
    val appSummaries: StateFlow<List<AppStorageSummary>> = _appSummaries
    private val _snapshotDelta = MutableStateFlow<SnapshotDelta?>(null)
    val snapshotDelta: StateFlow<SnapshotDelta?> = _snapshotDelta
    private val _snapshotHistory = MutableStateFlow<List<SnapshotPoint>>(emptyList())
    val snapshotHistory: StateFlow<List<SnapshotPoint>> = _snapshotHistory
    private val _advancedReport = MutableStateFlow<AdvancedStorageReport?>(null)
    val advancedReport: StateFlow<AdvancedStorageReport?> = _advancedReport
    private val _verifiedDuplicates = MutableStateFlow<DuplicateVerificationReport?>(null)
    val verifiedDuplicates: StateFlow<DuplicateVerificationReport?> = _verifiedDuplicates
    private val _certification = MutableStateFlow<DeviceCertificationReport?>(null)
    val certification: StateFlow<DeviceCertificationReport?> = _certification
    private val _packageProfiles = MutableStateFlow<List<PackageStorageProfile>>(emptyList())
    val packageProfiles: StateFlow<List<PackageStorageProfile>> = _packageProfiles
    private val _rootSearchResults = MutableStateFlow<List<RootFileSearchResult>>(emptyList())
    val rootSearchResults: StateFlow<List<RootFileSearchResult>> = _rootSearchResults
    private val _moduleReport = MutableStateFlow<RootModuleReport?>(null)
    val moduleReport: StateFlow<RootModuleReport?> = _moduleReport
    val message: StateFlow<String?> = _message

    init {
        refreshRoot()
        viewModelScope.launch { latestSession.collect { session ->
            if (_explorerPath.value == null) _explorerPath.value = session?.rootPath
            if (session?.status == "COMPLETED") refreshInsights(session.id)
        } }
    }

    fun refreshRoot() {
        _rootState.value = RootUiState.Checking
        viewModelScope.launch { _rootState.value = if (repository.rootAvailable()) RootUiState.Available else RootUiState.Unavailable }
    }
    fun startPreset(preset: ScanPreset) = startRequest(runCatching(preset::request))
    fun startCustom(path: String) = startRequest(runCatching { ScanRequest(rootPath = path.trim()).validated() })
    private fun startRequest(result: Result<ScanRequest>) {
        if (_rootState.value != RootUiState.Available) { _message.value = "Root access is required"; return }
        result.onSuccess { _explorerPath.value = it.rootPath; repository.startScan(it) }.onFailure { _message.value = it.message }
    }
    fun cancelActive() { latestSession.value?.id?.let(repository::cancelScan) }
    fun open(entry: ScanEntryEntity) {
        if (entry.isDirectory) _explorerPath.value = entry.path else select(entry)
    }
    fun select(entry: ScanEntryEntity) {
        _selectedEntry.value = entry
        _selectedIntelligence.value = repository.inspect(entry.path)
        _selectedRootInspection.value = null
        viewModelScope.launch { _selectedRootInspection.value = repository.inspectRootPath(entry.path) }
    }
    fun closeDetails() { _selectedEntry.value = null; _selectedIntelligence.value = null; _selectedRootInspection.value = null }
    fun navigate(path: String) { _explorerPath.value = path }
    fun up() {
        val root = latestSession.value?.rootPath ?: return
        val current = _explorerPath.value ?: root
        if (current == root) return
        _explorerPath.value = current.substringBeforeLast('/').ifBlank { "/" }.let { if (!it.startsWith(root)) root else it }
    }
    fun updateQuery(value: String) { _filter.value = _filter.value.copy(query = value) }
    fun filterCategory(value: String?) { _filter.value = _filter.value.copy(category = value) }
    fun cycleSort() { _filter.value = _filter.value.copy(sort = EntrySort.entries[(filter.value.sort.ordinal + 1) % EntrySort.entries.size]) }
    fun cycleType() { _filter.value = _filter.value.copy(type = EntryTypeFilter.entries[(filter.value.type.ordinal + 1) % EntryTypeFilter.entries.size]) }
    fun exportCsv() { latestSession.value?.let { repository.exportCsv(it, allEntries.value) } }

    fun toggleSelection(entry: ScanEntryEntity) {
        _selectedPaths.value = _selectedPaths.value.toMutableSet().also { if (!it.add(entry.path)) it.remove(entry.path) }
        _cleanupReview.value = null
    }
    fun clearSelection() { _selectedPaths.value = emptySet(); _cleanupReview.value = null }
    fun reviewCleanup() {
        val session = latestSession.value ?: return
        val chosen = allEntries.value.filter { it.path in _selectedPaths.value }
        if (chosen.isEmpty()) { _message.value = "Select at least one entry"; return }
        viewModelScope.launch { _cleanupReview.value = repository.reviewCleanup(session, chosen) }
    }
    fun dismissCleanupReview() { _cleanupReview.value = null }
    fun executeCleanup() {
        val session = latestSession.value ?: return
        val chosen = allEntries.value.filter { it.path in _selectedPaths.value }
        viewModelScope.launch {
            val result = repository.executeCleanup(session, chosen)
            _message.value = if (result.ok) "Quarantined ${formatBytes(result.reclaimedBytes)}" else result.message ?: "Cleanup failed"
            if (result.ok) { clearSelection(); refreshInsights() }
        }
    }
    fun restoreCleanup(operationId: String) {
        viewModelScope.launch {
            val result = repository.restoreCleanup(operationId)
            _message.value = if (result.ok) "Cleanup restored" else result.message ?: "Restore failed"
        }
    }
    fun refreshInsights(scanId: String? = latestSession.value?.id) {
        if (scanId == null) return
        viewModelScope.launch {
            _appSummaries.value = repository.appSummaries(scanId)
            _snapshotDelta.value = repository.snapshotDelta()
            _snapshotHistory.value = repository.snapshotHistory()
            _advancedReport.value = repository.advancedAnalysis(scanId)
            _packageProfiles.value = repository.packageProfiles(scanId)
            _moduleReport.value = repository.moduleAnalysis(scanId)
        }
    }

    fun rootSearch(query: String) {
        val scanId = latestSession.value?.id
        if (scanId == null) { _message.value = "Run a scan before searching"; return }
        viewModelScope.launch {
            val results = repository.rootSearch(scanId, RootFileSearchRequest(query = query))
            _rootSearchResults.value = results
            _message.value = "Found ${results.size} root-indexed result(s)"
        }
    }

    fun openSearchResult(result: RootFileSearchResult) {
        _explorerPath.value = result.parentPath
        _filter.value = _filter.value.copy(query = result.name)
        allEntries.value.firstOrNull { it.path == result.path }?.let { select(it) }
    }
    fun runDeviceCertification() {
        viewModelScope.launch {
            _certification.value = repository.deviceCertification()
            val report = _certification.value ?: return@launch
            _message.value = if (report.releaseReady) "Device certification passed with ${report.warnings} warning(s)" else "Device certification found ${report.failures} blocker(s)"
        }
    }

    fun verifyTopDuplicates() {
        val group = _advancedReport.value?.duplicateCandidates?.firstOrNull()
        if (group == null) { _message.value = "No duplicate candidates to verify"; return }
        viewModelScope.launch {
            val report = repository.verifyDuplicates(group)
            _verifiedDuplicates.value = report
            _message.value = report.message ?: "Verified ${report.checkedFiles} files"
        }
    }
    fun scheduleLatest(hours: Long = 24) {
        val request = latestSession.value?.optionsJson?.let { runCatching { ScanRequest.fromJson(it) }.getOrNull() }
        if (request == null) _message.value = "Run a scan before scheduling" else { repository.schedule(request, hours); _message.value = "Scheduled every $hours hours" }
    }
    fun cancelSchedule() { repository.cancelSchedule(); _message.value = "Scheduled scans disabled" }
    fun clearMessage() { _message.value = null }
}

sealed interface RootUiState { data object Checking : RootUiState; data object Available : RootUiState; data object Unavailable : RootUiState }
class MainViewModelFactory(private val repository: ScanRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repository) as T
}
