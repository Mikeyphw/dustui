package com.mikeyphw.dustui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikeyphw.dustui.data.ChartSlice
import com.mikeyphw.dustui.data.SnapshotPoint
import kotlin.math.max

@Composable
fun StorageDonut(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    selected: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    if (slices.isEmpty() || slices.sumOf { it.bytes } <= 0L) {
        Text(
            "No measured storage data for this view.",
            modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val total = slices.sumOf { it.bytes }
    val colors = chartColors()
    val description = slices.joinToString { "${it.label} ${formatBytes(it.bytes)}" }
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 480.dp
        if (compact) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DonutPlot(slices, total, colors, selected, description)
                DonutLegend(slices, colors, selected, onSelect, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DonutPlot(slices, total, colors, selected, description)
                DonutLegend(slices, colors, selected, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DonutPlot(
    slices: List<ChartSlice>,
    total: Long,
    colors: List<Color>,
    selected: String?,
    description: String,
) {
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Storage composition: $description" }) {
            var start = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = 360f * slice.bytes.toFloat() / total.toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = if (selected == slice.label) 34f else 26f, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatBytes(total), fontWeight = FontWeight.Bold)
            Text("measured", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DonutLegend(
    slices: List<ChartSlice>,
    colors: List<Color>,
    selectedLabel: String?,
    onSelect: ((String) -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slices.forEachIndexed { index, slice ->
            val interactive = if (onSelect == null) Modifier else Modifier
                .clickable(role = Role.Button) { onSelect(slice.label) }
                .semantics { role = Role.Button; selected = selectedLabel == slice.label }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).then(interactive).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(10.dp)) { drawCircle(colors[index % colors.size]) }
                Spacer(Modifier.width(8.dp))
                Text(slice.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(slice.bytes), fontWeight = if (selectedLabel == slice.label) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun StorageBars(slices: List<ChartSlice>, modifier: Modifier = Modifier, onSelect: ((String) -> Unit)? = null) {
    val maximum = slices.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.forEach { slice ->
            val interactive = if (onSelect == null) Modifier else Modifier.clickable(role = Role.Button) { onSelect(slice.label) }
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (onSelect == null) 0.dp else 48.dp).then(interactive)
                    .semantics { contentDescription = "${slice.label}, ${formatBytes(slice.bytes)}" }
                    .padding(vertical = 4.dp),
            ) {
                Row { Text(slice.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); Text(formatBytes(slice.bytes)) }
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    drawRoundRect(trackColor, size = size)
                    drawRoundRect(barColor, size = Size(size.width * slice.bytes.toFloat() / maximum.toFloat(), size.height))
                }
            }
        }
    }
}

@Composable
fun SnapshotLineChart(points: List<SnapshotPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) { Text("Complete at least two scans to show a trend.", modifier); return }
    val ordered = points.sortedBy { it.timestamp }
    val minValue = ordered.minOf { it.totalBytes }
    val maxValue = ordered.maxOf { it.totalBytes }
    val range = max(1L, maxValue - minValue)
    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.tertiary
    val description = ordered.joinToString { "${formatTimestamp(it.timestamp)} ${formatBytes(it.totalBytes)}" }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Storage history: $description" }) {
            val step = size.width / (ordered.size - 1)
            val chartHeight = size.height - 12f
            val offsets = ordered.mapIndexed { index, point ->
                Offset(index * step, chartHeight - ((point.totalBytes - minValue).toFloat() / range.toFloat()) * chartHeight + 6f)
            }
            offsets.zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, strokeWidth = 5f, cap = StrokeCap.Round) }
            offsets.forEach { drawCircle(pointColor, radius = 7f, center = it) }
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(formatTimestamp(ordered.first().timestamp), style = MaterialTheme.typography.labelSmall)
                Text(formatBytes(ordered.first().totalBytes), style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTimestamp(ordered.last().timestamp), style = MaterialTheme.typography.labelSmall)
                Text(formatBytes(ordered.last().totalBytes), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun chartColors(): List<Color> = listOf(
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.secondary,
    MaterialTheme.colorScheme.tertiary,
    MaterialTheme.colorScheme.error,
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.secondaryContainer,
)
