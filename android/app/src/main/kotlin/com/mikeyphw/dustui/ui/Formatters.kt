package com.mikeyphw.dustui.ui

import java.text.DateFormat
import java.util.Date
import kotlin.math.ln
import kotlin.math.pow

fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    val index = (ln(value.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size) - 1
    return "%.1f %s".format(value / 1024.0.pow(index + 1), units[index])
}

fun formatTimestamp(seconds: Long): String = if (seconds <= 0) "Unknown" else
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(seconds * 1000))
