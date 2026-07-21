package com.mikeyphw.dustui.data

data class VerifiedDuplicateGroup(
    val sha256: String,
    val sizeBytes: Long,
    val paths: List<String>,
    val reclaimableBytes: Long,
)

data class DuplicateVerificationReport(
    val groups: List<VerifiedDuplicateGroup>,
    val checkedFiles: Int,
    val skippedFiles: Int,
    val message: String? = null,
) {
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
}
