package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikeyphw.dustui.data.CleanupOperationEntity
import com.mikeyphw.dustui.data.CleanupRule
import com.mikeyphw.dustui.data.CleanupRuleConditions
import com.mikeyphw.dustui.data.CleanupRuleEngine
import com.mikeyphw.dustui.data.CleanupRuleEntryType
import com.mikeyphw.dustui.data.CleanupRuleDraftPreview
import com.mikeyphw.dustui.data.CleanupRulePreview
import com.mikeyphw.dustui.data.CleanupRuleResidueConfidence
import com.mikeyphw.dustui.data.CleanupVerificationReport
import com.mikeyphw.dustui.data.CleanupVerificationStatus
import com.mikeyphw.dustui.data.StorageGrowthAnalysis
import com.mikeyphw.dustui.data.StorageGrowthEngine
import com.mikeyphw.dustui.data.StorageGrowthKind
import com.mikeyphw.dustui.data.StorageGrowthPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupRulesScreen(
    modifier: Modifier,
    rules: List<CleanupRule>,
    preview: CleanupRulePreview?,
    loading: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    var editing by remember { mutableStateOf<CleanupRule?>(null) }
    var pathMatch by remember { mutableStateOf<com.mikeyphw.dustui.data.CleanupRuleMatch?>(null) }
    val draftPreview by vm.cleanupRuleDraftPreview.collectAsStateWithLifecycle()
    val draftLoading by vm.cleanupRuleDraftLoading.collectAsStateWithLifecycle()
    var importing by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DustSectionHeader(
                "Saved cleanup rules",
                subtitle = "Rules find exact candidates in the current scan. They never delete automatically and cannot override protected paths.",
            )
        }
        if (scanDataStale) item { StaleRuleBanner() }
        item {
            MetricGrid(
                listOf(
                    "Rules" to rules.size.toString(),
                    "Enabled" to rules.count { it.enabled }.toString(),
                    "Exact matches" to (preview?.exactPaths?.size ?: 0).toString(),
                    "Review bytes" to formatBytes(preview?.exactBytes ?: 0),
                ),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 32.dp)) {
                item { Button(onClick = { vm.refreshCleanupRules() }, enabled = !loading) { Text(if (loading) "Previewing…" else "Refresh previews") } }
                item { OutlinedButton(onClick = { vm.clearCleanupRuleDraftPreview(); editing = newCleanupRule() }) { Text("New rule") } }
                item { OutlinedButton(onClick = { importing = true }) { Text("Import") } }
                item { OutlinedButton(onClick = vm::exportCleanupRules, enabled = rules.isNotEmpty()) { Text("Export") } }
                item { OutlinedButton(onClick = vm::selectAllCleanupRuleMatches, enabled = preview?.exactPaths?.isNotEmpty() == true && !scanDataStale) { Text("Select matches") } }
                item { OutlinedButton(onClick = vm::reviewAllCleanupRuleMatches, enabled = preview?.exactPaths?.isNotEmpty() == true && !scanDataStale) { Text("Plan matches") } }
            }
        }
        if (rules.isEmpty()) {
            item { EmptyState("No cleanup rules", "Create a rule or import a versioned JSON rule set. Imported rules must be reviewed before they can be enabled.") }
        } else {
            items(rules, key = { it.id }) { rule ->
                val match = preview?.matches?.firstOrNull { it.ruleId == rule.id }
                val validation = CleanupRuleEngine.validate(rule)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.name, fontWeight = FontWeight.SemiBold)
                                Text(ruleSummary(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { vm.toggleCleanupRule(rule.id, it) },
                                enabled = validation.valid && rule.reviewed,
                            )
                        }
                        if (rule.imported && !rule.reviewed) {
                            Text("Imported · open and save to mark reviewed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        } else if (rule.imported) {
                            Text("Imported · reviewed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        validation.errors.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        validation.warnings.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) }
                        if (match != null) {
                            MetricGrid(listOf("Matches" to match.entries.size.toString(), "Actionable" to match.actionableEntries.size.toString(), "Bytes" to formatBytes(match.actionableBytes)))
                            if (match.blocked.isNotEmpty()) Text("${match.blocked.size} protected or outside-root path(s) are excluded.", style = MaterialTheme.typography.labelSmall)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                            item { TextButton(onClick = { vm.clearCleanupRuleDraftPreview(); editing = rule }) { Text(if (rule.reviewed) "Edit" else "Review") } }
                            item { TextButton(onClick = { pathMatch = match }, enabled = match != null) { Text("View exact paths") } }
                            item { TextButton(onClick = { vm.duplicateCleanupRule(rule.id) }) { Text("Duplicate") } }
                            item { TextButton(onClick = { vm.selectCleanupRule(rule.id) }, enabled = match?.actionableEntries?.isNotEmpty() == true && !scanDataStale) { Text("Select") } }
                            item { TextButton(onClick = { vm.reviewCleanupRule(rule.id) }, enabled = match?.actionableEntries?.isNotEmpty() == true && !scanDataStale) { Text("Plan") } }
                            item { TextButton(onClick = { match?.actionableEntries?.firstOrNull()?.path?.let(revealPath) }, enabled = match?.actionableEntries?.isNotEmpty() == true) { Text("Explorer") } }
                            item { TextButton(onClick = { vm.deleteCleanupRule(rule.id) }) { Text("Delete") } }
                        }
                    }
                }
            }
        }
        if (selectedPaths.isNotEmpty()) item { Text("${selectedPaths.size} path(s) selected across workbenches", style = MaterialTheme.typography.labelMedium) }
    }
    editing?.let { rule ->
        CleanupRuleEditor(
            rule = rule,
            preview = draftPreview,
            previewing = draftLoading,
            close = { vm.clearCleanupRuleDraftPreview(); editing = null },
            requestPreview = vm::previewCleanupRuleDraft,
            save = { vm.saveCleanupRule(it); vm.clearCleanupRuleDraftPreview(); editing = null },
        )
    }
    pathMatch?.let { match ->
        CleanupRulePathSheet(match = match, close = { pathMatch = null }, revealPath = revealPath, vm = vm, scanDataStale = scanDataStale)
    }
    if (importing) {
        ModalBottomSheet(onDismissRequest = { importing = false }) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Import cleanup rules", style = MaterialTheme.typography.headlineSmall)
                Text("Paste DustUI rule JSON. Imported rules receive new IDs, stay disabled, and cannot be enabled until opened and saved.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(importText, { importText = it }, Modifier.fillMaxWidth().fillMaxHeight(0.55f), label = { Text("Versioned JSON") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { importing = false }) { Text("Cancel") }
                    Button(onClick = { vm.importCleanupRules(importText); importing = false; importText = "" }, enabled = importText.isNotBlank()) { Text("Import disabled") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupRulePathSheet(
    match: com.mikeyphw.dustui.data.CleanupRuleMatch,
    close: () -> Unit,
    revealPath: (String) -> Unit,
    vm: MainViewModel,
    scanDataStale: Boolean,
) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(match.ruleName, style = MaterialTheme.typography.headlineSmall)
                Text("${match.actionableEntries.size} actionable · ${formatBytes(match.actionableBytes)} · ${match.blocked.size} blocked")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.selectCleanupRule(match.ruleId) }, enabled = match.actionableEntries.isNotEmpty() && !scanDataStale) { Text("Select all") }
                    Button(onClick = { vm.reviewCleanupRule(match.ruleId) }, enabled = match.actionableEntries.isNotEmpty() && !scanDataStale) { Text("Plan deletion") }
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(match.entries, key = { it.path }) { entry ->
                    val blocked = match.blocked.firstOrNull { it.path == entry.path }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PathText(entry.path, Modifier.fillMaxWidth(), maxLines = 3)
                            Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelMedium)
                            blocked?.let { Text(it.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vm.select(entry) }) { Text("Details") }
                                TextButton(onClick = { revealPath(entry.path) }) { Text("Explorer") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaleRuleBanner() {
    Card(Modifier.fillMaxWidth()) {
        Text("Files changed after deletion. Rules may still be inspected, but selection and deletion planning require a fresh scan.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error)
    }
}

private fun newCleanupRule(): CleanupRule = CleanupRule(name = "New cleanup rule", enabled = false, conditions = CleanupRuleConditions())

private fun ruleSummary(rule: CleanupRule): String = buildList {
    val conditions = rule.conditions
    conditions.includePaths.firstOrNull()?.let { add("under $it") }
    if (conditions.extensions.isNotEmpty()) add(conditions.extensions.joinToString(prefix = ".", separator = ", ."))
    if (conditions.minimumBytes > 0) add("≥ ${formatBytes(conditions.minimumBytes)}")
    conditions.olderThanDays?.let { add("older than $it days") }
    conditions.maximumDepth?.let { add("depth ≤ $it from include root") }
    conditions.category?.let { add(it) }
    conditions.packageName?.let { add(it) }
    if (conditions.duplicateOnly) add("verified duplicates")
    if (conditions.residueConfidence != CleanupRuleResidueConfidence.ANY) add(conditions.residueConfidence.label)
}.ifEmpty { listOf("Broad current-scan match") }.joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanupRuleEditor(
    rule: CleanupRule,
    preview: CleanupRuleDraftPreview?,
    previewing: Boolean,
    close: () -> Unit,
    requestPreview: (CleanupRule) -> Unit,
    save: (CleanupRule) -> Unit,
) {
    var name by rememberSaveable(rule.id) { mutableStateOf(rule.name) }
    var include by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.includePaths.joinToString("\n")) }
    var exclude by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.excludePaths.joinToString("\n")) }
    var extensions by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.extensions.joinToString(",")) }
    var minimumMiB by rememberSaveable(rule.id) { mutableStateOf((rule.conditions.minimumBytes / (1024L * 1024L)).takeIf { it > 0 }?.toString().orEmpty()) }
    var maximumMiB by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.maximumBytes?.div(1024L * 1024L)?.toString().orEmpty()) }
    var olderDays by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.olderThanDays?.toString().orEmpty()) }
    var regex by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.regularExpression.orEmpty()) }
    var packageName by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.packageName.orEmpty()) }
    var maximumDepth by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.maximumDepth?.toString().orEmpty()) }
    var category by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.category) }
    var type by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.entryType) }
    var duplicateOnly by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.duplicateOnly) }
    var residue by rememberSaveable(rule.id) { mutableStateOf(rule.conditions.residueConfidence) }
    var showExactPaths by rememberSaveable(rule.id) { mutableStateOf(false) }
    val candidate = rule.copy(
        name = name.trim(),
        enabled = false,
        conditions = rule.conditions.copy(
            includePaths = include.lines().map(String::trim).filter(String::isNotBlank),
            excludePaths = exclude.lines().map(String::trim).filter(String::isNotBlank),
            extensions = extensions.split(',').map(String::trim).filter(String::isNotBlank),
            minimumBytes = minimumMiB.toLongOrNull()?.times(1024L * 1024L) ?: 0,
            maximumBytes = maximumMiB.toLongOrNull()?.times(1024L * 1024L),
            olderThanDays = olderDays.toIntOrNull(),
            category = category,
            packageName = packageName.trim().takeIf(String::isNotBlank),
            regularExpression = regex.trim().takeIf(String::isNotBlank),
            maximumDepth = maximumDepth.toIntOrNull(),
            entryType = type,
            duplicateOnly = duplicateOnly,
            residueConfidence = residue,
        ),
    )
    val validation = CleanupRuleEngine.validate(candidate)
    androidx.compose.runtime.LaunchedEffect(candidate) {
        kotlinx.coroutines.delay(350)
        if (validation.valid) requestPreview(candidate)
    }
    ModalBottomSheet(onDismissRequest = close) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(if (rule.imported && !rule.reviewed) "Review imported cleanup rule" else "Cleanup rule", style = MaterialTheme.typography.headlineSmall) }
            item { Text("Preview updates automatically after edits. Paths are candidates only; deletion still requires exact review.", style = MaterialTheme.typography.bodySmall) }
            item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true) }
            item { OutlinedTextField(include, { include = it }, Modifier.fillMaxWidth(), label = { Text("Include paths · one per line") }, supportingText = { Text("Maximum depth is measured relative to the longest matching include path, or the scan root.") }, minLines = 2) }
            item { OutlinedTextField(exclude, { exclude = it }, Modifier.fillMaxWidth(), label = { Text("Exclude paths · one per line") }, supportingText = { Text("Excluded descendants never enter the deletion plan.") }, minLines = 2) }
            item { OutlinedTextField(extensions, { extensions = it }, Modifier.fillMaxWidth(), label = { Text("Extensions · comma separated") }, singleLine = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minimumMiB, { minimumMiB = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Min MiB") }, singleLine = true)
                    OutlinedTextField(maximumMiB, { maximumMiB = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Max MiB") }, singleLine = true)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(olderDays, { olderDays = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Older days") }, singleLine = true)
                    OutlinedTextField(maximumDepth, { maximumDepth = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Relative max depth") }, singleLine = true)
                }
            }
            item { OutlinedTextField(packageName, { packageName = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Package ownership") }, supportingText = { Text("Matches package-oriented Android paths only.") }, singleLine = true) }
            item { OutlinedTextField(regex, { regex = it }, Modifier.fillMaxWidth(), label = { Text("Safe path expression") }, supportingText = { Text("Supports anchors, character classes, ?, and bounded {m,n}; groups, alternation, *, +, lookarounds, and backreferences are rejected.") }, singleLine = true) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { AssistChip(onClick = { type = CleanupRuleEntryType.entries[(type.ordinal + 1) % CleanupRuleEntryType.entries.size] }, label = { Text("Type: ${type.label}") }) }
                    item {
                        val categories = listOf<String?>(null, "Android packages", "Images", "Video", "Audio", "Archives", "Databases", "Logs and text", "Runtime and native code", "Other")
                        AssistChip(onClick = { category = categories[(categories.indexOf(category).coerceAtLeast(0) + 1) % categories.size] }, label = { Text("Category: ${category ?: "Any"}") })
                    }
                    item { FilterChip(selected = duplicateOnly, onClick = { duplicateOnly = !duplicateOnly }, label = { Text("Verified duplicates only") }) }
                    item { AssistChip(onClick = { residue = CleanupRuleResidueConfidence.entries[(residue.ordinal + 1) % CleanupRuleResidueConfidence.entries.size] }, label = { Text("Residue: ${residue.label}") }) }
                }
            }
            item {
                validation.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                validation.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                val result = preview?.takeIf { it.rule == candidate }?.match
                if (result != null) {
                    MetricGrid(listOf("Draft matches" to result.entries.size.toString(), "Actionable" to result.actionableEntries.size.toString(), "Bytes" to formatBytes(result.actionableBytes), "Blocked" to result.blocked.size.toString()))
                    TextButton(onClick = { showExactPaths = !showExactPaths }) { Text(if (showExactPaths) "Hide exact paths" else "Show exact paths") }
                    if (showExactPaths) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.entries.take(100).forEach { entry ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        PathText(entry.path, Modifier.fillMaxWidth(), maxLines = 3)
                                        Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                        result.blocked.firstOrNull { it.path == entry.path }?.let { Text(it.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                            if (result.entries.size > 100) Text("Showing the first 100 of ${result.entries.size} exact matches.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (previewing) {
                    Text("Updating live preview…", style = MaterialTheme.typography.labelMedium)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                    item { TextButton(onClick = close) { Text("Cancel") } }
                    item { OutlinedButton(onClick = { requestPreview(candidate) }, enabled = validation.valid && !previewing) { Text("Refresh now") } }
                    item { Button(onClick = { save(candidate) }, enabled = validation.valid) { Text(if (rule.imported && !rule.reviewed) "Mark reviewed and save" else "Save rule") } }
                }
            }
        }
    }
}

@Composable
fun StorageGrowthScreen(
    modifier: Modifier,
    analysis: StorageGrowthAnalysis?,
    page: StorageGrowthPage,
    loading: Boolean,
    selectedPaths: Set<String>,
    scanDataStale: Boolean,
    vm: MainViewModel,
    revealPath: (String) -> Unit,
) {
    val retention by vm.storageGrowthRetentionLimit.collectAsStateWithLifecycle()
    val sessionCount by vm.storageGrowthSessionCount.collectAsStateWithLifecycle()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DustSectionHeader("Storage change history", subtitle = "Choose both comparison endpoints, inspect multi-scan growth, and open the exact current files behind each change.") }
        if (analysis == null) {
            item { EmptyState("Two completed scans are required", if (loading) "Comparing scan history…" else "Run another scan to unlock exact storage-growth analysis.") }
            item { OutlinedButton(onClick = { vm.reloadStorageGrowth() }, enabled = !loading) { Text("Refresh comparison") } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${formatTimestamp(analysis.previousSession.completedAt?.div(1000) ?: analysis.previousSession.startedAt / 1000)} → ${formatTimestamp(analysis.currentSession.completedAt?.div(1000) ?: analysis.currentSession.startedAt / 1000)}", fontWeight = FontWeight.SemiBold)
                        MetricGrid(listOf("Net change" to signedBytes(analysis.totalDeltaBytes), "Added or grown" to formatBytes(analysis.addedBytes), "Removed or shrunk" to formatBytes(analysis.removedBytes), "Changed paths" to analysis.changedPaths.toString()))
                        Text("Current endpoint", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { TextButton(onClick = vm::compareCurrentNewer) { Text("Newer current") } }
                            item { TextButton(onClick = vm::compareCurrentOlder) { Text("Older current") } }
                        }
                        Text("Previous endpoint", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { TextButton(onClick = vm::comparePreviousNewer) { Text("Newer previous") } }
                            item { TextButton(onClick = vm::comparePreviousOlder) { Text("Older previous") } }
                            item { TextButton(onClick = { vm.reloadStorageGrowth() }, enabled = !loading) { Text("Refresh") } }
                        }
                    }
                }
            }
            if (analysis.timeline.size >= 2) item {
                StorageGrowthTimelineChart(analysis.timeline, vm::selectStorageGrowthTimelineScan)
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Snapshot retention", fontWeight = FontWeight.SemiBold)
                        Text("$sessionCount completed snapshot(s) loaded. Applying retention permanently prunes older scan entries and sessions.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = vm::cycleStorageGrowthRetention, label = { Text("Keep $retention") })
                            OutlinedButton(onClick = vm::applyStorageGrowthRetention, enabled = sessionCount > retention) { Text("Apply retention & prune") }
                        }
                    }
                }
            }
            if (!analysis.currentIsLatest) item {
                Card {
                    Text(
                        "This comparison ends at a historical snapshot. File actions, rule creation, and targeted rescans are disabled until the newest scan is selected.",
                        Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (analysis.insights.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Growth insights", fontWeight = FontWeight.SemiBold)
                        analysis.insights.take(12).forEach { insight ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(insight.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(insight.title, fontWeight = FontWeight.Medium)
                                    Text(insight.detail, style = MaterialTheme.typography.bodySmall)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item { TextButton(onClick = { vm.selectStorageGrowthInsight(insight.id) }, enabled = analysis.currentIsLatest && insight.currentPaths.isNotEmpty() && !scanDataStale) { Text("Select") } }
                                        item { TextButton(onClick = { vm.reviewStorageGrowthInsight(insight.id) }, enabled = analysis.currentIsLatest && insight.currentPaths.isNotEmpty() && !scanDataStale) { Text("Plan deletion") } }
                                        item { TextButton(onClick = { vm.rescanStorageGrowthInsight(insight.id) }, enabled = analysis.currentIsLatest && insight.currentPaths.isNotEmpty() && !scanDataStale) { Text("Targeted rescan") } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (scanDataStale) item { Card { Text("Current scan data is stale after deletion. Historical comparison remains readable, but deletion actions require a new scan.", Modifier.padding(14.dp), color = MaterialTheme.colorScheme.error) } }
            item { OutlinedTextField(vm.storageGrowthFilter.value.query, vm::updateStorageGrowthQuery, Modifier.fillMaxWidth(), label = { Text("Search changed paths") }, singleLine = true) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 32.dp)) {
                    item { AssistChip(onClick = vm::cycleStorageGrowthScope, label = { Text("Scope: ${vm.storageGrowthFilter.value.scope.label}") }) }
                    item { AssistChip(onClick = vm::cycleStorageGrowthGroup, label = { Text("Group: ${vm.storageGrowthFilter.value.group.label}") }) }
                    item { FilterChip(selected = vm.storageGrowthFilter.value.showUnderstood, onClick = vm::toggleUnderstoodStorageGrowth, label = { Text("Show understood (${page.hiddenUnderstood})") }) }
                }
            }
            if (page.groups.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Largest contributors by ${vm.storageGrowthFilter.value.group.label.lowercase()}", fontWeight = FontWeight.SemiBold)
                        page.groups.take(12).forEach { (label, delta) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(signedBytes(delta), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            if (page.items.isEmpty()) item { EmptyState("No visible changes", "Change the scope, search, endpoint range, grouping, or reveal understood items.") }
            items(page.items, key = { "${it.path}|${it.kind}" }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.kind.label, color = if (item.deltaBytes > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(signedBytes(item.deltaBytes), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (item.path in selectedPaths) Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(item.name.ifBlank { item.path }, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        PathText(item.path, Modifier.fillMaxWidth(), maxLines = 3)
                        Text("${formatBytes(item.previousBytes)} → ${formatBytes(item.currentBytes)}", style = MaterialTheme.typography.bodySmall)
                        Text("${item.groupExtension} · ${item.groupModule} · ${item.groupMount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                            if (item.currentEntry != null && analysis.currentIsLatest) {
                                item { TextButton(onClick = { vm.select(item.currentEntry) }) { Text("Details") } }
                                item { TextButton(onClick = { revealPath(item.path) }) { Text("Explorer") } }
                                item { TextButton(onClick = { vm.selectStorageGrowthPath(item.path) }, enabled = !scanDataStale) { Text("Select") } }
                                item { TextButton(onClick = { vm.reviewStorageGrowthPath(item.path) }, enabled = !scanDataStale) { Text("Plan deletion") } }
                                item { TextButton(onClick = { vm.createRuleFromGrowthPath(item.path) }, enabled = !scanDataStale) { Text("Create rule") } }
                                item { TextButton(onClick = { vm.rescanStorageGrowthPath(item.path) }, enabled = !scanDataStale) { Text("Targeted rescan") } }
                            } else {
                                item { Text(if (item.currentEntry == null) "Historical only" else "Historical snapshot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            item {
                                val understood = item.path in page.understoodPaths
                                TextButton(onClick = { if (understood) vm.restoreStorageGrowth(item.path) else vm.markStorageGrowthUnderstood(item.path) }) { Text(if (understood) "Restore" else "Understood") }
                            }
                        }
                    }
                }
            }
            if (page.pageCount > 1) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::previousStorageGrowthPage, enabled = page.pageIndex > 0) { Text("Previous") }
                    Text("Page ${page.pageIndex + 1} of ${page.pageCount}")
                    TextButton(onClick = vm::nextStorageGrowthPage, enabled = page.pageIndex + 1 < page.pageCount) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun StorageGrowthTimelineChart(
    points: List<com.mikeyphw.dustui.data.StorageGrowthTimelinePoint>,
    onSelect: (String) -> Unit,
) {
    val ordered = points.sortedBy { it.completedAt }
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Storage timeline", fontWeight = FontWeight.SemiBold)
            val description = ordered.joinToString { point -> "${formatTimestamp(point.completedAt / 1000)} ${formatBytes(point.totalBytes)}" }
            androidx.compose.foundation.Canvas(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).height(150.dp)
                    .semantics { contentDescription = "Storage timeline. $description" },
            ) {
                if (ordered.size < 2) return@Canvas
                val minimum = ordered.minOf { it.totalBytes }
                val maximum = ordered.maxOf { it.totalBytes }
                val range = (maximum - minimum).coerceAtLeast(1L)
                val horizontalStep = size.width / (ordered.size - 1)
                var previous: androidx.compose.ui.geometry.Offset? = null
                ordered.forEachIndexed { index, point ->
                    val x = index * horizontalStep
                    val normalized = (point.totalBytes - minimum).toFloat() / range.toFloat()
                    val y = size.height - normalized * size.height
                    val current = androidx.compose.ui.geometry.Offset(x, y)
                    previous?.let { drawLine(primary, it, current, strokeWidth = 4f) }
                    drawCircle(primary, radius = 7f, center = current)
                    previous = current
                }
            }
            Text("Accessible timeline points", style = MaterialTheme.typography.labelMedium, color = onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                items(ordered, key = { it.sessionId }) { point ->
                    AssistChip(
                        onClick = { onSelect(point.sessionId) },
                        label = { Text("${formatTimestamp(point.completedAt / 1000)} · ${formatBytes(point.totalBytes)}") },
                    )
                }
            }
        }
    }
}

private fun signedBytes(value: Long): String = when {
    value > 0 -> "+${formatBytes(value)}"
    value < 0 -> "−${formatBytes(-value)}"
    else -> "0 B"
}

private enum class CleanupReportStatusFilter(val label: String) {
    ALL("All statuses"), VERIFIED("Verified"), PARTIAL("Partial"), FAILED("Failed"), BLOCKED("Blocked"), CHANGED("Changed"), UNAVAILABLE("Verification unavailable")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupReportsScreen(
    modifier: Modifier,
    reports: List<CleanupVerificationReport>,
    operations: List<CleanupOperationEntity>,
    selected: CleanupVerificationReport?,
    vm: MainViewModel,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var rootFilter by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(CleanupReportStatusFilter.ALL) }
    var dateWindowDays by rememberSaveable { mutableStateOf(0) }
    val diagnostics by vm.cleanupReportDiagnostics.collectAsStateWithLifecycle()
    val verifyingOperation by vm.cleanupReportVerifying.collectAsStateWithLifecycle()
    val cutoff = if (dateWindowDays == 0) Long.MIN_VALUE else System.currentTimeMillis() - dateWindowDays * 86_400_000L
    val visibleReports = reports.filter { report ->
        val matchesQuery = query.isBlank() || report.operationId.contains(query, ignoreCase = true) ||
            report.items.any { it.path.contains(query, ignoreCase = true) }
        val matchesRoot = rootFilter.isBlank() || report.rootPath.contains(rootFilter, ignoreCase = true)
        val matchesDate = report.completedAt >= cutoff
        val matchesStatus = when (statusFilter) {
            CleanupReportStatusFilter.ALL -> true
            CleanupReportStatusFilter.VERIFIED -> report.fullyVerified
            CleanupReportStatusFilter.PARTIAL -> report.items.any { it.status == CleanupVerificationStatus.PARTIAL }
            CleanupReportStatusFilter.FAILED -> report.items.any { it.status == CleanupVerificationStatus.FAILED }
            CleanupReportStatusFilter.BLOCKED -> report.items.any { it.status == CleanupVerificationStatus.BLOCKED }
            CleanupReportStatusFilter.CHANGED -> report.items.any { it.status == CleanupVerificationStatus.CHANGED_SINCE_REVIEW }
            CleanupReportStatusFilter.UNAVAILABLE -> report.unknownVerificationCount > 0
        }
        matchesQuery && matchesRoot && matchesDate && matchesStatus
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DustSectionHeader("Deletion verification", subtitle = "Path verification is authoritative. Root-reported bytes and free-space change are supporting evidence, never proof for an individual file.") }
        if (diagnostics.isNotEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${diagnostics.size} report file(s) could not be loaded", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    diagnostics.take(5).forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search operation or path") }, singleLine = true) }
        item { OutlinedTextField(rootFilter, { rootFilter = it }, Modifier.fillMaxWidth(), label = { Text("Filter root") }, singleLine = true) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 24.dp)) {
                item { OutlinedButton(onClick = vm::refreshCleanupReports) { Text("Refresh reports") } }
                item {
                    AssistChip(
                        onClick = { statusFilter = CleanupReportStatusFilter.entries[(statusFilter.ordinal + 1) % CleanupReportStatusFilter.entries.size] },
                        label = { Text("Status: ${statusFilter.label}") },
                    )
                }
                item {
                    val windows = listOf(0, 7, 30, 90)
                    AssistChip(
                        onClick = { dateWindowDays = windows[(windows.indexOf(dateWindowDays).coerceAtLeast(0) + 1) % windows.size] },
                        label = { Text(if (dateWindowDays == 0) "Date: all" else "Date: ${dateWindowDays}d") },
                    )
                }
            }
        }
        if (reports.isEmpty()) {
            item { EmptyState("No verified deletion reports", "Future permanent-deletion operations will record exact per-path outcomes, attempted bytes, confirmed bytes, and environment context.") }
            if (operations.isNotEmpty()) item { Text("${operations.size} legacy operation record(s) predate verification reports.", style = MaterialTheme.typography.bodySmall) }
        } else if (visibleReports.isEmpty()) {
            item { EmptyState("No reports match", "Change the search, root, status, or date filter.") }
        } else {
            items(visibleReports, key = { it.operationId }) { report ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (report.fullyVerified) "Verified" else "Needs attention", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (report.fullyVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Text(formatTimestamp(report.completedAt / 1000), style = MaterialTheme.typography.labelMedium)
                        }
                        MetricGrid(
                            listOf(
                                "Confirmed" to formatBytes(report.confirmedDeletedBytes),
                                "Attempted" to formatBytes(report.attemptedBytes),
                                "Partial" to formatBytes(report.partialBytes),
                                "Failed" to formatBytes(report.failedBytes),
                            ),
                        )
                        PathText(report.rootPath, Modifier.fillMaxWidth(), maxLines = 2)
                        Text("${report.environment.manufacturer} ${report.environment.model} · SDK ${report.environment.sdkInt} · ${report.environment.filesystem ?: "filesystem unknown"}", style = MaterialTheme.typography.bodySmall)
                        Text("Verification attempts: ${report.verificationAttempts} · last ${formatTimestamp(report.lastVerifiedAt / 1000)}", style = MaterialTheme.typography.labelSmall)
                        report.measuredFreeSpaceDelta?.let { Text("Measured free-space change: ${signedBytes(it)}", style = MaterialTheme.typography.bodySmall) }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { TextButton(onClick = { vm.openCleanupReport(report.operationId) }) { Text("Details") } }
                            item { TextButton(onClick = { vm.retryCleanupVerification(report.operationId) }, enabled = verifyingOperation == null && !report.fullyVerified) { Text(if (verifyingOperation == report.operationId) "Verifying…" else "Verify again") } }
                            item { TextButton(onClick = { vm.exportCleanupReport(report.operationId, true) }) { Text("JSON") } }
                            item { TextButton(onClick = { vm.exportCleanupReport(report.operationId, false) }) { Text("Text") } }
                            item { TextButton(onClick = { vm.rescanCleanupReport(report.operationId) }) { Text("Rescan affected folder") } }
                        }
                    }
                }
            }
        }
    }
    if (selected != null) {
        ModalBottomSheet(onDismissRequest = vm::closeCleanupReport) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Verified deletion details", style = MaterialTheme.typography.headlineSmall)
                    Text("Operation ${selected.operationId}", style = MaterialTheme.typography.labelMedium)
                    PathText(selected.rootPath, Modifier.fillMaxWidth(), maxLines = 2)
                    Text("Confirmed ${formatBytes(selected.confirmedDeletedBytes)} of ${formatBytes(selected.plannedBytes)} planned", fontWeight = FontWeight.SemiBold)
                    Text("Attempted ${formatBytes(selected.attemptedBytes)} · partial ${formatBytes(selected.partialBytes)} · failed ${formatBytes(selected.failedBytes)}")
                    Text("${selected.environment.manufacturer} ${selected.environment.model} · SDK ${selected.environment.sdkInt}", style = MaterialTheme.typography.bodySmall)
                    Text("Mount ${selected.environment.mountPoint ?: "unknown"} · ${selected.environment.filesystem ?: "unknown"} · source ${selected.environment.mountSource ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.retryCleanupVerification(selected.operationId) }, enabled = verifyingOperation == null && !selected.fullyVerified) { Text(if (verifyingOperation == selected.operationId) "Verifying…" else "Verify again") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(selected.items, key = { it.path }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(verificationLabel(item.status), fontWeight = FontWeight.SemiBold, color = verificationColor(item.status))
                                PathText(item.path, Modifier.fillMaxWidth(), maxLines = 3)
                                Text("Planned ${formatBytes(item.plannedBytes)} · reported ${formatBytes(item.reportedDeletedBytes)} · path=${item.pathPresence.name.lowercase()}", style = MaterialTheme.typography.bodySmall)
                                item.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun verificationColor(status: CleanupVerificationStatus) = when (status) {
    CleanupVerificationStatus.VERIFIED_DELETED -> MaterialTheme.colorScheme.primary
    CleanupVerificationStatus.PARTIAL -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun verificationLabel(status: CleanupVerificationStatus): String = when (status) {
    CleanupVerificationStatus.VERIFIED_DELETED -> "Verified deleted"
    CleanupVerificationStatus.PARTIAL -> "Partial deletion"
    CleanupVerificationStatus.FAILED -> "Failed"
    CleanupVerificationStatus.BLOCKED -> "Blocked"
    CleanupVerificationStatus.CHANGED_SINCE_REVIEW -> "Changed since review"
}
