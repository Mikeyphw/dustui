package com.mikeyphw.dustui.data

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
    val generation: Long = 1L,
    val observedEntryCount: Long = 0L,
    val persistedEntryCount: Long = 0L,
    val malformedEventCount: Long = 0L,
    val truncated: Boolean = false,
    val integrityStatus: String = ScanIntegrityStatus.UNKNOWN.name,
)

enum class ScanIntegrityStatus { UNKNOWN, COMPLETE, COMPLETE_WITH_WARNINGS, TRUNCATED, FAILED_INTEGRITY, INTERRUPTED }

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
