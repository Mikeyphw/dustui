package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikeyphw.dustui.data.CleanupRecommendation
import com.mikeyphw.dustui.data.CleanupRecommendationAnalysis
import com.mikeyphw.dustui.data.CleanupRecommendationDisposition
import com.mikeyphw.dustui.data.CleanupRecommendationKind
import com.mikeyphw.dustui.data.CleanupRecommendationPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupRecommendationsScreen(
    modifier: Modifier,
    analysis: CleanupRecommendationAnalysis?,
    page: CleanupRecommendationPage,
    loading: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    selectedRecommendationId: String?,
    openCandidate: (String) -> Unit,
    closeCandidate: () -> Unit,
    openWorkbench: (DustSemanticRoute) -> Unit,
    revealPath: (String) -> Unit,
) {
    val openRecommendation = analysis?.items?.firstOrNull { it.id == selectedRecommendationId }
    val totalBytes = analysis?.totalBytes ?: page.totalBytes
    val batchSafeBytes = analysis?.highConfidenceBytes ?: 0L
    val selectedVisiblePaths = page.items.sumOf { recommendation -> recommendation.paths.count { it in selectedPaths } }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 128.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustHeroCard(
                eyebrow = "Cleanup cockpit",
                title = "Recover up to ${formatBytes(totalBytes)}",
                detail = "Review-first cleanup cockpit for recommendations, duplicates, large files, app residue, and reusable rules.",
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DustMetricTile("Active", page.activeCount.toString(), Modifier.weight(1f), detail = "visible candidates")
                    DustMetricTile("Safe batch", formatBytes(batchSafeBytes), Modifier.weight(1f), detail = "verified or high-confidence")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DustMetricTile("Selected", selectedVisiblePaths.toString(), Modifier.weight(1f), detail = "current paths")
                    DustMetricTile("Hidden", page.hiddenCount.toString(), Modifier.weight(1f), detail = "dismissed or ignored")
                }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (scanDataStale) {
            item {
                DustStatusNotice(
                    title = "Stale cleanup locked",
                    detail = "Results are stale after deletion. Browse and inspect freely, but selection and deletion planning require a fresh scan.",
                    tone = DustNoticeTone.Warning,
                )
            }
        }
        item {
            DustStatusNotice(
                title = "Permanent deletion stays review-first",
                detail = "Recommendations never delete automatically. Exact paths still pass through the scan-bound, fingerprinted permanent-deletion review.",
                tone = DustNoticeTone.Success,
            )
        }
        item {
            CleanupCockpitChips(
                page = page,
                openWorkbench = openWorkbench,
                filterKind = vm::filterRecommendationKind,
            )
        }
        item {
            OutlinedTextField(
                value = page.filter.query,
                onValueChange = vm::updateRecommendationQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find by title, explanation, or path") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::cycleRecommendationSort, modifier = Modifier.weight(1f)) {
                    Text("Sort: ${page.filter.sort.label}")
                }
                FilterChip(
                    selected = page.filter.showHidden,
                    onClick = vm::toggleHiddenRecommendations,
                    label = { Text(if (page.filter.showHidden) "Showing hidden" else "Show hidden") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (page.items.isNotEmpty() && page.batchPaths.isNotEmpty() && !scanDataStale) {
            item {
                DustActionDock(
                    title = "${page.batchPaths.size} safe path(s) on this page",
                    detail = "Only active verified or high-confidence recommendations qualify for batch review.",
                    actionLabel = "Plan safe",
                    onAction = vm::reviewRecommendationPage,
                    modifier = Modifier.fillMaxWidth(),
                    secondaryAction = {
                        TextButton(onClick = vm::selectRecommendationPage, enabled = !scanDataStale) { Text("Select safe") }
                    },
                )
            }
        }
        if (page.items.isEmpty() && !loading) {
            item {
                EmptyState(
                    "No recommendations match",
                    if (page.hiddenCount > 0 && !page.filter.showHidden) {
                        "Change the filters or enable Show hidden to review dismissed and ignored recommendations."
                    } else {
                        "Run the duplicate analyzer, complete package visibility, or change the cleanup cockpit filter."
                    },
                )
            }
        }
        items(page.items, key = { "recommendation:${it.id}" }) { item ->
            RecommendationCard(
                item = item,
                disposition = page.dispositions[item.id] ?: CleanupRecommendationDisposition.ACTIVE,
                selectedCount = item.paths.count { it in selectedPaths },
                deletionEnabled = !scanDataStale,
                vm = vm,
                openPaths = { openCandidate(item.id) },
            )
        }
        if (page.pageCount > 1) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::previousRecommendationPage, enabled = page.hasPrevious, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Text("${page.pageIndex + 1} / ${page.pageCount}", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = vm::nextRecommendationPage, enabled = page.hasNext, modifier = Modifier.weight(1f)) { Text("Next") }
                }
            }
        }
    }
    if (openRecommendation != null) {
        RecommendationPathsSheet(
            recommendation = openRecommendation,
            selectedPaths = selectedPaths,
            deletionEnabled = !scanDataStale,
            vm = vm,
            close = closeCandidate,
            revealPath = { path ->
                closeCandidate()
                revealPath(path)
            },
        )
    }
}

