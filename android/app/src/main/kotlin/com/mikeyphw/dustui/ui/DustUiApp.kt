package com.mikeyphw.dustui.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.dustui.data.EntryIntelligence
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import com.mikeyphw.dustui.data.AppStorageSummary
import com.mikeyphw.dustui.data.SnapshotDelta
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.CleanupOperationEntity
import com.mikeyphw.dustui.scan.ScanStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DustUiApp(viewModel: MainViewModel) {
    DustTheme {
        val rootState by viewModel.rootState.collectAsStateWithLifecycle()
        val session by viewModel.latestSession.collectAsStateWithLifecycle()
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        val path by viewModel.explorerPath.collectAsStateWithLifecycle()
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val selected by viewModel.selectedEntry.collectAsStateWithLifecycle()
        val intelligence by viewModel.selectedIntelligence.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val apps by viewModel.appSummaries.collectAsStateWithLifecycle()
        val delta by viewModel.snapshotDelta.collectAsStateWithLifecycle()
        val selectedPaths by viewModel.selectedPaths.collectAsStateWithLifecycle()
        val cleanupReview by viewModel.cleanupReview.collectAsStateWithLifecycle()
        val cleanupOperations by viewModel.cleanupOperations.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        var showLauncher by remember { mutableStateOf(session == null) }
        var showInsights by remember { mutableStateOf(false) }
        LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() } }

        Scaffold(
            topBar = { TopAppBar(title = { Text("DustUI Root") }, actions = {
                TextButton(onClick = { showInsights = !showInsights; if (showInsights) { showLauncher = false; viewModel.refreshInsights() } }) { Text(if (showInsights) "Explorer" else "Insights") }
                TextButton(onClick = { showLauncher = !showLauncher; showInsights = false }) { Text(if (showLauncher) "Explorer" else "New scan") }
                TextButton(onClick = viewModel::exportCsv, enabled = session != null) { Text("Export") }
            }) },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val currentSession = session
            if (showInsights && currentSession != null) {
                Insights(Modifier.padding(padding), delta, apps, cleanupOperations, viewModel)
            } else if (showLauncher || currentSession == null) {
                ScanLauncher(Modifier.padding(padding), rootState, currentSession, viewModel) { showLauncher = false }
            } else {
                Explorer(
                    Modifier.padding(padding),
                    currentSession,
                    path ?: currentSession.rootPath,
                    entries,
                    filter,
                    selectedPaths,
                    viewModel,
                )
            }
        }
        if (selected != null) EntryDetails(selected!!, intelligence, viewModel::closeDetails)
        if (cleanupReview != null) CleanupReviewSheet(cleanupReview!!, viewModel::dismissCleanupReview, viewModel::executeCleanup)
    }
}

@Composable
private fun ScanLauncher(modifier: Modifier, root: RootUiState, session: ScanSessionEntity?, vm: MainViewModel, openExplorer: () -> Unit) {
    var custom by remember { mutableStateOf("/storage/emulated/0") }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Root service", fontWeight = FontWeight.SemiBold); Text(when(root){RootUiState.Checking->"Checking";RootUiState.Available->"Ready";RootUiState.Unavailable->"Unavailable"}) }
            if(root == RootUiState.Unavailable) OutlinedButton(onClick=vm::refreshRoot){Text("Retry")}
        } } }
        item { Text("Scan presets", style = MaterialTheme.typography.titleMedium) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(vm.presets, key={it.id}) { preset ->
            Card(onClick={vm.startPreset(preset);openExplorer()}, enabled=root==RootUiState.Available, modifier=Modifier.width(220.dp)) { Column(Modifier.padding(14.dp)) { Text(preset.title,fontWeight=FontWeight.SemiBold);Text(preset.description,style=MaterialTheme.typography.bodySmall);Text(preset.path,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.labelSmall) } }
        } } }
        item { Card { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(custom,{custom=it},Modifier.fillMaxWidth(),label={Text("Absolute path")},singleLine=true); Button(onClick={vm.startCustom(custom);openExplorer()},enabled=root==RootUiState.Available){Text("Scan")}} } }
        session?.let { item { SessionCard(it, vm::cancelActive); OutlinedButton(onClick=openExplorer,Modifier.fillMaxWidth()){Text("Open latest scan")}} }
    }
}

