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
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.DeviceCertificationReport
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
import com.mikeyphw.dustui.data.PackageStorageProfile
import com.mikeyphw.dustui.data.RootFileSearchResult
import com.mikeyphw.dustui.data.RootModuleReport
import com.mikeyphw.dustui.data.RootPathInspection
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.data.SnapshotDelta
import com.mikeyphw.dustui.data.SnapshotPoint
import com.mikeyphw.dustui.data.StorageBucket
import com.mikeyphw.dustui.data.VisualAnalytics
import com.mikeyphw.dustui.scan.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DustUiApp(viewModel: MainViewModel, requestNotificationPermission: () -> Unit = {}) {
    DustTheme {
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()
        val session by viewModel.latestSession.collectAsStateWithLifecycle()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
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
        val certification by viewModel.certification.collectAsStateWithLifecycle()
        val packageProfiles by viewModel.packageProfiles.collectAsStateWithLifecycle()
        val rootSearchResults by viewModel.rootSearchResults.collectAsStateWithLifecycle()
        val moduleReport by viewModel.moduleReport.collectAsStateWithLifecycle()
        val scanDataStale by viewModel.scanDataStale.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var destination by rememberSaveable { mutableStateOf(DustDestination.SCAN) }
        var menuOpen by remember { mutableStateOf(false) }
        var selectedApp by rememberSaveable { mutableStateOf<String?>(null) }

        fun selectDestination(target: DustDestination) {
            destination = target
            if (DustNavigationModel.refreshesInsights(target)) viewModel.refreshInsights()
        }

        LaunchedEffect(session?.id) {
            selectedApp = null
            if (session != null && destination == DustDestination.SCAN) destination = DustDestination.EXPLORER
        }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

        BoxWithConstraints {
            val wide = maxWidth >= 840.dp && maxHeight >= 520.dp
            val currentPath = path ?: session?.rootPath
            BackHandler(
                enabled = cleanupReview != null || categoryFiles != null || selected != null || selectedPaths.isNotEmpty() ||
                    (destination == DustDestination.EXPLORER && session != null && currentPath != session?.rootPath) ||
                    destination != DustDestination.SCAN,
            ) {
                when {
                    cleanupReview != null -> viewModel.dismissCleanupReview()
                    categoryFiles != null -> viewModel.dismissCategoryFiles()
                    selected != null -> viewModel.closeDetails()
                    selectedPaths.isNotEmpty() -> viewModel.clearSelection()
                    destination == DustDestination.EXPLORER && session != null && currentPath != session?.rootPath -> viewModel.up()
                    else -> DustNavigationModel.backTarget(destination, wide)?.let { destination = it }
                }
            }
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(destination.label) },
                        actions = {
                            TextButton(onClick = { menuOpen = true }) { Text(destination.actionLabel) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export CSV") },
                                    enabled = session != null,
                                    onClick = { menuOpen = false; viewModel.exportCsv() },
                                )
                                DropdownMenuItem(
                                    text = { Text("New scan") },
                                    onClick = { menuOpen = false; selectDestination(DustDestination.SCAN) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Run certification") },
                                    enabled = session != null,
                                    onClick = { menuOpen = false; selectDestination(DustDestination.CERTIFICATION); viewModel.runDeviceCertification() },
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            DustNavigationModel.compactDestinations.forEach { item ->
                                NavigationBarItem(
                                    selected = DustNavigationModel.compactSelection(destination) == item,
                                    onClick = { selectDestination(item) },
                                    icon = { Text(item.symbol, style = MaterialTheme.typography.titleMedium) },
                                    label = { Text(item.shortLabel) },
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    if (wide) {
                        NavigationRail(Modifier.fillMaxHeight()) {
                            DustNavigationModel.wideDestinations.forEach { item ->
                                NavigationRailItem(
                                    selected = destination == item,
                                    onClick = { selectDestination(item) },
                                    icon = { Text(item.symbol, style = MaterialTheme.typography.titleMedium) },
                                    label = { Text(item.shortLabel) },
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        if (scanDataStale) {
                            Surface(tonalElevation = 3.dp) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("Files were deleted. Scan results are now stale.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { selectDestination(DustDestination.SCAN) }) { Text("New scan") }
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            val currentSession = session
                            when {
                                currentSession == null && DustNavigationModel.needsScanData(destination) -> EmptyState(
                                    title = "No scan yet",
                                    detail = "Start a root scan before opening explorer, analytics, large-file cleanup, duplicate detection, app residue, search, packages, modules, certification, or deletion activity.",
                                )
                                destination == DustDestination.SCAN -> ScanLauncher(
                                    Modifier.fillMaxSize(),
                                    rootState,
                                    currentSession,
                                    viewModel,
                                    requestNotificationPermission,
                                ) { selectDestination(DustDestination.EXPLORER) }
                                destination == DustDestination.EXPLORER && currentSession != null -> Explorer(
                                    Modifier.fillMaxSize(),
                                    currentSession,
                                    path ?: currentSession.rootPath,
                                    entries,
                                    filter,
                                    selectedPaths,
                                    viewModel,
                                    openRootSearch = { selectDestination(DustDestination.SEARCH) },
                                )
                                destination == DustDestination.INSIGHTS -> InsightsOverview(
                                    Modifier.fillMaxSize(),
                                    delta,
                                    snapshotHistory,
                                    advancedReport,
                                    apps,
                                    selectedPaths,
                                    scanDataStale,
                                    viewModel,
                                    requestNotificationPermission,
                                    revealPath = { filePath ->
                                        viewModel.revealPath(filePath)
                                        selectDestination(DustDestination.EXPLORER)
                                    },
                                    openDuplicates = { selectDestination(DustDestination.DUPLICATES) },
                                )
                                destination == DustDestination.LARGE_FILES && currentSession != null -> LargeFileWorkbenchScreen(
                                    Modifier.fillMaxSize(),
                                    largeFiles,
                                    largeFileLoading,
                                    selectedPaths,
                                    scanDataStale,
                                    viewModel,
                                ) { filePath ->
                                    viewModel.revealPath(filePath)
                                    selectDestination(DustDestination.EXPLORER)
                                }
                                destination == DustDestination.DUPLICATES && currentSession != null -> DuplicateWorkbenchScreen(
                                    Modifier.fillMaxSize(),
                                    duplicateAnalysis,
                                    duplicatePage,
                                    selectedPaths,
                                    scanDataStale,
                                    viewModel,
                                ) { filePath ->
                                    viewModel.revealPath(filePath)
                                    selectDestination(DustDestination.EXPLORER)
                                }
                                destination == DustDestination.APP_RESIDUE && currentSession != null -> AppResidueWorkbenchScreen(
                                    Modifier.fillMaxSize(),
                                    residueAnalysis,
                                    residuePage,
                                    residueLoading,
                                    selectedPaths,
                                    scanDataStale,
                                    viewModel,
                                ) { filePath ->
                                    viewModel.revealPath(filePath)
                                    selectDestination(DustDestination.EXPLORER)
                                }
                                destination == DustDestination.SEARCH && currentSession != null -> RootSearchScreen(
                                    Modifier.fillMaxSize(),
                                    currentSession.id,
                                    rootSearchResults,
                                    selectedPaths,
                                    viewModel,
                                ) { result ->
                                    viewModel.revealPath(result.path)
                                    selectDestination(DustDestination.EXPLORER)
                                }
                                destination == DustDestination.PACKAGES && currentSession != null -> PackageIntelligenceScreen(
                                    Modifier.fillMaxSize(),
                                    currentSession.id,
                                    packageProfiles,
                                    apps,
                                    selectedApp,
                                    selectedPaths,
                                    viewModel,
                                    revealPath = { filePath ->
                                        viewModel.revealPath(filePath)
                                        selectDestination(DustDestination.EXPLORER)
                                    },
                                    onSelectApp = { selectedApp = it },
                                )
                                destination == DustDestination.MODULES -> ModuleAnalyzerScreen(
                                    Modifier.fillMaxSize(),
                                    moduleReport,
                                    selectedPaths,
                                    viewModel,
                                ) { filePath ->
                                    viewModel.revealPath(filePath)
                                    selectDestination(DustDestination.EXPLORER)
                                }
                                destination == DustDestination.CERTIFICATION -> CertificationScreen(Modifier.fillMaxSize(), certification, viewModel)
                                destination == DustDestination.CLEANUP -> CleanupHistoryScreen(Modifier.fillMaxSize(), cleanupOperations)
                                destination == DustDestination.MORE -> MoreScreen(Modifier.fillMaxSize(), ::selectDestination)
                            }
                            if (destination != DustDestination.EXPLORER && selectedPaths.isNotEmpty()) {
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
            deletionEnabled = !scanDataStale,
            close = viewModel::dismissCategoryFiles,
            reveal = { filePath ->
                viewModel.dismissCategoryFiles()
                viewModel.revealPath(filePath)
                destination = DustDestination.EXPLORER
            },
        )
        val selectedEntry = selected
        if (selectedEntry != null) EntryDetails(
            selectedEntry,
            intelligence,
            rootInspection,
            close = viewModel::closeDetails,
            reveal = {
                viewModel.closeDetails()
                viewModel.revealPath(selectedEntry.path)
                destination = DustDestination.EXPLORER
            },
            delete = {
                viewModel.closeDetails()
                viewModel.reviewPaths(listOf(selectedEntry.path))
            },
        )
        if (cleanupReview != null) CleanupReviewSheet(cleanupReview, viewModel::dismissCleanupReview, viewModel::executeCleanup)
    }
}


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

@Composable
private fun ScanLauncher(
    modifier: Modifier,
    root: RootUiState,
    session: ScanSessionEntity?,
    vm: MainViewModel,
    requestNotificationPermission: () -> Unit,
    openExplorer: () -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf("/storage/emulated/0") }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Root service", fontWeight = FontWeight.SemiBold)
                        Text(when (root) { RootUiState.Checking -> "Checking"; RootUiState.Available -> "Ready"; RootUiState.Unavailable -> "Unavailable" })
                    }
                    if (root == RootUiState.Unavailable) OutlinedButton(onClick = vm::refreshRoot) { Text("Retry") }
                }
            }
        }
        item { DustSectionHeader("Scan presets", subtitle = "Common storage scopes for root-aware Android analysis.") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.presets.forEach { preset ->
                    Card(
                        onClick = { requestNotificationPermission(); vm.startPreset(preset); openExplorer() },
                        enabled = root == RootUiState.Available,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preset.title, fontWeight = FontWeight.SemiBold)
                            Text(preset.description, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            PathText(preset.path)
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), label = { Text("Absolute path") }, singleLine = true)
                    Button(onClick = { requestNotificationPermission(); vm.startCustom(custom); openExplorer() }, enabled = root == RootUiState.Available) { Text("Scan") }
                }
            }
        }
        session?.let {
            item {
                SessionCard(it, vm::cancelActive)
                OutlinedButton(onClick = openExplorer, Modifier.fillMaxWidth()) { Text("Open latest scan") }
            }
        }
    }
}

@Composable
private fun Explorer(
    modifier: Modifier,
    session: ScanSessionEntity,
    path: String,
    entries: List<ScanEntryEntity>,
    filter: ExplorerFilter,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    openRootSearch: () -> Unit,
) {
    var treemapMode by rememberSaveable(path) { mutableStateOf(false) }
    var showLargestChart by rememberSaveable(path) { mutableStateOf(false) }
    val selectionMode = selectedPaths.isNotEmpty()
    val chartEntries = entries.filter { it.isDirectory }.ifEmpty { entries }.take(6)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = dustScreenPadding(horizontal = 0, bottom = if (selectionMode) 208 else 128),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        item { SessionCard(session, vm::cancelActive) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::up) { Text("Up") }
                PathText(path, Modifier.weight(1f))
            }
        }
        item { Breadcrumbs(session.rootPath, path, vm::navigate) }
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
        item {
            OutlinedTextField(
                filter.query,
                vm::updateQuery,
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                label = { Text("Current folder search") },
                singleLine = true,
                supportingText = null,
            )
        }
        item {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                contentPadding = PaddingValues(end = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { AssistChip(onClick = vm::cycleSort, label = { Text("Sort: ${filter.sort.name.lowercase()}") }) }
                item { AssistChip(onClick = vm::cycleType, label = { Text("Type: ${filter.type.name.lowercase()}") }) }
                item { SegmentedChoice("List", "Treemap", treemapMode, onFirst = { treemapMode = false }, onSecond = { treemapMode = true }) }
                item { FilterChip(selected = showLargestChart, onClick = { showLargestChart = !showLargestChart }, label = { Text("Chart") }) }
                item { AssistChip(onClick = openRootSearch, label = { Text("Whole scan") }) }
                filter.category?.let { category -> item { AssistChip(onClick = { vm.filterCategory(null) }, label = { Text(category) }, trailingIcon = { Text("×") }) } }
                item { Text("${entries.size} entries", style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (treemapMode && entries.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Treemap · tap a block to open", style = MaterialTheme.typography.titleSmall)
                        StorageTreemap(entries, vm::open)
                        Text("Labels appear on large blocks. Exact sizes remain available in the list and details sheet.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            if (entries.isEmpty()) {
                item { EmptyState("No matching direct children", "Change filters or scan a broader folder.", Modifier.padding(horizontal = 12.dp)) }
            } else {
                items(entries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        total = session.totalBytes,
                        selectionMode = selectionMode,
                        selected = entry.path in selectedPaths,
                        open = { vm.open(entry) },
                        details = { vm.select(entry) },
                        toggle = { vm.toggleSelection(entry) },
                    )
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


@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    clear: () -> Unit,
    review: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("$selectedCount selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = clear) { Text("Clear") }
            Button(onClick = review) { Text("Review deletion") }
        }
    }
}

@Composable
private fun Breadcrumbs(root: String, path: String, navigate: (String) -> Unit) {
    val relative = path.removePrefix(root).trim('/')
    val parts = if (relative.isBlank()) emptyList() else relative.split('/')
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item { AssistChip(onClick = { navigate(root) }, label = { Text(if (relative.isBlank()) "Scan root" else root.substringAfterLast('/').ifBlank { "/" }) }) }
        parts.forEachIndexed { index, part ->
            item(key = "$index:$part") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("/")
                    AssistChip(onClick = { navigate(root.trimEnd('/') + "/" + parts.take(index + 1).joinToString("/")) }, label = { Text(part) })
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
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable(onClick = open)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { toggle() })
                Column(Modifier.weight(1f)) {
                    Text((if (entry.isDirectory) "▸ " else "") + entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    PathText(entry.path)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatBytes(entry.sizeBytes), fontWeight = FontWeight.SemiBold)
                    Text("${entry.fileCount} files", style = MaterialTheme.typography.labelSmall)
                }
            }
            LinearProgressIndicator({ share }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTimestamp(entry.modifiedAt), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = toggle) { Text(if (selected) "Unselect" else "Select") }
                TextButton(onClick = details) { Text("Details") }
            }
        }
    }
}

@Composable
private fun SessionCard(session: ScanSessionEntity, cancel: () -> Unit) {
    val running = session.status in setOf(ScanStatus.STARTING.name, ScanStatus.RUNNING.name)
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PathText(session.rootPath)
                    Text(
                        "${session.status.lowercase()} · ${session.scannedItems} items · ${formatBytes(session.scannedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (running) OutlinedButton(onClick = cancel) { Text("Cancel") }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
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
                                enabled = !scanDataStale,
                                modifier = Modifier.weight(1f),
                            ) { Text("Select page") }
                            Button(
                                onClick = vm::reviewLargeFilePage,
                                enabled = !scanDataStale,
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
                        deletionEnabled = !scanDataStale,
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
                                enabled = !scanDataStale,
                                modifier = Modifier.weight(1f),
                            ) { Text(if (analysis?.resumable == true) "Resume" else "Run") }
                            OutlinedButton(
                                onClick = { vm.startDuplicateAnalysis(reset = true) },
                                enabled = !scanDataStale,
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
                                enabled = !scanDataStale && page.deletionPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Select page") }
                            Button(
                                onClick = vm::reviewDuplicatePage,
                                enabled = !scanDataStale && page.deletionPaths.isNotEmpty(),
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
                deletionEnabled = !scanDataStale,
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
                                enabled = !scanDataStale && page.recommendedPaths.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("Select safe") }
                            Button(
                                onClick = vm::reviewResiduePage,
                                enabled = !scanDataStale && page.recommendedPaths.isNotEmpty(),
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
                deletionEnabled = !scanDataStale,
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
private fun InsightsOverview(
    modifier: Modifier,
    delta: SnapshotDelta?,
    history: List<SnapshotPoint>,
    advanced: AdvancedStorageReport?,
    apps: List<AppStorageSummary>,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    requestNotificationPermission: () -> Unit,
    revealPath: (String) -> Unit,
    openDuplicates: () -> Unit,
) {
    var selectedType by rememberSaveable(advanced?.typeBuckets?.hashCode()) { mutableStateOf<String?>(null) }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            DustSectionHeader("Overview", subtitle = "Storage trends, composition, and cleanup signals.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DustSectionHeader("Storage snapshots")
                    SnapshotLineChart(history, Modifier.fillMaxWidth())
                    if (delta == null) {
                        Text("Complete two scans of the same scope to compare growth.")
                    } else {
                        Detail("Net change", signedBytes(delta.totalDeltaBytes))
                        Detail("Added", formatBytes(delta.addedBytes))
                        Detail("Removed", formatBytes(delta.removedBytes))
                        Detail("Changed", delta.changedEntries.toString())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestNotificationPermission(); vm.scheduleLatest(24) }) { Text("Daily scans") }
                        OutlinedButton(onClick = vm::cancelSchedule) { Text("Disable") }
                    }
                }
            }
        }
        if (advanced == null) {
            item { EmptyState("Visual analysis is preparing…", "Open this page again after the latest scan finishes indexing.") }
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
                                enabled = !scanDataStale,
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

@Composable
private fun RootSearchScreen(
    modifier: Modifier,
    scanId: String,
    results: List<RootFileSearchResult>,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    openResult: (RootFileSearchResult) -> Unit,
) {
    var query by rememberSaveable(scanId) { mutableStateOf("*.apk >10m") }
    var searched by rememberSaveable(scanId) { mutableStateOf(false) }
    fun submitSearch() { searched = true; vm.rootSearch(query) }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DustSectionHeader("Root file search", subtitle = "Search the whole persisted scan index, not just the current folder.")
                    OutlinedTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Whole scan query") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
                    Text("Examples: media, *.apk, ext:log, package:com.app, >100m, >1g", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { submitSearch() }, Modifier.fillMaxWidth()) { Text("Search") }
                }
            }
        }
        if (results.isEmpty()) {
            item {
                EmptyState(
                    if (searched) "No matches" else "No root search results yet",
                    if (searched) "No indexed paths matched this query. Try a broader name, extension, package, or size filter." else "Run a query to search the indexed scan. Results jump back into Explorer.",
                )
            }
        } else {
            item {
                DustSectionHeader(
                    "Search results",
                    subtitle = if (results.size > 80) "Showing 80 of ${results.size} indexed matches." else "${results.size} indexed matches.",
                )
            }
            items(results.take(80), key = { it.path }) { result ->
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
        }
    }
}

