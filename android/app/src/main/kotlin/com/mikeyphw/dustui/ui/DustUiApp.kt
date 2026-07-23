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
import com.mikeyphw.dustui.data.AdvancedStorageReport
import com.mikeyphw.dustui.data.AppStorageSummary
import com.mikeyphw.dustui.data.CertificationStatus
import com.mikeyphw.dustui.data.ChartSlice
import com.mikeyphw.dustui.data.CleanupOperationEntity
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.DeviceCertificationReport
import com.mikeyphw.dustui.data.EntryIntelligence
import com.mikeyphw.dustui.data.PackageStorageProfile
import com.mikeyphw.dustui.data.RootFileSearchResult
import com.mikeyphw.dustui.data.RootModuleReport
import com.mikeyphw.dustui.data.RootPathInspection
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.data.SnapshotDelta
import com.mikeyphw.dustui.data.SnapshotPoint
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
        val cleanupOperations by viewModel.cleanupOperations.collectAsStateWithLifecycle()
        val advancedReport by viewModel.advancedReport.collectAsStateWithLifecycle()
        val verifiedDuplicates by viewModel.verifiedDuplicates.collectAsStateWithLifecycle()
        val certification by viewModel.certification.collectAsStateWithLifecycle()
        val packageProfiles by viewModel.packageProfiles.collectAsStateWithLifecycle()
        val rootSearchResults by viewModel.rootSearchResults.collectAsStateWithLifecycle()
        val moduleReport by viewModel.moduleReport.collectAsStateWithLifecycle()
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
                enabled = cleanupReview != null || selected != null || selectedPaths.isNotEmpty() ||
                    (destination == DustDestination.EXPLORER && session != null && currentPath != session?.rootPath) ||
                    destination != DustDestination.SCAN,
            ) {
                when {
                    cleanupReview != null -> viewModel.dismissCleanupReview()
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
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        val currentSession = session
                        when {
                            currentSession == null && DustNavigationModel.needsScanData(destination) -> EmptyState(
                                title = "No scan yet",
                                detail = "Start a root scan before opening explorer, analytics, search, packages, modules, certification, or cleanup history.",
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
                                verifiedDuplicates,
                                viewModel,
                                requestNotificationPermission,
                            ) { category ->
                                viewModel.filterCategory(category)
                                selectDestination(DustDestination.EXPLORER)
                            }
                            destination == DustDestination.SEARCH && currentSession != null -> RootSearchScreen(
                                Modifier.fillMaxSize(),
                                currentSession.id,
                                rootSearchResults,
                                viewModel,
                            ) { result ->
                                viewModel.openSearchResult(result)
                                selectDestination(DustDestination.EXPLORER)
                            }
                            destination == DustDestination.PACKAGES && currentSession != null -> PackageIntelligenceScreen(
                                Modifier.fillMaxSize(),
                                currentSession.id,
                                packageProfiles,
                                apps,
                                selectedApp,
                                onSelectApp = { selectedApp = it },
                            )
                            destination == DustDestination.MODULES -> ModuleAnalyzerScreen(Modifier.fillMaxSize(), moduleReport)
                            destination == DustDestination.CERTIFICATION -> CertificationScreen(Modifier.fillMaxSize(), certification, viewModel)
                            destination == DustDestination.CLEANUP -> CleanupHistoryScreen(Modifier.fillMaxSize(), cleanupOperations, viewModel)
                            destination == DustDestination.MORE -> MoreScreen(Modifier.fillMaxSize(), ::selectDestination)
                        }
                    }
                }
            }
        }
        if (selected != null) EntryDetails(selected, intelligence, rootInspection, viewModel::closeDetails)
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
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedPaths.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = vm::clearSelection) { Text("Clear") }
                    Button(onClick = vm::reviewCleanup) { Text("Review") }
                }
            }
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
private fun EntryDetails(entry: ScanEntryEntity?, info: EntryIntelligence?, root: RootPathInspection?, close: () -> Unit) {
    if (entry == null) return
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
                Button(onClick = close, Modifier.fillMaxWidth()) { Text("Close") }
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
private fun InsightsOverview(
    modifier: Modifier,
    delta: SnapshotDelta?,
    history: List<SnapshotPoint>,
    advanced: AdvancedStorageReport?,
    apps: List<AppStorageSummary>,
    verified: com.mikeyphw.dustui.data.DuplicateVerificationReport?,
    vm: MainViewModel,
    requestNotificationPermission: () -> Unit,
    exploreCategory: (String) -> Unit,
) {
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DustSectionHeader("Storage composition")
                        StorageDonut(slices, Modifier.fillMaxWidth(), selectedType) { selectedType = it }
                        selectedType?.takeUnless { it == "Other" }?.let { category ->
                            Button(onClick = { exploreCategory(category) }, Modifier.fillMaxWidth()) { Text("Explore $category") }
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
                        Button(onClick = vm::verifyTopDuplicates, enabled = advanced.duplicateCandidates.isNotEmpty()) { Text("Verify top duplicate group") }
                        verified?.let {
                            MetricGrid(
                                listOf(
                                    "Verified groups" to it.groups.size.toString(),
                                    "Verified savings" to formatBytes(it.reclaimableBytes),
                                ),
                            )
                            Text("Checked ${it.checkedFiles}; skipped ${it.skippedFiles}", style = MaterialTheme.typography.bodySmall)
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
                                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                            Text(
                                                old.path.substringAfterLast('/'),
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(formatBytes(old.sizeBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("${old.ageDays} days old", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        PathText(old.path, Modifier.fillMaxWidth(), maxLines = 3)
                                    }
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
private fun RootSearchScreen(
    modifier: Modifier,
    scanId: String,
    results: List<RootFileSearchResult>,
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
                Card(onClick = { openResult(result) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Text(result.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(formatBytes(result.sizeBytes), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Text(result.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PathText(result.path, Modifier.fillMaxWidth(), maxLines = 2)
                    }
                }
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
                ListItem(
                    headlineContent = { Text(path.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${path.category} · ${path.path}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Text(formatBytes(path.sizeBytes)) },
                )
            }
        }
    }
}

@Composable
private fun ModuleAnalyzerScreen(modifier: Modifier, moduleReport: RootModuleReport?) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DustSectionHeader("Root module analyzer", subtitle = "KernelSU, Magisk, APatch, and generic module footprints from /data/adb/modules.") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val report = moduleReport
                    if (report == null) {
                        Text("Module analysis is preparing…")
                    } else {
                        Detail("Modules", report.modules.size.toString())
                        Detail("Total footprint", formatBytes(report.totalBytes))
                        Detail("Disabled", report.disabledCount.toString())
                        Detail("WebUI", report.webUiCount.toString())
                        Detail("Overlay conflicts", report.conflictCount.toString())
                        if (report.conflictCount > 0) Text("Multiple modules target the same overlay path. Review before cleanup or uninstall.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        moduleReport?.modules?.take(20)?.let { modules ->
            if (modules.isNotEmpty()) item {
                DustSectionHeader(
                    "Modules by footprint",
                    subtitle = if ((moduleReport?.modules?.size ?: 0) > 20) "Showing 20 of ${moduleReport?.modules?.size} modules." else null,
                )
            }
            items(modules, key = { it.moduleId }) { module ->
                ListItem(
                    headlineContent = { Text(module.moduleId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${module.entryCount} entries · ${module.overlayTargets.size} overlay target(s)${if (module.disabled) " · disabled" else ""}") },
                    trailingContent = { Text(formatBytes(module.totalBytes)) },
                )
            }
        }
        moduleReport?.overlayConflicts?.take(12)?.let { conflicts ->
            if (conflicts.isNotEmpty()) item {
                DustSectionHeader(
                    "Module overlay conflicts",
                    subtitle = if ((moduleReport?.overlayConflicts?.size ?: 0) > 12) "Showing 12 of ${moduleReport?.overlayConflicts?.size} conflicts." else null,
                )
            }
            items(conflicts, key = { it.targetPath }) { conflict ->
                ListItem(
                    headlineContent = { Text(conflict.targetPath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(conflict.moduleIds.joinToString(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { StatusPill("conflict", danger = true) },
                )
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
private fun CleanupHistoryScreen(modifier: Modifier, operations: List<CleanupOperationEntity>, vm: MainViewModel) {
    LazyColumn(modifier, contentPadding = dustScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DustSectionHeader("Cleanup activity", subtitle = "Quarantine and restore history lives here instead of being buried under analytics.") }
        if (operations.isEmpty()) {
            item { EmptyState("No cleanup operations yet", "Selected cleanup items will appear here after review and quarantine.") }
        } else {
            items(operations, key = { it.id }) { operation ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(operation.status.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                            Text("${operation.selectedCount} items · ${formatBytes(operation.reclaimedBytes)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (operation.status in setOf("QUARANTINED", "PARTIAL")) OutlinedButton(onClick = { vm.restoreCleanup(operation.id) }) { Text("Restore") }
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
    DustDestination.PACKAGES -> "App-owned storage profiles and largest paths."
    DustDestination.MODULES -> "Root module footprint and overlay conflict analysis."
    DustDestination.CERTIFICATION -> "Root backend, notification, lock-state, and release checks."
    DustDestination.CLEANUP -> "Quarantine, restore, and cleanup history."
    else -> destination.label
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupReviewSheet(review: CleanupReview?, close: () -> Unit, execute: () -> Unit) {
    if (review == null) return
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Review cleanup", style = MaterialTheme.typography.headlineSmall)
                Text("Quarantine is reversible. Permanent deletion is not part of the safe cleanup flow.")
                PathText(review.rootPath, Modifier.fillMaxWidth(), maxLines = 2)
                Text("Reviewed ${formatTimestamp(review.reviewedAt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MetricGrid(
                    listOf(
                        "Accepted" to review.accepted.size.toString(),
                        "Blocked" to review.blocked.size.toString(),
                        "Planned" to formatBytes(review.plannedBytes),
                    ),
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (review.accepted.isNotEmpty()) item { Text("Items to quarantine", fontWeight = FontWeight.SemiBold) }
                items(review.accepted, key = { it.path }) { accepted ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(accepted.path.substringAfterLast('/').ifBlank { accepted.path }, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(formatBytes(accepted.sizeBytes), fontWeight = FontWeight.SemiBold)
                            }
                            PathText(accepted.path, Modifier.fillMaxWidth(), maxLines = 3)
                            Text("Directories include their reviewed descendants.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (review.blocked.isNotEmpty()) item { Text("Blocked items", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                items(review.blocked, key = { it.path }) { blocked ->
                    ListItem(
                        headlineContent = { Text(blocked.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(blocked.reason) },
                    )
                }
                review.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            }
            Surface(tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = close, Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = execute, enabled = review.executable, modifier = Modifier.weight(1f)) { Text("Quarantine") }
                }
            }
        }
    }
}

private fun signedBytes(value: Long): String = (if (value > 0) "+" else "") + formatBytes(value)