@Composable
private fun Explorer(modifier: Modifier, session: ScanSessionEntity, path: String, entries: List<ScanEntryEntity>, filter: ExplorerFilter, selectedPaths: Set<String>, vm: MainViewModel) {
    Column(modifier.fillMaxSize()) {
        SessionCard(session, vm::cancelActive)
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=vm::up){Text("Up")}; Text(path,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=FontFamily.Monospace)
        }
        Breadcrumbs(session.rootPath,path,vm::navigate)
        OutlinedTextField(filter.query,vm::updateQuery,Modifier.fillMaxWidth().padding(horizontal=12.dp),label={Text("Search this directory")},singleLine=true)
        Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick=vm::cycleSort,label={Text("Sort: ${filter.sort.name.lowercase()}")})
            AssistChip(onClick=vm::cycleType,label={Text("Type: ${filter.type.name.lowercase()}")})
            Text("${entries.size} entries",style=MaterialTheme.typography.labelMedium)
        }
        if (selectedPaths.isNotEmpty()) {
            Surface(tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${selectedPaths.size} selected", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = vm::clearSelection) { Text("Clear") }
                Button(onClick = vm::reviewCleanup) { Text("Review cleanup") }
            } }
        }
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)) {
            items(entries,key={it.path}) { e -> EntryRow(e,session.totalBytes,e.path in selectedPaths,{vm.open(e)},{vm.select(e)},{vm.toggleSelection(e)}) }
            if(entries.isEmpty()) item { Box(Modifier.fillParentMaxSize(),contentAlignment=Alignment.Center){Text("No matching direct children")}}
        }
    }
}

@Composable
private fun Breadcrumbs(root:String,path:String,navigate:(String)->Unit){
    val relative=path.removePrefix(root).trim('/'); val parts=if(relative.isBlank()) emptyList() else relative.split('/')
    LazyRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){
        item{AssistChip(onClick={navigate(root)},label={Text(root.substringAfterLast('/').ifBlank{"/"})})}
        parts.forEachIndexed{index,part-> item(key="$index:$part"){Text("/");AssistChip(onClick={navigate(root.trimEnd('/')+"/"+parts.take(index+1).joinToString("/"))},label={Text(part)})}}
    }
}

