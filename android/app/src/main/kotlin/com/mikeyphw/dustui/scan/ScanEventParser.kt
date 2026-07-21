package com.mikeyphw.dustui.scan

import org.json.JSONObject

sealed interface ScanEvent {
    data class Start(val backend: String, val root: String) : ScanEvent
    data class Entry(
        val path: String,
        val parentPath: String,
        val name: String,
        val sizeBytes: Long,
        val allocatedBytes: Long,
        val directory: Boolean,
        val depth: Int,
        val modifiedAt: Long,
        val fileCount: Long,
    ) : ScanEvent
    data class Progress(val scannedItems: Long, val scannedBytes: Long, val currentPath: String) : ScanEvent
    data class Warning(val path: String, val message: String) : ScanEvent
    data class Complete(val totalBytes: Long, val scannedItems: Long, val scannedBytes: Long, val warnings: Long) : ScanEvent
    data class Cancelled(val scannedItems: Long, val scannedBytes: Long) : ScanEvent
    data class Error(val message: String) : ScanEvent
}

object ScanEventParser {
    fun parse(line: String): ScanEvent {
        val json = JSONObject(line)
        return when (json.getString("type")) {
            "start" -> ScanEvent.Start(json.getString("backend"), json.getString("root"))
            "entry" -> ScanEvent.Entry(
                path = json.getString("path"),
                parentPath = json.getString("parentPath"),
                name = json.getString("name"),
                sizeBytes = json.getLong("sizeBytes"),
                allocatedBytes = json.optLong("allocatedBytes", json.getLong("sizeBytes")),
                directory = json.getBoolean("directory"),
                depth = json.getInt("depth"),
                modifiedAt = json.optLong("modifiedAt", 0),
                fileCount = json.optLong("fileCount", 0),
            )
            "progress" -> ScanEvent.Progress(
                json.getLong("scannedItems"),
                json.getLong("scannedBytes"),
                json.optString("currentPath"),
            )
            "warning" -> ScanEvent.Warning(json.optString("path"), json.optString("message"))
            "complete" -> ScanEvent.Complete(
                json.getLong("totalBytes"),
                json.getLong("scannedItems"),
                json.getLong("scannedBytes"),
                json.optLong("warnings", 0),
            )
            "cancelled" -> ScanEvent.Cancelled(json.getLong("scannedItems"), json.getLong("scannedBytes"))
            "error" -> ScanEvent.Error(json.optString("message", "Native scanner failed"))
            else -> error("Unknown scan event type")
        }
    }
}
