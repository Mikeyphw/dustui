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
import com.mikeyphw.dustui.data.DuplicateAnalysisSnapshot
import com.mikeyphw.dustui.data.DuplicateFilter
import com.mikeyphw.dustui.data.DuplicateKeepStrategy
import com.mikeyphw.dustui.data.DuplicatePage
import com.mikeyphw.dustui.data.DuplicateThreshold
import com.mikeyphw.dustui.data.DuplicateWorkbenchEngine
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    private val _selectedContentHashes = MutableStateFlow<Map<String, String>>(emptyMap())
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
    private val _duplicateAnalysis = MutableStateFlow<DuplicateAnalysisSnapshot?>(null)
    val duplicateAnalysis: StateFlow<DuplicateAnalysisSnapshot?> = _duplicateAnalysis
    private val _duplicateFilter = MutableStateFlow(DuplicateFilter())
    val duplicateFilter: StateFlow<DuplicateFilter> = _duplicateFilter
    private val _duplicateManualKeep = MutableStateFlow<Map<String, String>>(emptyMap())
    val duplicatePage: StateFlow<DuplicatePage> = combine(_duplicateAnalysis, _duplicateFilter, _duplicateManualKeep) { analysis, filter, manualKeep ->
        DuplicateWorkbenchEngine.evaluate(analysis, filter, manualKeep)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatePage.empty())
    private var duplicateAnalysisJob: Job? = null
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
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
        _rootSearchResults.value = emptyList()
        _categoryFiles.value = null
        _largeFileSourceEntries.value = emptyList()
        _largeFileLoading.value = false
        _largeFileFilter.value = LargeFileFilter()
        duplicateAnalysisJob?.cancel()
        duplicateAnalysisJob = null
        _duplicateAnalysis.value = null
        _duplicateFilter.value = DuplicateFilter()
        _duplicateManualKeep.value = emptyMap()
        if (session?.status == "COMPLETED") {
            viewModelScope.launch(Dispatchers.IO) {
                val stored = repository.loadDuplicateAnalysis(session.id)
                if (latestSession.value?.id == session.id) {
                    _duplicateAnalysis.value = stored
                    stored?.let { snapshot ->
                        DuplicateThreshold.entries.firstOrNull { it.minBytes == snapshot.minSizeBytes }?.let { threshold ->
                            _duplicateFilter.value = _duplicateFilter.value.copy(threshold = threshold)
                        }
                    }
                }
            }
        }
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
        _selectedContentHashes.value = emptyMap()
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

    fun updateDuplicateQuery(value: String) {
        _duplicateFilter.value = _duplicateFilter.value.copy(query = value, page = 0)
    }

    fun setDuplicateThreshold(threshold: DuplicateThreshold) {
        if (_duplicateAnalysis.value?.running == true) return
        clearVerifiedDuplicateSelection()
        _duplicateFilter.value = _duplicateFilter.value.copy(threshold = threshold, page = 0)
    }

    fun setDuplicateKeepStrategy(strategy: DuplicateKeepStrategy) {
        clearVerifiedDuplicateSelection()
        _duplicateFilter.value = _duplicateFilter.value.copy(keepStrategy = strategy, page = 0)
    }

    fun updateDuplicatePreferredDirectory(value: String) {
        clearVerifiedDuplicateSelection()
        _duplicateFilter.value = _duplicateFilter.value.copy(preferredDirectory = value, page = 0)
    }

    fun previousDuplicatePage() {
        val current = _duplicateFilter.value
        _duplicateFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextDuplicatePage() {
        val current = _duplicateFilter.value
        val lastPage = (duplicatePage.value.pageCount - 1).coerceAtLeast(0)
        _duplicateFilter.value = current.copy(page = (current.page + 1).coerceAtMost(lastPage))
    }

    fun startDuplicateAnalysis(reset: Boolean = false) {
        val session = latestSession.value
        if (session?.status != "COMPLETED") {
            _message.value = "Complete a scan before verifying duplicate files"
            return
        }
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before verifying duplicates"
            return
        }
        if (duplicateAnalysisJob?.isActive == true) return
        clearVerifiedDuplicateSelection()
        val threshold = _duplicateFilter.value.threshold
        duplicateAnalysisJob = viewModelScope.launch(Dispatchers.IO) {
            val result = repository.analyzeDuplicates(session.id, threshold.minBytes, reset) { update ->
                if (latestSession.value?.id == session.id) _duplicateAnalysis.value = update
            }
            if (latestSession.value?.id == session.id) {
                _duplicateAnalysis.value = result
                _message.value = result.message
            }
        }
    }

    fun cancelDuplicateAnalysis() {
        val scanId = latestSession.value?.id
        if (scanId != null) {
            viewModelScope.launch(Dispatchers.IO) { repository.cancelDuplicateAnalysis(scanId) }
        }
        duplicateAnalysisJob?.cancel()
        duplicateAnalysisJob = null
    }

    fun chooseDuplicateKeep(groupId: String, copyKey: String) {
        clearVerifiedDuplicateSelection()
        _duplicateManualKeep.value = _duplicateManualKeep.value.toMutableMap().also { it[groupId] = copyKey }
        _duplicateFilter.value = _duplicateFilter.value.copy(keepStrategy = DuplicateKeepStrategy.MANUAL)
    }

    fun selectDuplicateGroup(groupId: String) {
        val item = duplicatePage.value.items.firstOrNull { it.group.sha256 == groupId }
        selectDuplicatePaths(item?.deletionPaths.orEmpty(), item?.group?.sha256, "duplicate group")
    }

    fun reviewDuplicateGroup(groupId: String) {
        val item = duplicatePage.value.items.firstOrNull { it.group.sha256 == groupId }
        val paths = item?.deletionPaths.orEmpty()
        if (paths.isEmpty()) {
            _message.value = "Choose a retained copy before planning deletion"
        } else {
            reviewPaths(paths, paths.associateWith { item?.group?.sha256.orEmpty() })
        }
    }

    fun selectDuplicatePage() {
        val hashes = duplicatePage.value.items.flatMap { item -> item.deletionPaths.map { it to item.group.sha256 } }.toMap()
        selectDuplicatePaths(hashes.keys.toList(), null, "duplicate page", hashes)
    }

    fun reviewDuplicatePage() {
        val paths = duplicatePage.value.deletionPaths
        if (paths.isEmpty()) {
            _message.value = "No safely removable duplicate copies are visible on this page"
        } else {
            val hashes = duplicatePage.value.items.flatMap { item -> item.deletionPaths.map { it to item.group.sha256 } }.toMap()
            reviewPaths(paths, hashes)
        }
    }

    private fun clearVerifiedDuplicateSelection() {
        if (_selectedContentHashes.value.isNotEmpty()) {
            _selectedPaths.value = emptySet()
            _selectedContentHashes.value = emptyMap()
            _cleanupReview.value = null
        }
    }

    private fun selectDuplicatePaths(
        paths: List<String>,
        sharedHash: String?,
        label: String,
        explicitHashes: Map<String, String> = emptyMap(),
    ) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        if (paths.isEmpty()) {
            _message.value = "No safely removable copies are available for this $label"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _selectedContentHashes.value = if (explicitHashes.isNotEmpty()) explicitHashes else sharedHash?.let { hash -> paths.associateWith { hash } }.orEmpty()
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} verified duplicate copy or copies"
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
        _selectedContentHashes.value = emptyMap()
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
        if (entry.path !in _selectedPaths.value) {
            _selectedContentHashes.value = _selectedContentHashes.value - entry.path
        }
        _cleanupReview.value = null
    }

    fun showPathDetails(path: String) {
        allEntries.value.firstOrNull { it.path == path }?.let { entry ->
            select(entry)
            return
        }
        val scanId = latestSession.value?.id ?: return
        viewModelScope.launch {
            val entry = repository.entry(scanId, path)
            if (latestSession.value?.id != scanId) return@launch
            if (entry == null) _message.value = "This path is no longer part of the active scan" else select(entry)
        }
    }

    fun revealPath(path: String) {
        allEntries.value.firstOrNull { it.path == path }?.let { entry ->
            _explorerPath.value = entry.parentPath
            _filter.value = ExplorerFilter()
            return
        }
        val scanId = latestSession.value?.id ?: return
        viewModelScope.launch {
            val entry = repository.entry(scanId, path)
            if (latestSession.value?.id != scanId) return@launch
            if (entry == null) {
                _message.value = "This path is no longer part of the active scan"
            } else {
                _explorerPath.value = entry.parentPath
                _filter.value = ExplorerFilter()
            }
        }
    }

    fun clearSelection() {
        _selectedPaths.value = emptySet()
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
    }

    fun reviewCleanup() = reviewPaths(_selectedPaths.value, _selectedContentHashes.value)

    fun reviewPaths(paths: Collection<String>) = reviewPaths(paths, emptyMap())

    fun reviewPaths(paths: Collection<String>, expectedSha256ByPath: Map<String, String>) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before planning another deletion"
            return
        }
        val session = latestSession.value ?: return
        val requested = paths.distinct()
        if (requested.isEmpty()) {
            _message.value = "Select at least one indexed path"
            return
        }
        _selectedPaths.value = requested.toCollection(linkedSetOf())
        _selectedContentHashes.value = expectedSha256ByPath.filterKeys { it in requested }
        _cleanupReview.value = null
        viewModelScope.launch {
            val resolved = repository.entriesByPaths(session.id, requested)
            if (latestSession.value?.id != session.id) {
                _message.value = "The active scan changed. Plan the deletion again."
                return@launch
            }
            val byPath = resolved.associateBy { it.path }
            val chosen = requested.mapNotNull(byPath::get)
            if (chosen.isEmpty()) {
                _message.value = "Selected paths no longer belong to the active scan"
                return@launch
            }
            _selectedPaths.value = chosen.mapTo(linkedSetOf()) { it.path }
            _selectedContentHashes.value = _selectedContentHashes.value.filterKeys { it in _selectedPaths.value }
            val review = repository.reviewCleanup(session, chosen, _selectedContentHashes.value)
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