@Composable
private fun EntryRow(entry:ScanEntryEntity,total:Long,selected:Boolean,open:()->Unit,details:()->Unit,toggle:()->Unit){
    val share=if(total>0)(entry.sizeBytes.toFloat()/total).coerceIn(0f,1f) else 0f
    Column(Modifier.fillMaxWidth().clickable(onClick=open).padding(vertical=9.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=selected,onCheckedChange={toggle()});Column(Modifier.weight(1f)){Text((if(entry.isDirectory)"▸ " else "")+entry.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text(entry.path,maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.labelSmall)};TextButton(onClick=details){Text("Info")};Text(formatBytes(entry.sizeBytes),fontWeight=FontWeight.SemiBold)}
        LinearProgressIndicator({share},Modifier.fillMaxWidth());Text("${entry.fileCount} files · ${formatTimestamp(entry.modifiedAt)}",style=MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SessionCard(s:ScanSessionEntity,cancel:()->Unit){val running=s.status in setOf(ScanStatus.STARTING.name,ScanStatus.RUNNING.name);Card(Modifier.fillMaxWidth().padding(12.dp)){Column(Modifier.padding(12.dp)){Row{Column(Modifier.weight(1f)){Text(s.rootPath,fontFamily=FontFamily.Monospace,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${s.status.lowercase()} · ${s.scannedItems} items · ${formatBytes(s.scannedBytes)}")};if(running)OutlinedButton(onClick=cancel){Text("Cancel")}};if(running)LinearProgressIndicator(Modifier.fillMaxWidth())}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetails(entry:ScanEntryEntity,info:EntryIntelligence?,close:()->Unit){ModalBottomSheet(onDismissRequest=close){LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text(entry.name,style=MaterialTheme.typography.headlineSmall);Text(entry.path,fontFamily=FontFamily.Monospace)};item{Detail("Size",formatBytes(entry.sizeBytes));Detail("Allocated",formatBytes(entry.allocatedBytes));Detail("Type",if(entry.isDirectory)"Directory" else "File");Detail("Files",entry.fileCount.toString());Detail("Modified",formatTimestamp(entry.modifiedAt))};info?.let{item{HorizontalDivider();Text("Android intelligence",style=MaterialTheme.typography.titleMedium);Detail("Category",it.category.label);Detail("Package",it.packageName?:"Not resolved");it.appLabel?.let{l->Detail("App",l)};Detail("Mount",it.mountPoint?:"Unknown");Detail("Filesystem",it.filesystem?:"Unknown");Detail("Mount mode",if(it.readOnly)"Read-only" else "Writable")}};item{Button(onClick=close,Modifier.fillMaxWidth()){Text("Close")};Spacer(Modifier.height(24.dp))}}}}
@Composable private fun Detail(label:String,value:String){Row(Modifier.fillMaxWidth()){Text(label,Modifier.width(110.dp),style=MaterialTheme.typography.labelMedium);Text(value,Modifier.weight(1f))}}


@Composable
private fun Insights(modifier: Modifier, delta: SnapshotDelta?, apps: List<AppStorageSummary>, operations: List<CleanupOperationEntity>, vm: MainViewModel) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Storage snapshots", style = MaterialTheme.typography.headlineSmall)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (delta == null) Text("Complete two scans of the same scope to compare growth.") else {
                        Detail("Net change", signedBytes(delta.totalDeltaBytes))
                        Detail("Added", formatBytes(delta.addedBytes))
                        Detail("Removed", formatBytes(delta.removedBytes))
                        Detail("Changed", delta.changedEntries.toString())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.scheduleLatest(24) }) { Text("Daily scans") }
                        OutlinedButton(onClick = vm::cancelSchedule) { Text("Disable") }
                    }
                }
            }
        }
        item { Text("Apps by measured footprint", style = MaterialTheme.typography.titleMedium) }
        items(apps, key = { it.packageName }) { app ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row { Text(app.packageName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(formatBytes(app.totalBytes)) }
                    Text("Private ${formatBytes(app.privateBytes)} · Cache ${formatBytes(app.cacheBytes)} · External ${formatBytes(app.externalBytes)}", style = MaterialTheme.typography.bodySmall)
                    if (app.orphaned) Text("Possibly orphaned package data", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (apps.isEmpty()) item { Text("No package-owned paths were identified in this scan.") }
        item { Text("Cleanup activity", style = MaterialTheme.typography.titleMedium) }
        items(operations, key = { it.id }) { operation ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(operation.status.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold); Text("${operation.selectedCount} items · ${formatBytes(operation.reclaimedBytes)}", style = MaterialTheme.typography.bodySmall) }
                if (operation.status in setOf("QUARANTINED", "PARTIAL")) OutlinedButton(onClick = { vm.restoreCleanup(operation.id) }) { Text("Restore") }
            } }
        }
        if (operations.isEmpty()) item { Text("No cleanup operations yet.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupReviewSheet(review: CleanupReview, close: () -> Unit, execute: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Review cleanup", style = MaterialTheme.typography.headlineSmall); Text("Quarantine is reversible. Permanent deletion is not part of Phase 4.") }
            item { Detail("Accepted", review.accepted.size.toString()); Detail("Blocked", review.blocked.size.toString()); Detail("Planned", formatBytes(review.plannedBytes)) }
            if (review.blocked.isNotEmpty()) item { Text("Blocked items", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            items(review.blocked, key = { it.path }) { blocked -> ListItem(headlineContent = { Text(blocked.path, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(blocked.reason) }) }
            review.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = close, Modifier.weight(1f)) { Text("Cancel") }; Button(onClick = execute, enabled = review.executable, modifier = Modifier.weight(1f)) { Text("Quarantine") } }; Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun signedBytes(value: Long): String = (if (value > 0) "+" else "") + formatBytes(value)
