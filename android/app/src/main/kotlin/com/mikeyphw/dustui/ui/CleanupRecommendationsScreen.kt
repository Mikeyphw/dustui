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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
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
    revealPath: (String) -> Unit,
) {
    var openRecommendationId by rememberSaveable { mutableStateOf<String?>(null) }
    val openRecommendation = analysis?.items?.firstOrNull { it.id == openRecommendationId }
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
            DustSectionHeader(
                "Cleanup recommendations",
                subtitle = "Explainable candidates ranked by reclaimable size, evidence confidence, deletion risk, and age.",
            )
        }
        item {
            MetricGrid(
                listOf(
                    "Active" to page.activeCount.toString(),
                    "Hidden" to page.hiddenCount.toString(),
                    "Matched size" to formatBytes(page.totalBytes),
                    "Batch-safe" to formatBytes(analysis?.highConfidenceBytes ?: 0L),
                ),
            )
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (scanDataStale) {
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "Results are stale after deletion. Browse and inspect freely, but selection and deletion planning require a fresh scan.",
                        Modifier.fillMaxWidth().padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Recommendations never delete automatically. Exact paths still pass through the scan-bound, fingerprinted permanent-deletion review.",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            Text("Category", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 24.dp),
            ) {
                item {
                    FilterChip(
                        selected = page.filter.kind == null,
                        onClick = { vm.filterRecommendationKind(null) },
                        label = { Text("All") },
                    )
                }
                items(CleanupRecommendationKind.entries, key = { it.name }) { kind ->
                    FilterChip(
                        selected = page.filter.kind == kind,
                        onClick = { vm.filterRecommendationKind(kind) },
                        label = { Text(kind.label) },
                    )
                }
            }
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
        if (page.items.isNotEmpty() && page.batchPaths.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Current page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${page.batchPaths.size} path(s) from active verified or high-confidence recommendations qualify for batch review.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = vm::selectRecommendationPage,
                                enabled = !scanDataStale,
                                modifier = Modifier.weight(1f),
                            ) { Text("Select safe") }
                            Button(
                                onClick = vm::reviewRecommendationPage,
                                enabled = !scanDataStale,
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
                    "No recommendations match",
                    if (page.hiddenCount > 0 && !page.filter.showHidden) {
                        "Change the filters or enable Show hidden to review dismissed and ignored recommendations."
                    } else {
                        "Run the duplicate analyzer, complete package visibility, or change the category and search filters."
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
                openPaths = { openRecommendationId = item.id },
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
            close = { openRecommendationId = null },
            revealPath = { path ->
                openRecommendationId = null
                revealPath(path)
            },
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                StatusPill("Score ${item.score}")
            }
            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                item { StatusPill(item.confidence.label) }
                item { StatusPill(item.risk.label, danger = item.risk.name == "HIGH") }
                if (!active) item { StatusPill(disposition.label, danger = disposition == CleanupRecommendationDisposition.IGNORED) }
            }
            MetricGrid(
                listOf(
                    "Reclaimable" to formatBytes(item.reclaimableBytes),
                    "Paths" to item.paths.size.toString(),
                    "Selected" to selectedCount.toString(),
                    "Batch eligible" to if (item.batchEligible) "Yes" else "No",
                ),
            )
            item.evidence.forEach { evidence ->
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
                    ) { Text("Select") }
                    Button(
                        onClick = { vm.reviewCleanupRecommendation(item.id) },
                        enabled = deletionEnabled,
                        modifier = Modifier.weight(1f),
                    ) { Text("Plan deletion") }
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
            DustSectionHeader(
                recommendation.title,
                subtitle = "Exact current-scan paths. Each action still uses the shared permanent-deletion safety pipeline.",
            )
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 560.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recommendation.paths, key = { "recommendation-path:$it" }) { path ->
                    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PathText(path, Modifier.fillMaxWidth(), maxLines = 4)
                            if (path in selectedPaths) Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.showPathDetails(path) }, modifier = Modifier.weight(1f)) { Text("Details") }
                                TextButton(onClick = { revealPath(path) }, modifier = Modifier.weight(1f)) { Text("Explorer") }
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
                                ) { Text("Plan") }
                            }
                        }
                    }
                }
            }
        }
    }
}
