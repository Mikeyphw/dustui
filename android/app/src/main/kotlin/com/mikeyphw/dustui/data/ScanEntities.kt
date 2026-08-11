package com.mikeyphw.dustui.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
    @ColumnInfo(defaultValue = "1") val generation: Long = 1L,
    @ColumnInfo(defaultValue = "0") val observedEntryCount: Long = 0L,
    @ColumnInfo(defaultValue = "0") val persistedEntryCount: Long = 0L,
    @ColumnInfo(defaultValue = "0") val malformedEventCount: Long = 0L,
    @ColumnInfo(defaultValue = "0") val truncated: Boolean = false,
    @ColumnInfo(defaultValue = "'UNKNOWN'") val integrityStatus: String = ScanIntegrityStatus.UNKNOWN.name,
)

enum class ScanIntegrityStatus { UNKNOWN, COMPLETE, COMPLETE_WITH_WARNINGS, TRUNCATED, FAILED_INTEGRITY, INTERRUPTED }

@Entity(
    tableName = "scan_entries",
    primaryKeys = ["scanId", "path"],
    indices = [Index("scanId"), Index(value = ["scanId", "sizeBytes"])],
    foreignKeys = [
        ForeignKey(
            entity = ScanSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
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