@Composable
private fun PackageIntelligenceScreen(
    modifier: Modifier,
    scanId: String,
    packageProfiles: List<PackageStorageProfile>,
    apps: List<AppStorageSummary>,
    selectedApp: String?,
    selectedPaths: Set<String>,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
    onSelectApp: (String) -> Unit,
) {
    var packageQuery by rememberSaveable(scanId) { mutableStateOf("") }
    val matchingPackages = remember(packageProfiles, packageQuery) {
        val needle = packageQuery.trim()
        if (needle.isBlank()) packageProfiles else packageProfiles.filter {
            it.packageName.contains(needle, ignoreCase = true) || it.appLabel?.contains(needle, ignoreCase = true) == true
        }
    }
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DustSectionHeader("Package intelligence", subtitle = "One app-focused surface for ownership, cache risk, largest paths, and measured footprint.") }
        if (packageProfiles.isEmpty()) {
            item { EmptyState("No package-owned paths", "The scan did not identify Android package-owned storage in this scope.") }
        } else {
            item {
                val profile = packageProfiles.firstOrNull { it.packageName == selectedApp } ?: packageProfiles.first()
                val app = apps.firstOrNull { it.packageName == profile.packageName }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.appLabel ?: profile.packageName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PathText(profile.packageName)
                        val statuses = buildList {
                            if (!profile.installed) add("orphaned" to true)
                            if (profile.hasLargeCache) add("large cache" to false)
                            if (profile.hasObb) add("OBB" to false)
                            if (profile.hasDuplicateCandidates) add("duplicates" to false)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            statuses.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { (label, danger) -> StatusPill(label, danger = danger) }
                                }
                            }
                        }
                        Detail("Total", formatBytes(profile.totalBytes))
                        Detail("Private", formatBytes(profile.privateBytes))
                        Detail("Cache", formatBytes(profile.cacheBytes))
                        Detail("External", formatBytes(profile.externalBytes))
                        Detail("OBB", formatBytes(profile.obbBytes))
                        Detail("Databases", formatBytes(profile.databaseBytes))
                        Detail("Preferences", formatBytes(profile.preferencesBytes))
                        Detail("Duplicate candidates", formatBytes(profile.duplicateCandidateBytes))
                        app?.let { StorageDonut(VisualAnalytics.appSlices(it), Modifier.fillMaxWidth()) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    packageQuery,
                    { packageQuery = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Find package") },
                    singleLine = true,
                )
                Text(
                    "Showing ${matchingPackages.size} of ${packageProfiles.size} packages",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                    items(matchingPackages, key = { it.packageName }) { candidate ->
                        Card(
                            onClick = { onSelectApp(candidate.packageName) },
                            modifier = Modifier.width(220.dp),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    candidate.appLabel ?: candidate.packageName.substringAfterLast('.'),
                                    fontWeight = if (candidate.packageName == (selectedApp ?: packageProfiles.first().packageName)) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                PathText(candidate.packageName, Modifier.fillMaxWidth(), maxLines = 2)
                            }
                        }
                    }
                }
            }
            val profile = packageProfiles.firstOrNull { it.packageName == selectedApp } ?: packageProfiles.first()
            item {
                DustSectionHeader(
                    "Largest package-owned paths",
                    subtitle = if (profile.largestPaths.size > 8) "Showing 8 of ${profile.largestPaths.size} paths." else null,
                )
            }
            items(profile.largestPaths.take(8), key = { it.path }) { path ->
                ActionablePathCard(
                    path = path.path,
                    name = path.path.substringAfterLast('/'),
                    sizeBytes = path.sizeBytes,
                    metadata = path.category,
                    selected = path.path in selectedPaths,
                    toggleSelection = { vm.togglePathSelection(path.path) },
                    showDetails = { vm.showPathDetails(path.path) },
                    reveal = { revealPath(path.path) },
                    delete = { vm.reviewPaths(listOf(path.path)) },
                )
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
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (report == null) {
                        Text("Module analysis is preparing…")
                    } else {
                        Detail("Modules", report.modules.size.toString())
                        Detail("Total footprint", formatBytes(report.totalBytes))
                        Detail("Disabled", report.disabledCount.toString())
                        Detail("WebUI", report.webUiCount.toString())
                        Detail("Overlay conflicts", report.conflictCount.toString())
                        if (report.conflictCount > 0) Text("Multiple modules target the same overlay path. Review the module-owned source files before deleting anything.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
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
private fun MoreScreen(modifier: Modifier, navigate: (DustDestination) -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DustSectionHeader("More", subtitle = "Specialized workbenches split out from the old Insights page.")
        DustNavigationModel.moreDestinations.forEach { destination ->
            Card(onClick = { navigate(destination) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(destination.symbol, style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(destination.label, fontWeight = FontWeight.SemiBold)
                        Text(moreDestinationDescription(destination), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun moreDestinationDescription(destination: DustDestination): String = when (destination) {
    DustDestination.LARGE_FILES -> "Filter, group, inspect, and batch-plan deletion for large files."
    DustDestination.DUPLICATES -> "Verify exact duplicate content and choose which physical copies to keep."
    DustDestination.APP_RESIDUE -> "Review evidence-ranked folders left by packages that are no longer installed."
    DustDestination.PACKAGES -> "App-owned storage profiles and largest paths."
    DustDestination.MODULES -> "Root module footprint and overlay conflict analysis."
    DustDestination.CERTIFICATION -> "Root backend, notification, lock-state, and release checks."
    DustDestination.CLEANUP -> "Permanent deletion activity and partial-failure details."
    else -> destination.label
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = close, Modifier.weight(1f)) { Text("Cancel") }
                        Button(onClick = execute, enabled = review.executable && confirmed, modifier = Modifier.weight(1f)) { Text("Delete permanently") }
                    }
                }
            }
        }
    }
}

private fun signedBytes(value: Long): String = (if (value > 0) "+" else "") + formatBytes(value)
