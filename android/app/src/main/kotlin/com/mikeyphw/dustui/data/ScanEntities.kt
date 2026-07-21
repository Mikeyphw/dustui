package com.mikeyphw.dustui.data

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "scan_sessions", primaryKeys = ["id"])
data class ScanSessionEntity(
    val id: String,
    val presetId: String?,
    val rootPath: String,
    val optionsJson: String,
    val status: String,
    val backend: String = "",
    val startedAt: Long,
    val completedAt: Long? = null,
    val scannedItems: Long = 0,
    val scannedBytes: Long = 0,
    val totalBytes: Long = 0,
    val warningCount: Long = 0,
    val currentPath: String = "",
    val errorMessage: String? = null,
)

@Entity(
    tableName = "scan_entries",
    primaryKeys = ["scanId", "path"],
    indices = [Index("scanId"), Index(value = ["scanId", "sizeBytes"])],
)
data class ScanEntryEntity(
    val scanId: String,
    val path: String,
    val parentPath: String,
    val name: String,
    val sizeBytes: Long,
    val allocatedBytes: Long,
    val isDirectory: Boolean,
    val depth: Int,
    val modifiedAt: Long,
    val fileCount: Long,
)
