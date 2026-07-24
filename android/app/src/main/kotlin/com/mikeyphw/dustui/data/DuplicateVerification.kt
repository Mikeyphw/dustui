package com.mikeyphw.dustui.data

data class DuplicateVerifiedPath(
    val path: String,
    val deviceId: Long,
    val inode: Long,
    val linkCount: Long,
    val modifiedAt: Long,
    val copyKey: String,
    val hardLinked: Boolean,
    val autoDeleteEligible: Boolean,
    val deletionBlockReason: String? = null,
)

data class DuplicateSkippedPath(
    val path: String,
    val reason: String,
)

data class VerifiedDuplicateGroup(
    val sha256: String,
    val partialHash: String,
    val sizeBytes: Long,
    val pathRecords: List<DuplicateVerifiedPath>,
    val physicalCopyCount: Int,
    val hardLinkedCopyCount: Int,
    val reclaimableBytes: Long,
    val safeReclaimableBytes: Long,
) {
    val paths: List<String> get() = pathRecords.map { it.path }
}

data class DuplicateVerificationReport(
    val groups: List<VerifiedDuplicateGroup>,
    val checkedFiles: Int,
    val skipped: List<DuplicateSkippedPath>,
    val partialGroups: Int,
    val bytesHashed: Long,
    val message: String? = null,
) {
    val skippedFiles: Int get() = skipped.size
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
    val safeReclaimableBytes: Long get() = groups.sumOf { it.safeReclaimableBytes }
}