@Composable
private fun CleanupCockpitChips(
    page: CleanupRecommendationPage,
    openWorkbench: (DustSemanticRoute) -> Unit,
    filterKind: (CleanupRecommendationKind?) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cleanup cockpit", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
            item {
                FilterChip(
                    selected = page.filter.kind == null,
                    onClick = { filterKind(null) },
                    label = { Text("Recommended") },
                )
            }
            item {
                FilterChip(
                    selected = page.filter.kind == CleanupRecommendationKind.EXACT_DUPLICATES,
                    onClick = { openWorkbench(DustSemanticRoute.CleanDuplicates) },
                    label = { Text("Duplicates") },
                )
            }
            item {
                FilterChip(
                    selected = page.filter.kind == CleanupRecommendationKind.LARGE_OLD_FILE,
                    onClick = { openWorkbench(DustSemanticRoute.FilesLarge) },
                    label = { Text("Large files") },
                )
            }
            item {
                FilterChip(
                    selected = page.filter.kind == CleanupRecommendationKind.APP_RESIDUE,
                    onClick = { openWorkbench(DustSemanticRoute.CleanResidue) },
                    label = { Text("App residue") },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = { openWorkbench(DustSemanticRoute.CleanRules) },
                    label = { Text("Rules") },
                )
            }
            items(CleanupRecommendationKind.entries, key = { it.name }) { kind ->
                if (kind !in setOf(CleanupRecommendationKind.EXACT_DUPLICATES, CleanupRecommendationKind.LARGE_OLD_FILE, CleanupRecommendationKind.APP_RESIDUE)) {
                    FilterChip(
                        selected = page.filter.kind == kind,
                        onClick = { filterKind(kind) },
                        label = { Text(kind.label) },
                    )
                }
            }
        }
        Text(
            "Recommended stays in this cockpit; Duplicates, Large files, App residue, and Rules open their specialized workbenches without bypassing review.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecommendationCard(
    item: CleanupRecommendation,
    disposition: CleanupRecommendationDisposition,
    selectedCount: Int,
    deletionEnabled: Boolean,
    vm: MainViewModel,
    openPaths: () -> Unit,
) {
    val active = disposition == CleanupRecommendationDisposition.ACTIVE
    val safeBatch = item.batchEligible && active
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selectedCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                StatusPill("Score ${item.score}")
            }
            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                item { StatusPill(if (safeBatch) "Safe to batch" else "Manual review", danger = !safeBatch) }
                item { StatusPill(item.confidence.label) }
                item { StatusPill(item.risk.label, danger = item.risk.name == "HIGH") }
                if (!active) item { StatusPill(disposition.label, danger = disposition == CleanupRecommendationDisposition.IGNORED) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DustMetricTile("Reclaimable", formatBytes(item.reclaimableBytes), Modifier.weight(1f))
                DustMetricTile("Paths", item.paths.size.toString(), Modifier.weight(1f), detail = "${selectedCount} selected")
            }
            item.evidence.take(3).forEach { evidence ->
                Text(
                    "${evidence.label}: ${evidence.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = openPaths, modifier = Modifier.fillMaxWidth()) {
                Text("View ${item.paths.size} actionable path(s)")
            }
            if (active) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.selectCleanupRecommendation(item.id) },
                        enabled = deletionEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (safeBatch) "Select safe" else "Select") }
                    Button(
                        onClick = { vm.reviewCleanupRecommendation(item.id) },
                        enabled = deletionEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Review deletion") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.createRuleFromRecommendation(item.id) }, modifier = Modifier.weight(1f)) { Text("Create rule") }
                    TextButton(onClick = { vm.dismissCleanupRecommendation(item.id) }, modifier = Modifier.weight(1f)) { Text("Dismiss") }
                    TextButton(onClick = { vm.ignoreCleanupRecommendation(item.id) }, modifier = Modifier.weight(1f)) { Text("Ignore") }
                }
            } else {
                Button(onClick = { vm.restoreCleanupRecommendation(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Restore recommendation") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendationPathsSheet(
    recommendation: CleanupRecommendation,
    selectedPaths: Set<String>,
    deletionEnabled: Boolean,
    vm: MainViewModel,
    close: () -> Unit,
    revealPath: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DustBottomSheetHeader(
                title = recommendation.title,
                detail = "Exact current-scan paths. Each action still uses the shared permanent-deletion safety pipeline.",
                onClose = close,
            )
            DustStatusNotice(
                title = "Path review required",
                detail = "Changed, missing, protected, or hash-mismatched entries are skipped by the permanent deletion planner.",
                tone = DustNoticeTone.Success,
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recommendation.paths, key = { "recommendation-path:$it" }) { path ->
                    Surface(Modifier.fillMaxWidth(), tonalElevation = 0.dp, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PathText(path, Modifier.fillMaxWidth(), maxLines = 4)
                            if (path in selectedPaths) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.showPathDetails(path) }, modifier = Modifier.weight(1f)) { Text("Details") }
                                TextButton(onClick = { revealPath(path) }, modifier = Modifier.weight(1f)) { Text("Files") }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { vm.toggleCleanupRecommendationPath(recommendation.id, path) },
                                    enabled = deletionEnabled,
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (path in selectedPaths) "Unselect" else "Select") }
                                Button(
                                    onClick = { vm.reviewCleanupRecommendationPath(recommendation.id, path) },
                                    enabled = deletionEnabled,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Review") }
                            }
                        }
                    }
                }
            }
        }
    }
}
