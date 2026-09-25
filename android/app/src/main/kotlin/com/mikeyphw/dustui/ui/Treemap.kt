package com.mikeyphw.dustui.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.TreemapInput
import com.mikeyphw.dustui.data.TreemapLayout
import kotlin.math.max

@Composable
fun StorageTreemap(entries: List<ScanEntryEntity>, onOpen: (ScanEntryEntity) -> Unit, modifier: Modifier = Modifier) {
    // Every visual block has a matching accessible row below it.
    val shown = entries.filter { it.sizeBytes > 0 }.sortedByDescending { it.sizeBytes }.take(16)
    val border = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val description = "Storage treemap. ${DustVisualAccessibility.treemap(shown)}"
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription = description }) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { 300.dp.toPx() }
            val textSizePx = with(density) { 11.sp.toPx() }
            val rects = remember(shown, widthPx, heightPx) { TreemapLayout.layout(shown.map { TreemapInput(it.path, it.sizeBytes) }, widthPx, heightPx) }
            Canvas(Modifier.matchParentSize().pointerInput(rects, shown) {
                detectTapGestures { point ->
                    rects.firstOrNull { point.x in it.left..it.right && point.y in it.top..it.bottom }
                        ?.let { hit -> shown.firstOrNull { it.path == hit.id }?.let(onOpen) }
                }
            }) {
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = labelColor
                    textSize = textSizePx
                    isFakeBoldText = true
                }
                rects.forEachIndexed { index, item ->
                    val rect = Rect(item.left, item.top, item.right, item.bottom)
                    drawRect(palette[index % palette.size], topLeft = rect.topLeft, size = rect.size)
                    drawRect(border, topLeft = rect.topLeft, size = rect.size, style = Stroke(width = 2f))
                    val entry = shown.firstOrNull { it.path == item.id }
                    if (entry != null && item.width > 72f && item.height > 34f) {
                        val maxChars = max(4, (item.width / 8f).toInt())
                        val label = entry.name.take(maxChars)
                        drawContext.canvas.nativeCanvas.drawText(label, item.left + 8f, item.top + 18f, textPaint)
                        if (item.height > 52f) drawContext.canvas.nativeCanvas.drawText(formatBytes(entry.sizeBytes), item.left + 8f, item.top + 34f, textPaint)
                    }
                }
            }
        }
        Text("Accessible largest items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        shown.forEachIndexed { index, entry ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onOpen(entry) }
                    .semantics { role = Role.Button; contentDescription = "Open ${entry.name}, ${formatBytes(entry.sizeBytes)}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(palette[index % palette.size]))
                Text(entry.name, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
