package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupSafetyPolicyTest {
    @Test fun blocksCriticalPathsAndScanRoot() {
        assertEquals("The scan root cannot be deleted", CleanupSafetyPolicy.blockReason("/data/user/0", "/data/user/0"))
        assertEquals("Protected Android path", CleanupSafetyPolicy.blockReason("/data/system", "/data"))
        assertEquals("Protected Android path", CleanupSafetyPolicy.blockReason("/proc/123/maps", "/"))
    }

    @Test fun blocksEscapesAndAllowsReviewedChildren() {
        assertEquals("Path is outside the reviewed scan root", CleanupSafetyPolicy.blockReason("/data/local/tmp/file", "/storage/emulated/0"))
        assertNull(CleanupSafetyPolicy.blockReason("/storage/emulated/0/Download/archive.zip", "/storage/emulated/0"))
    }

    @Test fun cleanupReviewIsBoundToItsScanAndRoot() {
        val review = CleanupReview(
            ok = true,
            operationId = "operation",
            scanId = "scan-a",
            rootPath = "/storage/emulated/0",
            reviewedAt = 123L,
            accepted = listOf(
                CleanupApprovedItem(
                    path = "/storage/emulated/0/Download/archive.zip",
                    plannedBytes = 42L,
                    isDirectory = false,
                    fingerprint = CleanupPathFingerprint(1, 2, 3, 42, 4, "digest", 1),
                ),
            ),
            blocked = emptyList(),
            plannedBytes = 42L,
        )
        assertTrue(review.matches("scan-a", "/storage/emulated/0"))
        assertFalse(review.matches("scan-b", "/storage/emulated/0"))
        assertFalse(review.matches("scan-a", "/data"))
        assertTrue(review.toExecutionJson().contains("treeDigest"))
    }

    @Test fun blockedItemsDoNotPreventApprovedDeletion() {
        val review = CleanupReview(
            ok = true,
            operationId = "operation",
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            reviewedAt = 1,
            accepted = listOf(
                CleanupApprovedItem(
                    "/storage/emulated/0/Download/ok.zip",
                    10,
                    false,
                    CleanupPathFingerprint(1, 2, 3, 10, 4, "digest", 1),
                ),
            ),
            blocked = listOf(CleanupBlockedItem("/data/system", "Protected Android path")),
            plannedBytes = 10,
        )
        assertTrue(review.executable)
    }

    @Test fun cleanupPlanRemovesNestedDuplicates() {
        val plan = CleanupPlan(
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            items = listOf(
                CleanupPlanItem("/storage/emulated/0/Download", 100, isDirectory = true),
                CleanupPlanItem("/storage/emulated/0/Download/nested.zip", 50),
                CleanupPlanItem("/storage/emulated/0/DCIM/old.jpg", 25),
            ),
        ).normalized()
        assertEquals(listOf("/storage/emulated/0/Download", "/storage/emulated/0/DCIM/old.jpg"), plan.items.map { it.path })
    }
}
