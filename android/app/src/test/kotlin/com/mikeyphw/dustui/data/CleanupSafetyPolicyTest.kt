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


    @Test fun blocksProtectedAndExcludedDescendantsBidirectionally() {
        assertEquals(
            "Path intersects a protected selection",
            CleanupSafetyPolicy.blockReason("/storage/emulated/0/Download", "/storage/emulated/0", protectedPaths = listOf("/storage/emulated/0/Download/private")),
        )
        assertEquals(
            "Path intersects an excluded selection",
            CleanupSafetyPolicy.blockReason("/storage/emulated/0/Download/private/file.txt", "/storage/emulated/0", excludedPaths = listOf("/storage/emulated/0/Download/private")),
        )
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
        assertTrue(review.matches("scan-a", "/storage/emulated/0", 1L))
        assertFalse(review.matches("scan-a", "/storage/emulated/0", 2L))
        assertFalse(review.matches("scan-b", "/storage/emulated/0", 1L))
        assertFalse(review.matches("scan-a", "/data", 1L))
        assertTrue(review.toExecutionJson().contains("treeDigest"))
        assertTrue(review.toExecutionJson().contains("scanGeneration"))
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

    @Test fun duplicateContentHashTravelsFromPlanToExecutionFingerprint() {
        val hash = "a".repeat(64)
        val plan = CleanupPlan(
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            items = listOf(CleanupPlanItem("/storage/emulated/0/a.bin", 10, expectedSha256 = hash)),
        )
        assertTrue(plan.toJson().contains("\"expectedSha256\":\"$hash\""))
        val review = CleanupReview(
            ok = true,
            operationId = "operation",
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            reviewedAt = 1,
            accepted = listOf(
                CleanupApprovedItem(
                    "/storage/emulated/0/a.bin",
                    10,
                    false,
                    CleanupPathFingerprint(1, 2, 3, 10, 4, "digest", 1, contentSha256 = hash),
                ),
            ),
            blocked = emptyList(),
            plannedBytes = 10,
        )
        assertTrue(review.toExecutionJson().contains("\"contentSha256\":\"$hash\""))
    }

    @Test fun duplicateRetainedCopyAndPackageEvidenceTravelToExecution() {
        val hash = "b".repeat(64)
        val review = CleanupReview(
            ok = true,
            operationId = "operation",
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            reviewedAt = 1,
            accepted = listOf(
                CleanupApprovedItem(
                    path = "/storage/emulated/0/delete.bin",
                    plannedBytes = 10,
                    isDirectory = false,
                    fingerprint = CleanupPathFingerprint(1, 2, 3, 10, 4, "digest", 1, contentSha256 = hash),
                    retainedPath = "/storage/emulated/0/keep.bin",
                    retainedSha256 = hash,
                    packageName = "com.example.gone",
                ),
            ),
            blocked = emptyList(),
            plannedBytes = 10,
            scanGeneration = 42,
        )
        val json = review.toExecutionJson()
        assertTrue(json.contains("\"retainedPath\":\"/storage/emulated/0/keep.bin\""))
        assertTrue(json.contains("\"retainedSha256\":\"$hash\""))
        assertTrue(json.contains("\"packageName\":\"com.example.gone\""))
        assertTrue(review.matches("scan", "/storage/emulated/0", 42))
    }

    @Test fun mountIdentityTravelsWithCleanupFingerprint() {
        val review = CleanupReview(
            ok = true,
            operationId = "operation",
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            reviewedAt = 1,
            accepted = listOf(
                CleanupApprovedItem(
                    path = "/storage/emulated/0/delete.bin",
                    plannedBytes = 10,
                    isDirectory = false,
                    fingerprint = CleanupPathFingerprint(1, 2, 3, 10, 4, "digest", 1, mountId = "37"),
                ),
            ),
            blocked = emptyList(),
            plannedBytes = 10,
        )
        assertTrue(review.toExecutionJson().contains("\"mountId\":\"37\""))
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
