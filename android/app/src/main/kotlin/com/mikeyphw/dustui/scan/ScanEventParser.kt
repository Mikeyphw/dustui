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
    data class Warning(
        val code: String,
        val phase: String,
        val path: String,
        val message: String,
        val errno: Int?,
    ) : ScanEvent
    data class Complete(
        val totalBytes: Long,
        val scannedItems: Long,
        val scannedBytes: Long,
        val warnings: Long,
        val emittedEntries: Long,
        val emittedFiles: Long,
        val emittedDirectories: Long,
        val truncated: Boolean,
        val depthLimitedDirectories: Long,
        val skippedFilesystemEntries: Long,
        val rootOpened: Boolean,
    ) : ScanEvent
    data class Cancelled(val scannedItems: Long, val scannedBytes: Long) : ScanEvent
    data class Error(
        val code: String,
        val phase: String,
        val path: String,
        val message: String,
        val errno: Int?,
    ) : ScanEvent
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
            "warning" -> ScanEvent.Warning(
                code = json.optString("code", "SCANNER_WARNING"),
                phase = json.optString("phase", "traversal"),
                path = json.optString("path"),
                message = json.optString("message", "Native scanner warning"),
                errno = json.optInt("errno").takeIf { json.has("errno") && !json.isNull("errno") },
            )
            "complete" -> ScanEvent.Complete(
                totalBytes = json.getLong("totalBytes"),
                scannedItems = json.getLong("scannedItems"),
                scannedBytes = json.getLong("scannedBytes"),
                warnings = json.optLong("warnings", 0),
                emittedEntries = json.optLong("emittedEntries", 0),
                emittedFiles = json.optLong("emittedFiles", 0),
                emittedDirectories = json.optLong("emittedDirectories", 0),
                truncated = json.optBoolean("truncated"),
                depthLimitedDirectories = json.optLong("depthLimitedDirectories", 0),
                skippedFilesystemEntries = json.optLong("skippedFilesystemEntries", 0),
                rootOpened = json.optBoolean("rootOpened"),
            )
            "cancelled" -> ScanEvent.Cancelled(json.getLong("scannedItems"), json.getLong("scannedBytes"))
            "error" -> ScanEvent.Error(
                code = json.optString("code", "NATIVE_SCANNER_FAILED"),
                phase = json.optString("phase", "scanner"),
                path = json.optString("path"),
                message = json.optString("message", "Native scanner failed"),
                errno = json.optInt("errno").takeIf { json.has("errno") && !json.isNull("errno") },
            )
            else -> error("Unknown scan event type")
        }
    }
}
