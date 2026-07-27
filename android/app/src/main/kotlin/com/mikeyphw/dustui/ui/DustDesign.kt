package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class DustDestination(val label: String, val shortLabel: String, val symbol: String, val actionLabel: String) {
    SCAN("Scan", "Scan", "↯", "Options"),
    EXPLORER("Files", "Files", "▦", "Actions"),
    INSIGHTS("Storage health", "Home", "◌", "Export"),
    RECOMMENDATIONS("Cleanup", "Clean", "✦", "Actions"),
    RULES("Cleanup rules", "Rules", "≡", "Actions"),
    GROWTH("Growth history", "Growth", "↗", "Actions"),
    LARGE_FILES("Large files", "Large", "▤", "Actions"),
    DUPLICATES("Duplicates", "Dupes", "⧉", "Actions"),
    APP_RESIDUE("App residue", "Residue", "⌫", "Actions"),
    SEARCH("Root search", "Search", "⌕", "Tools"),
    PACKAGES("App storage", "Apps", "▣", "Apps"),
    MODULES("Modules", "Modules", "▥", "Tools"),
    CERTIFICATION("Certification", "Cert", "✓", "Run"),
    CLEANUP("Deletion reports", "Reports", "↺", "Actions"),
    MORE("Tools", "Tools", "•••", "Tools"),
}

object DustNavigationModel {
    val compactDestinations = listOf(
        DustDestination.INSIGHTS,
        DustDestination.EXPLORER,
        DustDestination.RECOMMENDATIONS,
        DustDestination.MORE,
    )

    val wideDestinations = listOf(
        DustDestination.SCAN,
        DustDestination.EXPLORER,
        DustDestination.INSIGHTS,
        DustDestination.RECOMMENDATIONS,
        DustDestination.RULES,
        DustDestination.GROWTH,
        DustDestination.LARGE_FILES,
        DustDestination.DUPLICATES,
        DustDestination.APP_RESIDUE,
        DustDestination.SEARCH,
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
    )

    val moreDestinations = listOf(
        DustDestination.RULES,
        DustDestination.GROWTH,
        DustDestination.LARGE_FILES,
        DustDestination.DUPLICATES,
        DustDestination.APP_RESIDUE,
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
    )

    fun compactSelection(destination: DustDestination): DustDestination = when (destination) {
        DustDestination.SCAN -> DustDestination.INSIGHTS
        DustDestination.SEARCH -> DustDestination.EXPLORER
        in compactDestinations -> destination
        else -> DustDestination.MORE
    }

    fun compactNavigationTarget(destination: DustDestination, hasScanData: Boolean): DustDestination = when (destination) {
        DustDestination.INSIGHTS -> if (hasScanData) DustDestination.INSIGHTS else DustDestination.SCAN
        else -> destination
    }

    fun needsScanData(destination: DustDestination): Boolean = destination !in setOf(DustDestination.SCAN, DustDestination.CLEANUP)

    fun refreshesInsights(destination: DustDestination): Boolean = destination in setOf(
        DustDestination.INSIGHTS,
        DustDestination.RECOMMENDATIONS,
        DustDestination.RULES,
        DustDestination.GROWTH,
        DustDestination.LARGE_FILES,
        DustDestination.DUPLICATES,
        DustDestination.APP_RESIDUE,
        DustDestination.SEARCH,
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
        DustDestination.MORE,
    )

    fun backTarget(destination: DustDestination, wide: Boolean): DustDestination? = when {
        !wide && destination in moreDestinations -> DustDestination.MORE
        !wide && destination == DustDestination.SEARCH -> DustDestination.EXPLORER
        !wide && destination == DustDestination.RECOMMENDATIONS -> DustDestination.INSIGHTS
        !wide && destination !in setOf(DustDestination.SCAN, DustDestination.INSIGHTS) -> DustDestination.INSIGHTS
        wide && destination != DustDestination.SCAN -> DustDestination.SCAN
        else -> null
    }
}

@Composable
fun DustSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PathText(value: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        SelectionContainer {
            Text(
                value,
                modifier = modifier,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    label,
                    Modifier.weight(0.34f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(0.66f)) { SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) } }
            }
        }
    }
}


@Composable
fun MetricGrid(metrics: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 420.dp) 1 else 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) ->
                        MetricTile(label = label, value = value, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 0.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun SegmentedChoice(
    first: String,
    second: String,
    secondSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.FilterChip(
            selected = !secondSelected,
            onClick = onFirst,
            label = { Text(first) },
        )
        androidx.compose.material3.FilterChip(
            selected = secondSelected,
            onClick = onSecond,
            label = { Text(second) },
        )
    }
}

@Composable
fun StatusPill(label: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier, shape = MaterialTheme.shapes.small, color = color, contentColor = contentColor) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
