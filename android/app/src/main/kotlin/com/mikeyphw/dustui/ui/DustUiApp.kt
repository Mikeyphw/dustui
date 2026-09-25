package com.mikeyphw.dustui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.dustui.data.ActivityRun
import com.mikeyphw.dustui.data.ActivityEvidence
import com.mikeyphw.dustui.data.ActivityFilter
import com.mikeyphw.dustui.data.ActivityStateFilter
import com.mikeyphw.dustui.data.AdvancedStorageAnalyzer
import com.mikeyphw.dustui.data.AdvancedStorageReport
import com.mikeyphw.dustui.data.AppResidueAnalysis
import com.mikeyphw.dustui.data.AppResidueConfidence
import com.mikeyphw.dustui.data.AppResidueGroup
import com.mikeyphw.dustui.data.AppResidueLocation
import com.mikeyphw.dustui.data.AppResiduePage
import com.mikeyphw.dustui.data.AppStorageSummary
import com.mikeyphw.dustui.data.CertificationStatus
import com.mikeyphw.dustui.data.ChartSlice
import com.mikeyphw.dustui.data.CleanupOperationEntity
import com.mikeyphw.dustui.data.CleanupRecommendationAnalysis
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.DeviceCertificationReport
import com.mikeyphw.dustui.data.DiagnosticsAuthoritySnapshot
import com.mikeyphw.dustui.data.DiagnosticProblem
import com.mikeyphw.dustui.data.DiagnosticRemediationAction
import com.mikeyphw.dustui.data.DebugCategory
import com.mikeyphw.dustui.data.DebugExportMode
import com.mikeyphw.dustui.data.DebugExportResult
import com.mikeyphw.dustui.data.DebugEvent
import com.mikeyphw.dustui.data.DebugSeverity
import com.mikeyphw.dustui.data.DebugProbeTarget
import com.mikeyphw.dustui.data.DebugProbeStatus
import com.mikeyphw.dustui.data.DebugProbeResult
import com.mikeyphw.dustui.data.DebugProbeGroup
import com.mikeyphw.dustui.data.DuplicateAnalysisSnapshot
import com.mikeyphw.dustui.data.DuplicateGroupItem
import com.mikeyphw.dustui.data.DuplicateKeepStrategy
import com.mikeyphw.dustui.data.DuplicatePage
import com.mikeyphw.dustui.data.DuplicatePhysicalCopy
import com.mikeyphw.dustui.data.DuplicateThreshold
import com.mikeyphw.dustui.data.EntryIntelligence
import com.mikeyphw.dustui.data.LargeFileItem
import com.mikeyphw.dustui.data.LargeFilePage
import com.mikeyphw.dustui.data.LargeFilePreset
import com.mikeyphw.dustui.data.RootFileSearchResult
import com.mikeyphw.dustui.data.ModuleAnalysisDiagnostic
import com.mikeyphw.dustui.data.ModuleDiagnosticStatus
import com.mikeyphw.dustui.data.OverviewStorageSemantics
import com.mikeyphw.dustui.data.RootModuleReport
import com.mikeyphw.dustui.data.RootPathInspection
import com.mikeyphw.dustui.data.ScanDiagnosticSeverity
import com.mikeyphw.dustui.data.ScanDiagnostics
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.data.SnapshotDelta
import com.mikeyphw.dustui.data.SnapshotPoint
import com.mikeyphw.dustui.data.StorageBucket
import com.mikeyphw.dustui.data.VisualAnalytics
import com.mikeyphw.dustui.scan.ScanStatus
import com.mikeyphw.dustui.root.RootServiceClient
import com.mikeyphw.dustui.scan.ScanDepthPolicy
import com.mikeyphw.dustui.scan.ScanDepthProfile
import com.mikeyphw.dustui.scan.ScanLaunchState
import com.mikeyphw.dustui.scan.ScanLaunchStage
import com.mikeyphw.dustui.ui.shell.DustRemediationAction
import com.mikeyphw.dustui.ui.shell.DustScreenState
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DustUiApp(
    viewModel: MainViewModel,
    requestNotificationPermission: () -> Unit = {},
    incomingDeepLink: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    DustTheme {
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()
        val rootServiceInfo by viewModel.rootServiceInfo.collectAsStateWithLifecycle()
        val rootFailureMessage by viewModel.rootFailureMessage.collectAsStateWithLifecycle()
        val scanDiagnostics by viewModel.scanDiagnostics.collectAsStateWithLifecycle()
        val pathPreflight by viewModel.pathPreflight.collectAsStateWithLifecycle()
        val session by viewModel.latestSession.collectAsStateWithLifecycle()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        val explorerPage by viewModel.explorerPage.collectAsStateWithLifecycle()
        val path by viewModel.explorerPath.collectAsStateWithLifecycle()
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val selected by viewModel.selectedEntry.collectAsStateWithLifecycle()
        val intelligence by viewModel.selectedIntelligence.collectAsStateWithLifecycle()
        val rootInspection by viewModel.selectedRootInspection.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val apps by viewModel.appSummaries.collectAsStateWithLifecycle()
        val delta by viewModel.snapshotDelta.collectAsStateWithLifecycle()
        val snapshotHistory by viewModel.snapshotHistory.collectAsStateWithLifecycle()
        val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
        val cleanupReview by viewModel.cleanupReview.collectAsStateWithLifecycle()
        val categoryFiles by viewModel.categoryFiles.collectAsStateWithLifecycle()
        val cleanupOperations by viewModel.cleanupOperations.collectAsStateWithLifecycle()
        val advancedReport by viewModel.advancedReport.collectAsStateWithLifecycle()
        val largeFiles by viewModel.largeFiles.collectAsStateWithLifecycle()
        val largeFileLoading by viewModel.largeFileLoading.collectAsStateWithLifecycle()
        val duplicateAnalysis by viewModel.duplicateAnalysis.collectAsStateWithLifecycle()
        val duplicatePage by viewModel.duplicatePage.collectAsStateWithLifecycle()
        val residueAnalysis by viewModel.residueAnalysis.collectAsStateWithLifecycle()
        val residuePage by viewModel.residuePage.collectAsStateWithLifecycle()
        val residueLoading by viewModel.residueLoading.collectAsStateWithLifecycle()
        val recommendationAnalysis by viewModel.recommendationAnalysis.collectAsStateWithLifecycle()
        val recommendationPage by viewModel.recommendationPage.collectAsStateWithLifecycle()
        val recommendationLoading by viewModel.recommendationLoading.collectAsStateWithLifecycle()
        val cleanupRules by viewModel.cleanupRules.collectAsStateWithLifecycle()
        val cleanupRulePreview by viewModel.cleanupRulePreview.collectAsStateWithLifecycle()
        val cleanupRuleLoading by viewModel.cleanupRuleLoading.collectAsStateWithLifecycle()
        val storageGrowthAnalysis by viewModel.storageGrowthAnalysis.collectAsStateWithLifecycle()
        val storageGrowthPage by viewModel.storageGrowthPage.collectAsStateWithLifecycle()
        val storageGrowthLoading by viewModel.storageGrowthLoading.collectAsStateWithLifecycle()
        val certification by viewModel.certification.collectAsStateWithLifecycle()
        val appStorageInspector by viewModel.appStorageInspector.collectAsStateWithLifecycle()
        val appStoragePage by viewModel.appStoragePage.collectAsStateWithLifecycle()
        val appStorageLoading by viewModel.appStorageLoading.collectAsStateWithLifecycle()
        val rootSearchResults by viewModel.rootSearchResults.collectAsStateWithLifecycle()
        val rootSearchPage by viewModel.rootSearchPage.collectAsStateWithLifecycle()
        val moduleReport by viewModel.moduleReport.collectAsStateWithLifecycle()
        val debugEvents by viewModel.debugEvents.collectAsStateWithLifecycle()
        val durableDiagnosticEvents by viewModel.durableDiagnosticEvents.collectAsStateWithLifecycle()
        val debugProbeResults by viewModel.debugProbeResults.collectAsStateWithLifecycle()
        val debugExportResult by viewModel.debugExportResult.collectAsStateWithLifecycle()
        val debugOverlayVisible by viewModel.debugOverlayVisible.collectAsStateWithLifecycle()
        val scanDepthProfile by viewModel.scanDepthProfile.collectAsStateWithLifecycle()
        val scanLaunchState by viewModel.scanLaunchState.collectAsStateWithLifecycle()
        val scanDataStale by viewModel.scanDataStale.collectAsStateWithLifecycle()
        val overviewStorage by viewModel.overviewStorage.collectAsStateWithLifecycle()
        val activityRuns by viewModel.activityRuns.collectAsStateWithLifecycle()
        val selectedActivityRun by viewModel.selectedActivityRun.collectAsStateWithLifecycle()
        val selectedActivityEvidence by viewModel.selectedActivityEvidence.collectAsStateWithLifecycle()
        val diagnosticsAuthority by viewModel.diagnosticsAuthority.collectAsStateWithLifecycle()
        val routeScreenState by viewModel.routeScreenState.collectAsStateWithLifecycle()

        val snackbar = remember { SnackbarHostState() }
        var routeName by rememberSaveable { mutableStateOf(DustSemanticRoute.OverviewHome.name) }
        var routeEntity by rememberSaveable { mutableStateOf<String?>(null) }
        var routeGeneration by rememberSaveable { mutableStateOf(1L) }
        var routeProblem by remember { mutableStateOf<DustRouteProblem?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        var showScanSheet by rememberSaveable { mutableStateOf(false) }
        var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedAppSessionId by rememberSaveable { mutableStateOf<String?>(null) }
        var diagnosticsFocusOperationId by rememberSaveable { mutableStateOf<String?>(null) }

        val route = runCatching { DustSemanticRoute.valueOf(routeName) }.getOrDefault(DustSemanticRoute.OverviewHome)
        val selection = runCatching { DustRouteSelection(route, routeEntity, routeGeneration) }
            .getOrElse { DustRouteSelection(DustSemanticRoute.OverviewHome, generation = routeGeneration.coerceAtLeast(1L)) }

        fun navigate(target: DustSemanticRoute, entityId: String? = null) {
            if (target.entityKind != null && entityId.isNullOrBlank()) {
                routeProblem = DustRouteProblem(DustRouteProblemCode.EntityRequired, "${target.entityKind} identity is required", target)
                return
            }
            routeGeneration += 1L
            routeName = target.name
            routeEntity = entityId
            routeProblem = null
            val nextSelection = DustRouteSelection(target, entityId, routeGeneration)
            viewModel.activateRoute(nextSelection)
            when (target) {
                DustSemanticRoute.CleanRules -> viewModel.refreshCleanupRules()
                DustSemanticRoute.OverviewTrends -> viewModel.reloadStorageGrowth()
                DustSemanticRoute.ActivityList,
                DustSemanticRoute.ActivityRun,
                DustSemanticRoute.ActivityEvidence -> viewModel.refreshActivityRuns()
                DustSemanticRoute.SystemDiagnostics -> viewModel.refreshDiagnosticsAuthority()
                else -> viewModel.refreshInsights()
            }
        }

        fun navigate(selection: DustRouteSelection) {
            routeGeneration = selection.generation.coerceAtLeast(routeGeneration + 1L)
            routeName = selection.route.name
            routeEntity = selection.entityId
            routeProblem = null
            viewModel.activateRoute(DustRouteSelection(selection.route, selection.entityId, routeGeneration))
        }

        LaunchedEffect(incomingDeepLink) {
            val raw = incomingDeepLink ?: return@LaunchedEffect
            when (val resolved = DustSemanticNavigation.resolveDeepLink(raw, routeGeneration + 1L)) {
                is DustRouteResolution.Resolved -> navigate(resolved.selection)
                is DustRouteResolution.Rejected -> routeProblem = resolved.problem
            }
            onDeepLinkConsumed()
        }

        LaunchedEffect(scanLaunchState.stage) {
            if (scanLaunchState.stage != ScanLaunchStage.ChooseScope) showScanSheet = true
        }

        LaunchedEffect(session?.id) {
            val currentSessionId = session?.id
            if (selectedAppSessionId != null && selectedAppSessionId != currentSessionId) selectedApp = null
            selectedAppSessionId = currentSessionId
        }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }
        LaunchedEffect(cleanupReview?.operationId) {
            val review = cleanupReview ?: return@LaunchedEffect
            if (selection.domain == DustRouteDomain.Clean && selection.route != DustSemanticRoute.CleanReview) {
                navigate(DustSemanticRoute.CleanReview, review.semanticManifestId())
            }
        }
        LaunchedEffect(selection.route, selection.entityId, session?.id) {
            when (selection.route) {
                DustSemanticRoute.FilesItem -> {
                    val entity = selection.entityId ?: return@LaunchedEffect
                    if (session == null) {
                        routeProblem = DustRouteProblem(DustRouteProblemCode.EntityStale, "No active scan can resolve this file identity", selection.route, entity)
                    } else if (!viewModel.resolveFileRoute(entity)) {
                        routeProblem = DustRouteProblem(DustRouteProblemCode.EntityNotFound, "The file no longer exists in the active scan", selection.route, entity)
                    }
                }
                DustSemanticRoute.ActivityRun,
                DustSemanticRoute.ActivityEvidence -> selection.entityId?.let(viewModel::selectActivityRun)
                else -> Unit
            }
        }
        LaunchedEffect(selection.route, selection.entityId, appStorageInspector, activityRuns, cleanupReview, recommendationAnalysis) {
            val entity = selection.entityId
            if (entity != null) {
                when (selection.route) {
                    DustSemanticRoute.AppsPackage,
                    DustSemanticRoute.AppsPaths -> if (appStorageInspector != null) {
                        val present = appStorageInspector?.packages?.any { it.packageName == entity } == true
                        if (!present) routeProblem = DustRouteProblem(DustRouteProblemCode.EntityNotFound, "Package is not present in the current evidence set", selection.route, entity)
                        else selectedApp = entity
                    }
                    DustSemanticRoute.ActivityRun,
                    DustSemanticRoute.ActivityEvidence -> if (activityRuns.isNotEmpty() && activityRuns.none { it.operationId == entity }) {
                        routeProblem = DustRouteProblem(DustRouteProblemCode.EntityNotFound, "Operation is no longer present in durable activity", selection.route, entity)
                    }
                    DustSemanticRoute.CleanCandidate -> if (recommendationAnalysis != null && recommendationAnalysis?.items?.none { it.id == entity } == true) {
                        routeProblem = DustRouteProblem(DustRouteProblemCode.EntityNotFound, "Cleanup candidate is not present in the current recommendation evidence", selection.route, entity)
                    }
                    DustSemanticRoute.CleanReview -> if (cleanupReview == null || cleanupReview?.semanticManifestId() != entity) {
                        routeProblem = DustRouteProblem(DustRouteProblemCode.EntityStale, "Cleanup review identity no longer matches the active manifest", selection.route, entity)
                    }
                    else -> Unit
                }
            }
        }

        LaunchedEffect(selection.route, selection.entityId, selection.generation, session?.id, session?.generation, scanDataStale) {
            viewModel.refreshRoutePresentation(selection)
        }

        BoxWithConstraints {
            val adaptiveLayout = DustAdaptiveLayoutPolicy.resolve(maxWidth.value, maxHeight.value, selection)
            val wide = adaptiveLayout.windowClass == DustWindowClass.Expanded
            val currentPath = path ?: session?.rootPath
            val adaptiveMasterItems = DustAdaptiveMasterModel.forSelection(
                selection = selection,
                files = entries,
                cleanup = recommendationAnalysis,
                apps = appStorageInspector,
                activity = activityRuns,
            )
            LaunchedEffect(selection.route, selection.entityId, selection.generation, adaptiveLayout.windowClass) {
                viewModel.recordNavigation(selection, wide)
            }

            fun openScanLauncher() { showScanSheet = true }

            BackHandler(
                enabled = cleanupReview != null || categoryFiles != null || selected != null || selectedPaths.isNotEmpty() ||
                    (selection.route == DustSemanticRoute.FilesExplorer && session != null && currentPath != session?.rootPath) ||
                    DustSemanticNavigation.backTarget(selection) != null,
            ) {
                when {
                    cleanupReview != null -> {
                        viewModel.dismissCleanupReview()
                        navigate(DustSemanticRoute.CleanRecommended)
                    }
                    categoryFiles != null -> viewModel.dismissCategoryFiles()
                    selected != null -> {
                        viewModel.closeDetails()
                        navigate(DustSemanticRoute.FilesExplorer)
                    }
                    selectedPaths.isNotEmpty() -> viewModel.clearSelection()
                    selection.route == DustSemanticRoute.FilesExplorer && session != null && currentPath != session?.rootPath -> viewModel.up()
                    else -> DustSemanticNavigation.backTarget(selection)?.let(::navigate)
                }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(selection.route.title) },
                        actions = {
                            TextButton(onClick = { openScanLauncher() }) { Text("Scan") }
                            TextButton(onClick = { menuOpen = true }) { Text(selection.route.actionLabel) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export CSV") },
                                    onClick = { menuOpen = false; viewModel.exportCsv() },
                                )
                                DropdownMenuItem(
                                    text = { Text("New scan") },
                                    onClick = { menuOpen = false; openScanLauncher() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Run certification") },
                                    onClick = { menuOpen = false; navigate(DustSemanticRoute.SystemCertification); viewModel.runDeviceCertification() },
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!adaptiveLayout.usesNavigationRail) {
                        NavigationBar(Modifier.dustPrimaryNavigationTraversal()) {
                            DustSemanticNavigation.primaryRoutes.forEach { item ->
                                NavigationBarItem(
                                    selected = selection.domain == item.domain,
                                    onClick = { navigate(item) },
                                    icon = { Text(item.domain.symbol, style = MaterialTheme.typography.titleMedium) },
                                    label = { Text(item.domain.shortLabel) },
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    if (adaptiveLayout.usesNavigationRail) {
                        NavigationRail(Modifier.fillMaxHeight().dustPrimaryNavigationTraversal()) {
                            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                                DustSemanticNavigation.primaryRoutes.forEach { item ->
                                    NavigationRailItem(
                                        selected = selection.domain == item.domain,
                                        onClick = { navigate(item) },
                                        icon = { Text(item.domain.symbol, style = MaterialTheme.typography.titleMedium) },
                                        label = { Text(item.domain.shortLabel) },
                                    )
                                }
                            }
                        }
                    }
                    if (adaptiveLayout.showsMasterPane) {
                        DustAdaptiveMasterPane(
                            layout = adaptiveLayout,
                            selection = selection,
                            items = adaptiveMasterItems,
                            onNavigate = { target, entity -> navigate(target, entity) },
                        )
                    }
                    Column(Modifier.weight(1f).fillMaxSize().dustDetailTraversal()) {
                        activityRuns.firstOrNull { it.active }?.let { active ->
                            GlobalActiveOperationSurface(
                                run = active,
                                open = { navigate(DustSemanticRoute.ActivityRun, active.operationId) },
                                cancel = if (active.canCancel) ({ viewModel.cancelActivityRun(active.operationId) }) else null,
                            )
                        }
                        if (scanDataStale) {
                            Surface(tonalElevation = 3.dp) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("Files were deleted. Scan results are now stale.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { openScanLauncher() }) { Text("New scan") }
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            val currentSession = session
                            val presentation = routeScreenState
                            when {
                                presentation is DustScreenState.Loading -> SharedRouteLoadingScreen(Modifier.fillMaxSize())
                                presentation is DustScreenState.Empty -> SharedRouteEmptyScreen(
                                    state = presentation,
                                    onRecovery = { action ->
                                        when (action.code) {
                                            "start_scan" -> openScanLauncher()
                                            else -> action.routeKey?.let { key -> DustSemanticRoute.entries.firstOrNull { it.routeKey == key }?.let { navigate(it) } }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                presentation is DustScreenState.Blocked -> SharedRouteBlockedScreen(
                                    state = presentation,
                                    onRemediation = { action ->
                                        when (action.code) {
                                            "start_scan" -> openScanLauncher()
                                            "open_diagnostics" -> navigate(DustSemanticRoute.SystemDiagnostics)
                                            "open_activity" -> navigate(DustSemanticRoute.ActivityList)
                                            else -> action.routeKey?.let { key -> DustSemanticRoute.entries.firstOrNull { it.routeKey == key }?.let { navigate(it) } }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                presentation is DustScreenState.Error -> SharedRouteErrorScreen(
                                    state = presentation,
                                    retry = { viewModel.refreshRoutePresentation(selection) },
                                    openDiagnostics = { navigate(DustSemanticRoute.SystemDiagnostics) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                routeProblem != null -> SemanticRouteProblemScreen(
                                    problem = requireNotNull(routeProblem),
                                    retry = { routeProblem = null; navigate(selection.route, selection.entityId) },
                                    goHome = { navigate(DustSemanticRoute.OverviewHome) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                selection.route in setOf(DustSemanticRoute.FilesExplorer, DustSemanticRoute.FilesTreemap, DustSemanticRoute.FilesItem) && currentSession != null -> Explorer(
                                    Modifier.fillMaxSize(),
                                    currentSession,
                                    scanDiagnostics,
                                    path ?: currentSession.rootPath,
                                    entries,
                                    explorerPage,
                                    filter,
                                    selectedPaths,
                                    viewModel,
                                    openRootSearch = { navigate(DustSemanticRoute.FilesSearch) },
                                    initialTreemap = selection.route == DustSemanticRoute.FilesTreemap,
                                    onTreemapModeChanged = { treemap -> navigate(if (treemap) DustSemanticRoute.FilesTreemap else DustSemanticRoute.FilesExplorer) },
                                    openEntryDetails = { entry -> viewModel.select(entry); navigate(DustSemanticRoute.FilesItem, entry.path) },
                                )
                                selection.route in setOf(DustSemanticRoute.OverviewHome, DustSemanticRoute.OverviewComposition) -> HomeDashboardScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    session = currentSession,
                                    diagnostics = scanDiagnostics,
                                    overviewStorage = overviewStorage,
                                    activityRuns = activityRuns,
                                    delta = delta,
                                    history = snapshotHistory,
                                    advanced = advancedReport,
                                    apps = apps,
                                    recommendationAnalysis = recommendationAnalysis,
                                    selectedPaths = selectedPaths,
                                    scanDataStale = scanDataStale,
                                    vm = viewModel,
                                    requestNotificationPermission = requestNotificationPermission,
                                    onStartScan = { openScanLauncher() },
                                    revealPath = { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) },
                                    openClean = { navigate(DustSemanticRoute.CleanRecommended) },
                                    openDuplicates = { navigate(DustSemanticRoute.CleanDuplicates) },
                                    openLargeFiles = { navigate(DustSemanticRoute.FilesLarge) },
                                    openResidue = { navigate(DustSemanticRoute.CleanResidue) },
                                    openDiagnostics = { diagnosticsFocusOperationId = null; navigate(DustSemanticRoute.SystemDiagnostics) },
                                    openActivityRun = { operationId -> navigate(DustSemanticRoute.ActivityRun, operationId) },
                                )
                                selection.route == DustSemanticRoute.OverviewTrends && currentSession != null -> StorageGrowthScreen(
                                    Modifier.fillMaxSize(), storageGrowthAnalysis, storageGrowthPage, storageGrowthLoading, selectedPaths, scanDataStale, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.OverviewScanHistory -> ScanHistoryRouteScreen(snapshotHistory, openScan = { openScanLauncher() }, modifier = Modifier.fillMaxSize())
                                selection.route == DustSemanticRoute.FilesSearch && currentSession != null -> RootSearchScreen(
                                    Modifier.fillMaxSize(), currentSession.id, rootSearchResults, rootSearchPage, selectedPaths, viewModel,
                                ) { result -> viewModel.revealPath(result.path); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.FilesLarge && currentSession != null -> LargeFileWorkbenchScreen(
                                    Modifier.fillMaxSize(), largeFiles, largeFileLoading, selectedPaths, scanDataStale, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route in setOf(DustSemanticRoute.CleanRecommended, DustSemanticRoute.CleanCandidate, DustSemanticRoute.CleanReview) && currentSession != null -> CleanupRecommendationsScreen(
                                    Modifier.fillMaxSize(), recommendationAnalysis, recommendationPage, recommendationLoading, selectedPaths, scanDataStale, viewModel,
                                    selectedRecommendationId = selection.entityId.takeIf { selection.route == DustSemanticRoute.CleanCandidate },
                                    openCandidate = { candidateId -> navigate(DustSemanticRoute.CleanCandidate, candidateId) },
                                    closeCandidate = { navigate(DustSemanticRoute.CleanRecommended) },
                                    openWorkbench = { navigate(it) },
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.CleanRules && currentSession != null -> CleanupRulesScreen(
                                    Modifier.fillMaxSize(), cleanupRules, cleanupRulePreview, cleanupRuleLoading, selectedPaths, scanDataStale, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.CleanDuplicates && currentSession != null -> DuplicateWorkbenchScreen(
                                    Modifier.fillMaxSize(), duplicateAnalysis, duplicatePage, selectedPaths, scanDataStale, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.CleanResidue && currentSession != null -> AppResidueWorkbenchScreen(
                                    Modifier.fillMaxSize(), residueAnalysis, residuePage, residueLoading, selectedPaths, scanDataStale, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route in setOf(DustSemanticRoute.AppsList, DustSemanticRoute.AppsPackage, DustSemanticRoute.AppsPaths) && currentSession != null -> AppStorageInspectorScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    analysis = appStorageInspector,
                                    page = appStoragePage,
                                    loading = appStorageLoading,
                                    selectedPackage = selection.entityId ?: selectedApp,
                                    selectedPaths = selectedPaths,
                                    scanDataStale = scanDataStale,
                                    vm = viewModel,
                                    revealPath = { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) },
                                    onSelectPackage = { packageName -> selectedApp = packageName; selectedAppSessionId = currentSession.id; navigate(DustSemanticRoute.AppsPackage, packageName) },
                                )
                                selection.domain == DustRouteDomain.Activity -> ActivitySemanticScreen(
                                    route = selection,
                                    runs = activityRuns,
                                    selectedRun = selectedActivityRun,
                                    evidence = selectedActivityEvidence,
                                    vm = viewModel,
                                    openRoute = { target, entity -> navigate(target, entity) },
                                    openDiagnostics = { operationId -> diagnosticsFocusOperationId = operationId; navigate(DustSemanticRoute.SystemDiagnostics) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                selection.route == DustSemanticRoute.SystemRuntime -> SystemRuntimeScreen(rootState, rootServiceInfo, rootFailureMessage, currentSession, openDiagnostics = { navigate(DustSemanticRoute.SystemDiagnostics) }, modifier = Modifier.fillMaxSize())
                                selection.route == DustSemanticRoute.SystemModules -> ModuleAnalyzerScreen(
                                    Modifier.fillMaxSize(), moduleReport, selectedPaths, viewModel,
                                ) { filePath -> viewModel.revealPath(filePath); navigate(DustSemanticRoute.FilesExplorer) }
                                selection.route == DustSemanticRoute.SystemAutomation -> SystemAutomationScreen(activityRuns, openActivity = { navigate(DustSemanticRoute.ActivityList) }, modifier = Modifier.fillMaxSize())
                                selection.route == DustSemanticRoute.SystemDiagnostics -> DiagnosticsAuthorityScreen(
                                    snapshot = diagnosticsAuthority,
                                    events = durableDiagnosticEvents,
                                    probeResults = debugProbeResults,
                                    exportResult = debugExportResult,
                                    focusedOperationId = diagnosticsFocusOperationId,
                                    refresh = viewModel::refreshDiagnosticsAuthority,
                                    exportBundle = { mode -> viewModel.exportDebugBundle(mode, selection, wide) },
                                    openRoute = { target, entity -> navigate(target, entity) },
                                    clearFocus = { diagnosticsFocusOperationId = null },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                selection.route == DustSemanticRoute.SystemCertification -> CertificationScreen(Modifier.fillMaxSize(), certification, viewModel)
                                selection.route == DustSemanticRoute.SystemSettings -> SystemSettingsScreen(
                                    scanDepthProfile = scanDepthProfile,
                                    debugOverlayVisible = debugOverlayVisible,
                                    vm = viewModel,
                                    openRoute = { target, entity -> navigate(target, entity) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                else -> SemanticRouteProblemScreen(
                                    problem = DustRouteProblem(DustRouteProblemCode.UnknownRoute, "No renderer is bound to ${selection.route.routeKey}", selection.route, selection.entityId),
                                    retry = { navigate(selection.route, selection.entityId) },
                                    goHome = { navigate(DustSemanticRoute.OverviewHome) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (debugOverlayVisible) {
                                SemanticDebugOverlayHud(
                                    selection = selection,
                                    wide = wide,
                                    session = currentSession,
                                    rootState = rootState,
                                    selectedCount = selectedPaths.size,
                                    scanDataStale = scanDataStale,
                                    moduleReport = moduleReport,
                                    eventCount = debugEvents.size,
                                    openDiagnostics = { navigate(DustSemanticRoute.SystemDiagnostics) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                )
                            }
                            if (selection.domain != DustRouteDomain.Files && selectedPaths.isNotEmpty()) {
                                SelectionActionBar(
                                    selectedCount = selectedPaths.size,
                                    clear = viewModel::clearSelection,
                                    review = viewModel::reviewCleanup,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }
            }
        }

        val categorySelection = categoryFiles
        if (categorySelection != null) CategoryFilesSheet(
            selection = categorySelection,
            selectedPaths = selectedPaths,
            vm = viewModel,
            deletionEnabled = true,
            close = viewModel::dismissCategoryFiles,
            reveal = { filePath ->
                viewModel.dismissCategoryFiles()
                viewModel.revealPath(filePath)
                navigate(DustSemanticRoute.FilesExplorer)
            },
        )
        val selectedEntry = selected
        if (selectedEntry != null && selection.route == DustSemanticRoute.FilesItem) EntryDetails(
            selectedEntry,
            intelligence,
            rootInspection,
            close = { viewModel.closeDetails(); navigate(DustSemanticRoute.FilesExplorer) },
            reveal = {
                viewModel.closeDetails()
                viewModel.revealPath(selectedEntry.path)
                navigate(DustSemanticRoute.FilesExplorer)
            },
            delete = {
                viewModel.closeDetails()
                viewModel.reviewPaths(listOf(selectedEntry.path))
            },
        )
        if (cleanupReview != null && selection.route == DustSemanticRoute.CleanReview) CleanupReviewSheet(
            cleanupReview,
            close = { viewModel.dismissCleanupReview(); navigate(DustSemanticRoute.CleanRecommended) },
            execute = viewModel::executeCleanup,
        )
        if (showScanSheet) {
            ScanLauncherSheet(
                root = rootState,
                rootInfo = rootServiceInfo,
                rootFailure = rootFailureMessage,
                session = session,
                diagnostics = scanDiagnostics,
                pathPreflight = pathPreflight,
                vm = viewModel,
                scanDepthProfile = scanDepthProfile,
                launchState = scanLaunchState,
                requestNotificationPermission = requestNotificationPermission,
                close = { showScanSheet = false },
                openHome = { navigate(DustSemanticRoute.OverviewHome) },
            )
        }
    }
}


@Composable
private fun SemanticRouteProblemScreen(
    problem: DustRouteProblem,
    retry: () -> Unit,
    goHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DustHeroCard(
            title = when (problem.code) {
                DustRouteProblemCode.EntityNotFound -> "Item not found"
                DustRouteProblemCode.EntityStale -> "Item is stale"
                DustRouteProblemCode.EntityRequired -> "Route identity is incomplete"
                DustRouteProblemCode.UnknownRoute -> "Unknown route"
                DustRouteProblemCode.InvalidDeepLink -> "Invalid link"
            },
            detail = problem.detail,
            eyebrow = "Semantic navigation",
        ) {
            problem.route?.let { MetricRow("Route", it.routeKey) }
            problem.entityId?.let { MetricRow("Identity", it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = goHome, modifier = Modifier.weight(1f)) { Text("Overview") }
                Button(onClick = retry, modifier = Modifier.weight(1f)) { Text("Retry") }
            }
        }
    }
}

@Composable
private fun ScanHistoryRouteScreen(
    history: List<SnapshotPoint>,
    openScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Scan history",
                detail = "Persisted storage snapshots keep their own semantic route instead of being folded into a layout-specific dashboard.",
                eyebrow = "Overview",
                trailing = { Button(onClick = openScan) { Text("New scan") } },
            ) {
                DustMetricTile("Snapshots", history.size.toString(), Modifier.fillMaxWidth(), detail = "Completed evidence points")
            }
        }
        if (history.isEmpty()) {
            item { EmptyState("No completed snapshots", "Complete a scan to create the first storage history point.") }
        } else {
            items(history.asReversed(), key = { it.timestamp }) { point ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatBytes(point.totalBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatTimestamp(point.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalActiveOperationSurface(
    run: ActivityRun,
    open: () -> Unit,
    cancel: (() -> Unit)?,
) {
    Surface(tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${run.kind.replace('-', ' ').replaceFirstChar { it.uppercase() }} · ${run.statusLabel}", fontWeight = FontWeight.SemiBold)
                Text("Tap for durable progress and evidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (cancel != null) TextButton(onClick = cancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun ActivitySemanticScreen(
    route: DustRouteSelection,
    runs: List<ActivityRun>,
    selectedRun: ActivityRun?,
    evidence: ActivityEvidence?,
    vm: MainViewModel,
    openRoute: (DustSemanticRoute, String?) -> Unit,
    openDiagnostics: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val run = route.entityId?.let { id -> selectedRun?.takeIf { it.operationId == id } ?: runs.firstOrNull { it.operationId == id } }
    when (route.route) {
        DustSemanticRoute.ActivityList -> {
            var stateName by rememberSaveable { mutableStateOf(ActivityStateFilter.All.name) }
            var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
            val stateFilter = runCatching { ActivityStateFilter.valueOf(stateName) }.getOrDefault(ActivityStateFilter.All)
            val filter = ActivityFilter(stateFilter, kindFilter)
            val visibleRuns = runs.filter(filter::accepts)
            val kinds = runs.map { it.kind }.distinct().sorted()
            LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    DustHeroCard(
                        title = "Activity",
                        detail = "Every durable scan, analysis, cleanup, automation, and support operation is shown from Go-owned lifecycle truth.",
                        eyebrow = "Operations",
                    ) {
                        MetricGrid(listOf(
                            "Runs" to runs.size.toString(),
                            "Active" to runs.count { it.active }.toString(),
                            "Recovery" to runs.count { it.recoveryRequired }.toString(),
                            "Failed" to runs.count { it.terminalTruth in setOf("failure", "interrupted") }.toString(),
                        ))
                    }
                }
                item {
                    DustSectionHeader("Filter", subtitle = "Filters never change operation truth; they only narrow the durable list.")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ActivityStateFilter.entries, key = { it.name }) { state ->
                            FilterChip(selected = state == stateFilter, onClick = { stateName = state.name }, label = { Text(state.label) })
                        }
                    }
                    if (kinds.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("All types") }) }
                            items(kinds, key = { it }) { kind ->
                                FilterChip(selected = kindFilter == kind, onClick = { kindFilter = kind }, label = { Text(kind.replace('-', ' ')) })
                            }
                        }
                    }
                }
                if (runs.isEmpty()) item { EmptyState("No durable activity", "Scans, cleanup, analysis, exports, and other durable work will appear here.") }
                else if (visibleRuns.isEmpty()) item { EmptyState("No activity matches", "Change the state or operation-type filter.") }
                items(visibleRuns, key = { it.operationId }) { item ->
                    DustTaskCard(
                        title = item.kind.replace('-', ' ').replaceFirstChar { it.uppercase() },
                        detail = "${item.statusLabel} · request ${item.requestId}${if (item.recoveryRequired) " · recovery required" else ""}",
                        value = if (item.active) "Active" else item.terminalTruth,
                        onClick = { openRoute(DustSemanticRoute.ActivityRun, item.operationId) },
                    )
                }
            }
        }
        DustSemanticRoute.ActivityRun -> LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (run == null) {
                item { EmptyState("Operation unavailable", "The durable operation identity could not be resolved.") }
            } else {
                item {
                    DustHeroCard(
                        title = run.kind.replace('-', ' ').replaceFirstChar { it.uppercase() },
                        detail = run.statusLabel,
                        eyebrow = "Run ${run.operationId}",
                    ) {
                        MetricGrid(listOf(
                            "State" to run.state,
                            "Terminal truth" to run.terminalTruth,
                            "Recovery" to if (run.recoveryRequired) "Required" else "Clear",
                            "Updated" to formatTimestamp(run.updatedAt / 1000),
                        ))
                        if (run.recoveryRequired) DustStatusNotice(
                            "Recovery required",
                            "The durable operation cannot be treated as failed or restarted automatically. Reconcile its evidence first.",
                            DustNoticeTone.Warning,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (run.canCancel) OutlinedButton(onClick = { vm.cancelActivityRun(run.operationId) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                            if (run.canReconcile) Button(onClick = { vm.reconcileActivityRun(run.operationId) }, modifier = Modifier.weight(1f)) { Text("Reconcile") }
                            if (run.canRetry) Button(onClick = { vm.retryActivityRun(run.operationId) }, modifier = Modifier.weight(1f)) { Text("Retry") }
                        }
                        TextButton(onClick = { openRoute(DustSemanticRoute.ActivityEvidence, run.operationId) }, modifier = Modifier.fillMaxWidth()) { Text("Open evidence") }
                    }
                }
                item { MetricRow("Request", run.requestId) }
                item { MetricRow("Evidence correlation", run.evidenceCorrelation.ifBlank { "none" }) }
                item { MetricRow("Sources", run.sources.joinToString().ifBlank { "none" }) }
                item { EvidenceJsonCard("Latest progress", run.progressJson) }
            }
        }
        DustSemanticRoute.ActivityEvidence -> LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DustSectionHeader("Operation evidence", subtitle = "Progress, result, verification, and correlated problems share the same durable operation identity.") }
            if (run == null) item { EmptyState("Evidence unavailable", "The operation is missing or stale.") }
            else {
                val selectedEvidence = evidence?.takeIf { it.operationId == run.operationId }
                selectedEvidence?.cleanupVerification?.let { report ->
                    item {
                        DustHeroCard(
                            title = "Deletion verification",
                            detail = "Cleanup verification is Activity evidence, not a separate product destination.",
                            eyebrow = "Cleanup run",
                        ) {
                            MetricGrid(listOf(
                                "Confirmed" to formatBytes(report.confirmedDeletedBytes),
                                "Attempted" to formatBytes(report.attemptedBytes),
                                "Partial" to formatBytes(report.partialBytes),
                                "Failed" to formatBytes(report.failedBytes),
                            ))
                            Text("Verification attempts ${report.verificationAttempts} · generation ${selectedEvidence.verificationGeneration ?: 0}", style = MaterialTheme.typography.bodySmall)
                            PathText(report.rootPath, Modifier.fillMaxWidth(), maxLines = 2)
                        }
                    }
                }
                item { EvidenceJsonCard("Progress", run.progressJson) }
                item { EvidenceJsonCard("Result", run.resultJson) }
                item { EvidenceJsonCard("Verification", run.verificationJson) }
                selectedEvidence?.problems?.forEach { problem ->
                    item(key = "activity-problem:${problem.id}") {
                        DustStatusNotice(problem.reason, problem.guidance, if (problem.recoveryRequired) DustNoticeTone.Warning else DustNoticeTone.Success)
                    }
                }
                item {
                    OutlinedButton(onClick = { openDiagnostics(run.operationId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open correlated diagnostics")
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun EvidenceJsonCard(title: String, json: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            PathText(json.ifBlank { "{}" }, Modifier.fillMaxWidth(), maxLines = 14)
        }
    }
}

@Composable
private fun SystemRuntimeScreen(
    rootState: RootUiState,
    rootServiceInfo: RootServiceClient.RootServiceInfo?,
    rootFailure: String?,
    session: ScanSessionEntity?,
    openDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Runtime",
                detail = "Privileged runtime identity and current scan authority live here; they are no longer hidden in a generic Tools destination.",
                eyebrow = "System",
            ) {
                MetricGrid(listOf("Root" to rootUiStateLabel(rootState), "Backend" to (rootServiceInfo?.backend ?: "unavailable"), "UID" to (rootServiceInfo?.uid?.toString() ?: "unknown"), "Scan" to (session?.status ?: "none")))
                rootFailure?.let { DustStatusNotice("Runtime problem", it, DustNoticeTone.Warning) }
                Button(onClick = openDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Open diagnostics") }
            }
        }
    }
}

@Composable
private fun SystemAutomationScreen(
    runs: List<ActivityRun>,
    openActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Automation",
                detail = "Automation surfaces expose durable operation truth while Activity owns individual run identities.",
                eyebrow = "System",
            ) {
                MetricGrid(listOf("Active" to runs.count { it.active }.toString(), "Recovery" to runs.count { it.recoveryRequired }.toString(), "History" to runs.count { it.history }.toString()))
                Button(onClick = openActivity, modifier = Modifier.fillMaxWidth()) { Text("Open activity") }
            }
        }
    }
}

@Composable
private fun DiagnosticsAuthorityScreen(
    snapshot: DiagnosticsAuthoritySnapshot?,
    events: List<DebugEvent>,
    probeResults: List<DebugProbeResult>,
    exportResult: DebugExportResult?,
    focusedOperationId: String?,
    refresh: () -> Unit,
    exportBundle: (DebugExportMode) -> Unit,
    openRoute: (DustSemanticRoute, String?) -> Unit,
    clearFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    val problems = snapshot?.problems.orEmpty().sortedWith(
        compareByDescending<DiagnosticProblem> { it.operationId == focusedOperationId }
            .thenByDescending { it.recoveryRequired }
            .thenBy { it.id },
    )
    fun openRemediation(problem: DiagnosticProblem, action: DiagnosticRemediationAction) {
        val target = action.route?.let { DustSemanticRoute.byKey[it] } ?: return
        val entity = if (target.entityKind != null) action.entityId ?: problem.operationId else null
        if (target.entityKind == null || !entity.isNullOrBlank()) openRoute(target, entity)
    }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Diagnostics",
                detail = "Root causes and remediation come from the durable Go ProblemGraph. Developer probes and traces stay behind Advanced details.",
                eyebrow = "System health",
            ) {
                MetricGrid(listOf(
                    "Root causes" to problems.size.toString(),
                    "Recovery" to problems.count { it.recoveryRequired }.toString(),
                    "Evidence issues" to (snapshot?.unhealthyEvidence?.size ?: 0).toString(),
                    "Support export" to (snapshot?.supportExportState ?: "unknown"),
                ))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = refresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    Button(onClick = { exportBundle(DebugExportMode.Safe) }, modifier = Modifier.weight(1f)) { Text("Export support bundle") }
                }
            }
        }
        if (focusedOperationId != null) item {
            DustStatusNotice(
                "Correlated operation",
                "Showing problems for $focusedOperationId first. Diagnostics and Activity share the same durable operation identity.",
                DustNoticeTone.Warning,
            )
            TextButton(onClick = clearFocus) { Text("Clear operation focus") }
        }
        if (problems.isEmpty()) {
            item { DustStatusNotice("No active root cause", "Current evidence does not expose a durable problem. Evidence health remains visible below.", DustNoticeTone.Success) }
        } else {
            item { DustSectionHeader("Root causes", subtitle = "Plain-language explanation first; raw implementation traces are intentionally secondary.") }
            problems.forEach { problem ->
                item(key = "problem:${problem.id}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(problem.reason, fontWeight = FontWeight.SemiBold)
                            Text(problem.guidance.ifBlank { "Review the affected evidence before continuing." }, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Risk ${problem.risk}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (problem.recoveryRequired) Text("Recovery required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            problem.operationId?.let { operationId ->
                                OutlinedButton(onClick = { openRoute(DustSemanticRoute.ActivityRun, operationId) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Open related Activity run")
                                }
                            }
                            problem.remediationActions.forEachIndexed { index, action ->
                                val target = action.route?.let { DustSemanticRoute.byKey[it] }
                                if (target != null && (target.entityKind == null || action.entityId != null || problem.operationId != null)) {
                                    if (index == 0) Button(onClick = { openRemediation(problem, action) }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                                    else OutlinedButton(onClick = { openRemediation(problem, action) }, modifier = Modifier.fillMaxWidth()) { Text(action.label) }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { DustSectionHeader("Evidence health", subtitle = "Missing, stale, or degraded evidence remains explicit instead of being rendered as a healthy zero.") }
        snapshot?.evidence.orEmpty().forEach { evidence ->
            item(key = "evidence:${evidence.subject}") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(evidence.subject.replace('-', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Text(evidence.health.uppercase(), style = MaterialTheme.typography.labelMedium)
                        }
                        Text("Generation ${evidence.generation}", style = MaterialTheme.typography.bodySmall)
                        evidence.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (advanced) "Hide advanced diagnostics" else "Show advanced diagnostics")
            }
        }
        if (advanced) {
            item {
                DustSectionHeader(
                    "Advanced diagnostics",
                    subtitle = "Structured durable events and local evidence-only probes. These details support troubleshooting but are not the primary product explanation.",
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricRow("Protocol", snapshot?.protocol ?: "not loaded")
                        MetricRow("Durable events", events.size.toString())
                        MetricRow("Local probes", probeResults.size.toString())
                        MetricRow("Latest export", exportResult?.fileName ?: "none")
                    }
                }
            }
            events.takeLast(25).asReversed().forEach { event ->
                item(key = "event:${event.id}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${event.category} · ${event.name}", fontWeight = FontWeight.SemiBold)
                            Text(event.message, style = MaterialTheme.typography.bodySmall)
                            Text(formatTimestamp(event.timestampMillis / 1000), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            probeResults.forEach { probe ->
                item(key = "probe:${probe.target.name}") {
                    DustStatusNotice(probe.target.title, probe.message, if (probe.status == DebugProbeStatus.Failed) DustNoticeTone.Error else if (probe.status == DebugProbeStatus.Warning) DustNoticeTone.Warning else DustNoticeTone.Success)
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsScreen(
    scanDepthProfile: ScanDepthProfile,
    debugOverlayVisible: Boolean,
    vm: MainViewModel,
    openRoute: (DustSemanticRoute, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Settings",
                detail = "Behavioral preferences live here; runtime, certification, and diagnostics retain their own stable System routes.",
                eyebrow = "System",
            ) {
                DustSectionHeader("Scan depth", subtitle = scanDepthProfile.description)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ScanDepthProfile.entries, key = { it.name }) { profile ->
                        FilterChip(selected = profile == scanDepthProfile, onClick = { vm.selectScanDepthProfile(profile) }, label = { Text(profile.shortTitle) })
                    }
                }
                FilterChip(selected = debugOverlayVisible, onClick = vm::toggleDebugOverlay, label = { Text(if (debugOverlayVisible) "Debug overlay on" else "Debug overlay off") })
            }
        }
        item { DustTaskCard("Runtime", "Privileged backend identity and scan authority.", value = "System", onClick = { openRoute(DustSemanticRoute.SystemRuntime, null) }) }
        item { DustTaskCard("Diagnostics", "Problem graph, evidence generations, probes, and support export.", value = "Evidence", onClick = { openRoute(DustSemanticRoute.SystemDiagnostics, null) }) }
        item { DustTaskCard("Certification", "Release-readiness checks for the current device.", value = "Gate", onClick = { openRoute(DustSemanticRoute.SystemCertification, null) }) }
    }
}

@Composable
private fun SemanticDebugOverlayHud(
    selection: DustRouteSelection,
    wide: Boolean,
    session: ScanSessionEntity?,
    rootState: RootUiState,
    selectedCount: Int,
    scanDataStale: Boolean,
    moduleReport: RootModuleReport?,
    eventCount: Int,
    openDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = openDiagnostics),
        shape = MaterialTheme.shapes.large,
        color = DustModernColors.SurfaceHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Diagnostics overlay", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("${selection.route.routeKey} · ${if (wide) "wide" else "compact"} · scan=${session?.status?.lowercase() ?: "none"}", style = MaterialTheme.typography.labelSmall)
            Text("root=${rootUiStateLabel(rootState)} · stale=$scanDataStale · selected=$selectedCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("modules=${moduleReport?.diagnostic?.status?.name?.lowercase() ?: "unknown"} · events=$eventCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun CleanupReview.semanticManifestId(): String =
    runCatching { JSONObject(goManifestJson.orEmpty()).optString("id") }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: operationId

private fun dustScreenPadding(horizontal: Int = 16, bottom: Int = 128): PaddingValues = PaddingValues(
    start = horizontal.dp,
    top = 16.dp,
    end = horizontal.dp,
    bottom = bottom.dp,
)

private fun dustSheetPadding(): PaddingValues = PaddingValues(
    start = 20.dp,
    top = 20.dp,
    end = 20.dp,
    bottom = 96.dp,
)


private data class DisabledActionReason(val title: String, val detail: String)

@Composable
private fun DisabledReasonNotice(reason: DisabledActionReason) {
    DustStatusNotice(
        title = reason.title,
        detail = reason.detail,
        tone = DustNoticeTone.Warning,
    )
}

private fun scanActionDisabledReason(rootReady: Boolean, rootFailure: String?): DisabledActionReason? =
    if (!rootReady) DisabledActionReason(
        title = "Scan action disabled",
        detail = rootFailure ?: "Root identity is not available yet. Retry root diagnostics before scanning.",
    ) else null

private fun cleanupExecutionDisabledReason(confirmed: Boolean): DisabledActionReason? = when {
    !confirmed -> DisabledActionReason(
        title = "Confirmation required",
        detail = "Check the irreversible-deletion confirmation before executing the reviewed plan.",
    )
    else -> null
}


@Composable
private fun SharedRouteLoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Loading route state", style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Waiting for the bound Go response. Older route/entity responses cannot populate this screen.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SharedRouteEmptyScreen(
    state: DustScreenState.Empty,
    onRecovery: (DustRemediationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DustStatusNotice(title = state.reasonCode, detail = state.message, tone = DustNoticeTone.Warning)
        Button(onClick = { onRecovery(state.primaryRecovery) }) { Text(state.primaryRecovery.label) }
    }
}

@Composable
private fun SharedRouteBlockedScreen(
    state: DustScreenState.Blocked,
    onRemediation: (DustRemediationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remediation = state.actionGates
        .filterNot { it.allowed }
        .flatMap { it.remediationActions }
        .distinctBy { "${it.code}:${it.routeKey}" }
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DustStatusNotice(title = "Blocked · ${state.reasonCode}", detail = state.message, tone = DustNoticeTone.Warning)
        remediation.forEachIndexed { index, action ->
            if (index == 0) Button(onClick = { onRemediation(action) }) { Text(action.label) }
            else OutlinedButton(onClick = { onRemediation(action) }) { Text(action.label) }
        }
    }
}

@Composable
private fun SharedRouteErrorScreen(
    state: DustScreenState.Error,
    retry: () -> Unit,
    openDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DustStatusNotice(title = "Unable to load route", detail = "${state.message} (${state.problemRef})", tone = DustNoticeTone.Error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = retry) { Text("Retry") }
            OutlinedButton(onClick = openDiagnostics) { Text("Diagnostics") }
        }
    }
}

@Composable
private fun ScanDepthSelector(
    selected: ScanDepthProfile,
    onSelect: (ScanDepthProfile) -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DustSectionHeader(
                "Depth policy",
                subtitle = "Depth caps are adjustable and forgiving by default. Unlimited depth still keeps an entry cap so the UI stays responsive.",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanDepthProfile.entries, key = { it.name }) { profile ->
                    FilterChip(
                        selected = selected == profile,
                        onClick = { onSelect(profile) },
                        label = { Text(profile.shortTitle) },
                    )
                }
            }
            DustStatusNotice(
                title = "${selected.title}: ${ScanDepthPolicy.label(selected.maxDepth)}",
                detail = "${selected.description} Entry cap: ${selected.maxEntries}. Depth-cap messages stay informational unless zero-file, truncation, warning, or error evidence appears.",
                tone = DustNoticeTone.Success,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanLauncherSheet(
    root: RootUiState,
    rootInfo: RootServiceClient.RootServiceInfo?,
    rootFailure: String?,
    session: ScanSessionEntity?,
    diagnostics: ScanDiagnostics?,
    pathPreflight: RootServiceClient.ScanRootPreflight?,
    vm: MainViewModel,
    scanDepthProfile: ScanDepthProfile,
    launchState: ScanLaunchState,
    requestNotificationPermission: () -> Unit,
    close: () -> Unit,
    openHome: () -> Unit,
) {
    var custom by rememberSaveable(session?.rootPath) { mutableStateOf(session?.rootPath ?: "/data/data/com.termux") }
    val rootReady = root == RootUiState.Available
    fun dismiss() { vm.resetScanLaunch(); close() }
    ModalBottomSheet(onDismissRequest = ::dismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = dustSheetPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DustBottomSheetHeader(
                    title = when (launchState.stage) {
                        ScanLaunchStage.ChooseScope -> "Prepare a scan"
                        ScanLaunchStage.Preflight -> "Checking scan scope"
                        ScanLaunchStage.Review -> "Review scan request"
                        ScanLaunchStage.Starting -> "Starting scan"
                    },
                    detail = "Every user-started scan follows scope → privileged preflight → review → explicit Start. Choosing a preset never starts work immediately.",
                    onClose = ::dismiss,
                )
            }
            item { ScanRootStatusCard(root, rootInfo, rootFailure, vm::refreshRoot) }

            if (launchState.stage == ScanLaunchStage.ChooseScope) {
                item { ScanDepthSelector(scanDepthProfile, vm::selectScanDepthProfile) }
                item { DustSectionHeader("Choose scope", subtitle = "Preset and custom paths only prepare a request. Preflight must complete before Start becomes available.") }
                items(vm.presets) { preset ->
                    DustTaskCard(
                        title = preset.title,
                        detail = preset.description,
                        value = preset.path,
                        onClick = if (rootReady) { { vm.preparePresetScan(preset) } } else null,
                        selected = custom == preset.path,
                        leading = { Text(preset.title.take(1).ifBlank { "•" }, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Card {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Custom root", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), label = { Text("Absolute path") }, singleLine = true)
                            Button(
                                onClick = { vm.prepareCustomScan(custom) },
                                enabled = rootReady && custom.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Review this scope") }
                            scanActionDisabledReason(rootReady, rootFailure)?.let(::DisabledReasonNotice)
                        }
                    }
                }
            } else {
                launchState.request?.let { request ->
                    item {
                        Card {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DustSectionHeader("Requested scan", subtitle = launchState.scopeLabel ?: "Selected scope")
                                Detail("Path", request.rootPath)
                                Detail("Depth", ScanDepthPolicy.label(request.maxDepth))
                                Detail("Entry cap", request.maxEntries.toString())
                                Detail("Hidden files", if (request.includeHidden) "Included" else "Excluded")
                            }
                        }
                    }
                }
                when (launchState.stage) {
                    ScanLaunchStage.Preflight -> item {
                        DustStatusNotice("Running privileged preflight", "DustUI is verifying root identity, canonical path access, mount information, and the Go scan.start action gate before review.", DustNoticeTone.Warning)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    ScanLaunchStage.Review -> {
                        pathPreflight?.let { preflight -> item { ScanPreflightEvidence(preflight) } }
                        launchState.decision?.let { decision ->
                            item {
                                DustStatusNotice(
                                    title = if (decision.allowed) "Ready for explicit Start" else "Scan cannot start · ${decision.reasonCode}",
                                    detail = decision.message,
                                    tone = if (decision.allowed) DustNoticeTone.Success else DustNoticeTone.Error,
                                )
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = vm::resetScanLaunch, modifier = Modifier.weight(1f)) { Text("Change scope") }
                                Button(
                                    onClick = {
                                        requestNotificationPermission()
                                        vm.confirmReviewedScan()
                                        close()
                                        openHome()
                                    },
                                    enabled = launchState.canStart,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Start") }
                            }
                        }
                    }
                    ScanLaunchStage.Starting -> item {
                        DustStatusNotice("Starting reviewed scan", "The reviewed request has been handed to the durable scan operation. Progress continues in Activity.", DustNoticeTone.Success)
                    }
                    ScanLaunchStage.ChooseScope -> Unit
                }
            }
            diagnostics?.let { details -> item { ScanLatestDiagnosticsNotice(details) } }
            session?.takeIf { launchState.stage == ScanLaunchStage.ChooseScope }?.let { latest ->
                item {
                    SessionCard(latest, diagnostics, vm::cancelActive)
                    OutlinedButton(onClick = { dismiss(); openHome() }, modifier = Modifier.fillMaxWidth()) { Text("Open latest scan") }
                }
            }
        }
    }
}

@Composable
private fun ScanRootStatusCard(
    root: RootUiState,
    rootInfo: RootServiceClient.RootServiceInfo?,
    rootFailure: String?,
    retry: () -> Unit,
) {
    val ready = root == RootUiState.Available
    val title = when (root) {
        RootUiState.Checking -> "Checking root identity"
        RootUiState.Available -> "Root identity verified"
        RootUiState.Unavailable -> "Root access unavailable"
    }
    val detail = when (root) {
        RootUiState.Checking -> "Waiting for the privileged service before scanning."
        RootUiState.Available -> "UID ${rootInfo?.uid ?: 0} via ${rootInfo?.backend ?: "root"}. Scanner ${rootInfo?.scanner?.ifBlank { "unavailable" } ?: "unavailable"}."
        RootUiState.Unavailable -> rootFailure ?: "The privileged service is unavailable. Scans cannot start until this is fixed."
    }
    DustStatusNotice(
        title = title,
        detail = detail,
        tone = if (ready) DustNoticeTone.Success else DustNoticeTone.Error,
        actionLabel = if (ready) null else "Retry",
        onAction = if (ready) null else retry,
    )
    rootInfo?.let { info ->
        MetricGrid(
            listOf(
                "Root identity" to "UID ${info.uid}, GID ${info.gid}",
                "Backend" to info.backend,
                "Scanner" to info.scanner.ifBlank { "Unavailable" },
                "SELinux" to when (info.selinuxEnforcing) { true -> "Enforcing"; false -> "Permissive"; null -> "Unknown" },
            ),
        )
    }
}

@Composable
private fun ScanPreflightEvidence(preflight: RootServiceClient.ScanRootPreflight) {
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DustStatusNotice(
                title = if (preflight.ok) "Path is ready" else "Fatal preflight failure",
                detail = preflight.message,
                tone = if (preflight.ok) DustNoticeTone.Success else DustNoticeTone.Error,
            )
            Detail("Diagnostic code", preflight.code + (preflight.errno?.let { " · errno $it" }.orEmpty()))
            Detail("Requested path", preflight.requestedPath)
            preflight.canonicalPath?.let { Detail("Canonical path", it) }
            Detail("Path access", "exists=${preflight.exists} · directory=${preflight.directory} · opened=${preflight.opened}")
            preflight.mountPoint?.let { Detail("Mount", "$it · ${preflight.filesystem ?: "unknown"} · ${preflight.mountOptions ?: "options unavailable"}") }
        }
    }
}

@Composable
private fun ScanLatestDiagnosticsNotice(diagnostics: ScanDiagnostics) {
    val zeroFiles = diagnostics.completedAt != null && diagnostics.emittedFiles == 0L
    val tone = when {
        diagnostics.hasFailure -> DustNoticeTone.Error
        zeroFiles || diagnostics.truncated || diagnostics.warningCount > 0 -> DustNoticeTone.Warning
        diagnostics.depthLimitedDirectories > 0 -> DustNoticeTone.Success
        else -> DustNoticeTone.Success
    }
    DustStatusNotice(
        title = when {
            diagnostics.hasFailure -> "Latest scan has failure evidence"
            zeroFiles -> "Zero-file scan warning"
            diagnostics.truncated -> "Latest scan was truncated"
            diagnostics.depthLimitedDirectories > 0 -> "Latest scan noted depth-cap boundaries"
            diagnostics.warningCount > 0 -> "Latest scan has persisted warnings"
            else -> "Latest scan diagnostics are clean"
        },
        detail = "${diagnostics.emittedFiles} files · ${diagnostics.emittedDirectories} directories · ${diagnostics.warningCount} warnings · ${diagnostics.errorCount} errors",
        tone = tone,
    )
}

@Composable
private fun Explorer(
    modifier: Modifier,
    session: ScanSessionEntity,
    diagnostics: ScanDiagnostics?,
    path: String,
    entries: List<ScanEntryEntity>,
    page: com.mikeyphw.dustui.data.ScanBrowsePage,
    filter: ExplorerFilter,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    openRootSearch: () -> Unit,
    initialTreemap: Boolean = false,
    onTreemapModeChanged: (Boolean) -> Unit = {},
    openEntryDetails: (ScanEntryEntity) -> Unit = vm::select,
) {
    var treemapMode by rememberSaveable(path, initialTreemap) { mutableStateOf(initialTreemap) }
    var showLargestChart by rememberSaveable(path) { mutableStateOf(false) }
    var wholeScanHint by rememberSaveable(session.id, path) { mutableStateOf(false) }
    val selectionMode = selectedPaths.isNotEmpty()
    val chartEntries = entries.filter { it.isDirectory }.ifEmpty { entries }.take(6)
    val typeOptions = listOf("All", "Folders", "Files")

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = dustScreenPadding(horizontal = 0, bottom = if (selectionMode) 208 else 128),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SessionCard(session, diagnostics, vm::cancelActive) }
            item {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = vm::up) { Text("Up") }
                            PathText(path, Modifier.weight(1f))
                        }
                        Breadcrumbs(session.rootPath, path, vm::navigate)
                    }
                }
            }
            item {
                Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                filter.query,
                                vm::updateQuery,
                                Modifier.weight(1f),
                                label = { Text("Current folder search") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            )
                            OutlinedButton(onClick = { wholeScanHint = !wholeScanHint; if (wholeScanHint) openRootSearch() }) {
                                Text(if (wholeScanHint) "Whole scan" else "This folder")
                            }
                        }
                        Text(
                            if (wholeScanHint) "Whole-scan search opens the persisted scan index and jumps results back into Files." else "This folder filters only direct children. Use Whole scan for indexed paths outside this folder.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DustFilterChips(
                            options = typeOptions,
                            selected = entryTypeLabel(filter.type),
                            onSelected = { selected ->
                                vm.selectEntryType(
                                    when (selected) {
                                        "Folders" -> EntryTypeFilter.DIRECTORIES
                                        "Files" -> EntryTypeFilter.FILES
                                        else -> EntryTypeFilter.ALL
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyRow(
                            Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(end = 56.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item { AssistChip(onClick = vm::cycleSort, label = { Text("Sort: ${entrySortLabel(filter.sort)}") }) }
                            item { SegmentedChoice("List", "Treemap", treemapMode, onFirst = { treemapMode = false; onTreemapModeChanged(false) }, onSecond = { treemapMode = true; onTreemapModeChanged(true) }) }
                            item { FilterChip(selected = showLargestChart, onClick = { showLargestChart = !showLargestChart }, label = { Text("Chart") }) }
                            item { AssistChip(onClick = openRootSearch, label = { Text("Whole-scan search") }) }
                            filter.category?.let { category -> item { AssistChip(onClick = { vm.filterCategory(null) }, label = { Text(category) }, trailingIcon = { Text("×") }) } }
                            item { Text("${page.totalCount} items", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
            if (showLargestChart && chartEntries.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Largest children", style = MaterialTheme.typography.titleSmall)
                            StorageBars(chartEntries.map { ChartSlice(it.name, it.sizeBytes, it.fileCount.toInt()) }) { label ->
                                chartEntries.firstOrNull { it.name == label }?.let(vm::open)
                            }
                        }
                    }
                }
            }
            if (treemapMode && entries.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Treemap · tap a block to open", style = MaterialTheme.typography.titleSmall)
                            StorageTreemap(entries) { entry ->
                                if (entry.isDirectory) vm.open(entry) else openEntryDetails(entry)
                            }
                            Text("Every visual block keeps a matching accessible row. Exact sizes remain available in the list and details sheet.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                if (entries.isEmpty()) {
                    item { EmptyState("No matching direct children", "Change filters, clear the category chip, or search the whole scan index.", Modifier.padding(horizontal = 12.dp)) }
                } else {
                    items(entries, key = { it.path }) { entry ->
                        EntryRow(
                            entry = entry,
                            total = session.totalBytes,
                            selectionMode = selectionMode,
                            selected = entry.path in selectedPaths,
                            open = { if (entry.isDirectory) vm.open(entry) else openEntryDetails(entry) },
                            details = { openEntryDetails(entry) },
                            toggle = { vm.toggleSelection(entry) },
                        )
                    }
                }
            }
            if (page.pageCount > 1) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = vm::previousExplorerPage, enabled = page.hasPrevious) { Text("Previous") }
                        Text("Page ${page.pageIndex + 1} / ${page.pageCount}", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = vm::nextExplorerPage, enabled = page.hasMore) { Text("Next") }
                    }
                }
            }
        }
        if (selectionMode) {
            SelectionActionBar(
                selectedCount = selectedPaths.size,
                clear = vm::clearSelection,
                review = vm::reviewCleanup,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun entryTypeLabel(type: EntryTypeFilter): String = when (type) {
    EntryTypeFilter.ALL -> "All"
    EntryTypeFilter.DIRECTORIES -> "Folders"
    EntryTypeFilter.FILES -> "Files"
}

private fun entrySortLabel(sort: EntrySort): String = when (sort) {
    EntrySort.SIZE -> "Size ↓"
    EntrySort.NAME -> "Name A–Z"
    EntrySort.MODIFIED -> "Modified ↓"
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    clear: () -> Unit,
    review: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DustActionDock(
        title = "$selectedCount selected",
        detail = "Current-scan fingerprints will be rechecked before deletion.",
        actionLabel = "Review",
        onAction = review,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        secondaryAction = { TextButton(onClick = clear) { Text("Clear") } },
    )
}

@Composable
private fun Breadcrumbs(root: String, path: String, navigate: (String) -> Unit) {
    val relative = path.removePrefix(root).trim('/')
    val parts = if (relative.isBlank()) emptyList() else relative.split('/')
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        item {
            AssistChip(
                onClick = { navigate(root) },
                label = { Text(if (relative.isBlank()) "Scan root" else root.substringAfterLast('/').ifBlank { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
        parts.forEachIndexed { index, part ->
            item(key = "$index:$part") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AssistChip(
                        onClick = { navigate(root.trimEnd('/') + "/" + parts.take(index + 1).joinToString("/")) },
                        label = { Text(part, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: ScanEntryEntity,
    total: Long,
    selectionMode: Boolean,
    selected: Boolean,
    open: () -> Unit,
    details: () -> Unit,
    toggle: () -> Unit,
) {
    val share = if (total > 0) (entry.sizeBytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = open),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { toggle() })
                Text(if (entry.isDirectory) "▦" else "▧", color = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(if (entry.isDirectory) "Folder" else "File")
                            append(" · ").append(entry.fileCount).append(" files")
                            append(" · ").append(formatTimestamp(entry.modifiedAt))
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(entry.sizeBytes), fontWeight = FontWeight.SemiBold)
                    Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator({ share }, Modifier.fillMaxWidth())
            PathText(entry.path, Modifier.fillMaxWidth(), maxLines = 1)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                TextButton(onClick = toggle) { Text(if (selected) "Unselect" else "Select") }
                TextButton(onClick = details) { Text("Details") }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ScanSessionEntity, diagnostics: ScanDiagnostics?, cancel: () -> Unit) {
    val running = session.status in setOf(ScanStatus.STARTING.name, ScanStatus.RUNNING.name)
    val latestProblem = diagnostics?.issues?.lastOrNull { it.severity == ScanDiagnosticSeverity.ERROR }
        ?: diagnostics?.issues?.lastOrNull { it.severity == ScanDiagnosticSeverity.WARNING }
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PathText(session.rootPath)
                    val countSummary = diagnostics?.let {
                        "${it.emittedFiles} files · ${it.emittedDirectories} directories · ${formatBytes(session.scannedBytes)}"
                    } ?: "${session.scannedItems} scanned items · ${formatBytes(session.scannedBytes)}"
                    Text(
                        "${session.status.lowercase()} · $countSummary",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (running) OutlinedButton(onClick = cancel) { Text("Cancel") }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            session.errorMessage?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Scan failed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            latestProblem?.let { problem ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (problem.severity == ScanDiagnosticSeverity.ERROR) "Failure diagnostic" else "Scan warning",
                            fontWeight = FontWeight.SemiBold,
                            color = if (problem.severity == ScanDiagnosticSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(problem.message, style = MaterialTheme.typography.bodySmall)
                        problem.path?.let { PathText(it, maxLines = 2) }
                        Text(
                            buildString {
                                append(problem.code)
                                append(" · ").append(problem.phase)
                                problem.errno?.let { append(" · errno ").append(it) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            diagnostics?.let { details ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(session.warningCount).append(" warnings")
                            if (details.truncated) append(" · results truncated")
                            if (details.depthLimitedDirectories > 0) append(" · depth cap info")
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide diagnostics" else "Diagnostics") }
                }
                if (expanded) {
                    HorizontalDivider()
                    Detail("Root identity", "UID ${details.rootUid}, GID ${details.rootGid}, PID ${details.rootPid}")
                    Detail("Backend", details.rootBackend)
                    Detail("Scanner", details.scannerVersion.ifBlank { "Unavailable" })
                    Detail("SELinux", buildString {
                        append(when (details.selinuxEnforcing) { true -> "Enforcing"; false -> "Permissive"; null -> "Unknown" })
                        details.selinuxContext?.let { append(" · ").append(it) }
                    })
                    Detail("Mount namespace", details.mountNamespace ?: "Unavailable")
                    Detail("Root preflight", if (details.preflightOk && details.rootOpened) "Opened successfully" else "Failed")
                    details.canonicalRoot?.let { Detail("Canonical root", it) }
                    details.mountPoint?.let { mount -> Detail("Mount", "$mount · ${details.filesystem ?: "unknown"} · ${details.mountOptions ?: "options unavailable"}") }
                    Detail("Emitted", "${details.emittedFiles} files · ${details.emittedDirectories} directories")
                    Detail("Traversal limits", "${details.depthLimitedDirectories} depth-capped director${if (details.depthLimitedDirectories == 1L) "y" else "ies"} · ${details.skippedFilesystemEntries} filesystem skips")
                    if (details.issues.isNotEmpty()) {
                        DustSectionHeader("Recorded notices", subtitle = "Exact scanner and root-service diagnostics retained for this scan.")
                        details.issues.takeLast(8).forEach { issue ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text("${issue.severity.name.lowercase()} · ${issue.code}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(issue.message, style = MaterialTheme.typography.bodySmall)
                                issue.path?.let { PathText(it, maxLines = 2) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetails(
    entry: ScanEntryEntity,
    info: EntryIntelligence?,
    root: RootPathInspection?,
    close: () -> Unit,
    reveal: () -> Unit,
    delete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = close) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = dustSheetPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(entry.name, style = MaterialTheme.typography.headlineSmall)
                PathText(entry.path, maxLines = 3)
            }
            item {
                Detail("Size", formatBytes(entry.sizeBytes))
                Detail("Allocated", formatBytes(entry.allocatedBytes))
                Detail("Type", if (entry.isDirectory) "Directory" else "File")
                Detail("Files", entry.fileCount.toString())
                Detail("Modified", formatTimestamp(entry.modifiedAt))
            }
            info?.let {
                item {
                    HorizontalDivider()
                    DustSectionHeader("Android intelligence")
                    Detail("Category", it.category.label)
                    Detail("Package", it.packageName ?: "Not resolved")
                    it.appLabel?.let { label -> Detail("App", label) }
                    Detail("Mount", it.mountPoint ?: "Unknown")
                    Detail("Filesystem", it.filesystem ?: "Unknown")
                    Detail("Mount mode", if (it.readOnly) "Read-only" else "Writable")
                }
            }
            item {
                HorizontalDivider()
                DustSectionHeader("Root metadata")
                if (root == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (!root.ok) {
                    Text(root.message ?: "Unable to inspect path")
                } else {
                    Detail("Permissions", root.permissions)
                    Detail("UID / GID", "${root.uid} / ${root.gid}")
                    Detail("Inode", root.inode.toString())
                    Detail("SELinux", root.selinuxContext ?: "Unavailable")
                    root.symlinkTarget?.let { Detail("Symlink", it) }
                    Detail("Mount source", root.mountSource ?: "Unknown")
                    Detail("Filesystem", root.filesystem ?: "Unknown")
                    Detail("Read-only", if (root.readOnly) "Yes" else "No")
                    if (root.bindMount || root.overlayMount) {
                        Detail("Mount flags", listOfNotNull("bind".takeIf { root.bindMount }, "overlay".takeIf { root.overlayMount }).joinToString())
                    }
                }
            }
            root?.preview?.let { preview ->
                item {
                    HorizontalDivider()
                    DustSectionHeader("Inline preview${if (root.previewTruncated) " · truncated" else ""}")
                    Surface(tonalElevation = 1.dp) {
                        PathText(preview, Modifier.fillMaxWidth().padding(12.dp), maxLines = 24)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = reveal, Modifier.weight(1f)) { Text("Show in Explorer") }
                    Button(onClick = delete, Modifier.weight(1f)) { Text("Delete…") }
                }
                TextButton(onClick = close, Modifier.fillMaxWidth()) { Text("Close") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    MetricRow(label, value, Modifier.padding(vertical = 2.dp))
}

@Composable
private fun ActionablePathCard(
    path: String,
    name: String,
    sizeBytes: Long?,
    metadata: String,
    selected: Boolean,
    toggleSelection: () -> Unit,
    showDetails: () -> Unit,
    reveal: () -> Unit,
    delete: () -> Unit,
    modifier: Modifier = Modifier,
    deletionEnabled: Boolean = true,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Text(name.ifBlank { path }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                sizeBytes?.let { Text(formatBytes(it), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
            Text(metadata, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PathText(path, Modifier.fillMaxWidth(), maxLines = 3)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { toggleSelection() }, enabled = deletionEnabled)
                Text(
                    when {
                        !deletionEnabled -> "Run a new scan before selecting for deletion"
                        selected -> "Selected for batch deletion"
                        else -> "Select for batch deletion"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = showDetails, Modifier.weight(1f)) { Text("Details") }
                OutlinedButton(onClick = reveal, Modifier.weight(1f)) { Text("Explorer") }
            }
            Button(onClick = delete, enabled = deletionEnabled, modifier = Modifier.fillMaxWidth()) { Text("Delete permanently…") }
        }
    }
}

@Composable
private fun StorageCompositionActions(
    slice: ChartSlice,
    sourceBuckets: List<StorageBucket>,
    enabled: Boolean,
    viewFiles: () -> Unit,
    selectAll: () -> Unit,
    planDeletion: () -> Unit,
    viewCategory: (StorageBucket) -> Unit,
) {
    Surface(tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${slice.label} actions", fontWeight = FontWeight.SemiBold)
            Text(
                "${formatBytes(slice.bytes)} across ${slice.count} indexed files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewFiles, Modifier.fillMaxWidth()) { Text("View files") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = selectAll, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Select all") }
                Button(onClick = planDeletion, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Plan deletion") }
            }
            if (slice.sourceLabels.size > 1) {
                HorizontalDivider()
                Text(
                    "Other includes ${slice.sourceLabels.size} categories. Open one category or use the actions above for the full group.",
                    style = MaterialTheme.typography.bodySmall,
                )
                sourceBuckets.sortedByDescending { it.bytes }.forEach { bucket ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(bucket.label, fontWeight = FontWeight.Medium)
                            Text(
                                "${formatBytes(bucket.bytes)} · ${bucket.count} files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewCategory(bucket) }) { Text("View") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilesSheet(
    selection: CategoryFileSelection,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    deletionEnabled: Boolean,
    close: () -> Unit,
    reveal: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DustSectionHeader(
                "${selection.title} files",
                subtitle = "${selection.entries.size} indexed files · ${formatBytes(selection.entries.sumOf { it.sizeBytes })}",
            )
            if (selection.categories.size > 1) {
                Text(
                    selection.categories.sorted().joinToString(prefix = "Includes: "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!deletionEnabled) {
                Text(
                    "These results are stale after deletion. Viewing is available, but deletion actions require a new scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.selectCategoryPaths(selection.categories) },
                    enabled = deletionEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Select all") }
                Button(
                    onClick = { close(); vm.reviewCategoryPaths(selection.categories) },
                    enabled = deletionEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Plan deletion") }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(selection.entries, key = { it.path }) { entry ->
                    ActionablePathCard(
                        path = entry.path,
                        name = entry.name,
                        sizeBytes = entry.sizeBytes,
                        metadata = AdvancedStorageAnalyzer.fileCategory(entry.name),
                        selected = entry.path in selectedPaths,
                        toggleSelection = { vm.togglePathSelection(entry.path) },
                        showDetails = { close(); vm.showPathDetails(entry.path) },
                        reveal = { reveal(entry.path) },
                        delete = { close(); vm.reviewPaths(listOf(entry.path)) },
                        deletionEnabled = deletionEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun LargeFileWorkbenchScreen(
    modifier: Modifier,
    page: LargeFilePage,
    loading: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = dustScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustSectionHeader(
                "Large file workbench",
                subtitle = "Filter and group current-scan files, then inspect or batch-plan permanent deletion.",
            )
        }
        item {
            MetricGrid(
                listOf(
                    "Matches" to page.totalMatches.toString(),
                    "Matched size" to formatBytes(page.totalBytes),
                    "Page" to if (page.pageCount == 0) "0 / 0" else "${page.pageIndex + 1} / ${page.pageCount}",
                    "Page files" to page.pageItems.size.toString(),
                ),
            )
        }
        if (loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (scanDataStale) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "Results are stale after deletion. Browsing remains available, but selection and deletion planning require a fresh scan.",
                        Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = page.filter.query,
                onValueChange = vm::updateLargeFileQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find by name, path, or package") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            Text("Presets", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                items(LargeFilePreset.entries, key = { it.name }) { preset ->
                    FilterChip(
                        selected = page.filter.preset == preset,
                        onClick = { vm.applyLargeFilePreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
        item {
            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                item {
                    FilterChip(
                        selected = page.filter.category == null,
                        onClick = { vm.filterLargeFilesByCategory(null) },
                        label = { Text("All") },
                    )
                }
                items(page.categories, key = { it }) { category ->
                    FilterChip(
                        selected = page.filter.category == category,
                        onClick = { vm.filterLargeFilesByCategory(category) },
                        label = { Text(category) },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::cycleLargeFileSort, Modifier.weight(1f)) {
                    Text("Sort: ${page.filter.sort.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = vm::cycleLargeFileGroup, Modifier.weight(1f)) {
                    Text("Group: ${page.filter.group.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (page.pageItems.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Current page",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${page.pageItems.size} file(s) · ${formatBytes(page.pageItems.sumOf { it.entry.sizeBytes })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = vm::selectLargeFilePage,
                                enabled = true,
                                modifier = Modifier.weight(1f),
                            ) { Text("Select page") }
                            Button(
                                onClick = vm::reviewLargeFilePage,
                                enabled = true,
                                modifier = Modifier.weight(1f),
                            ) { Text("Plan page") }
                        }
                    }
                }
            }
        }
        if (page.pageItems.isEmpty() && !loading) {
            item {
                EmptyState(
                    "No files match",
                    "Change the preset, category, or search query. Directories are intentionally excluded.",
                )
            }
        } else {
            page.sections.forEach { section ->
                item(key = "large-section:${section.label}") {
                    DustSectionHeader(
                        section.label,
                        subtitle = "${section.items.size} file(s) · ${formatBytes(section.totalBytes)} on this page",
                    )
                }
                items(section.items, key = { "large:${it.entry.path}" }) { item ->
                    ActionablePathCard(
                        path = item.entry.path,
                        name = item.entry.name,
                        sizeBytes = item.entry.sizeBytes,
                        metadata = largeFileMetadata(item),
                        selected = item.entry.path in selectedPaths,
                        toggleSelection = { vm.togglePathSelection(item.entry.path) },
                        showDetails = { vm.showPathDetails(item.entry.path) },
                        reveal = { revealPath(item.entry.path) },
                        delete = { vm.reviewPaths(listOf(item.entry.path)) },
                        deletionEnabled = true,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = vm::previousLargeFilePage,
                        enabled = page.hasPrevious,
                        modifier = Modifier.weight(1f),
                    ) { Text("Previous") }
                    Text(
                        "${page.pageIndex + 1} / ${page.pageCount}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(
                        onClick = vm::nextLargeFilePage,
                        enabled = page.hasNext,
                        modifier = Modifier.weight(1f),
                    ) { Text("Next") }
                }
            }
        }
    }
}

private fun largeFileMetadata(item: LargeFileItem): String = buildList {
    add(item.category)
    item.ageDays?.let { add("${it}d old") }
    item.packageName?.let { add(it) }
    if (item.extension.isNotBlank()) add(".${item.extension}")
}.joinToString(" · ")

@Composable
private fun DuplicateWorkbenchScreen(
    modifier: Modifier,
    analysis: DuplicateAnalysisSnapshot?,
    page: DuplicatePage,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = dustScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustSectionHeader(
                "Duplicate file workbench",
                subtitle = "Size buckets narrow candidates, edge fingerprints reduce work, and complete SHA-256 hashes prove exact content matches.",
            )
        }
        if (scanDataStale) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "Results are stale after deletion. Run a new scan before hashing, selecting, or deleting duplicate copies.",
                        Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            MetricGrid(
                listOf(
                    "Verified groups" to (analysis?.groups?.size ?: 0).toString(),
                    "Safe savings" to formatBytes(analysis?.safeReclaimableBytes ?: 0L),
                    "Checked files" to (analysis?.checkedFiles ?: 0).toString(),
                    "Progress" to duplicateProgressLabel(analysis),
                ),
            )
        }
        item {
            Text("Minimum file size", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                items(DuplicateThreshold.entries, key = { it.name }) { threshold ->
                    FilterChip(
                        selected = page.filter.threshold == threshold,
                        onClick = { vm.setDuplicateThreshold(threshold) },
                        enabled = analysis?.running != true,
                        label = { Text(threshold.label) },
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(duplicateStatusTitle(analysis), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        analysis?.message ?: "Run verification to find exact duplicate content in the active scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (analysis?.running == true) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        analysis.currentSizeBytes?.let { Text("Current bucket: ${formatBytes(it)} files", style = MaterialTheme.typography.labelMedium) }
                        OutlinedButton(onClick = vm::cancelDuplicateAnalysis, modifier = Modifier.fillMaxWidth()) { Text("Pause analysis") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.startDuplicateAnalysis(reset = false) },
                                enabled = true,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (analysis?.resumable == true) "Resume" else "Run") }
                            OutlinedButton(
                                onClick = { vm.startDuplicateAnalysis(reset = true) },
                                enabled = true,
                                modifier = Modifier.weight(1f),
                            ) { Text("Restart") }
                        }
                    }
                    if (analysis != null) {
                        Text(
                            "Hashed ${formatBytes(analysis.bytesHashed)} · skipped ${analysis.skippedFiles} · partial groups ${analysis.partialGroups}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (analysis?.groups?.isNotEmpty() == true) {
            item {
                OutlinedTextField(
                    value = page.filter.query,
                    onValueChange = vm::updateDuplicateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Find by path, filename, or hash") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            item {
                Text("Copy to keep", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                ) {
                    items(DuplicateKeepStrategy.entries, key = { it.name }) { strategy ->
                        FilterChip(
                            selected = page.filter.keepStrategy == strategy,
                            onClick = { vm.setDuplicateKeepStrategy(strategy) },
                            label = { Text(strategy.label) },
                        )
                    }
                }
            }
            if (page.filter.keepStrategy == DuplicateKeepStrategy.PREFERRED_DIRECTORY) {
                item {
                    OutlinedTextField(
                        value = page.filter.preferredDirectory,
                        onValueChange = vm::updateDuplicatePreferredDirectory,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Preferred directory to keep") },
                        supportingText = { Text("When no copy is inside this folder, the shortest path is kept.") },
                        singleLine = true,
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${page.items.size} group(s) · ${page.deletionPaths.size} recommended copy or copies · ${formatBytes(page.items.sumOf { it.plannedBytes })}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = vm::selectDuplicatePage,
                                enabled = page.deletionPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Select page") }
                            Button(
                                onClick = vm::reviewDuplicatePage,
                                enabled = page.deletionPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Plan page") }
                        }
                    }
                }
            }
        }
        if (analysis != null && !analysis.running && page.totalGroups == 0) {
            item {
                EmptyState(
                    "No verified duplicates",
                    if (analysis.status == "COMPLETED") "No exact content matches were found for this threshold." else "Run or resume duplicate verification.",
                )
            }
        }
        items(page.items, key = { "duplicate:${it.group.sha256}" }) { item ->
            DuplicateGroupCard(
                item = item,
                selectedPaths = selectedPaths,
                deletionEnabled = true,
                vm = vm,
                revealPath = revealPath,
            )
        }
        if (page.pageCount > 1) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::previousDuplicatePage, enabled = page.hasPrevious, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Text("${page.pageIndex + 1} / ${page.pageCount}", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = vm::nextDuplicatePage, enabled = page.hasNext, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    item: DuplicateGroupItem,
    selectedPaths: Set<String>,
    deletionEnabled: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Exact duplicate group", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${item.group.physicalCopyCount} physical copies · ${item.group.pathRecords.size} paths",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatBytes(item.group.sizeBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            PathText("SHA-256 ${item.group.sha256}", Modifier.fillMaxWidth(), maxLines = 2)
            MetricGrid(
                listOf(
                    "Possible savings" to formatBytes(item.group.reclaimableBytes),
                    "Safely selectable" to formatBytes(item.plannedBytes),
                    "Hard-link copies" to item.hardLinkedCopies.toString(),
                    "Recommended deletes" to item.deletionPaths.size.toString(),
                ),
            )
            item.copies.forEach { copy ->
                DuplicateCopyCard(
                    groupId = item.group.sha256,
                    copy = copy,
                    kept = copy.key == item.keepCopyKey,
                    selectedPaths = selectedPaths,
                    vm = vm,
                    revealPath = revealPath,
                )
            }
            if (item.hardLinkedCopies > 0) {
                Text(
                    "Hard-linked copies are shown but never auto-selected because deleting one link may reclaim no storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.selectDuplicateGroup(item.group.sha256) },
                    enabled = deletionEnabled && item.deletionPaths.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Select recommended") }
                Button(
                    onClick = { vm.reviewDuplicateGroup(item.group.sha256) },
                    enabled = deletionEnabled && item.deletionPaths.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Plan deletion") }
            }
        }
    }
}

@Composable
private fun DuplicateCopyCard(
    groupId: String,
    copy: DuplicatePhysicalCopy,
    kept: Boolean,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    Surface(tonalElevation = if (kept) 3.dp else 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        copy.hardLinked -> "Hard-linked physical copy"
                        copy.deletionBlockReason != null -> "Protected physical copy"
                        kept -> "Copy to keep"
                        else -> "Removable physical copy"
                    },
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                if (copy.primaryPath in selectedPaths) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Modified ${formatTimestamp(copy.modifiedAt)} · ${copy.paths.size} indexed path${if (copy.paths.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            copy.deletionBlockReason?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            copy.paths.forEach { record ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PathText(record.path, Modifier.fillMaxWidth(), maxLines = 3)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.showPathDetails(record.path) }, modifier = Modifier.weight(1f)) { Text("Details") }
                        TextButton(onClick = { revealPath(record.path) }, modifier = Modifier.weight(1f)) { Text("Explorer") }
                    }
                }
            }
            OutlinedButton(
                onClick = { vm.chooseDuplicateKeep(groupId, copy.key) },
                enabled = copy.autoDeleteEligible && !kept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !copy.autoDeleteEligible -> "Retained by safety"
                        kept -> "Kept"
                        else -> "Keep this physical copy"
                    },
                )
            }
        }
    }
}

private fun duplicateProgressLabel(analysis: DuplicateAnalysisSnapshot?): String = when {
    analysis == null -> "Not run"
    analysis.totalBuckets == 0 && analysis.status == "COMPLETED" -> "Complete"
    analysis.totalBuckets == 0 -> analysis.status.lowercase().replaceFirstChar { it.titlecase() }
    else -> "${analysis.completedBuckets} / ${analysis.totalBuckets}"
}

private fun duplicateStatusTitle(analysis: DuplicateAnalysisSnapshot?): String = when (analysis?.status) {
    "RUNNING" -> "Verifying duplicate content"
    "PAUSED" -> "Analysis paused"
    "FAILED" -> "Analysis needs attention"
    "COMPLETED" -> "Analysis complete"
    else -> "Duplicate verification"
}

@Composable
private fun AppResidueWorkbenchScreen(
    modifier: Modifier,
    analysis: AppResidueAnalysis?,
    page: AppResiduePage,
    loading: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    LazyColumn(
        modifier,
        contentPadding = dustScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustSectionHeader(
                "Uninstalled-app residue",
                subtitle = "Evidence-ranked package folders that remain after an app is absent across checked Android users.",
            )
        }
        item {
            val checkedUsers = analysis?.checkedUsers?.sorted()?.joinToString().orEmpty().ifBlank { "Unknown" }
            MetricGrid(
                listOf(
                    "Matches" to page.totalGroups.toString(),
                    "Matched size" to formatBytes(page.totalBytes),
                    "Recommended" to formatBytes(analysis?.recommendedBytes ?: 0L),
                    "Checked users" to checkedUsers,
                ),
            )
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (analysis != null && !analysis.inventoryComplete) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Package visibility is incomplete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text(
                            analysis.message ?: "DustUI could not prove package absence across every Android user. Results remain uncertain and are never batch-selected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (scanDataStale) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "Results are stale after deletion. Inspection remains available, but selection and deletion planning require a fresh scan.",
                        Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = page.filter.query,
                onValueChange = vm::updateResidueQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find package or path") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            Text("Confidence", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                item {
                    FilterChip(
                        selected = page.filter.confidence == null,
                        onClick = { vm.filterResidueConfidence(null) },
                        label = { Text("All") },
                    )
                }
                items(AppResidueConfidence.entries, key = { it.name }) { confidence ->
                    FilterChip(
                        selected = page.filter.confidence == confidence,
                        onClick = { vm.filterResidueConfidence(confidence) },
                        label = { Text(confidence.label) },
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = vm::cycleResidueSort, modifier = Modifier.fillMaxWidth()) {
                Text("Sort: ${page.filter.sort.label}")
            }
        }
        if (page.items.isNotEmpty()) {
            item {
                val recommendedBytes = page.items.sumOf { group ->
                    group.locations.filter { it.path in group.recommendedDeletionPaths }.sumOf { it.sizeBytes }
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${page.items.size} package group(s) · ${page.recommendedPaths.size} high-confidence location(s) · ${formatBytes(recommendedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = vm::selectResiduePage,
                                enabled = page.recommendedPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Select safe") }
                            Button(
                                onClick = vm::reviewResiduePage,
                                enabled = page.recommendedPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Plan safe") }
                        }
                    }
                }
            }
        }
        if (page.items.isEmpty() && !loading) {
            item {
                EmptyState(
                    "No residue matches",
                    "Run a complete scan or change the confidence filter. Active packages are excluded, and name-only folders remain uncertain.",
                )
            }
        }
        items(page.items, key = { "residue:${it.packageName}" }) { group ->
            AppResidueGroupCard(
                group = group,
                selectedPaths = selectedPaths,
                deletionEnabled = true,
                vm = vm,
                revealPath = revealPath,
            )
        }
        if (page.pageCount > 1) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::previousResiduePage, enabled = page.hasPrevious, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Text("${page.pageIndex + 1} / ${page.pageCount}", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = vm::nextResiduePage, enabled = page.hasNext, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun AppResidueGroupCard(
    group: AppResidueGroup,
    selectedPaths: Set<String>,
    deletionEnabled: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(group.appLabel ?: group.packageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (group.appLabel != null) PathText(group.packageName, Modifier.fillMaxWidth(), maxLines = 2)
                }
                StatusPill(group.confidence.label, danger = group.confidence == AppResidueConfidence.UNCERTAIN)
            }
            MetricGrid(
                listOf(
                    "Total size" to formatBytes(group.totalBytes),
                    "Locations" to group.locations.size.toString(),
                    "Indexed files" to group.totalFiles.toString(),
                    "Recommended" to group.recommendedDeletionPaths.size.toString(),
                ),
            )
            if (group.relatedInstalledPackages.isNotEmpty()) {
                Text(
                    "Related installed package(s): ${group.relatedInstalledPackages.joinToString()}. Automatic confidence is reduced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (group.confidence != AppResidueConfidence.HIGH) {
                Text(
                    "This group is never batch-selected. Any deletion must be chosen path by path and will still receive the full permanent-deletion review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.locations.forEach { location ->
                AppResidueLocationCard(location, selectedPaths, deletionEnabled, vm, revealPath)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.selectResidueGroup(group.packageName) },
                    enabled = deletionEnabled && group.recommendedDeletionPaths.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Select safe") }
                Button(
                    onClick = { vm.reviewResidueGroup(group.packageName) },
                    enabled = deletionEnabled && group.recommendedDeletionPaths.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Plan safe") }
            }
        }
    }
}

@Composable
private fun AppResidueLocationCard(
    location: AppResidueLocation,
    selectedPaths: Set<String>,
    deletionEnabled: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(location.kind.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatBytes(location.sizeBytes)} · ${location.fileCount} indexed file(s) · ${formatTimestamp(location.modifiedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (location.path in selectedPaths) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            PathText(location.path, Modifier.fillMaxWidth(), maxLines = 3)
            location.evidence.forEach { evidence ->
                Text(
                    "${evidence.label}: ${evidence.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (evidence.code == "USER_PROTECTED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.showPathDetails(location.path) }, modifier = Modifier.weight(1f)) { Text("Details") }
                TextButton(onClick = { revealPath(location.path) }, modifier = Modifier.weight(1f)) { Text("Explorer") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.togglePathSelection(location.path) },
                    enabled = deletionEnabled && location.deletionEligible,
                    modifier = Modifier.weight(1f),
                ) { Text(if (location.path in selectedPaths) "Unselect" else "Select") }
                Button(
                    onClick = { vm.reviewPaths(listOf(location.path)) },
                    enabled = deletionEnabled && location.deletionEligible,
                    modifier = Modifier.weight(1f),
                ) { Text("Plan deletion") }
            }
            TextButton(
                onClick = {
                    if (location.userProtected) vm.unprotectResiduePath(location.path) else vm.protectResiduePath(location.path)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (location.userProtected) "Remove protection" else "Protect this folder")
            }
        }
    }
}



@Composable
private fun HomeDashboardScreen(
    modifier: Modifier,
    session: ScanSessionEntity?,
    diagnostics: ScanDiagnostics?,
    overviewStorage: OverviewStorageSemantics?,
    activityRuns: List<ActivityRun>,
    delta: SnapshotDelta?,
    history: List<SnapshotPoint>,
    advanced: AdvancedStorageReport?,
    apps: List<AppStorageSummary>,
    recommendationAnalysis: CleanupRecommendationAnalysis?,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    requestNotificationPermission: () -> Unit,
    onStartScan: () -> Unit,
    revealPath: (String) -> Unit,
    openClean: () -> Unit,
    openDuplicates: () -> Unit,
    openLargeFiles: () -> Unit,
    openResidue: () -> Unit,
    openDiagnostics: () -> Unit,
    openActivityRun: (String) -> Unit,
) {
    var selectedType by rememberSaveable(advanced?.typeBuckets?.hashCode()) { mutableStateOf<String?>(null) }
    val potentialCleanupBytes = homePotentialCleanupBytes(recommendationAnalysis, advanced)
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (session == null) {
            item {
                DustHeroCard(
                    title = "Start your first scan",
                    detail = "Pick a root-aware scope to populate results. Files, Clean, Activity, and System stay reachable so you can inspect why data is unavailable.",
                    eyebrow = "Storage health",
                    trailing = { DustPathPill("root-aware") },
                ) {
                    DustStatusNotice(
                        title = "Root diagnostics run before indexing",
                        detail = "DustUI checks root identity, path access, scanner backend, zero-file warnings, and fatal preflight failures before it treats a scan as usable.",
                        tone = DustNoticeTone.Warning,
                    )
                    Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) { Text("Prepare first scan") }
                }
            }
            item {
                DustTaskCard(
                    title = "Termux workspace",
                    detail = "Suggested first scope for verifying private app storage and the /data/data/com.termux zero-file diagnostic path.",
                    value = "/data/data/com.termux",
                    onClick = onStartScan,
                    leading = { Text("T", fontWeight = FontWeight.Bold) },
                )
            }
            item {
                DustTaskCard(
                    title = "Root modules",
                    detail = "Scan /data/adb/modules when module values are unavailable instead of showing silent zeroes.",
                    value = "/data/adb/modules",
                    onClick = onStartScan,
                    leading = { Text("M", fontWeight = FontWeight.Bold) },
                )
            }
        } else {

        item {
            val logicalBytes = overviewStorage?.logicalBytes ?: session.scannedBytes
            val allocatedBytes = overviewStorage?.allocatedBytes
            DustHeroCard(
                title = "${formatBytes(logicalBytes)} logical data indexed",
                detail = homeScanSummary(session, diagnostics, scanDataStale),
                eyebrow = if (scanDataStale) "Stale scan evidence" else "Current scan evidence",
                trailing = { DustPathPill(session.rootPath) },
            ) {
                MetricGrid(
                    listOf(
                        "Logical bytes" to formatBytes(logicalBytes),
                        "Allocated bytes" to (allocatedBytes?.let(::formatBytes) ?: "Unavailable"),
                        "Files indexed" to (overviewStorage?.fileCount ?: session.scannedItems).toString(),
                        "Potential cleanup" to formatBytes(potentialCleanupBytes),
                        "Warnings" to ((diagnostics?.warningCount ?: session.warningCount).toString()),
                        "Errors" to ((diagnostics?.errorCount ?: 0).toString()),
                    ),
                )
                Text(
                    "Scan ${session.id} · generation ${session.generation} · integrity ${overviewStorage?.integrityStatus ?: session.integrityStatus} · ${if (scanDataStale) "stale after mutation" else "fresh"}. Logical bytes are file sizes; allocated bytes are filesystem blocks and are never substituted for logical size.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartScan, modifier = Modifier.weight(1f)) { Text(if (scanDataStale) "Prepare rescan" else "Prepare scan") }
                    OutlinedButton(onClick = openClean, enabled = true, modifier = Modifier.weight(1f)) { Text("Open cleanup") }
                }
            }
        }

        item {
            HomeDiagnosticsNotice(
                session = session,
                diagnostics = diagnostics,
                scanDataStale = scanDataStale,
                onReview = openDiagnostics,
                onRescan = onStartScan,
            )
        }

        activityRuns.firstOrNull { it.active }?.let { active ->
            item {
                DustTaskCard(
                    title = "Active operation · ${active.kind.replace('-', ' ')}",
                    detail = "${active.statusLabel}. Tap to inspect durable progress, evidence, and available recovery actions.",
                    value = active.state,
                    onClick = { openActivityRun(active.operationId) },
                )
            }
        }
        if (activityRuns.isNotEmpty()) {
            item { DustSectionHeader("Recent activity", subtitle = "Durable Go operation truth. Tap any run to open its stable Activity identity.") }
            items(activityRuns.take(3), key = { "overview-activity:${it.operationId}" }) { run ->
                DustTaskCard(
                    title = run.kind.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    detail = "${run.statusLabel}${if (run.recoveryRequired) " · reconcile before continuing" else ""}",
                    value = run.terminalTruth,
                    onClick = { openActivityRun(run.operationId) },
                )
            }
        }

        item {
            DustSectionHeader("Best next actions", subtitle = "Task-first shortcuts ranked by confidence and safety.")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DustTaskCard(
                    title = "Recommended cleanup",
                    detail = if (scanDataStale) "Locked until a fresh scan confirms paths and fingerprints." else "Review verified duplicates and high-confidence residue before permanent deletion.",
                    value = formatBytes(potentialCleanupBytes),
                    selected = false,
                    onClick = openClean,
                    leading = { Text("✦", fontWeight = FontWeight.Bold) },
                )
                DustTaskCard(
                    title = "Exact duplicates",
                    detail = "Open the duplicate workbench with hash-backed keep strategies and protected-copy anchoring.",
                    value = formatBytes(advanced?.duplicateCandidates?.sumOf { it.estimatedSavingsBytes } ?: 0L),
                    selected = false,
                    onClick = openDuplicates,
                    leading = { Text("⧉", fontWeight = FontWeight.Bold) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DustTaskCard(
                        title = "Large files",
                        detail = "Find aged archives, build outputs, and oversized logs.",
                        value = (advanced?.largeOldFiles?.size ?: 0).toString(),
                        onClick = openLargeFiles,
                        leading = { Text("▤", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                    )
                    DustTaskCard(
                        title = "App residue",
                        detail = "Review uninstalled-package residue with evidence-ranked confidence.",
                        value = (advanced?.orphanedPackages?.size ?: 0).toString(),
                        onClick = openResidue,
                        leading = { Text("⌫", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (advanced == null) {
            item {
                DustStatusNotice(
                    title = "Visual analysis is preparing",
                    detail = "Open this page again after the latest scan finishes indexing to see composition, cleanup potential, and trend cards.",
                    tone = DustNoticeTone.Warning,
                )
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    val slices = VisualAnalytics.compactSlices(advanced.typeBuckets)
                    val selectedSlice = slices.firstOrNull { it.label == selectedType }
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DustSectionHeader(
                            "Storage composition",
                            subtitle = "Tap a doughnut slice to open its files, or select a category below for batch actions.",
                        )
                        StorageDonut(
                            slices = slices,
                            modifier = Modifier.fillMaxWidth(),
                            selected = selectedType,
                            onSelect = { slice -> selectedType = slice.label.takeUnless { it == selectedType } },
                            onSliceOpen = { slice ->
                                selectedType = slice.label
                                vm.openCategoryFiles(slice.label, slice.sourceLabels)
                            },
                        )
                        if (scanDataStale) {
                            Text(
                                "This chart is stale after deletion. Run a new scan before planning another category deletion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        selectedSlice?.let { slice ->
                            StorageCompositionActions(
                                slice = slice,
                                sourceBuckets = advanced.typeBuckets.filter { it.label in slice.sourceLabels },
                                enabled = true,
                                viewFiles = { vm.openCategoryFiles(slice.label, slice.sourceLabels) },
                                selectAll = { vm.selectCategoryPaths(slice.sourceLabels) },
                                planDeletion = { vm.reviewCategoryPaths(slice.sourceLabels) },
                                viewCategory = { bucket -> vm.openCategoryFiles(bucket.label, setOf(bucket.label)) },
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DustSectionHeader("Storage snapshots", subtitle = "Growth stays secondary to the main scan → understand → clean flow.")
                        SnapshotLineChart(history, Modifier.fillMaxWidth())
                        if (delta == null) {
                            Text("Complete two scans of the same scope to compare growth.")
                        } else {
                            MetricGrid(
                                listOf(
                                    "Net change" to signedBytes(delta.totalDeltaBytes),
                                    "Added" to formatBytes(delta.addedBytes),
                                    "Removed" to formatBytes(delta.removedBytes),
                                    "Changed" to delta.changedEntries.toString(),
                                ),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { requestNotificationPermission(); vm.scheduleLatest(24) }) { Text("Daily scans") }
                            OutlinedButton(onClick = vm::cancelSchedule) { Text("Disable") }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DustSectionHeader("File age")
                        StorageBars(advanced.ageBuckets.map { ChartSlice(it.label, it.bytes, it.count) })
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DustSectionHeader("Analysis summary")
                        MetricGrid(
                            listOf(
                                "Duplicates" to advanced.duplicateCandidates.size.toString(),
                                "Savings" to formatBytes(advanced.duplicateCandidates.sumOf { it.estimatedSavingsBytes }),
                                "Old files" to advanced.largeOldFiles.size.toString(),
                                "Modules" to advanced.moduleFootprints.size.toString(),
                                "Bind/overlays" to advanced.mountTopology.size.toString(),
                                "Measured apps" to apps.size.toString(),
                            ),
                        )
                        Button(onClick = openDuplicates, enabled = advanced.duplicateCandidates.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text("Open duplicate workbench")
                        }
                    }
                }
            }
            if (advanced.largeOldFiles.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DustSectionHeader(
                                "Large old files",
                                subtitle = if (advanced.largeOldFiles.size > 8) "Showing 8 of ${advanced.largeOldFiles.size} oldest large files." else "Oldest large files from the latest scan.",
                            )
                            advanced.largeOldFiles.take(8).forEach { old ->
                                ActionablePathCard(
                                    path = old.path,
                                    name = old.path.substringAfterLast('/'),
                                    sizeBytes = old.sizeBytes,
                                    metadata = "${old.ageDays} days old",
                                    selected = old.path in selectedPaths,
                                    toggleSelection = { vm.togglePathSelection(old.path) },
                                    showDetails = { vm.showPathDetails(old.path) },
                                    reveal = { revealPath(old.path) },
                                    delete = { vm.reviewPaths(listOf(old.path)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun HomeDiagnosticsNotice(
    session: ScanSessionEntity,
    diagnostics: ScanDiagnostics?,
    scanDataStale: Boolean,
    onReview: () -> Unit,
    onRescan: () -> Unit,
) {
    val zeroFiles = diagnostics?.let { it.completedAt != null && it.emittedFiles == 0L } ?: (session.scannedItems == 0L && session.completedAt != null)
    when {
        scanDataStale -> DustStatusNotice(
            title = "Cleanup changed this scope",
            detail = "Run a new scan before trusting counts, recommendations, or module-source diagnostics.",
            tone = DustNoticeTone.Warning,
            actionLabel = "Prepare rescan",
            onAction = onRescan,
        )
        diagnostics?.hasFailure == true -> DustStatusNotice(
            title = "Latest scan has failure evidence",
            detail = "Root identity, path access, or scanner preflight failed. DustUI keeps stale numbers hidden and exposes persisted evidence.",
            tone = DustNoticeTone.Error,
            actionLabel = "Review",
            onAction = onReview,
        )
        zeroFiles -> DustStatusNotice(
            title = "Zero-file scan is suspicious",
            detail = "The path completed with zero indexed files. Check root identity, path access, scanner output, and truncation evidence before acting.",
            tone = DustNoticeTone.Warning,
            actionLabel = "Review",
            onAction = onReview,
        )
        diagnostics?.truncated == true -> DustStatusNotice(
            title = "Latest scan was truncated",
            detail = "Results are usable for browsing, but cleanup planning should inspect the persisted truncation evidence first.",
            tone = DustNoticeTone.Warning,
            actionLabel = "Review",
            onAction = onReview,
        )
        (diagnostics?.depthLimitedDirectories ?: 0L) > 0L -> DustStatusNotice(
            title = "Depth-cap boundaries noted",
            detail = "The scan remains usable. Depth boundaries are quiet informational evidence by default; choose Forgiving or Deep in the scan sheet to remove or relax depth limits for the next scan.",
            tone = DustNoticeTone.Success,
            actionLabel = "Review evidence",
            onAction = onReview,
        )
        (diagnostics?.warningCount ?: session.warningCount.toInt()) > 0 -> DustStatusNotice(
            title = "Latest scan has persisted warnings",
            detail = "${diagnostics?.warningCount ?: session.warningCount} warning(s) were preserved with the scan instead of disappearing into a snackbar.",
            tone = DustNoticeTone.Warning,
            actionLabel = "Review",
            onAction = onReview,
        )
        else -> DustStatusNotice(
            title = "Scan diagnostics are clean",
            detail = "Root identity and preflight passed. No fatal failure, zero-file warning, truncation, or depth-limit evidence is active.",
            tone = DustNoticeTone.Success,
        )
    }
}

private fun homePotentialCleanupBytes(recommendationAnalysis: CleanupRecommendationAnalysis?, advanced: AdvancedStorageReport?): Long {
    val recommended = recommendationAnalysis?.highConfidenceBytes ?: 0L
    if (recommended > 0L) return recommended
    return advanced?.duplicateCandidates?.sumOf { it.estimatedSavingsBytes } ?: 0L
}

private fun homeScanSummary(session: ScanSessionEntity, diagnostics: ScanDiagnostics?, scanDataStale: Boolean): String = when {
    scanDataStale -> "Results are stale after deletion. A targeted rescan will refresh counts and cleanup recommendations."
    diagnostics?.hasFailure == true -> "Root or scanner diagnostics reported failure evidence for ${session.rootPath}."
    diagnostics?.let { it.completedAt != null && it.emittedFiles == 0L } == true -> "Zero files were indexed; review root identity and path access evidence before acting."
    else -> "Root access verified. ${session.scannedItems} entries indexed from ${session.rootPath}."
}

@Composable
private fun RootSearchScreen(
    modifier: Modifier,
    scanId: String,
    results: List<RootFileSearchResult>,
    page: com.mikeyphw.dustui.data.RootFileSearchPage,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    openResult: (RootFileSearchResult) -> Unit,
) {
    var query by rememberSaveable(scanId) { mutableStateOf("*.apk >10m") }
    var searched by rememberSaveable(scanId) { mutableStateOf(false) }
    fun submitSearch() { searched = true; vm.rootSearch(query) }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustHeroCard(
                title = "Whole-scan search",
                detail = "Search the persisted scan index across every indexed path, then jump matching files back into Files.",
                eyebrow = "Files",
                trailing = { DustPathPill("indexed scope") },
            ) {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Whole scan query") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                )
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 56.dp),
                ) {
                    listOf("media", "*.apk", "ext:log", ">100m", ">1g").forEach { example ->
                        item { AssistChip(onClick = { query = example; submitSearch() }, label = { Text(example) }) }
                    }
                }
                Button(onClick = { submitSearch() }, Modifier.fillMaxWidth()) { Text("Search whole scan") }
            }
        }
        if (results.isEmpty()) {
            item {
                EmptyState(
                    if (searched) "No matches" else "No whole-scan results yet",
                    if (searched) "No indexed paths matched this query. Try a broader name, extension, package, or size filter." else "Run a query to search every indexed path. Results can be revealed in Files or added to deletion review.",
                )
            }
        } else {
            item {
                DustSectionHeader(
                    "Search results",
                    subtitle = "${page.totalCount} indexed matches · page ${page.pageIndex + 1} of ${page.pageCount.coerceAtLeast(1)}",
                )
            }
            items(results, key = { it.path }) { result ->
                ActionablePathCard(
                    path = result.path,
                    name = result.name,
                    sizeBytes = result.sizeBytes,
                    metadata = buildString {
                        append(result.category.label)
                        if (result.isDirectory) append(" · directory")
                    },
                    selected = result.path in selectedPaths,
                    toggleSelection = { vm.togglePathSelection(result.path) },
                    showDetails = { vm.showPathDetails(result.path) },
                    reveal = { openResult(result) },
                    delete = { vm.reviewPaths(listOf(result.path)) },
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::previousRootSearchPage, enabled = page.hasPrevious) { Text("Previous") }
                    Text("Page ${page.pageIndex + 1} / ${page.pageCount.coerceAtLeast(1)}", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = vm::nextRootSearchPage, enabled = page.hasMore) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun ModuleAnalyzerScreen(
    modifier: Modifier,
    moduleReport: RootModuleReport?,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    val report = moduleReport
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DustSectionHeader("Root module analyzer", subtitle = "KernelSU, Magisk, APatch, and generic module footprints from /data/adb/modules.") }
        item { ModuleDiagnosticSummary(report = report, onScanModules = vm::startModuleScan) }
        if (report != null) {
            val modules = report.modules.take(20)
            if (modules.isNotEmpty()) item {
                DustSectionHeader(
                    "Modules by footprint",
                    subtitle = if (report.modules.size > 20) "Showing 20 of ${report.modules.size} modules. Each module exposes up to three actionable files." else "Each module exposes up to three actionable files.",
                )
            }
            modules.forEach { module ->
                item(key = "module:${module.moduleId}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(module.moduleId, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(module.totalBytes), fontWeight = FontWeight.SemiBold)
                            }
                            Text("${module.entryCount} entries · ${module.overlayTargets.size} overlay target(s)${if (module.disabled) " · disabled" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(module.largestPaths.take(3), key = { "${module.moduleId}:${it.path}" }) { source ->
                    ActionablePathCard(
                        path = source.path,
                        name = source.path.substringAfterLast('/'),
                        sizeBytes = source.sizeBytes,
                        metadata = "${module.moduleId} · ${source.category}",
                        selected = source.path in selectedPaths,
                        toggleSelection = { vm.togglePathSelection(source.path) },
                        showDetails = { vm.showPathDetails(source.path) },
                        reveal = { revealPath(source.path) },
                        delete = { vm.reviewPaths(listOf(source.path)) },
                    )
                }
            }

            val conflicts = report.overlayConflicts.take(12)
            if (conflicts.isNotEmpty()) item {
                DustSectionHeader(
                    "Module overlay conflicts",
                    subtitle = if (report.overlayConflicts.size > 12) "Showing 12 of ${report.overlayConflicts.size} conflicts. Actions apply to module source files, never the protected system target." else "Actions apply to module source files, never the protected system target.",
                )
            }
            conflicts.forEach { conflict ->
                item(key = "conflict:${conflict.targetPath}") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Target overlay path", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PathText(conflict.targetPath, Modifier.fillMaxWidth(), maxLines = 3)
                            Text("Modules: ${conflict.moduleIds.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(conflict.sourcePaths, key = { "conflict-source:${conflict.targetPath}:${it.path}" }) { source ->
                    ActionablePathCard(
                        path = source.path,
                        name = source.path.substringAfterLast('/'),
                        sizeBytes = source.sizeBytes,
                        metadata = "Module overlay source · ${source.category}",
                        selected = source.path in selectedPaths,
                        toggleSelection = { vm.togglePathSelection(source.path) },
                        showDetails = { vm.showPathDetails(source.path) },
                        reveal = { revealPath(source.path) },
                        delete = { vm.reviewPaths(listOf(source.path)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificationScreen(modifier: Modifier, certification: DeviceCertificationReport?, vm: MainViewModel) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DustSectionHeader("Device certification", subtitle = "Run these checks on each supported root backend before release.")
                    Button(onClick = vm::runDeviceCertification, Modifier.fillMaxWidth()) { Text("Run certification") }
                    certification?.let { report ->
                        StatusPill(if (report.releaseReady) "release gate passed" else "${report.failures} blocker(s)", danger = !report.releaseReady)
                    }
                }
            }
        }
        certification?.checks?.let { checks ->
            items(checks, key = { it.id }) { check ->
                val marker = when (check.status) { CertificationStatus.PASS -> "Pass"; CertificationStatus.WARNING -> "Warning"; CertificationStatus.FAIL -> "Fail" }
                ListItem(
                    headlineContent = { Text(check.label) },
                    supportingContent = { Text(check.detail) },
                    trailingContent = { Text(marker, color = if (check.status == CertificationStatus.FAIL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

@Composable
private fun CleanupHistoryScreen(modifier: Modifier, operations: List<CleanupOperationEntity>) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DustSectionHeader("Deletion activity", subtitle = "Permanent deletion plans, completed bytes, and partial-failure details.") }
        if (operations.isEmpty()) {
            item { EmptyState("No deletion operations yet", "Reviewed permanent deletions will appear here after execution.") }
        } else {
            items(operations, key = { it.id }) { operation ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(operation.status.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${operation.selectedCount} planned · ${formatBytes(operation.deletedBytes)} deleted of ${formatBytes(operation.plannedBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            operation.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}







@Composable
private fun DebugProbeCard(target: DebugProbeTarget, result: DebugProbeResult?, runProbe: () -> Unit) {
    val status = result?.status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                StatusPill(
                    status?.let { debugProbeStatusLabel(it) } ?: "Not run",
                    danger = status == DebugProbeStatus.Failed,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(target.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${debugProbeGroupLabel(target.group)} · ${target.detail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = runProbe) { Text(if (result == null) "Run" else "Rerun") }
            }
            if (result == null) {
                Text(
                    "No result yet. Run this probe to capture current evidence without changing app data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(result.message, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Updated ${debugTimestamp(result.updatedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (result.evidence.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        result.evidence.entries.take(8).forEach { (key, value) -> MetricRow(key, value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugEventCard(event: DebugEvent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                StatusPill(debugSeverityLabel(event.severity), danger = event.severity == DebugSeverity.Error)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(event.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${debugCategoryLabel(event.category)} · ${debugTimestamp(event.timestampMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
            if (event.evidence.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    event.evidence.entries.take(8).forEach { (key, value) ->
                        MetricRow(key, value)
                    }
                }
            }
        }
    }
}

private fun rootUiStateLabel(state: RootUiState): String = when (state) {
    RootUiState.Available -> "available"
    RootUiState.Checking -> "checking"
    RootUiState.Unavailable -> "unavailable"
}

private fun debugProbeStatusLabel(status: DebugProbeStatus): String = when (status) {
    DebugProbeStatus.Pass -> "Pass"
    DebugProbeStatus.Warning -> "Warning"
    DebugProbeStatus.Failed -> "Failed"
}

private fun debugProbeGroupLabel(group: DebugProbeGroup): String = when (group) {
    DebugProbeGroup.Root -> "Root"
    DebugProbeGroup.Scan -> "Scan"
    DebugProbeGroup.Module -> "Module"
    DebugProbeGroup.Cleanup -> "Cleanup"
    DebugProbeGroup.Search -> "Search"
}

private fun debugCategoryLabel(category: DebugCategory): String = when (category) {
    DebugCategory.App -> "App"
    DebugCategory.Navigation -> "Navigation"
    DebugCategory.Scan -> "Scan"
    DebugCategory.Root -> "Root"
    DebugCategory.Module -> "Module"
    DebugCategory.Search -> "Search"
    DebugCategory.Recommendation -> "Recommendation"
    DebugCategory.Deletion -> "Deletion"
    DebugCategory.Persistence -> "Persistence"
    DebugCategory.Performance -> "Performance"
}

private fun debugSeverityLabel(severity: DebugSeverity): String = when (severity) {
    DebugSeverity.Trace -> "Trace"
    DebugSeverity.Info -> "Info"
    DebugSeverity.Warning -> "Warning"
    DebugSeverity.Error -> "Error"
}

private fun debugTimestamp(timestampMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(timestampMillis))



@Composable
private fun ToolsGroupHeader(title: String, detail: String) {
    DustSectionHeader(title, subtitle = detail)
}

@Composable
private fun ModuleDiagnosticSummary(report: RootModuleReport?, onScanModules: () -> Unit) {
    val diagnostic = report?.diagnostic
    val tone = moduleDiagnosticTone(diagnostic?.status)
    DustHeroCard(
        title = moduleDiagnosticTitle(diagnostic?.status),
        detail = diagnostic?.message ?: "Module analysis has not produced evidence yet. Scan /data/adb/modules to populate module-source values.",
        eyebrow = "Module-source diagnostics",
    ) {
        DustStatusNotice(
            title = moduleDiagnosticNoticeTitle(diagnostic?.status),
            detail = moduleDiagnosticNoticeDetail(diagnostic),
            tone = tone,
            actionLabel = "Scan /data/adb/modules",
            onAction = onScanModules,
        )
        MetricGrid(
            listOf(
                "Current scan root" to (diagnostic?.currentScanRoot ?: "No active module scan"),
                "Required module root" to (diagnostic?.expectedRoot ?: "/data/adb/modules"),
                "Root preflight" to modulePreflightLabel(diagnostic),
                "Source status" to moduleSourceStatusLabel(diagnostic?.status),
            ),
        )
        if (report != null && report.modules.isNotEmpty()) {
            MetricGrid(
                listOf(
                    "Modules" to report.modules.size.toString(),
                    "Total footprint" to formatBytes(report.totalBytes),
                    "Disabled" to report.disabledCount.toString(),
                    "WebUI" to report.webUiCount.toString(),
                    "Overlay conflicts" to report.conflictCount.toString(),
                ),
            )
        }
        DustStatusNotice(
            title = "Module actions are source-only",
            detail = "DustUI can review module-owned files under /data/adb/modules. Protected mounted targets remain evidence, not deletion targets.",
            tone = DustNoticeTone.Success,
        )
    }
}

private fun moduleDiagnosticTitle(status: ModuleDiagnosticStatus?): String = when (status) {
    ModuleDiagnosticStatus.READY -> "Module data ready"
    ModuleDiagnosticStatus.WRONG_SCAN_ROOT -> "Current scan does not include modules"
    ModuleDiagnosticStatus.ROOT_ACCESS_FAILED -> "Root access to modules failed"
    ModuleDiagnosticStatus.SCAN_FAILED -> "Module source scan failed"
    ModuleDiagnosticStatus.NO_MODULES_FOUND -> "No module files were indexed"
    ModuleDiagnosticStatus.INCOMPLETE_RESULTS -> "Module results are incomplete"
    null -> "Module analysis is preparing"
}

private fun moduleDiagnosticNoticeTitle(status: ModuleDiagnosticStatus?): String = when (status) {
    ModuleDiagnosticStatus.READY -> "Module source evidence available"
    ModuleDiagnosticStatus.WRONG_SCAN_ROOT -> "Scan /data/adb/modules to fill module values"
    ModuleDiagnosticStatus.ROOT_ACCESS_FAILED -> "Module root preflight failed"
    ModuleDiagnosticStatus.SCAN_FAILED -> "Module source scan failed"
    ModuleDiagnosticStatus.NO_MODULES_FOUND -> "No module files found"
    ModuleDiagnosticStatus.INCOMPLETE_RESULTS -> "Module source results are incomplete"
    null -> "Module scan needed"
}

private fun moduleDiagnosticNoticeDetail(diagnostic: ModuleAnalysisDiagnostic?): String = when (diagnostic?.status) {
    ModuleDiagnosticStatus.READY -> "Footprints, disabled state, WebUI/script badges, and overlay conflicts come from indexed module source files."
    ModuleDiagnosticStatus.WRONG_SCAN_ROOT -> "The current scan root is ${diagnostic.currentScanRoot ?: "not available"}; module values require ${diagnostic.expectedRoot}."
    ModuleDiagnosticStatus.ROOT_ACCESS_FAILED -> "DustUI could not read ${diagnostic.expectedRoot}${diagnostic.rootCode?.let { " · $it" }.orEmpty()}."
    ModuleDiagnosticStatus.SCAN_FAILED -> "The module source scan failed before reliable module values could be produced."
    ModuleDiagnosticStatus.NO_MODULES_FOUND -> "The module root was readable, but no module source files were indexed."
    ModuleDiagnosticStatus.INCOMPLETE_RESULTS -> "Some module paths could not be indexed; keep conflict and footprint values marked as partial."
    null -> "Open a focused module scan so empty module cards never masquerade as successful zeroes."
}

private fun moduleDiagnosticTone(status: ModuleDiagnosticStatus?): DustNoticeTone = when (status) {
    ModuleDiagnosticStatus.READY -> DustNoticeTone.Success
    ModuleDiagnosticStatus.ROOT_ACCESS_FAILED, ModuleDiagnosticStatus.SCAN_FAILED -> DustNoticeTone.Error
    else -> DustNoticeTone.Warning
}

private fun modulePreflightLabel(diagnostic: ModuleAnalysisDiagnostic?): String = when (diagnostic?.modulePathAccessible) {
    true -> "Accessible"
    false -> "Failed${diagnostic.rootCode?.let { " · $it" }.orEmpty()}"
    null -> "Not run"
}

private fun moduleSourceStatusLabel(status: ModuleDiagnosticStatus?): String = when (status) {
    ModuleDiagnosticStatus.READY -> "Ready"
    ModuleDiagnosticStatus.WRONG_SCAN_ROOT -> "Wrong scope"
    ModuleDiagnosticStatus.ROOT_ACCESS_FAILED -> "Access failed"
    ModuleDiagnosticStatus.SCAN_FAILED -> "Scan failed"
    ModuleDiagnosticStatus.NO_MODULES_FOUND -> "No modules"
    ModuleDiagnosticStatus.INCOMPLETE_RESULTS -> "Partial"
    null -> "Pending"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupReviewSheet(review: CleanupReview?, close: () -> Unit, execute: () -> Unit) {
    if (review == null) return
    var confirmed by remember(review.operationId) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Review permanent deletion", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Approved paths will be deleted immediately and cannot be restored. Each path is fingerprinted now and revalidated before execution.",
                    color = MaterialTheme.colorScheme.error,
                )
                PathText(review.rootPath, Modifier.fillMaxWidth(), maxLines = 2)
                Text("Reviewed ${formatTimestamp(review.reviewedAt / 1000)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetricGrid(
                    listOf(
                        "Approved" to review.accepted.size.toString(),
                        "Blocked" to review.blocked.size.toString(),
                        "Planned" to formatBytes(review.plannedBytes),
                    ),
                )
                if (review.blocked.isNotEmpty() && review.accepted.isNotEmpty()) {
                    Text("Blocked paths will be skipped; approved paths can still be deleted.", style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (review.accepted.isNotEmpty()) item { Text("Exact paths to delete", fontWeight = FontWeight.SemiBold) }
                items(review.accepted, key = { it.path }) { accepted ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(accepted.path.substringAfterLast('/').ifBlank { accepted.path }, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(accepted.plannedBytes), fontWeight = FontWeight.SemiBold)
                            }
                            PathText(accepted.path, Modifier.fillMaxWidth(), maxLines = 3)
                            Text(
                                if (accepted.isDirectory) "Directory tree · ${accepted.fingerprint.entryCount} fingerprinted entries" else "File fingerprint verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (review.blocked.isNotEmpty()) item { Text("Blocked paths", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                items(review.blocked, key = { it.path }) { blocked ->
                    ListItem(
                        headlineContent = { Text(blocked.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(blocked.reason) },
                    )
                }
                review.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("I understand this deletion is irreversible.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                    cleanupExecutionDisabledReason(confirmed)?.let { reason ->
                        DisabledReasonNotice(reason)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = close, Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = execute, enabled = confirmed, modifier = Modifier.weight(1f)) { Text("Delete permanently") }
                    }
                }
            }
        }
    }
}

private fun signedBytes(value: Long): String = (if (value > 0) "+" else "") + formatBytes(value)
