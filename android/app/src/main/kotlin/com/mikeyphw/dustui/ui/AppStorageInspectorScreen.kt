package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikeyphw.dustui.data.AppStorageBucket
import com.mikeyphw.dustui.data.AppStorageBucketKind
import com.mikeyphw.dustui.data.AppStorageInspectorAnalysis
import com.mikeyphw.dustui.data.AppStorageInspectorPage
import com.mikeyphw.dustui.data.AppStoragePackageReport
import com.mikeyphw.dustui.data.AppStoragePathItem
import com.mikeyphw.dustui.data.ChartSlice

@Composable
fun AppStorageInspectorScreen(
    modifier: Modifier,
    analysis: AppStorageInspectorAnalysis?,
    page: AppStorageInspectorPage,
    loading: Boolean,
    selectedPackage: String?,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
    onSelectPackage: (String) -> Unit,
) {
    var expandedBucket by rememberSaveable(selectedPackage) { mutableStateOf<String?>(null) }
    val filter by vm.appStorageFilter.collectAsStateWithLifecycle()
    val report = page.items.firstOrNull { it.packageName == selectedPackage }
        ?: page.items.firstOrNull()
        ?: analysis?.packages?.firstOrNull { it.packageName == selectedPackage }
        ?: analysis?.packages?.firstOrNull()
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustSectionHeader(
                "App storage inspector",
                subtitle = "Package-centered APK, internal data, cache, OBB, media, logs, exports, duplicates, residue, and recent growth.",
            )
        }
        if (scanDataStale) item {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Filesystem contents changed after deletion. App storage remains readable, but selection and deletion planning require a fresh scan.",
                    Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = vm::updateAppStorageQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find app or package") },
                singleLine = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, end = 16.dp)) {
                item { FilterChip(filter.installedOnly, vm::toggleInstalledAppStorageOnly, label = { Text("Installed only") }) }
                item { AssistChip(vm::cycleAppStorageSort, label = { Text("Sort: ${filter.sort.label}") }) }
            }
            Text("Showing ${page.items.size} of ${page.totalMatches} packages", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (loading && analysis == null) {
            item { EmptyState("Inspecting app storage", "Building package buckets, duplicate links, residue evidence, and growth totals…") }
        } else if (page.items.isEmpty()) {
            item { EmptyState("No package storage", "Run a complete scan or broaden the package filter.") }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                    items(page.items, key = { it.packageName }) { candidate ->
                        Card(onClick = { onSelectPackage(candidate.packageName) }, modifier = Modifier.width(230.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(candidate.appLabel, fontWeight = if (candidate.packageName == report?.packageName) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                PathText(candidate.packageName, Modifier.fillMaxWidth(), maxLines = 2)
                                Text("${formatBytes(candidate.totalBytes)} · ${if (candidate.installed) "installed" else "not installed"}", style = MaterialTheme.typography.labelMedium)
                                if (candidate.recentGrowthBytes != 0L) Text("Change ${signedBytes(candidate.recentGrowthBytes)}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (page.pageCount > 1) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(vm::previousAppStoragePage, enabled = page.pageIndex > 0) { Text("Previous") }
                    Text("Page ${page.pageIndex + 1} of ${page.pageCount}", style = MaterialTheme.typography.labelMedium)
                    TextButton(vm::nextAppStoragePage, enabled = page.pageIndex + 1 < page.pageCount) { Text("Next") }
                }
            }
            report?.let { selected ->
                item { AppPackageSummary(selected, vm) }
                item {
                    val slices = selected.buckets.filter { it.bytes > 0L }.map { ChartSlice(it.kind.label, it.bytes, it.fileCount) }
                    StorageDonut(
                        slices = slices,
                        modifier = Modifier.fillMaxWidth(),
                        selected = expandedBucket,
                        onSelect = { expandedBucket = if (expandedBucket == it.label) null else it.label },
                    )
                }
                items(selected.buckets, key = { it.kind.name }) { bucket ->
                    AppStorageBucketCard(selected, bucket, expandedBucket == bucket.kind.label, selectedPaths, scanDataStale, vm, revealPath) {
                        expandedBucket = if (expandedBucket == bucket.kind.label) null else bucket.kind.label
                    }
                }
                if (selected.duplicatePaths.isNotEmpty()) item {
                    SpecialPathGroup(
                        title = "Verified duplicate copies",
                        detail = "${selected.duplicatePaths.size} SHA-256-verified removable copies · ${formatBytes(selected.duplicateBytes)}",
                        paths = selected.duplicatePaths,
                        selectedPaths = selectedPaths,
                        scanDataStale = scanDataStale,
                        vm = vm,
                        revealPath = revealPath,
                        selectAll = { vm.selectAppStorageDuplicates(selected.packageName) },
                        planAll = { vm.reviewAppStorageDuplicates(selected.packageName) },
                    )
                }
                if (selected.residuePaths.isNotEmpty()) item {
                    SpecialPathGroup(
                        title = "Uninstalled-app residue",
                        detail = "${selected.residuePaths.size} high-confidence location(s) · ${formatBytes(selected.residueBytes)}",
                        paths = selected.residuePaths,
                        selectedPaths = selectedPaths,
                        scanDataStale = scanDataStale,
                        vm = vm,
                        revealPath = revealPath,
                        selectAll = null,
                        planAll = { vm.reviewAppStorageResidue(selected.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPackageSummary(report: AppStoragePackageReport, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(report.appLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            PathText(report.packageName, Modifier.fillMaxWidth(), maxLines = 2)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(if (report.installed) "installed" else "not installed", danger = !report.installed)
                if (report.systemApp) StatusPill("system app")
                if (report.recommendationsIgnored) StatusPill("recommendations ignored")
            }
            MetricGrid(listOf(
                "Measured total" to formatBytes(report.totalBytes),
                "Recent change" to signedBytes(report.recentGrowthBytes),
                "Base APK" to formatBytes(report.baseApkBytes),
                "Split APKs" to formatBytes(report.splitApkBytes),
                "Native / other code" to formatBytes(report.nativeAndOtherCodeBytes),
                "Private state" to formatBytes(report.privateStateBytes),
                "Cache" to formatBytes(report.cacheBytes),
                "Code cache" to formatBytes(report.codeCacheBytes),
                "OBB" to formatBytes(report.obbBytes),
                "Shared media" to formatBytes(report.sharedMediaBytes),
                "Logs" to formatBytes(report.logBytes),
                "Exports / backups" to formatBytes(report.exportBackupBytes),
                "Verified duplicates" to formatBytes(report.duplicateBytes),
                "Residue" to formatBytes(report.residueBytes),
            ))
            report.versionName?.let { Text("Version $it${report.versionCode?.let { code -> " ($code)" }.orEmpty()}", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (report.installed) {
                    Button(onClick = { vm.openAppStorageSettings(report.packageName) }) { Text("App settings") }
                    OutlinedButton(onClick = { vm.requestAppUninstall(report.packageName) }, enabled = !report.systemApp) { Text("Uninstall") }
                }
            }
            TextButton(onClick = { vm.setAppStorageRecommendationsIgnored(report.packageName, !report.recommendationsIgnored) }) {
                Text(if (report.recommendationsIgnored) "Restore cleanup recommendations" else "Ignore cleanup recommendations for this app")
            }
        }
    }
}

@Composable
private fun AppStorageBucketCard(
    report: AppStoragePackageReport,
    bucket: AppStorageBucket,
    expanded: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
    toggleExpanded: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(bucket.kind.label, fontWeight = FontWeight.SemiBold)
                    Text("${formatBytes(bucket.bytes)} · ${bucket.fileCount} file(s) · ${bucket.kind.risk.label}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = toggleExpanded) { Text(if (expanded) "Hide" else "Files") }
            }
            val settingsOnly = report.installed && bucket.kind in setOf(AppStorageBucketKind.CACHE, AppStorageBucketKind.CODE_CACHE)
            if (settingsOnly) {
                Text("Active app cache is not deleted directly. Use Android app settings so the platform owns the operation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.openAppStorageSettings(report.packageName) }) { Text("Clear cache in settings") }
            } else if (bucket.deletionPaths.isEmpty()) {
                Text(bucket.paths.firstOrNull()?.deletionReason ?: "This bucket is informational.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.selectAppStorageBucket(report.packageName, bucket.kind) }, enabled = !scanDataStale) { Text("Select eligible") }
                    Button(onClick = { vm.reviewAppStorageBucket(report.packageName, bucket.kind) }, enabled = !scanDataStale) { Text("Plan deletion") }
                }
            }
            if (expanded) bucket.paths.take(20).forEach { path ->
                InspectorPathRow(path, vm, selected = path.path in selectedPaths, revealPath = revealPath)
            }
            if (expanded && bucket.paths.size > 20) Text("Showing 20 of ${bucket.paths.size} files. Use Explorer for the full list.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SpecialPathGroup(
    title: String,
    detail: String,
    paths: List<AppStoragePathItem>,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
    selectAll: (() -> Unit)?,
    planAll: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selectAll?.let { OutlinedButton(it, enabled = !scanDataStale) { Text("Select all") } }
                Button(planAll, enabled = !scanDataStale && paths.any { it.deletionEligible }) { Text("Plan deletion") }
            }
            paths.take(12).forEach { item ->
                InspectorPathRow(item, vm, item.path in selectedPaths, revealPath)
            }
            if (paths.size > 12) Text("Showing 12 of ${paths.size} paths.", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun InspectorPathRow(
    item: AppStoragePathItem,
    vm: MainViewModel,
    selected: Boolean = false,
    revealPath: ((String) -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.name.ifBlank { item.path.substringAfterLast('/') }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PathText(item.path, Modifier.fillMaxWidth(), maxLines = 2)
            Text("${formatBytes(item.sizeBytes)} · ${item.deletionReason}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.showPathDetails(item.path) }) { Text("Details") }
                revealPath?.let { TextButton(onClick = { it(item.path) }) { Text("Explorer") } }
                if (item.deletionEligible) TextButton(onClick = { vm.togglePathSelection(item.path) }) { Text(if (selected) "Unselect" else "Select") }
                if (item.deletionEligible || item.protected) {
                    TextButton(onClick = { vm.protectAppStoragePath(item.path, !item.protected) }) { Text(if (item.protected) "Unprotect" else "Protect") }
                }
            }
        }
    }
}

private fun signedBytes(value: Long): String = when {
    value > 0L -> "+${formatBytes(value)}"
    value < 0L -> "-${formatBytes(-value)}"
    else -> "No change"
}
