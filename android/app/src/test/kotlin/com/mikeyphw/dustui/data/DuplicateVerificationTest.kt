package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateVerificationTest {
    @Test fun `report totals physical and safe reclaimable bytes`() {
        val report = DuplicateVerificationReport(
            groups = listOf(
                verifiedGroup("a", 10, physicalCopies = 2, hardLinkedCopies = 0, reclaimable = 10, safe = 10),
                verifiedGroup("b", 20, physicalCopies = 3, hardLinkedCopies = 1, reclaimable = 40, safe = 20),
            ),
            checkedFiles = 5,
            skipped = listOf(DuplicateSkippedPath("/missing", "Missing")),
            partialGroups = 2,
            bytesHashed = 100,
        )
        assertEquals(50L, report.reclaimableBytes)
        assertEquals(30L, report.safeReclaimableBytes)
        assertEquals(1, report.skippedFiles)
    }

    @Test fun `empty report has no reclaimable bytes`() {
        val report = DuplicateVerificationReport(emptyList(), 0, emptyList(), 0, 0)
        assertEquals(0L, report.reclaimableBytes)
        assertEquals(0L, report.safeReclaimableBytes)
    }

    private fun verifiedGroup(
        hash: String,
        size: Long,
        physicalCopies: Int,
        hardLinkedCopies: Int,
        reclaimable: Long,
        safe: Long,
    ) = VerifiedDuplicateGroup(
        sha256 = hash,
        partialHash = "partial-$hash",
        sizeBytes = size,
        pathRecords = (0 until physicalCopies).map { index ->
            DuplicateVerifiedPath(
                path = "/copy-$index",
                deviceId = 1,
                inode = index.toLong() + 1,
                linkCount = if (index < hardLinkedCopies) 2 else 1,
                modifiedAt = index.toLong(),
                copyKey = "1:${index + 1}",
                hardLinked = index < hardLinkedCopies,
                autoDeleteEligible = index >= hardLinkedCopies,
            )
        },
        physicalCopyCount = physicalCopies,
        hardLinkedCopyCount = hardLinkedCopies,
        reclaimableBytes = reclaimable,
        safeReclaimableBytes = safe,
    )
}
