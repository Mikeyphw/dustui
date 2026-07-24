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
import com.mikeyphw.dustui.data.LargeFileFilter
import com.mikeyphw.dustui.data.LargeFileGroup
import com.mikeyphw.dustui.data.LargeFilePage
import com.mikeyphw.dustui.data.LargeFilePreset
import com.mikeyphw.dustui.data.LargeFileSort
import com.mikeyphw.dustui.data.LargeFileWorkbenchEngine
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
    private var activeSessionId: String? = null
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
    private val _scanDataStale = MutableStateFlow(false)
    val scanDataStale: StateFlow<Boolean> = _scanDataStale
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
    private val _categoryFiles = MutableStateFlow<CategoryFileSelection?>(null)
    val categoryFiles: StateFlow<CategoryFileSelection?> = _categoryFiles
    private val _largeFileSourceEntries = MutableStateFlow<List<ScanEntryEntity>>(emptyList())
    private val _largeFileLoading = MutableStateFlow(false)
    val largeFileLoading: StateFlow<Boolean> = _largeFileLoading
    private val _largeFileFilter = MutableStateFlow(LargeFileFilter())
    val largeFileFilter: StateFlow<LargeFileFilter> = _largeFileFilter
    val largeFiles: StateFlow<LargeFilePage> = combine(_largeFileSourceEntries, _largeFileFilter) { entries, filter ->
        LargeFileWorkbenchEngine.evaluate(entries, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LargeFilePage.empty())
    private val _moduleReport = MutableStateFlow<RootModuleReport?>(null)
    val moduleReport: StateFlow<RootModuleReport?> = _moduleReport
    val message: StateFlow<String?> = _message

    init {
        refreshRoot()
        viewModelScope.launch { latestSession.collect { session ->
            if (session?.id != activeSessionId) {
                activeSessionId = session?.id
                resetScanScopedState(session)
            }
            if (session?.status == "COMPLETED") refreshInsights(session.id)
        } }
    }

    private fun resetScanScopedState(session: ScanSessionEntity?) {
        _explorerPath.value = session?.rootPath
        _filter.value = ExplorerFilter()
        _selectedEntry.value = null
        _selectedIntelligence.value = null
        _selectedRootInspection.value = null
        _selectedPaths.value = emptySet()
        _cleanupReview.value = null
        _rootSearchResults.value = emptyList()
        _categoryFiles.value = null
        _largeFileSourceEntries.value = emptyList()
        _largeFileLoading.value = false
        _largeFileFilter.value = LargeFileFilter()
        _verifiedDuplicates.value = null
        _appSummaries.value = emptyList()
        _snapshotDelta.value = null
        _snapshotHistory.value = emptyList()
        _advancedReport.value = null
        _packageProfiles.value = emptyList()
        _moduleReport.value = null
        _scanDataStale.value = false
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

    fun openCategoryFiles(label: String, categories: Set<String>) {
        val matching = CategoryPathSelector.matchingEntries(allEntries.value, categories)
        if (matching.isEmpty()) {
            _message.value = "No indexed files are available for $label"
            return
        }
        _categoryFiles.value = CategoryFileSelection(label, categories.toSet(), matching)
    }

    fun dismissCategoryFiles() { _categoryFiles.value = null }

    fun updateLargeFileQuery(value: String) {
        _largeFileFilter.value = _largeFileFilter.value.copy(query = value, page = 0)
    }

    fun applyLargeFilePreset(preset: LargeFilePreset) {
        _largeFileFilter.value = _largeFileFilter.value.copy(preset = preset, category = null, page = 0)
        reloadLargeFileEntries(preset)
    }

    private fun reloadLargeFileEntries(preset: LargeFilePreset = _largeFileFilter.value.preset) {
        val scanId = latestSession.value?.id ?: return
        _largeFileLoading.value = true
        viewModelScope.launch {
            val result = runCatching { repository.largeFileEntries(scanId, preset) }
            if (latestSession.value?.id == scanId && _largeFileFilter.value.preset == preset) {
                result.onSuccess { _largeFileSourceEntries.value = it }
                    .onFailure { _message.value = it.message ?: "Unable to load large files" }
                _largeFileLoading.value = false
            }
        }
    }

    fun filterLargeFilesByCategory(category: String?) {
        _largeFileFilter.value = _largeFileFilter.value.copy(category = category, page = 0)
    }

    fun cycleLargeFileSort() {
        val current = _largeFileFilter.value
        val options = LargeFileSort.entries
        _largeFileFilter.value = current.copy(sort = options[(current.sort.ordinal + 1) % options.size], page = 0)
    }

    fun cycleLargeFileGroup() {
        val current = _largeFileFilter.value
        val options = LargeFileGroup.entries
        _largeFileFilter.value = current.copy(group = options[(current.group.ordinal + 1) % options.size], page = 0)
    }

    fun previousLargeFilePage() {
        val current = _largeFileFilter.value
        _largeFileFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextLargeFilePage() {
        val current = _largeFileFilter.value
        val lastPage = (largeFiles.value.pageCount - 1).coerceAtLeast(0)
        _largeFileFilter.value = current.copy(page = (current.page + 1).coerceAtMost(lastPage))
    }

    fun selectLargeFilePage() {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        val paths = largeFiles.value.pageItems.map { it.entry.path }
        if (paths.isEmpty()) {
            _message.value = "No large files are visible on this page"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} large file(s) from this page"
    }

    fun reviewLargeFilePage() {
        val paths = largeFiles.value.pageItems.map { it.entry.path }
        if (paths.isEmpty()) {
            _message.value = "No large files are visible on this page"
            return
        }
        reviewPaths(paths)
    }

    fun selectCategoryPaths(categories: Set<String>) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        val matching = CategoryPathSelector.matchingEntries(allEntries.value, categories)
        if (matching.isEmpty()) {
            _message.value = "No indexed files are available in this category"
            return
        }
        _selectedPaths.value = matching.mapTo(linkedSetOf()) { it.path }
        _cleanupReview.value = null
        _message.value = "Selected ${matching.size} category file(s)"
    }

    fun reviewCategoryPaths(categories: Set<String>) {
        val matching = CategoryPathSelector.matchingEntries(allEntries.value, categories)
        if (matching.isEmpty()) {
            _message.value = "No indexed files are available in this category"
            return
        }
        reviewPaths(matching.map { it.path })
    }

    fun cycleSort() { _filter.value = _filter.value.copy(sort = EntrySort.entries[(filter.value.sort.ordinal + 1) % EntrySort.entries.size]) }
    fun cycleType() { _filter.value = _filter.value.copy(type = EntryTypeFilter.entries[(filter.value.type.ordinal + 1) % EntryTypeFilter.entries.size]) }
    fun exportCsv() { latestSession.value?.let { repository.exportCsv(it, allEntries.value) } }

    fun toggleSelection(entry: ScanEntryEntity) = togglePathSelection(entry.path)

    fun togglePathSelection(path: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        val entry = allEntries.value.firstOrNull { it.path == path }
        if (entry == null) {
            _message.value = "This path is no longer part of the active scan"
            return
        }
        _selectedPaths.value = _selectedPaths.value.toMutableSet().also { selected ->
            if (!selected.add(entry.path)) selected.remove(entry.path)
        }
        _cleanupReview.value = null
    }

    fun showPathDetails(path: String) {
        val entry = allEntries.value.firstOrNull { it.path == path }
        if (entry == null) {
            _message.value = "This path is no longer part of the active scan"
        } else {
            select(entry)
        }
    }

    fun revealPath(path: String) {
        val entry = allEntries.value.firstOrNull { it.path == path }
        if (entry == null) {
            _message.value = "This path is no longer part of the active scan"
            return
        }
        _explorerPath.value = entry.parentPath
        _filter.value = ExplorerFilter()
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
        _cleanupReview.value = null
    }

    fun reviewCleanup() = reviewPaths(_selectedPaths.value)

    fun reviewPaths(paths: Collection<String>) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before planning another deletion"
            return
        }
        val session = latestSession.value ?: return
        val requested = paths.toSet()
        val chosen = allEntries.value.filter { it.path in requested }
        if (chosen.isEmpty()) {
            _message.value = "Select at least one indexed path"
            return
        }
        _selectedPaths.value = chosen.mapTo(linkedSetOf()) { it.path }
        _cleanupReview.value = null
        viewModelScope.launch {
            val review = repository.reviewCleanup(session, chosen)
            if (latestSession.value?.id == session.id) {
                _cleanupReview.value = review
                if (review.accepted.isEmpty()) {
                    _message.value = review.message ?: "No selected paths passed deletion safety checks"
                }
            } else {
                _message.value = "The active scan changed. Plan the deletion again."
            }
        }
    }

    fun dismissCleanupReview() { _cleanupReview.value = null }

    fun executeCleanup() {
        val session = latestSession.value ?: return
        val review = _cleanupReview.value ?: run {
            _message.value = "Review the permanent deletion plan first"
            return
        }
        if (!review.matches(session.id, session.rootPath)) {
            _cleanupReview.value = null
            _message.value = "The active scan changed. Plan the deletion again."
            return
        }
        if (!review.executable) {
            _message.value = review.message ?: "This deletion plan has no approved paths"
            return
        }
        viewModelScope.launch {
            if (latestSession.value?.id != review.scanId) {
                _cleanupReview.value = null
                _message.value = "The active scan changed. Plan the deletion again."
                return@launch
            }
            val result = repository.executeCleanup(review)
            _cleanupReview.value = null
            val filesystemChanged = result.items.any { it.status == "DELETED" || it.status == "PARTIAL" }
            if (filesystemChanged) {
                _scanDataStale.value = true
                clearSelection()
            }
            _message.value = when {
                result.ok -> "Deleted ${formatBytes(result.deletedBytes)} permanently. Run a new scan to refresh results."
                result.deletedBytes > 0 -> result.message ?: "Deleted ${formatBytes(result.deletedBytes)}, but some paths failed. Run a new scan."
                else -> result.message ?: "Permanent deletion failed"
            }
        }
    }

    fun refreshInsights(scanId: String? = latestSession.value?.id) {
        if (scanId == null) return
        viewModelScope.launch {
            val appSummaries = repository.appSummaries(scanId)
            val snapshotDelta = repository.snapshotDelta()
            val snapshotHistory = repository.snapshotHistory()
            val advancedReport = repository.advancedAnalysis(scanId)
            val largeFilePreset = _largeFileFilter.value.preset
            _largeFileLoading.value = true
            val largeFileEntries = repository.largeFileEntries(scanId, largeFilePreset)
            val packageProfiles = repository.packageProfiles(scanId)
            val moduleReport = repository.moduleAnalysis(scanId)
            if (latestSession.value?.id != scanId) return@launch
            _appSummaries.value = appSummaries
            _snapshotDelta.value = snapshotDelta
            _snapshotHistory.value = snapshotHistory
            _advancedReport.value = advancedReport
            if (_largeFileFilter.value.preset == largeFilePreset) {
                _largeFileSourceEntries.value = largeFileEntries
                _largeFileLoading.value = false
            }
            _packageProfiles.value = packageProfiles
            _moduleReport.value = moduleReport
        }
    }

    fun rootSearch(query: String) {
        val scanId = latestSession.value?.id
        if (scanId == null) { _message.value = "Run a scan before searching"; return }
        viewModelScope.launch {
            val results = repository.rootSearch(scanId, RootFileSearchRequest(query = query))
            if (latestSession.value?.id != scanId) return@launch
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
