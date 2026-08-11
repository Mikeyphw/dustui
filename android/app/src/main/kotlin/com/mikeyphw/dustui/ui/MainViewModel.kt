package com.mikeyphw.dustui.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mikeyphw.dustui.data.EntryIntelligence
import com.mikeyphw.dustui.data.AppResidueAnalysis
import com.mikeyphw.dustui.data.AppResidueConfidence
import com.mikeyphw.dustui.data.AppResidueFilter
import com.mikeyphw.dustui.data.AppResiduePage
import com.mikeyphw.dustui.data.AppResidueSort
import com.mikeyphw.dustui.data.AppResidueWorkbenchEngine
import com.mikeyphw.dustui.data.CleanupRecommendationAnalysis
import com.mikeyphw.dustui.data.CleanupRecommendationFilter
import com.mikeyphw.dustui.data.CleanupRecommendationKind
import com.mikeyphw.dustui.data.CleanupRecommendationPage
import com.mikeyphw.dustui.data.CleanupRecommendationPreferences
import com.mikeyphw.dustui.data.CleanupRecommendationSort
import com.mikeyphw.dustui.data.CleanupRecommendationEngine
import com.mikeyphw.dustui.data.CleanupRule
import com.mikeyphw.dustui.data.CleanupRuleConditions
import com.mikeyphw.dustui.data.CleanupRuleDraftPreview
import com.mikeyphw.dustui.data.CleanupRuleEntryType
import com.mikeyphw.dustui.data.CleanupRuleResidueConfidence
import com.mikeyphw.dustui.data.CleanupRulePreview
import com.mikeyphw.dustui.data.StorageGrowthAnalysis
import com.mikeyphw.dustui.data.StorageGrowthEngine
import com.mikeyphw.dustui.data.StorageGrowthFilter
import com.mikeyphw.dustui.data.StorageGrowthGroup
import com.mikeyphw.dustui.data.StorageGrowthPage
import com.mikeyphw.dustui.data.StorageGrowthScope
import com.mikeyphw.dustui.data.CleanupVerificationReport
import com.mikeyphw.dustui.data.ScanDiagnostics
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
import com.mikeyphw.dustui.data.DebugCategory
import com.mikeyphw.dustui.data.DebugEvent
import com.mikeyphw.dustui.data.DebugEventStore
import com.mikeyphw.dustui.data.DebugExportMode
import com.mikeyphw.dustui.data.DebugExportRequest
import com.mikeyphw.dustui.data.DebugExportResult
import com.mikeyphw.dustui.data.ModuleDiagnosticStatus
import com.mikeyphw.dustui.data.DebugProbeTarget
import com.mikeyphw.dustui.data.DebugProbeStatus
import com.mikeyphw.dustui.data.DebugProbeResult
import com.mikeyphw.dustui.data.DebugSeverity
import com.mikeyphw.dustui.data.RootPathInspection
import com.mikeyphw.dustui.data.RootModuleReport
import com.mikeyphw.dustui.data.RootFileSearchResult
import com.mikeyphw.dustui.data.RootFileSearchRequest
import com.mikeyphw.dustui.data.AppStorageBucketKind
import com.mikeyphw.dustui.data.AppStorageInspectorAnalysis
import com.mikeyphw.dustui.data.AppStorageInspectorEngine
import com.mikeyphw.dustui.data.AppStorageInspectorFilter
import com.mikeyphw.dustui.data.AppStorageInspectorPage
import com.mikeyphw.dustui.data.AppStorageSort
import com.mikeyphw.dustui.data.LargeFileFilter
import com.mikeyphw.dustui.data.LargeFileGroup
import com.mikeyphw.dustui.data.LargeFilePage
import com.mikeyphw.dustui.data.LargeFilePreset
import com.mikeyphw.dustui.data.LargeFileSort
import com.mikeyphw.dustui.data.LargeFileWorkbenchEngine
import com.mikeyphw.dustui.scan.ScanDepthProfile
import com.mikeyphw.dustui.scan.ScanPreset
import com.mikeyphw.dustui.scan.ScanRequest
import com.mikeyphw.dustui.root.RootServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: ScanRepository) : ViewModel() {
    private var activeSessionId: String? = null
    val presets = ScanPreset.defaults
    private val _scanDepthProfile = MutableStateFlow(ScanDepthProfile.default)
    val scanDepthProfile: StateFlow<ScanDepthProfile> = _scanDepthProfile
    val latestSession: StateFlow<ScanSessionEntity?> = repository.observeLatestSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val allEntries: StateFlow<List<ScanEntryEntity>> = latestSession
        .flatMapLatest { session -> session?.let { repository.observeEntries(it.id) } ?: flowOf(emptyList()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val debugEventStore = DebugEventStore()
    val debugEvents: StateFlow<List<DebugEvent>> = debugEventStore.events
    private val _debugProbeResults = MutableStateFlow<List<DebugProbeResult>>(emptyList())
    val debugProbeResults: StateFlow<List<DebugProbeResult>> = _debugProbeResults
    private val _debugExportResult = MutableStateFlow<DebugExportResult?>(null)
    val debugExportResult: StateFlow<DebugExportResult?> = _debugExportResult
    private val _debugOverlayVisible = MutableStateFlow(false)
    val debugOverlayVisible: StateFlow<Boolean> = _debugOverlayVisible

    private fun recordDebugEvent(
        category: DebugCategory,
        severity: DebugSeverity,
        name: String,
        message: String,
        evidence: Map<String, String> = emptyMap(),
    ) {
        debugEventStore.record(category, severity, name, message, evidence)
    }

    fun recordNavigation(destination: DustDestination, wide: Boolean) {
        recordDebugEvent(
            DebugCategory.Navigation,
            DebugSeverity.Trace,
            "destination_selected",
            "Selected ${destination.label}",
            mapOf("destination" to destination.name, "layout" to if (wide) "wide" else "compact"),
        )
    }

    fun recordDebugWorkbenchOpened(destination: DustDestination, wide: Boolean) {
        recordDebugEvent(
            DebugCategory.App,
            DebugSeverity.Info,
            "debug_workbench_opened",
            "Opened Debug workbench",
            mapOf("fromDestination" to destination.name, "layout" to if (wide) "wide" else "compact"),
        )
    }

    fun clearDebugEvents() {
        debugEventStore.clear()
        recordDebugEvent(DebugCategory.App, DebugSeverity.Info, "debug_timeline_cleared", "Debug timeline cleared")
    }

    fun setDebugOverlayVisible(visible: Boolean) {
        if (_debugOverlayVisible.value == visible) return
        _debugOverlayVisible.value = visible
        recordDebugEvent(
            DebugCategory.App,
            DebugSeverity.Info,
            if (visible) "ui_debug_overlay_enabled" else "ui_debug_overlay_disabled",
            if (visible) "UI debug overlay enabled" else "UI debug overlay disabled",
        )
    }

    fun toggleDebugOverlay() {
        setDebugOverlayVisible(!_debugOverlayVisible.value)
    }

    fun selectScanDepthProfile(profile: ScanDepthProfile) {
        if (_scanDepthProfile.value == profile) return
        _scanDepthProfile.value = profile
        recordDebugEvent(
            DebugCategory.Scan,
            DebugSeverity.Info,
            "scan_depth_profile_selected",
            "Selected ${profile.title} scan depth policy",
            mapOf("maxDepth" to profile.maxDepth.toString(), "maxEntries" to profile.maxEntries.toString()),
        )
    }


    fun exportDebugBundle(mode: DebugExportMode, destination: DustDestination, wide: Boolean) {
        val request = DebugExportRequest(
            mode = mode,
            destination = destination.name,
            destinationLabel = destination.label,
            layout = if (wide) "wide" else "compact",
            session = latestSession.value,
            entries = allEntries.value,
            events = debugEvents.value,
            probeResults = debugProbeResults.value,
            rootServiceInfo = rootServiceInfo.value,
            rootFailureMessage = rootFailureMessage.value,
            pathPreflight = pathPreflight.value,
            diagnostics = scanDiagnostics.value,
            moduleReport = moduleReport.value,
            recommendationAnalysis = recommendationAnalysis.value,
            selectedPaths = selectedPaths.value,
            cleanupReview = cleanupReview.value,
            cleanupReports = cleanupReports.value,
            scanDataStale = scanDataStale.value,
        )
        runCatching { repository.exportDebugBundle(request) }
            .onSuccess { result ->
                _debugExportResult.value = result
                _message.value = "Debug bundle exported: ${result.mode.title}"
                recordDebugEvent(
                    DebugCategory.App,
                    DebugSeverity.Info,
                    "debug_export_created",
                    "Debug bundle exported",
                    mapOf(
                        "mode" to result.mode.name,
                        "fileName" to result.fileName,
                        "eventCount" to result.eventCount.toString(),
                        "probeCount" to result.probeCount.toString(),
                        "entryCount" to result.entryCount.toString(),
                        "pathPolicy" to result.pathPolicy,
                    ),
                )
            }
            .onFailure { error ->
                _message.value = error.message ?: "Unable to export debug bundle"
                recordDebugEvent(
                    DebugCategory.App,
                    DebugSeverity.Error,
                    "debug_export_failed",
                    _message.value ?: "Unable to export debug bundle",
                    mapOf("mode" to mode.name, "error" to error::class.java.simpleName),
                )
            }
    }

    fun runAllDebugProbes() {
        recordDebugEvent(DebugCategory.App, DebugSeverity.Info, "debug_probes_run_all", "Running every diagnostic probe")
        DebugProbeTarget.entries.forEach(::runDebugProbe)
    }

    fun runDebugProbe(target: DebugProbeTarget) {
        recordDebugEvent(
            target.debugCategory(),
            DebugSeverity.Trace,
            "debug_probe_started",
            "Started ${target.title} probe",
            mapOf("probe" to target.name, "group" to target.group.name),
        )
        viewModelScope.launch {
            val result = evaluateDebugProbe(target)
            _debugProbeResults.value = (_debugProbeResults.value.filterNot { it.target == target } + result)
                .sortedBy { it.target.ordinal }
            recordDebugEvent(
                target.debugCategory(),
                when (result.status) {
                    DebugProbeStatus.Pass -> DebugSeverity.Info
                    DebugProbeStatus.Warning -> DebugSeverity.Warning
                    DebugProbeStatus.Failed -> DebugSeverity.Error
                },
                "debug_probe_finished",
                result.message,
                result.evidence + ("probe" to target.name),
            )
        }
    }

    private suspend fun evaluateDebugProbe(target: DebugProbeTarget): DebugProbeResult = when (target) {
        DebugProbeTarget.RootService -> rootServiceProbe()
        DebugProbeTarget.DataRootAccess -> preflightProbe(DebugProbeTarget.DataRootAccess, "/data")
        DebugProbeTarget.ModuleRootAccess -> preflightProbe(DebugProbeTarget.ModuleRootAccess, "/data/adb/modules")
        DebugProbeTarget.ActiveScan -> activeScanProbe()
        DebugProbeTarget.ZeroFileScan -> zeroFileScanProbe()
        DebugProbeTarget.ModuleScope -> moduleScopeProbe()
        DebugProbeTarget.DeletionReadiness -> deletionReadinessProbe()
        DebugProbeTarget.SearchReadiness -> searchReadinessProbe()
    }

    private suspend fun rootServiceProbe(): DebugProbeResult = runCatching { repository.rootServiceInfo() }
        .fold(
            onSuccess = { info ->
                val ready = info.ok && info.uid == 0
                DebugProbeResult(
                    target = DebugProbeTarget.RootService,
                    status = if (ready) DebugProbeStatus.Pass else DebugProbeStatus.Warning,
                    message = if (ready) "Root service is available as UID 0" else (info.message ?: "Root service is not running as UID 0"),
                    evidence = mapOf(
                        "ok" to info.ok.toString(),
                        "uid" to info.uid.toString(),
                        "backend" to info.backend,
                        "scanner" to info.scanner,
                        "selinux" to (info.selinuxEnforcing?.let { if (it) "enforcing" else "permissive" } ?: "unknown"),
                    ),
                )
            },
            onFailure = { error ->
                DebugProbeResult(
                    target = DebugProbeTarget.RootService,
                    status = DebugProbeStatus.Failed,
                    message = error.message ?: "Unable to bind the root service",
                    evidence = mapOf("error" to error::class.java.simpleName),
                )
            },
        )

    private suspend fun preflightProbe(target: DebugProbeTarget, rootPath: String): DebugProbeResult = runCatching {
        repository.preflightScan(
            ScanRequest(
                rootPath = rootPath,
                maxDepth = 1,
                maxEntries = 64,
                includeHidden = true,
            ).validated(),
        )
    }.fold(
        onSuccess = { preflight ->
            DebugProbeResult(
                target = target,
                status = if (preflight.ok) DebugProbeStatus.Pass else DebugProbeStatus.Warning,
                message = if (preflight.ok) "${target.title} preflight passed" else preflight.message,
                evidence = mapOf(
                    "requestedPath" to preflight.requestedPath,
                    "ok" to preflight.ok.toString(),
                    "message" to preflight.message,
                    "exists" to preflight.exists.toString(),
                    "directory" to preflight.directory.toString(),
                    "opened" to preflight.opened.toString(),
                    "hasChildren" to (preflight.hasChildren?.toString() ?: "unknown"),
                    "code" to preflight.code,
                ),
            )
        },
        onFailure = { error ->
            DebugProbeResult(
                target = target,
                status = DebugProbeStatus.Failed,
                message = error.message ?: "${target.title} preflight failed",
                evidence = mapOf("requestedPath" to rootPath, "error" to error::class.java.simpleName),
            )
        },
    )

    private fun activeScanProbe(): DebugProbeResult {
        val session = latestSession.value
        return when {
            session == null -> DebugProbeResult(
                target = DebugProbeTarget.ActiveScan,
                status = DebugProbeStatus.Warning,
                message = "No scan session is loaded yet",
                evidence = mapOf("scanId" to "none"),
            )
            session.status == "COMPLETED" -> DebugProbeResult(
                target = DebugProbeTarget.ActiveScan,
                status = DebugProbeStatus.Pass,
                message = "Latest scan completed and can feed app workbenches",
                evidence = scanSessionEvidence(session),
            )
            session.status == "RUNNING" -> DebugProbeResult(
                target = DebugProbeTarget.ActiveScan,
                status = DebugProbeStatus.Warning,
                message = "A scan is still running; values may update as entries arrive",
                evidence = scanSessionEvidence(session),
            )
            else -> DebugProbeResult(
                target = DebugProbeTarget.ActiveScan,
                status = DebugProbeStatus.Failed,
                message = session.errorMessage ?: "Latest scan ended with status ${session.status}",
                evidence = scanSessionEvidence(session),
            )
        }
    }

    private fun zeroFileScanProbe(): DebugProbeResult {
        val session = latestSession.value
        val entries = allEntries.value.size
        val diagnostics = _scanDiagnostics.value
        return when {
            session == null -> DebugProbeResult(
                target = DebugProbeTarget.ZeroFileScan,
                status = DebugProbeStatus.Warning,
                message = "No scan exists, so zero-file state cannot be evaluated yet",
                evidence = mapOf("scanId" to "none"),
            )
            session.status == "COMPLETED" && entries == 0 -> DebugProbeResult(
                target = DebugProbeTarget.ZeroFileScan,
                status = DebugProbeStatus.Failed,
                message = "Completed scan indexed zero entries; inspect preflight, truncation, and root evidence",
                evidence = scanSessionEvidence(session) + mapOf(
                    "indexedEntries" to entries.toString(),
                    "warnings" to (diagnostics?.warningCount ?: 0).toString(),
                    "errors" to (diagnostics?.errorCount ?: 0).toString(),
                ),
            )
            session.status == "COMPLETED" -> DebugProbeResult(
                target = DebugProbeTarget.ZeroFileScan,
                status = DebugProbeStatus.Pass,
                message = "Completed scan indexed entries; zero-file guard is clear",
                evidence = scanSessionEvidence(session) + ("indexedEntries" to entries.toString()),
            )
            else -> DebugProbeResult(
                target = DebugProbeTarget.ZeroFileScan,
                status = DebugProbeStatus.Warning,
                message = "Wait for scan completion before evaluating zero-file behavior",
                evidence = scanSessionEvidence(session) + ("indexedEntries" to entries.toString()),
            )
        }
    }

    private fun moduleScopeProbe(): DebugProbeResult {
        val report = _moduleReport.value
        val diagnostic = report?.diagnostic
        val status = diagnostic?.status
        return when (status) {
            ModuleDiagnosticStatus.READY -> DebugProbeResult(
                target = DebugProbeTarget.ModuleScope,
                status = DebugProbeStatus.Pass,
                message = "Module values are backed by module-source scan evidence",
                evidence = mapOf(
                    "status" to status.name,
                    "currentScanRoot" to (diagnostic.currentScanRoot ?: "none"),
                    "expectedRoot" to diagnostic.expectedRoot,
                    "modules" to report.modules.size.toString(),
                ),
            )
            ModuleDiagnosticStatus.ROOT_ACCESS_FAILED, ModuleDiagnosticStatus.SCAN_FAILED -> DebugProbeResult(
                target = DebugProbeTarget.ModuleScope,
                status = DebugProbeStatus.Failed,
                message = diagnostic.message,
                evidence = moduleDiagnosticEvidence(diagnostic),
            )
            null -> DebugProbeResult(
                target = DebugProbeTarget.ModuleScope,
                status = DebugProbeStatus.Warning,
                message = "Module diagnostics have not loaded yet. Scan /data/adb/modules for source-backed values.",
                evidence = mapOf("expectedRoot" to "/data/adb/modules"),
            )
            else -> DebugProbeResult(
                target = DebugProbeTarget.ModuleScope,
                status = DebugProbeStatus.Warning,
                message = diagnostic.message,
                evidence = moduleDiagnosticEvidence(diagnostic),
            )
        }
    }

    private fun deletionReadinessProbe(): DebugProbeResult {
        val session = latestSession.value
        val selected = _selectedPaths.value.size
        val review = _cleanupReview.value
        return when {
            session == null -> DebugProbeResult(
                target = DebugProbeTarget.DeletionReadiness,
                status = DebugProbeStatus.Warning,
                message = "Run a scan before planning review-first deletion",
                evidence = mapOf("scanId" to "none", "selectedPaths" to selected.toString()),
            )
            _scanDataStale.value -> DebugProbeResult(
                target = DebugProbeTarget.DeletionReadiness,
                status = DebugProbeStatus.Failed,
                message = "Current scan is stale after cleanup. Rescan before creating a new deletion plan.",
                evidence = mapOf("scanId" to session.id, "selectedPaths" to selected.toString(), "stale" to "true"),
            )
            selected == 0 && review == null -> DebugProbeResult(
                target = DebugProbeTarget.DeletionReadiness,
                status = DebugProbeStatus.Warning,
                message = "Select exact indexed paths before opening deletion review",
                evidence = mapOf("scanId" to session.id, "selectedPaths" to "0"),
            )
            else -> DebugProbeResult(
                target = DebugProbeTarget.DeletionReadiness,
                status = DebugProbeStatus.Pass,
                message = if (review != null) "A review-first deletion plan is open" else "Selected paths can be sent to deletion review",
                evidence = mapOf(
                    "scanId" to session.id,
                    "selectedPaths" to selected.toString(),
                    "reviewOpen" to (review != null).toString(),
                    "accepted" to (review?.accepted?.size ?: 0).toString(),
                    "blocked" to (review?.blocked?.size ?: 0).toString(),
                ),
            )
        }
    }

    private fun searchReadinessProbe(): DebugProbeResult {
        val session = latestSession.value
        val entries = allEntries.value.size
        return when {
            session == null -> DebugProbeResult(
                target = DebugProbeTarget.SearchReadiness,
                status = DebugProbeStatus.Warning,
                message = "Run a scan before using whole-scan search",
                evidence = mapOf("scanId" to "none"),
            )
            entries == 0 -> DebugProbeResult(
                target = DebugProbeTarget.SearchReadiness,
                status = DebugProbeStatus.Warning,
                message = "Whole-scan search has no indexed entries to search",
                evidence = scanSessionEvidence(session) + ("indexedEntries" to "0"),
            )
            else -> DebugProbeResult(
                target = DebugProbeTarget.SearchReadiness,
                status = DebugProbeStatus.Pass,
                message = "Whole-scan search has indexed entries available",
                evidence = scanSessionEvidence(session) + ("indexedEntries" to entries.toString()),
            )
        }
    }

    private fun DebugProbeTarget.debugCategory(): DebugCategory = when (group) {
        com.mikeyphw.dustui.data.DebugProbeGroup.Root -> DebugCategory.Root
        com.mikeyphw.dustui.data.DebugProbeGroup.Scan -> DebugCategory.Scan
        com.mikeyphw.dustui.data.DebugProbeGroup.Module -> DebugCategory.Module
        com.mikeyphw.dustui.data.DebugProbeGroup.Cleanup -> DebugCategory.Deletion
        com.mikeyphw.dustui.data.DebugProbeGroup.Search -> DebugCategory.Search
    }

    private fun scanSessionEvidence(session: ScanSessionEntity): Map<String, String> = mapOf(
        "scanId" to session.id,
        "rootPath" to session.rootPath,
        "status" to session.status,
        "totalBytes" to session.totalBytes.toString(),
        "scannedItems" to session.scannedItems.toString(),
        "scannedBytes" to session.scannedBytes.toString(),
        "warningCount" to session.warningCount.toString(),
        "message" to (session.errorMessage ?: "none"),
    )

    private fun moduleDiagnosticEvidence(diagnostic: com.mikeyphw.dustui.data.ModuleAnalysisDiagnostic): Map<String, String> = mapOf(
        "status" to diagnostic.status.name,
        "currentScanRoot" to (diagnostic.currentScanRoot ?: "none"),
        "expectedRoot" to diagnostic.expectedRoot,
        "modulePathAccessible" to diagnostic.modulePathAccessible.toString(),
        "rootCode" to (diagnostic.rootCode ?: "none"),
    )

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
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _rootState = MutableStateFlow<RootUiState>(RootUiState.Checking)
    val rootState: StateFlow<RootUiState> = _rootState
    private val _rootServiceInfo = MutableStateFlow<RootServiceClient.RootServiceInfo?>(null)
    val rootServiceInfo: StateFlow<RootServiceClient.RootServiceInfo?> = _rootServiceInfo
    private val _rootFailureMessage = MutableStateFlow<String?>(null)
    val rootFailureMessage: StateFlow<String?> = _rootFailureMessage
    private val _scanDiagnostics = MutableStateFlow<ScanDiagnostics?>(null)
    val scanDiagnostics: StateFlow<ScanDiagnostics?> = _scanDiagnostics
    private val _pathPreflight = MutableStateFlow<RootServiceClient.ScanRootPreflight?>(null)
    val pathPreflight: StateFlow<RootServiceClient.ScanRootPreflight?> = _pathPreflight
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
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DuplicatePage.empty())
    private var duplicateAnalysisJob: Job? = null
    private var insightsRefreshJob: Job? = null
    private val rootRefreshRequestId = AtomicLong(0)
    private val scanPreflightRequestId = AtomicLong(0)
    private val rootInspectionRequestId = AtomicLong(0)
    private val largeFileRequestId = AtomicLong(0)
    private val residueRequestId = AtomicLong(0)
    private val recommendationRequestId = AtomicLong(0)
    private val cleanupRuleRequestId = AtomicLong(0)
    private val cleanupRuleDraftRequestId = AtomicLong(0)
    private val storageGrowthRequestId = AtomicLong(0)
    private val cleanupVerificationRequestId = AtomicLong(0)
    private val rootSearchRequestId = AtomicLong(0)
    private val insightsRequestId = AtomicLong(0)
    private val certificationRequestId = AtomicLong(0)
    private val _certification = MutableStateFlow<DeviceCertificationReport?>(null)
    val certification: StateFlow<DeviceCertificationReport?> = _certification
    private val _appStorageInspector = MutableStateFlow<AppStorageInspectorAnalysis?>(null)
    val appStorageInspector: StateFlow<AppStorageInspectorAnalysis?> = _appStorageInspector
    private val _appStorageLoading = MutableStateFlow(false)
    val appStorageLoading: StateFlow<Boolean> = _appStorageLoading
    private val _appStorageFilter = MutableStateFlow(AppStorageInspectorFilter())
    val appStorageFilter: StateFlow<AppStorageInspectorFilter> = _appStorageFilter
    val appStoragePage: StateFlow<AppStorageInspectorPage> = combine(_appStorageInspector, _appStorageFilter) { analysis, filter ->
        AppStorageInspectorEngine.evaluate(analysis, filter)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStorageInspectorPage.empty())
    private val _residueAnalysis = MutableStateFlow<AppResidueAnalysis?>(null)
    val residueAnalysis: StateFlow<AppResidueAnalysis?> = _residueAnalysis
    private val _residueLoading = MutableStateFlow(false)
    val residueLoading: StateFlow<Boolean> = _residueLoading
    private val _residueFilter = MutableStateFlow(AppResidueFilter())
    val residueFilter: StateFlow<AppResidueFilter> = _residueFilter
    val residuePage: StateFlow<AppResiduePage> = combine(_residueAnalysis, _residueFilter) { analysis, filter ->
        AppResidueWorkbenchEngine.evaluate(analysis, filter)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppResiduePage.empty())
    private val _recommendationAnalysis = MutableStateFlow<CleanupRecommendationAnalysis?>(null)
    val recommendationAnalysis: StateFlow<CleanupRecommendationAnalysis?> = _recommendationAnalysis
    private val _recommendationLoading = MutableStateFlow(false)
    val recommendationLoading: StateFlow<Boolean> = _recommendationLoading
    private val _recommendationFilter = MutableStateFlow(CleanupRecommendationFilter())
    val recommendationFilter: StateFlow<CleanupRecommendationFilter> = _recommendationFilter
    private val _recommendationPreferences = MutableStateFlow(CleanupRecommendationPreferences())
    val recommendationPage: StateFlow<CleanupRecommendationPage> = combine(
        _recommendationAnalysis,
        _recommendationFilter,
        _recommendationPreferences,
    ) { analysis, filter, preferences ->
        CleanupRecommendationEngine.evaluate(analysis, filter, preferences)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanupRecommendationPage.empty())
    private val _cleanupRules = MutableStateFlow(repository.cleanupRules())
    val cleanupRules: StateFlow<List<CleanupRule>> = _cleanupRules
    private val _cleanupRulePreview = MutableStateFlow<CleanupRulePreview?>(null)
    val cleanupRulePreview: StateFlow<CleanupRulePreview?> = _cleanupRulePreview
    private val _cleanupRuleLoading = MutableStateFlow(false)
    val cleanupRuleLoading: StateFlow<Boolean> = _cleanupRuleLoading
    private val _cleanupRuleDraftPreview = MutableStateFlow<CleanupRuleDraftPreview?>(null)
    val cleanupRuleDraftPreview: StateFlow<CleanupRuleDraftPreview?> = _cleanupRuleDraftPreview
    private val _cleanupRuleDraftLoading = MutableStateFlow(false)
    val cleanupRuleDraftLoading: StateFlow<Boolean> = _cleanupRuleDraftLoading
    private val _storageGrowthAnalysis = MutableStateFlow<StorageGrowthAnalysis?>(null)
    val storageGrowthAnalysis: StateFlow<StorageGrowthAnalysis?> = _storageGrowthAnalysis
    private val _storageGrowthFilter = MutableStateFlow(StorageGrowthFilter())
    val storageGrowthFilter: StateFlow<StorageGrowthFilter> = _storageGrowthFilter
    private val _storageGrowthUnderstood = MutableStateFlow(repository.storageGrowthUnderstood())
    private val _storageGrowthCurrentOffset = MutableStateFlow(0)
    private val _storageGrowthPreviousOffset = MutableStateFlow(1)
    private val _storageGrowthRetentionLimit = MutableStateFlow(repository.storageGrowthRetentionLimit())
    val storageGrowthRetentionLimit: StateFlow<Int> = _storageGrowthRetentionLimit
    private val _storageGrowthSessionCount = MutableStateFlow(0)
    val storageGrowthSessionCount: StateFlow<Int> = _storageGrowthSessionCount
    val storageGrowthPage: StateFlow<StorageGrowthPage> = combine(
        _storageGrowthAnalysis,
        _storageGrowthFilter,
        _storageGrowthUnderstood,
    ) { analysis, filter, understood ->
        StorageGrowthEngine.evaluate(analysis, filter, understood)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageGrowthPage.empty())
    private val _storageGrowthLoading = MutableStateFlow(false)
    val storageGrowthLoading: StateFlow<Boolean> = _storageGrowthLoading
    private val initialCleanupReportLoad = repository.cleanupReportLoad()
    private val _cleanupReports = MutableStateFlow(initialCleanupReportLoad.reports)
    val cleanupReports: StateFlow<List<CleanupVerificationReport>> = _cleanupReports
    private val _cleanupReportDiagnostics = MutableStateFlow(initialCleanupReportLoad.diagnostics)
    val cleanupReportDiagnostics: StateFlow<List<String>> = _cleanupReportDiagnostics
    private val _selectedCleanupReport = MutableStateFlow<CleanupVerificationReport?>(null)
    private val _cleanupReportVerifying = MutableStateFlow<String?>(null)
    val cleanupReportVerifying: StateFlow<String?> = _cleanupReportVerifying
    val selectedCleanupReport: StateFlow<CleanupVerificationReport?> = _selectedCleanupReport

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
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LargeFilePage.empty())
    private val _moduleReport = MutableStateFlow<RootModuleReport?>(null)
    val moduleReport: StateFlow<RootModuleReport?> = _moduleReport
    val message: StateFlow<String?> = _message

    init {
        recordDebugEvent(DebugCategory.App, DebugSeverity.Info, "app_state_initialized", "MainViewModel initialized")
        refreshRoot()
        viewModelScope.launch { latestSession.collect { session ->
            _scanDiagnostics.value = session?.let { repository.scanDiagnostics(it.id) }
            if (session?.id != activeSessionId) {
                activeSessionId = session?.id
                resetScanScopedState(session)
            }
            when (session?.status) {
                "COMPLETED" -> refreshInsights(session.id)
                "FAILED", "CANCELLED" -> viewModelScope.launch {
                    _moduleReport.value = repository.moduleAnalysis(session.id)
                }
            }
        } }
    }

    private fun invalidateScanScopedRequests() {
        rootInspectionRequestId.incrementAndGet()
        largeFileRequestId.incrementAndGet()
        residueRequestId.incrementAndGet()
        recommendationRequestId.incrementAndGet()
        cleanupRuleRequestId.incrementAndGet()
        cleanupRuleDraftRequestId.incrementAndGet()
        storageGrowthRequestId.incrementAndGet()
        cleanupVerificationRequestId.incrementAndGet()
        rootSearchRequestId.incrementAndGet()
        insightsRequestId.incrementAndGet()
    }

    private fun resetScanScopedState(session: ScanSessionEntity?) {
        invalidateScanScopedRequests()
        recordDebugEvent(
            DebugCategory.Scan,
            DebugSeverity.Info,
            "scan_scope_reset",
            if (session == null) "Cleared scan-scoped state" else "Loaded scan-scoped state",
            mapOf("scanId" to (session?.id ?: "none"), "status" to (session?.status ?: "none"), "rootPath" to (session?.rootPath ?: "none")),
        )
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
        insightsRefreshJob?.cancel()
        insightsRefreshJob = null
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
        _appStorageInspector.value = null
        _appStorageLoading.value = false
        _appStorageFilter.value = AppStorageInspectorFilter()
        _residueAnalysis.value = null
        _residueLoading.value = false
        _residueFilter.value = AppResidueFilter()
        _recommendationAnalysis.value = null
        _recommendationLoading.value = false
        _recommendationFilter.value = CleanupRecommendationFilter()
        _recommendationPreferences.value = CleanupRecommendationPreferences()
        _cleanupRules.value = repository.cleanupRules()
        _cleanupRulePreview.value = null
        _cleanupRuleLoading.value = false
        _cleanupRuleDraftPreview.value = null
        _cleanupRuleDraftLoading.value = false
        _storageGrowthAnalysis.value = null
        _storageGrowthFilter.value = StorageGrowthFilter()
        _storageGrowthUnderstood.value = repository.storageGrowthUnderstood()
        _storageGrowthCurrentOffset.value = 0
        _storageGrowthPreviousOffset.value = 1
        _storageGrowthSessionCount.value = 0
        _storageGrowthLoading.value = false
        _selectedCleanupReport.value = null
        repository.cleanupReportLoad().also { load ->
            _cleanupReports.value = load.reports
            _cleanupReportDiagnostics.value = load.diagnostics
        }
        _moduleReport.value = null
        _scanDataStale.value = false
    }

    fun refreshRoot() {
        val requestId = rootRefreshRequestId.incrementAndGet()
        _rootState.value = RootUiState.Checking
        _rootFailureMessage.value = null
        recordDebugEvent(DebugCategory.Root, DebugSeverity.Trace, "root_refresh_started", "Checking root service state", mapOf("requestId" to requestId.toString()))
        viewModelScope.launch {
            runCatching { repository.rootServiceInfo() }
                .onSuccess { info ->
                    if (rootRefreshRequestId.get() != requestId) return@onSuccess
                    _rootServiceInfo.value = info
                    val ready = info.ok && info.uid == 0
                    _rootState.value = if (ready) RootUiState.Available else RootUiState.Unavailable
                    if (!ready) _rootFailureMessage.value = info.message ?: "Root service is not running as UID 0"
                    recordDebugEvent(
                        DebugCategory.Root,
                        if (ready) DebugSeverity.Info else DebugSeverity.Warning,
                        "root_refresh_finished",
                        if (ready) "Root service is available" else "Root service is not ready",
                        mapOf(
                            "ok" to info.ok.toString(),
                            "uid" to info.uid.toString(),
                            "backend" to info.backend,
                            "message" to (info.message ?: "none"),
                        ),
                    )
                }
                .onFailure { error ->
                    if (rootRefreshRequestId.get() != requestId) return@onFailure
                    _rootServiceInfo.value = null
                    _rootState.value = RootUiState.Unavailable
                    _rootFailureMessage.value = error.message ?: "Unable to bind the root service"
                    recordDebugEvent(
                        DebugCategory.Root,
                        DebugSeverity.Error,
                        "root_refresh_failed",
                        error.message ?: "Unable to bind the root service",
                        mapOf("error" to error::class.java.simpleName),
                    )
                }
        }
    }
    fun startPreset(preset: ScanPreset) = startRequest(runCatching { preset.request(_scanDepthProfile.value) })
    fun diagnoseScanPath(path: String) {
        val requestId = scanPreflightRequestId.incrementAndGet()
        _pathPreflight.value = null
        val normalizedPath = path.trim()
        recordDebugEvent(DebugCategory.Scan, DebugSeverity.Trace, "scan_preflight_started", "Testing scan-root access", mapOf("rootPath" to normalizedPath, "requestId" to requestId.toString()))
        viewModelScope.launch {
            runCatching { ScanRequest(rootPath = normalizedPath, maxDepth = _scanDepthProfile.value.maxDepth, maxEntries = _scanDepthProfile.value.maxEntries).validated() }
                .mapCatching { repository.preflightScan(it) }
                .onSuccess {
                    if (scanPreflightRequestId.get() != requestId) return@onSuccess
                    _pathPreflight.value = it
                    recordDebugEvent(
                        DebugCategory.Scan,
                        if (it.ok) DebugSeverity.Info else DebugSeverity.Warning,
                        "scan_preflight_finished",
                        if (it.ok) "Scan-root preflight passed" else "Scan-root preflight reported blockers",
                        mapOf("rootPath" to it.requestedPath, "ok" to it.ok.toString(), "message" to it.message),
                    )
                }
                .onFailure { error ->
                    if (scanPreflightRequestId.get() != requestId) return@onFailure
                    _message.value = error.message ?: "Unable to test scan-root access"
                    recordDebugEvent(DebugCategory.Scan, DebugSeverity.Error, "scan_preflight_failed", error.message ?: "Unable to test scan-root access")
                }
        }
    }
    fun startCustom(path: String) = startRequest(runCatching { ScanRequest(rootPath = path.trim(), maxDepth = _scanDepthProfile.value.maxDepth, maxEntries = _scanDepthProfile.value.maxEntries).validated() })
    fun startModuleScan() {
        recordDebugEvent(DebugCategory.Module, DebugSeverity.Info, "module_scan_requested", "Requested /data/adb/modules module-source scan", mapOf("rootPath" to "/data/adb/modules"))
        startRequest(runCatching {
            ScanRequest(
                presetId = "root-modules",
                rootPath = "/data/adb/modules",
                maxDepth = _scanDepthProfile.value.maxDepth,
                maxEntries = _scanDepthProfile.value.maxEntries,
                includeHidden = true,
            ).validated()
        })
    }
    private fun startRequest(result: Result<ScanRequest>) {
        if (_rootState.value != RootUiState.Available) {
            _message.value = "Root access is required"
            recordDebugEvent(DebugCategory.Scan, DebugSeverity.Warning, "scan_request_blocked", "Root access is required")
            return
        }
        result.onSuccess {
            latestSession.value?.takeIf { session -> session.status == "STARTING" || session.status == "RUNNING" || session.status == "CANCELLING" }?.let { busy ->
                _message.value = "A scan is already ${busy.status.lowercase()}. Cancel or wait before starting another."
                recordDebugEvent(DebugCategory.Scan, DebugSeverity.Warning, "scan_request_blocked", _message.value ?: "Scan already active", mapOf("activeScanId" to busy.id, "status" to busy.status))
                return
            }
            _pathPreflight.value = null
            _explorerPath.value = it.rootPath
            recordDebugEvent(
                DebugCategory.Scan,
                DebugSeverity.Info,
                "scan_requested",
                "Started scan request",
                mapOf("rootPath" to it.rootPath, "presetId" to (it.presetId ?: "custom"), "maxDepth" to it.maxDepth.toString(), "maxEntries" to it.maxEntries.toString()),
            )
            repository.startScan(it)
        }.onFailure {
            _message.value = it.message
            recordDebugEvent(DebugCategory.Scan, DebugSeverity.Error, "scan_request_invalid", it.message ?: "Scan request is invalid")
        }
    }
    fun cancelActive() {
        latestSession.value?.id?.let { scanId ->
            recordDebugEvent(DebugCategory.Scan, DebugSeverity.Warning, "scan_cancel_requested", "Cancel requested for active scan", mapOf("scanId" to scanId))
            repository.cancelScan(scanId)
        }
    }
    fun open(entry: ScanEntryEntity) {
        if (entry.isDirectory) _explorerPath.value = entry.path else select(entry)
    }
    fun select(entry: ScanEntryEntity) {
        _selectedEntry.value = entry
        _selectedIntelligence.value = repository.inspect(entry.path)
        _selectedRootInspection.value = null
        val requestId = rootInspectionRequestId.incrementAndGet()
        val requestedPath = entry.path
        viewModelScope.launch {
            val inspection = repository.inspectRootPath(requestedPath)
            if (rootInspectionRequestId.get() == requestId && _selectedEntry.value?.path == requestedPath) {
                _selectedRootInspection.value = inspection
            }
        }
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
    fun selectEntryType(type: EntryTypeFilter) { _filter.value = _filter.value.copy(type = type) }
    fun selectEntrySort(sort: EntrySort) { _filter.value = _filter.value.copy(sort = sort) }

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
        val session = latestSession.value ?: return
        val scanId = session.id
        val generation = session.generation
        val requestId = largeFileRequestId.incrementAndGet()
        _largeFileLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.largeFileEntries(scanId, preset) }
            if (latestSession.value?.let { it.id == scanId && it.generation == generation } == true &&
                _largeFileFilter.value.preset == preset && largeFileRequestId.get() == requestId
            ) {
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

    fun updateResidueQuery(value: String) {
        _residueFilter.value = _residueFilter.value.copy(query = value, page = 0)
    }

    fun filterResidueConfidence(confidence: AppResidueConfidence?) {
        _residueFilter.value = _residueFilter.value.copy(confidence = confidence, page = 0)
    }

    fun cycleResidueSort() {
        val current = _residueFilter.value
        val options = AppResidueSort.entries
        _residueFilter.value = current.copy(sort = options[(current.sort.ordinal + 1) % options.size], page = 0)
    }

    fun previousResiduePage() {
        val current = _residueFilter.value
        _residueFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextResiduePage() {
        val current = _residueFilter.value
        val lastPage = (residuePage.value.pageCount - 1).coerceAtLeast(0)
        _residueFilter.value = current.copy(page = (current.page + 1).coerceAtMost(lastPage))
    }

    fun selectResidueGroup(packageName: String) {
        val paths = residuePage.value.items.firstOrNull { it.packageName == packageName }?.recommendedDeletionPaths.orEmpty()
        selectResiduePaths(paths, "high-confidence residue for $packageName")
    }

    fun reviewResidueGroup(packageName: String) {
        val paths = residuePage.value.items.firstOrNull { it.packageName == packageName }?.recommendedDeletionPaths.orEmpty()
        if (paths.isEmpty()) {
            _message.value = "This group has no high-confidence unprotected locations. Review individual paths instead."
        } else {
            reviewPaths(paths, emptyMap(), emptyMap(), paths.associateWith { packageName })
        }
    }

    fun selectResiduePage() = selectResiduePaths(residuePage.value.recommendedPaths, "high-confidence residue on this page")

    fun reviewResiduePage() {
        val page = residuePage.value
        val paths = page.recommendedPaths
        if (paths.isEmpty()) {
            _message.value = "No high-confidence unprotected residue is visible on this page"
        } else {
            val packageByPath = page.items.flatMap { group -> group.recommendedDeletionPaths.map { path -> path to group.packageName } }.toMap()
            reviewPaths(paths, emptyMap(), emptyMap(), packageByPath)
        }
    }

    private fun selectResiduePaths(paths: List<String>, label: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        if (paths.isEmpty()) {
            _message.value = "No $label is available"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} residue location(s)"
    }

    fun protectResiduePath(path: String) {
        repository.protectResiduePath(path)
        _selectedPaths.value = _selectedPaths.value - path
        _selectedContentHashes.value = _selectedContentHashes.value - path
        _cleanupReview.value = null
        reloadResidueAnalysis("Protected residue path")
    }

    fun unprotectResiduePath(path: String) {
        repository.unprotectResiduePath(path)
        reloadResidueAnalysis("Removed residue protection")
    }

    private fun reloadResidueAnalysis(message: String? = null) {
        val session = latestSession.value ?: return
        val scanId = session.id
        val generation = session.generation
        val requestId = residueRequestId.incrementAndGet()
        _residueLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.appResidueAnalysis(scanId) }
            if (latestSession.value?.let { it.id == scanId && it.generation == generation } == true &&
                residueRequestId.get() == requestId
            ) {
                result.onSuccess { _residueAnalysis.value = it }
                    .onFailure { _message.value = it.message ?: "Unable to analyze app residue" }
                _residueLoading.value = false
                if (result.isSuccess) reloadCleanupRecommendations(message)
            }
        }
    }

    private fun reloadCleanupRecommendations(message: String? = null) {
        val session = latestSession.value ?: return
        val scanId = session.id
        val generation = session.generation
        val requestId = recommendationRequestId.incrementAndGet()
        _recommendationLoading.value = true
        recordDebugEvent(DebugCategory.Recommendation, DebugSeverity.Trace, "recommendations_refresh_started", "Refreshing cleanup recommendations", mapOf("scanId" to scanId, "generation" to generation.toString(), "requestId" to requestId.toString()))
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.cleanupRecommendations(scanId) }
            if (latestSession.value?.let { it.id == scanId && it.generation == generation } == true &&
                recommendationRequestId.get() == requestId
            ) {
                result.onSuccess { analysis ->
                    _recommendationAnalysis.value = analysis
                    _recommendationPreferences.value = repository.cleanupRecommendationPreferences(scanId)
                    recordDebugEvent(DebugCategory.Recommendation, DebugSeverity.Info, "recommendations_refresh_finished", "Cleanup recommendations refreshed", mapOf("items" to analysis.items.size.toString(), "requestId" to requestId.toString()))
                    if (message != null) _message.value = message
                }.onFailure {
                    _message.value = it.message ?: "Unable to rank cleanup recommendations"
                    recordDebugEvent(DebugCategory.Recommendation, DebugSeverity.Error, "recommendations_refresh_failed", _message.value ?: "Unable to rank cleanup recommendations")
                }
                _recommendationLoading.value = false
            }
        }
    }

    fun updateRecommendationQuery(value: String) {
        _recommendationFilter.value = _recommendationFilter.value.copy(query = value, page = 0)
    }

    fun filterRecommendationKind(kind: CleanupRecommendationKind?) {
        _recommendationFilter.value = _recommendationFilter.value.copy(kind = kind, page = 0)
    }

    fun cycleRecommendationSort() {
        val current = _recommendationFilter.value
        val options = CleanupRecommendationSort.entries
        _recommendationFilter.value = current.copy(sort = options[(current.sort.ordinal + 1) % options.size], page = 0)
    }

    fun toggleHiddenRecommendations() {
        val current = _recommendationFilter.value
        _recommendationFilter.value = current.copy(showHidden = !current.showHidden, page = 0)
    }

    fun previousRecommendationPage() {
        val current = _recommendationFilter.value
        _recommendationFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextRecommendationPage() {
        val current = _recommendationFilter.value
        val lastPage = (recommendationPage.value.pageCount - 1).coerceAtLeast(0)
        _recommendationFilter.value = current.copy(page = (current.page + 1).coerceAtMost(lastPage))
    }

    fun selectCleanupRecommendation(recommendationId: String) {
        val item = _recommendationAnalysis.value?.items?.firstOrNull { it.id == recommendationId }
        selectRecommendationPaths(item?.paths.orEmpty(), item?.expectedSha256ByPath.orEmpty(), "recommendation")
    }

    fun reviewCleanupRecommendation(recommendationId: String) {
        val item = _recommendationAnalysis.value?.items?.firstOrNull { it.id == recommendationId }
        val paths = item?.paths.orEmpty()
        if (paths.isEmpty()) {
            _message.value = "This recommendation has no current paths"
        } else {
            reviewPaths(paths, item?.expectedSha256ByPath.orEmpty())
        }
    }

    fun toggleCleanupRecommendationPath(recommendationId: String, path: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        val item = _recommendationAnalysis.value?.items?.firstOrNull { it.id == recommendationId } ?: return
        if (path !in item.paths) {
            _message.value = "This path is no longer part of the recommendation"
            return
        }
        val selected = _selectedPaths.value.toMutableSet()
        val hashes = _selectedContentHashes.value.toMutableMap()
        if (!selected.add(path)) {
            selected.remove(path)
            hashes.remove(path)
        } else {
            item.expectedSha256ByPath[path]?.let { hashes[path] = it }
        }
        _selectedPaths.value = selected
        _selectedContentHashes.value = hashes
        _cleanupReview.value = null
    }

    fun reviewCleanupRecommendationPath(recommendationId: String, path: String) {
        val item = _recommendationAnalysis.value?.items?.firstOrNull { it.id == recommendationId } ?: return
        if (path !in item.paths) {
            _message.value = "This path is no longer part of the recommendation"
            return
        }
        reviewPaths(listOf(path), item.expectedSha256ByPath.filterKeys { it == path })
    }

    fun selectRecommendationPage() = selectRecommendationPaths(
        recommendationPage.value.batchPaths,
        recommendationPage.value.batchHashes,
        "high-confidence recommendation page",
    )

    fun reviewRecommendationPage() {
        val paths = recommendationPage.value.batchPaths
        if (paths.isEmpty()) {
            _message.value = "No active high-confidence recommendations are visible on this page"
        } else {
            reviewPaths(paths, recommendationPage.value.batchHashes)
        }
    }

    fun createRuleFromRecommendation(recommendationId: String) {
        val item = _recommendationAnalysis.value?.items?.firstOrNull { it.id == recommendationId } ?: run {
            _message.value = "Recommendation was not found"
            return
        }
        val conditions = when (item.kind) {
            CleanupRecommendationKind.EXACT_DUPLICATES -> CleanupRuleConditions(duplicateOnly = true)
            CleanupRecommendationKind.APP_RESIDUE -> CleanupRuleConditions(
                entryType = CleanupRuleEntryType.ALL,
                residueConfidence = CleanupRuleResidueConfidence.HIGH,
            )
            CleanupRecommendationKind.STALE_INSTALLER -> CleanupRuleConditions(
                extensions = listOf("apk", "apex"), minimumBytes = 10L * 1024L * 1024L, olderThanDays = 30,
            )
            CleanupRecommendationKind.OLD_ARCHIVE -> CleanupRuleConditions(
                extensions = listOf("zip", "7z", "rar", "tar", "gz", "zst"), minimumBytes = 50L * 1024L * 1024L, olderThanDays = 180,
            )
            CleanupRecommendationKind.OVERSIZED_LOG -> CleanupRuleConditions(
                extensions = listOf("log", "txt", "trace", "trc"), minimumBytes = 50L * 1024L * 1024L, olderThanDays = 30,
            )
            CleanupRecommendationKind.LARGE_OLD_FILE -> CleanupRuleConditions(
                minimumBytes = 250L * 1024L * 1024L, olderThanDays = 180,
            )
        }
        val rule = CleanupRule(name = item.kind.label, enabled = false, conditions = conditions)
        repository.saveCleanupRule(rule)
        _cleanupRules.value = repository.cleanupRules()
        _message.value = "Created disabled rule ${rule.name}. Review it before enabling."
    }

    private fun selectRecommendationPaths(paths: List<String>, hashes: Map<String, String>, label: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        if (paths.isEmpty()) {
            _message.value = "No $label paths are available"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _selectedContentHashes.value = hashes.filterKeys { it in _selectedPaths.value }
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} recommendation path(s)"
    }

    fun dismissCleanupRecommendation(recommendationId: String) = updateRecommendationPreference(recommendationId) { scanId ->
        repository.dismissCleanupRecommendation(scanId, recommendationId)
        "Dismissed until the next scan"
    }

    fun ignoreCleanupRecommendation(recommendationId: String) = updateRecommendationPreference(recommendationId) { scanId ->
        repository.ignoreCleanupRecommendation(scanId, recommendationId)
        "Ignored permanently. Hidden recommendations remain available through Show hidden."
    }

    fun restoreCleanupRecommendation(recommendationId: String) = updateRecommendationPreference(recommendationId) { scanId ->
        repository.restoreCleanupRecommendation(scanId, recommendationId)
        "Recommendation restored"
    }

    private fun updateRecommendationPreference(
        recommendationId: String,
        mutation: (String) -> String,
    ) {
        val scanId = latestSession.value?.id ?: return
        _message.value = mutation(scanId)
        _recommendationPreferences.value = repository.cleanupRecommendationPreferences(scanId)
        if (recommendationPage.value.items.isEmpty() && recommendationPage.value.pageIndex > 0) {
            previousRecommendationPage()
        }
    }

    fun refreshCleanupRules(preview: Boolean = true) {
        _cleanupRules.value = repository.cleanupRules()
        if (!preview) return
        val session = latestSession.value ?: return
        val scanId = session.id
        val generation = session.generation
        val requestId = cleanupRuleRequestId.incrementAndGet()
        _cleanupRuleLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { repository.cleanupRulePreview(scanId) }
            if (latestSession.value?.let { it.id == scanId && it.generation == generation } == true &&
                cleanupRuleRequestId.get() == requestId
            ) {
                result.onSuccess { _cleanupRulePreview.value = it }
                    .onFailure { _message.value = it.message ?: "Unable to preview cleanup rules" }
                _cleanupRuleLoading.value = false
            }
        }
    }

    fun previewCleanupRuleDraft(rule: CleanupRule) {
        val session = latestSession.value ?: run {
            _message.value = "Run a scan before previewing a cleanup rule"
            return
        }
        val scanId = session.id
        val generation = session.generation
        val requestId = cleanupRuleDraftRequestId.incrementAndGet()
        val validation = com.mikeyphw.dustui.data.CleanupRuleEngine.validate(rule.copy(enabled = false))
        if (!validation.valid) {
            _cleanupRuleDraftPreview.value = null
            _cleanupRuleDraftLoading.value = false
            return
        }
        _cleanupRuleDraftLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val draft = rule.copy(enabled = false)
            val result = runCatching { repository.cleanupRulePreview(scanId, listOf(draft), includeDisabled = true) }
            if (latestSession.value?.let { it.id == scanId && it.generation == generation } == true &&
                cleanupRuleDraftRequestId.get() == requestId
            ) {
                result.onSuccess { preview ->
                    _cleanupRuleDraftPreview.value = preview.matches.firstOrNull()?.let { CleanupRuleDraftPreview(draft, it) }
                }.onFailure { _message.value = it.message ?: "Unable to preview this cleanup rule" }
                _cleanupRuleDraftLoading.value = false
            }
        }
    }

    fun clearCleanupRuleDraftPreview() {
        _cleanupRuleDraftPreview.value = null
        _cleanupRuleDraftLoading.value = false
    }

    fun saveCleanupRule(rule: CleanupRule) {
        val reviewed = if (rule.imported) rule.copy(reviewedAt = System.currentTimeMillis(), enabled = false) else rule
        repository.saveCleanupRule(reviewed)
        refreshCleanupRules()
        _message.value = if (rule.imported) "Reviewed and saved ${rule.name}. It remains disabled until you enable it." else "Saved cleanup rule ${rule.name}"
    }

    fun toggleCleanupRule(ruleId: String, enabled: Boolean) {
        repository.setCleanupRuleEnabled(ruleId, enabled)
            .onSuccess { refreshCleanupRules() }
            .onFailure { _message.value = it.message ?: "Unable to change cleanup rule" }
    }

    fun deleteCleanupRule(ruleId: String) {
        repository.deleteCleanupRule(ruleId)
        refreshCleanupRules()
        _message.value = "Cleanup rule deleted"
    }

    fun duplicateCleanupRule(ruleId: String) {
        val duplicate = repository.duplicateCleanupRule(ruleId)
        refreshCleanupRules(preview = false)
        _message.value = duplicate?.let { "Created disabled copy ${it.name}" } ?: "Cleanup rule was not found"
    }

    fun importCleanupRules(raw: String) {
        repository.importCleanupRules(raw)
            .onSuccess { imported ->
                refreshCleanupRules(preview = false)
                _message.value = "Imported ${imported.size} disabled cleanup rule(s). Review before enabling."
            }
            .onFailure { _message.value = it.message ?: "Unable to import cleanup rules" }
    }

    fun exportCleanupRules() = repository.exportCleanupRules()

    fun selectCleanupRule(ruleId: String) {
        val match = _cleanupRulePreview.value?.matches?.firstOrNull { it.ruleId == ruleId }
        selectRulePaths(match?.actionableEntries.orEmpty().map { it.path }, "cleanup rule")
    }

    fun reviewCleanupRule(ruleId: String) {
        val match = _cleanupRulePreview.value?.matches?.firstOrNull { it.ruleId == ruleId }
        val paths = match?.actionableEntries.orEmpty().map { it.path }
        if (paths.isEmpty()) _message.value = "This cleanup rule has no current actionable matches" else reviewPaths(paths)
    }

    fun selectAllCleanupRuleMatches() = selectRulePaths(_cleanupRulePreview.value?.exactPaths.orEmpty(), "enabled cleanup rules")

    fun reviewAllCleanupRuleMatches() {
        val paths = _cleanupRulePreview.value?.exactPaths.orEmpty()
        if (paths.isEmpty()) _message.value = "Enabled cleanup rules have no current actionable matches" else reviewPaths(paths)
    }

    private fun selectRulePaths(paths: List<String>, label: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        if (paths.isEmpty()) {
            _message.value = "No $label matches are available"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} exact rule match(es)"
    }

    fun reloadStorageGrowth(
        currentOffset: Int = _storageGrowthCurrentOffset.value,
        previousOffset: Int = _storageGrowthPreviousOffset.value,
    ) {
        val current = currentOffset.coerceAtLeast(0)
        val previous = previousOffset.coerceAtLeast(current + 1)
        val requestId = storageGrowthRequestId.incrementAndGet()
        _storageGrowthCurrentOffset.value = current
        _storageGrowthPreviousOffset.value = previous
        _storageGrowthLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sessions = runCatching { repository.completedStorageGrowthSessions() }.getOrElse { emptyList() }
            val result = runCatching { repository.storageGrowth(current, previous) }
            if (storageGrowthRequestId.get() != requestId || _storageGrowthCurrentOffset.value != current || _storageGrowthPreviousOffset.value != previous) return@launch
            _storageGrowthSessionCount.value = sessions.size
            result.onSuccess { analysis ->
                if (analysis == null) {
                    _message.value = "No completed scans exist at that comparison range"
                } else {
                    _storageGrowthAnalysis.value = analysis
                    _storageGrowthUnderstood.value = repository.storageGrowthUnderstood()
                    _storageGrowthFilter.value = _storageGrowthFilter.value.copy(page = 0)
                }
            }.onFailure { _message.value = it.message ?: "Unable to compare scan history" }
            _storageGrowthLoading.value = false
        }
    }

    fun compareCurrentOlder() = reloadStorageGrowth(_storageGrowthCurrentOffset.value + 1, (_storageGrowthPreviousOffset.value + 1).coerceAtLeast(_storageGrowthCurrentOffset.value + 2))
    fun compareCurrentNewer() = reloadStorageGrowth((_storageGrowthCurrentOffset.value - 1).coerceAtLeast(0), _storageGrowthPreviousOffset.value.coerceAtLeast(_storageGrowthCurrentOffset.value + 1))
    fun comparePreviousOlder() = reloadStorageGrowth(_storageGrowthCurrentOffset.value, _storageGrowthPreviousOffset.value + 1)
    fun comparePreviousNewer() = reloadStorageGrowth(_storageGrowthCurrentOffset.value, (_storageGrowthPreviousOffset.value - 1).coerceAtLeast(_storageGrowthCurrentOffset.value + 1))

    fun selectStorageGrowthTimelineScan(sessionId: String) {
        val analysis = _storageGrowthAnalysis.value ?: return
        val newestFirst = analysis.timeline.sortedByDescending { it.completedAt }
        val currentIndex = newestFirst.indexOfFirst { it.sessionId == sessionId }
        if (currentIndex < 0 || currentIndex + 1 >= newestFirst.size) {
            _message.value = "Choose a timeline point that has an older comparison scan"
            return
        }
        reloadStorageGrowth(currentIndex, currentIndex + 1)
    }

    fun cycleStorageGrowthRetention() {
        val options = listOf(6, 12, 24, 50)
        val current = _storageGrowthRetentionLimit.value
        _storageGrowthRetentionLimit.value = options[(options.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % options.size }]
    }

    fun applyStorageGrowthRetention() {
        val keep = _storageGrowthRetentionLimit.value
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.pruneStorageGrowthHistory(keep) }
                .onSuccess { remaining ->
                    _storageGrowthSessionCount.value = remaining
                    _message.value = "Kept the newest $remaining completed scan snapshot(s)"
                    reloadStorageGrowth(0, 1)
                }
                .onFailure { _message.value = it.message ?: "Unable to prune scan history" }
        }
    }

    fun updateStorageGrowthQuery(value: String) {
        _storageGrowthFilter.value = _storageGrowthFilter.value.copy(query = value, page = 0)
    }

    fun cycleStorageGrowthScope() {
        val options = StorageGrowthScope.entries
        val current = _storageGrowthFilter.value
        _storageGrowthFilter.value = current.copy(scope = options[(current.scope.ordinal + 1) % options.size], page = 0)
    }

    fun cycleStorageGrowthGroup() {
        val options = StorageGrowthGroup.entries
        val current = _storageGrowthFilter.value
        _storageGrowthFilter.value = current.copy(group = options[(current.group.ordinal + 1) % options.size], page = 0)
    }

    fun toggleUnderstoodStorageGrowth() {
        val current = _storageGrowthFilter.value
        _storageGrowthFilter.value = current.copy(showUnderstood = !current.showUnderstood, page = 0)
    }

    fun previousStorageGrowthPage() {
        val current = _storageGrowthFilter.value
        _storageGrowthFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextStorageGrowthPage() {
        val current = _storageGrowthFilter.value
        val last = (storageGrowthPage.value.pageCount - 1).coerceAtLeast(0)
        _storageGrowthFilter.value = current.copy(page = (current.page + 1).coerceAtMost(last))
    }

    fun markStorageGrowthUnderstood(path: String) {
        val analysis = _storageGrowthAnalysis.value ?: return
        val item = analysis.items.firstOrNull { it.path == path } ?: return
        repository.markStorageGrowthUnderstood(StorageGrowthEngine.identity(item, analysis))
        _storageGrowthUnderstood.value = repository.storageGrowthUnderstood()
    }

    fun restoreStorageGrowth(path: String) {
        val analysis = _storageGrowthAnalysis.value ?: return
        val item = analysis.items.firstOrNull { it.path == path } ?: return
        repository.restoreStorageGrowth(StorageGrowthEngine.identity(item, analysis))
        _storageGrowthUnderstood.value = repository.storageGrowthUnderstood()
    }

    private fun latestActionableGrowthAnalysis(): StorageGrowthAnalysis? {
        val analysis = _storageGrowthAnalysis.value
        if (analysis == null) {
            _message.value = "Load a storage comparison first"
            return null
        }
        if (!analysis.currentIsLatest || analysis.currentSession.id != latestSession.value?.id) {
            _message.value = "Historical comparisons are informational. Choose the newest scan before acting on files."
            return null
        }
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before acting on storage-history files"
            return null
        }
        return analysis
    }

    fun selectStorageGrowthPath(path: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val current = analysis.items.firstOrNull { it.path == path }?.currentEntry
        if (current == null) _message.value = "Historical removed paths are informational only" else selectRulePaths(listOf(current.path), "growth path")
    }

    fun reviewStorageGrowthPath(path: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val current = analysis.items.firstOrNull { it.path == path }?.currentEntry
        if (current == null) _message.value = "Historical removed paths cannot be deletion targets" else reviewPaths(listOf(current.path))
    }

    fun createRuleFromGrowthPath(path: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val current = analysis.items.firstOrNull { it.path == path }?.currentEntry
        if (current == null) {
            _message.value = "Only a current path can become a cleanup rule"
            return
        }
        val include = if (current.isDirectory) current.path else current.parentPath
        val rule = CleanupRule(
            name = "Review growth under ${include.substringAfterLast('/').ifBlank { include }}",
            enabled = false,
            conditions = CleanupRuleConditions(includePaths = listOf(include)),
        )
        repository.saveCleanupRule(rule)
        _cleanupRules.value = repository.cleanupRules()
        _message.value = "Created a disabled cleanup rule from this growth path"
    }

    fun selectStorageGrowthInsight(insightId: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val paths = analysis.insights.firstOrNull { it.id == insightId }?.currentPaths.orEmpty()
        selectRulePaths(paths, "growth insight")
    }

    fun reviewStorageGrowthInsight(insightId: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val paths = analysis.insights.firstOrNull { it.id == insightId }?.currentPaths.orEmpty()
        if (paths.isEmpty()) _message.value = "This insight has no current deletion targets" else reviewPaths(paths)
    }

    fun rescanStorageGrowthPath(path: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val current = analysis.items.firstOrNull { it.path == path }?.currentEntry
        if (current == null) _message.value = "Historical removed paths cannot be rescanned" else startCustom(if (current.isDirectory) current.path else current.parentPath)
    }

    fun rescanStorageGrowthInsight(insightId: String) {
        val analysis = latestActionableGrowthAnalysis() ?: return
        val paths = analysis.insights.firstOrNull { it.id == insightId }?.currentPaths.orEmpty()
        if (paths.isEmpty()) {
            _message.value = "This insight has no current path to rescan"
        } else {
            startCustom(com.mikeyphw.dustui.data.CleanupVerificationEngine.targetedRescanRoot(analysis.currentSession.rootPath, paths))
        }
    }

    fun refreshCleanupReports() {
        val load = repository.cleanupReportLoad()
        _cleanupReports.value = load.reports
        _cleanupReportDiagnostics.value = load.diagnostics
        _selectedCleanupReport.value = _selectedCleanupReport.value?.operationId?.let { id -> load.reports.firstOrNull { it.operationId == id } }
    }

    fun openCleanupReport(operationId: String) {
        _selectedCleanupReport.value = _cleanupReports.value.firstOrNull { it.operationId == operationId }
    }

    fun closeCleanupReport() { _selectedCleanupReport.value = null }

    fun retryCleanupVerification(operationId: String) {
        if (_cleanupReportVerifying.value != null) return
        val requestId = cleanupVerificationRequestId.incrementAndGet()
        _cleanupReportVerifying.value = operationId
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.retryCleanupVerification(operationId) }
                .onSuccess { updated ->
                    if (cleanupVerificationRequestId.get() != requestId || _cleanupReportVerifying.value != operationId) return@onSuccess
                    refreshCleanupReports()
                    _selectedCleanupReport.value = updated
                    _message.value = "Verification retried for ${updated.items.size} path(s)"
                }
                .onFailure {
                    if (cleanupVerificationRequestId.get() == requestId) _message.value = it.message ?: "Unable to retry verification"
                }
            if (cleanupVerificationRequestId.get() == requestId) _cleanupReportVerifying.value = null
        }
    }

    fun exportCleanupReport(operationId: String, asJson: Boolean) {
        _cleanupReports.value.firstOrNull { it.operationId == operationId }?.let { repository.exportCleanupReport(it, asJson) }
    }

    fun rescanCleanupReport(operationId: String) {
        val report = _cleanupReports.value.firstOrNull { it.operationId == operationId }
        if (report == null) {
            _message.value = "Deletion report was not found"
        } else {
            startCustom(com.mikeyphw.dustui.data.CleanupVerificationEngine.targetedRescanRoot(report.rootPath, report.items.map { it.path }))
        }
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
        val requestedGeneration = session.generation
        duplicateAnalysisJob = viewModelScope.launch(Dispatchers.IO) {
            fun stillCurrent(): Boolean = latestSession.value?.let { it.id == session.id && it.generation == requestedGeneration } == true
            val result = repository.analyzeDuplicates(session.id, threshold.minBytes, reset) { update ->
                if (stillCurrent()) _duplicateAnalysis.value = update
            }
            if (stillCurrent()) {
                _duplicateAnalysis.value = result
                _message.value = result.message
                reloadCleanupRecommendations()
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
            val retained = item?.copies?.firstOrNull { it.key == item.keepCopyKey }?.primaryPath
            val retainedEvidence = if (retained.isNullOrBlank()) emptyMap() else paths.associateWith { retained to item.group.sha256 }
            reviewPaths(paths, paths.associateWith { item?.group?.sha256.orEmpty() }, retainedEvidence)
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
            val page = duplicatePage.value
            val hashes = page.items.flatMap { item -> item.deletionPaths.map { it to item.group.sha256 } }.toMap()
            val retainedEvidence = page.items.flatMap { item ->
                val retained = item.copies.firstOrNull { copy -> copy.key == item.keepCopyKey }?.primaryPath
                if (retained.isNullOrBlank()) emptyList() else item.deletionPaths.map { path -> path to (retained to item.group.sha256) }
            }.toMap()
            reviewPaths(paths, hashes, retainedEvidence)
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
        allEntries.value.firstOrNull { it.path == path }?.let { entry ->
            toggleResolvedPath(entry.path)
            return
        }
        val scanId = latestSession.value?.id ?: return
        viewModelScope.launch {
            val entry = repository.entry(scanId, path)
            if (latestSession.value?.id != scanId) return@launch
            if (entry == null) {
                _message.value = "This path is no longer part of the active scan"
            } else {
                toggleResolvedPath(entry.path)
            }
        }
    }

    private fun toggleResolvedPath(path: String) {
        _selectedPaths.value = _selectedPaths.value.toMutableSet().also { selected ->
            if (!selected.add(path)) selected.remove(path)
        }
        if (path !in _selectedPaths.value) {
            _selectedContentHashes.value = _selectedContentHashes.value - path
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
        val previousCount = _selectedPaths.value.size
        _selectedPaths.value = emptySet()
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
        if (previousCount > 0) recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Trace, "selection_cleared", "Cleared selected deletion paths", mapOf("count" to previousCount.toString()))
    }

    fun reviewCleanup() = reviewPaths(_selectedPaths.value, _selectedContentHashes.value)

    fun reviewPaths(paths: Collection<String>) = reviewPaths(paths, emptyMap(), emptyMap(), emptyMap())

    fun reviewPaths(paths: Collection<String>, expectedSha256ByPath: Map<String, String>) = reviewPaths(paths, expectedSha256ByPath, emptyMap(), emptyMap())

    fun reviewPaths(
        paths: Collection<String>,
        expectedSha256ByPath: Map<String, String>,
        retainedEvidenceByPath: Map<String, Pair<String, String>>,
        packageNameByPath: Map<String, String> = emptyMap(),
    ) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before planning another deletion"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_review_blocked", "Run a new scan before planning another deletion", mapOf("reason" to "stale_scan"))
            return
        }
        val session = latestSession.value ?: return
        if (session.status != "COMPLETED") {
            _message.value = "Complete a scan before planning permanent deletion"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_review_blocked", "Complete a scan before planning permanent deletion", mapOf("status" to session.status))
            return
        }
        if (session.truncated || session.integrityStatus == "TRUNCATED" || session.integrityStatus == "FAILED_INTEGRITY" || session.integrityStatus == "INTERRUPTED") {
            _message.value = "Run a complete scan before planning permanent deletion"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_review_blocked", "Run a complete scan before planning permanent deletion", mapOf("integrityStatus" to session.integrityStatus, "truncated" to session.truncated.toString()))
            return
        }
        val requested = paths.distinct()
        if (requested.isEmpty()) {
            _message.value = "Select at least one indexed path"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_review_blocked", "Select at least one indexed path", mapOf("reason" to "empty_selection"))
            return
        }
        recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Info, "deletion_review_requested", "Building permanent deletion review", mapOf("scanId" to session.id, "requestedCount" to requested.size.toString()))
        _selectedPaths.value = requested.toCollection(linkedSetOf())
        _selectedContentHashes.value = expectedSha256ByPath.filterKeys { it in requested }
        _cleanupReview.value = null
        viewModelScope.launch {
            val resolved = repository.entriesByPaths(session.id, requested)
            if (latestSession.value?.id != session.id || latestSession.value?.generation != session.generation) {
                _message.value = "The active scan changed. Plan the deletion again."
                return@launch
            }
            val byPath = resolved.associateBy { it.path }
            val missing = requested.filterNot { it in byPath }
            if (missing.isNotEmpty()) {
                _message.value = "${missing.size} selected path(s) no longer belong to the active scan. Plan the deletion again."
                recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_review_blocked", _message.value ?: "Selected paths missing", mapOf("missing" to missing.size.toString()))
                return@launch
            }
            val chosen = requested.mapNotNull(byPath::get)
            if (chosen.isEmpty()) {
                _message.value = "Selected paths no longer belong to the active scan"
                return@launch
            }
            _selectedPaths.value = chosen.mapTo(linkedSetOf()) { it.path }
            _selectedContentHashes.value = _selectedContentHashes.value.filterKeys { it in _selectedPaths.value }
            val review = repository.reviewCleanup(
                session,
                chosen,
                _selectedContentHashes.value,
                retainedEvidenceByPath.filterKeys { it in _selectedPaths.value },
                packageNameByPath.filterKeys { it in _selectedPaths.value },
            )
            if (latestSession.value?.id == session.id && latestSession.value?.generation == session.generation) {
                _cleanupReview.value = review
                recordDebugEvent(
                    DebugCategory.Deletion,
                    if (review.accepted.isEmpty()) DebugSeverity.Warning else DebugSeverity.Info,
                    "deletion_review_created",
                    review.message ?: "Deletion review created",
                    mapOf("accepted" to review.accepted.size.toString(), "blocked" to review.blocked.size.toString(), "executable" to review.executable.toString()),
                )
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
        if (!review.matches(session.id, session.rootPath, session.generation)) {
            _cleanupReview.value = null
            _message.value = "The active scan changed. Plan the deletion again."
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_execute_blocked", "The active scan changed. Plan the deletion again.", mapOf("reason" to "scan_changed"))
            return
        }
        if (session.status != "COMPLETED" || session.truncated || session.integrityStatus == "TRUNCATED" || session.integrityStatus == "FAILED_INTEGRITY" || session.integrityStatus == "INTERRUPTED") {
            _cleanupReview.value = null
            _message.value = "Run a complete scan before executing permanent deletion"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_execute_blocked", _message.value ?: "Scan is not a complete authoritative source", mapOf("status" to session.status, "integrityStatus" to session.integrityStatus))
            return
        }
        if (!review.executable) {
            _message.value = review.message ?: "This deletion plan has no approved paths"
            recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_execute_blocked", _message.value ?: "This deletion plan has no approved paths", mapOf("reason" to "not_executable"))
            return
        }
        recordDebugEvent(DebugCategory.Deletion, DebugSeverity.Warning, "deletion_execute_started", "Executing reviewed permanent deletion plan", mapOf("accepted" to review.accepted.size.toString(), "plannedBytes" to review.accepted.sumOf { it.plannedBytes }.toString()))
        viewModelScope.launch {
            if (latestSession.value?.id != review.scanId || latestSession.value?.generation != review.scanGeneration) {
                _cleanupReview.value = null
                _message.value = "The active scan changed. Plan the deletion again."
                return@launch
            }
            val result = repository.executeCleanup(review)
            refreshCleanupReports()
            _cleanupReview.value = null
            val filesystemChanged = result.status == "UNKNOWN_OUTCOME" || result.items.any { it.status == "DELETED" || it.status == "PARTIAL" || it.status == "UNKNOWN_OUTCOME" }
            if (filesystemChanged) {
                _scanDataStale.value = true
                clearSelection()
            }
            _message.value = when {
                result.ok -> "Deleted ${formatBytes(result.deletedBytes)} permanently. Run a new scan to refresh results."
                result.status == "UNKNOWN_OUTCOME" -> result.message ?: "Deletion outcome is ambiguous. Reverify the report and run a new scan."
                result.deletedBytes > 0 -> result.message ?: "Deleted ${formatBytes(result.deletedBytes)}, but some paths failed. Run a new scan."
                else -> result.message ?: "Permanent deletion failed"
            }
            recordDebugEvent(
                DebugCategory.Deletion,
                if (result.ok) DebugSeverity.Info else if (result.deletedBytes > 0) DebugSeverity.Warning else DebugSeverity.Error,
                "deletion_execute_finished",
                _message.value ?: "Permanent deletion finished",
                mapOf("deletedBytes" to result.deletedBytes.toString(), "items" to result.items.size.toString(), "filesystemChanged" to filesystemChanged.toString()),
            )
        }
    }

    fun refreshInsights(scanId: String? = latestSession.value?.id) {
        if (scanId == null) return
        val requestedSession = latestSession.value?.takeIf { it.id == scanId }
        val requestedGeneration = requestedSession?.generation ?: return
        val requestId = insightsRequestId.incrementAndGet()
        insightsRefreshJob?.cancel()
        recordDebugEvent(DebugCategory.App, DebugSeverity.Trace, "insights_refresh_started", "Refreshing storage intelligence off the UI thread", mapOf("scanId" to scanId, "generation" to requestedGeneration.toString(), "requestId" to requestId.toString()))
        insightsRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            fun scanStillCurrent(): Boolean = latestSession.value?.let { it.id == scanId && it.generation == requestedGeneration } == true && insightsRequestId.get() == requestId
            suspend fun checkpoint() {
                coroutineContext.ensureActive()
                if (!scanStillCurrent()) throw kotlinx.coroutines.CancellationException("Stale insights request")
                yield()
            }
            val analysisEntries = repository.entriesForAnalysis(scanId)
            checkpoint()
            val appSummaries = repository.appSummaries(scanId, analysisEntries)
            checkpoint()
            val snapshotDelta = repository.snapshotDelta(scanId, analysisEntries)
            val snapshotHistory = repository.snapshotHistory()
            checkpoint()
            val advancedReport = repository.advancedAnalysis(scanId, analysisEntries)
            checkpoint()
            val largeFilePreset = _largeFileFilter.value.preset
            _largeFileLoading.value = true
            val largeFileEntries = repository.largeFileEntries(scanId, largeFilePreset)
            checkpoint()
            _residueLoading.value = true
            val residueResult = runCatching { repository.appResidueAnalysis(scanId, analysisEntries) }
            val residueAnalysis = residueResult.getOrNull()
            checkpoint()
            _recommendationLoading.value = true
            val recommendationResult = runCatching { repository.cleanupRecommendations(scanId, residueAnalysis, analysisEntries) }
            checkpoint()
            _appStorageLoading.value = true
            val appStorageResult = runCatching { repository.appStorageInspector(scanId, residueAnalysis, analysisEntries) }
            checkpoint()
            val moduleReport = repository.moduleAnalysis(scanId, analysisEntries)
            if (!scanStillCurrent()) return@launch
            _appSummaries.value = appSummaries
            _snapshotDelta.value = snapshotDelta
            _snapshotHistory.value = snapshotHistory
            _advancedReport.value = advancedReport
            if (_largeFileFilter.value.preset == largeFilePreset) {
                _largeFileSourceEntries.value = largeFileEntries
                _largeFileLoading.value = false
            }
            appStorageResult.onSuccess { _appStorageInspector.value = it }
                .onFailure { _message.value = it.message ?: "Unable to inspect app storage" }
            _appStorageLoading.value = false
            residueResult.onSuccess { _residueAnalysis.value = it }
                .onFailure { _message.value = it.message ?: "Unable to analyze app residue" }
            _residueLoading.value = false
            recommendationResult.onSuccess { analysis ->
                _recommendationAnalysis.value = analysis
                _recommendationPreferences.value = repository.cleanupRecommendationPreferences(scanId)
            }.onFailure { _message.value = it.message ?: "Unable to rank cleanup recommendations" }
            _recommendationLoading.value = false
            _cleanupRules.value = repository.cleanupRules()
            _storageGrowthUnderstood.value = repository.storageGrowthUnderstood()
            refreshCleanupReports()
            _moduleReport.value = moduleReport
            recordDebugEvent(
                DebugCategory.Module,
                if (moduleReport.diagnostic.status.name == "READY") DebugSeverity.Info else DebugSeverity.Warning,
                "module_diagnostics_refreshed",
                moduleReport.diagnostic.message,
                mapOf("status" to moduleReport.diagnostic.status.name, "moduleCount" to moduleReport.modules.size.toString()),
            )
            recordDebugEvent(
                DebugCategory.App,
                DebugSeverity.Info,
                "insights_refresh_finished",
                "Storage intelligence refreshed with cooperative checkpoints",
                mapOf("scanId" to scanId, "entries" to analysisEntries.size.toString(), "recommendations" to (_recommendationAnalysis.value?.items?.size ?: 0).toString()),
            )
            if (scanStillCurrent()) insightsRefreshJob = null
        }
    }

    fun updateAppStorageQuery(value: String) {
        _appStorageFilter.value = _appStorageFilter.value.copy(query = value, page = 0)
    }

    fun toggleInstalledAppStorageOnly() {
        val current = _appStorageFilter.value
        _appStorageFilter.value = current.copy(installedOnly = !current.installedOnly, page = 0)
    }

    fun cycleAppStorageSort() {
        val current = _appStorageFilter.value
        val options = AppStorageSort.entries
        _appStorageFilter.value = current.copy(sort = options[(current.sort.ordinal + 1) % options.size], page = 0)
    }

    fun previousAppStoragePage() {
        val current = _appStorageFilter.value
        _appStorageFilter.value = current.copy(page = (current.page - 1).coerceAtLeast(0))
    }

    fun nextAppStoragePage() {
        val current = _appStorageFilter.value
        val last = (appStoragePage.value.pageCount - 1).coerceAtLeast(0)
        _appStorageFilter.value = current.copy(page = (current.page + 1).coerceAtMost(last))
    }

    fun selectAppStorageBucket(packageName: String, kind: AppStorageBucketKind) {
        val bucket = _appStorageInspector.value?.packages?.firstOrNull { it.packageName == packageName }?.buckets?.firstOrNull { it.kind == kind }
        selectAppStoragePaths(bucket?.deletionPaths.orEmpty(), bucket?.expectedSha256ByPath.orEmpty(), "${kind.label} for $packageName")
    }

    fun reviewAppStorageBucket(packageName: String, kind: AppStorageBucketKind) {
        val bucket = _appStorageInspector.value?.packages?.firstOrNull { it.packageName == packageName }?.buckets?.firstOrNull { it.kind == kind }
        val paths = bucket?.deletionPaths.orEmpty()
        if (paths.isEmpty()) {
            _message.value = "This storage bucket has no paths eligible for direct deletion"
        } else {
            reviewPaths(paths, bucket?.expectedSha256ByPath.orEmpty())
        }
    }

    fun selectAppStorageDuplicates(packageName: String) {
        val report = _appStorageInspector.value?.packages?.firstOrNull { it.packageName == packageName }
        val paths = report?.duplicatePaths.orEmpty()
        selectAppStoragePaths(paths.map { it.path }, paths.mapNotNull { item -> item.expectedSha256?.let { item.path to it } }.toMap(), "verified duplicates for $packageName")
    }

    fun reviewAppStorageDuplicates(packageName: String) {
        val report = _appStorageInspector.value?.packages?.firstOrNull { it.packageName == packageName }
        val paths = report?.duplicatePaths.orEmpty()
        reviewPaths(paths.map { it.path }, paths.mapNotNull { item -> item.expectedSha256?.let { item.path to it } }.toMap())
    }

    fun reviewAppStorageResidue(packageName: String) {
        val paths = _appStorageInspector.value?.packages?.firstOrNull { it.packageName == packageName }?.residuePaths.orEmpty().filter { it.deletionEligible }.map { it.path }
        if (paths.isEmpty()) _message.value = "No high-confidence unprotected residue is eligible" else reviewPaths(paths, emptyMap(), emptyMap(), paths.associateWith { packageName })
    }

    private fun selectAppStoragePaths(paths: List<String>, hashes: Map<String, String>, label: String) {
        if (_scanDataStale.value) {
            _message.value = "Run a new scan before selecting files for deletion"
            return
        }
        if (paths.isEmpty()) {
            _message.value = "No deletion-eligible paths are available in $label"
            return
        }
        _selectedPaths.value = paths.toCollection(linkedSetOf())
        _selectedContentHashes.value = hashes.filterKeys { it in _selectedPaths.value }
        _cleanupReview.value = null
        _message.value = "Selected ${paths.size} path(s) from $label"
    }

    fun openAppStorageSettings(packageName: String) {
        repository.openAppSettings(packageName).onFailure { _message.value = it.message ?: "Unable to open Android app settings" }
    }

    fun requestAppUninstall(packageName: String) {
        repository.requestAppUninstall(packageName).onFailure { _message.value = it.message ?: "Unable to open Android uninstall flow" }
    }

    fun setAppStorageRecommendationsIgnored(packageName: String, ignored: Boolean) {
        repository.setAppStorageRecommendationsIgnored(packageName, ignored)
        val current = _appStorageInspector.value ?: return
        _appStorageInspector.value = current.copy(packages = current.packages.map { report ->
            if (report.packageName == packageName) report.copy(recommendationsIgnored = ignored) else report
        })
        latestSession.value?.id?.let { reloadCleanupRecommendations(if (ignored) "Recommendations ignored for $packageName" else "Recommendations restored for $packageName") }
    }

    fun protectAppStoragePath(path: String, protect: Boolean) {
        if (protect) repository.protectAppStoragePath(path) else repository.unprotectAppStoragePath(path)
        _selectedPaths.value = emptySet()
        _selectedContentHashes.value = emptyMap()
        _cleanupReview.value = null
        latestSession.value?.id?.let(::refreshInsights)
        _message.value = if (protect) "Protected $path" else "Removed protection from $path"
    }

    fun rootSearch(query: String) {
        val session = latestSession.value
        val scanId = session?.id
        val generation = session?.generation
        if (scanId == null || generation == null) {
            _message.value = "Run a scan before searching"
            recordDebugEvent(DebugCategory.Search, DebugSeverity.Warning, "root_search_blocked", "Run a scan before searching")
            return
        }
        val requestId = rootSearchRequestId.incrementAndGet()
        recordDebugEvent(DebugCategory.Search, DebugSeverity.Trace, "root_search_started", "Whole-scan search started", mapOf("scanId" to scanId, "generation" to generation.toString(), "requestId" to requestId.toString(), "queryLength" to query.length.toString()))
        viewModelScope.launch {
            val results = repository.rootSearch(scanId, RootFileSearchRequest(query = query))
            val stillCurrent = latestSession.value?.let { it.id == scanId && it.generation == generation } == true && rootSearchRequestId.get() == requestId
            if (!stillCurrent) return@launch
            _rootSearchResults.value = results
            _message.value = "Found ${results.size} root-indexed result(s)"
            recordDebugEvent(DebugCategory.Search, DebugSeverity.Info, "root_search_finished", "Whole-scan search finished", mapOf("resultCount" to results.size.toString(), "requestId" to requestId.toString()))
        }
    }

    fun openSearchResult(result: RootFileSearchResult) {
        _explorerPath.value = result.parentPath
        _filter.value = _filter.value.copy(query = result.name)
        allEntries.value.firstOrNull { it.path == result.path }?.let { select(it) }
    }
    fun runDeviceCertification() {
        val requestId = certificationRequestId.incrementAndGet()
        recordDebugEvent(DebugCategory.App, DebugSeverity.Trace, "device_certification_started", "Running device certification", mapOf("requestId" to requestId.toString()))
        viewModelScope.launch(Dispatchers.IO) {
            val report = repository.deviceCertification()
            if (certificationRequestId.get() != requestId) return@launch
            _certification.value = report
            _message.value = if (report.releaseReady) "Device certification passed with ${report.warnings} warning(s)" else "Device certification found ${report.failures} blocker(s)"
            recordDebugEvent(
                DebugCategory.App,
                if (report.releaseReady) DebugSeverity.Info else DebugSeverity.Warning,
                "device_certification_finished",
                _message.value ?: "Device certification finished",
                mapOf("releaseReady" to report.releaseReady.toString(), "warnings" to report.warnings.toString(), "failures" to report.failures.toString()),
            )
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
