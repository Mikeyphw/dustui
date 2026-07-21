package com.mikeyphw.dustui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class DustDestination(val label: String, val shortLabel: String, val symbol: String) {
    SCAN("Scan", "Scan", "◆"),
    EXPLORER("Explorer", "Files", "▤"),
    INSIGHTS("Overview", "Overview", "◎"),
    SEARCH("Root search", "Search", "⌕"),
    PACKAGES("Packages", "Apps", "◉"),
    MODULES("Modules", "Modules", "▣"),
    CERTIFICATION("Certification", "Cert", "✓"),
    CLEANUP("Cleanup", "Cleanup", "↺"),
    MORE("More", "More", "⋯"),
}

object DustNavigationModel {
    val compactDestinations = listOf(
        DustDestination.SCAN,
        DustDestination.EXPLORER,
        DustDestination.INSIGHTS,
        DustDestination.SEARCH,
        DustDestination.MORE,
    )

    val wideDestinations = listOf(
        DustDestination.SCAN,
        DustDestination.EXPLORER,
        DustDestination.INSIGHTS,
        DustDestination.SEARCH,
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
    )

    val moreDestinations = listOf(
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
    )

    fun compactSelection(destination: DustDestination): DustDestination =
        if (destination in compactDestinations) destination else DustDestination.MORE

    fun needsScanData(destination: DustDestination): Boolean = destination != DustDestination.SCAN

    fun refreshesInsights(destination: DustDestination): Boolean = destination in setOf(
        DustDestination.INSIGHTS,
        DustDestination.SEARCH,
        DustDestination.PACKAGES,
        DustDestination.MODULES,
        DustDestination.CERTIFICATION,
        DustDestination.CLEANUP,
        DustDestination.MORE,
    )
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
    SelectionContainer {
        Text(
            value,
            modifier = modifier,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )
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
