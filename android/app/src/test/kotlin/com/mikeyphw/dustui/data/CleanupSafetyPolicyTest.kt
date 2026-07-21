package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanupSafetyPolicyTest {
    @Test fun blocksCriticalPathsAndScanRoot() {
        assertEquals("The scan root cannot be quarantined", CleanupSafetyPolicy.blockReason("/data/user/0", "/data/user/0"))
        assertEquals("Protected Android path", CleanupSafetyPolicy.blockReason("/data/system", "/data"))
        assertEquals("Virtual filesystem content is protected", CleanupSafetyPolicy.blockReason("/proc/123/maps", "/"))
    }

    @Test fun blocksEscapesAndAllowsReviewedChildren() {
        assertEquals("Path is outside the reviewed scan root", CleanupSafetyPolicy.blockReason("/data/local/tmp/file", "/storage/emulated/0"))
        assertNull(CleanupSafetyPolicy.blockReason("/storage/emulated/0/Download/archive.zip", "/storage/emulated/0"))
    }

    @Test fun cleanupPlanRemovesNestedDuplicates() {
        val plan = CleanupPlan(
            scanId = "scan",
            rootPath = "/storage/emulated/0",
            items = listOf(
                CleanupPlanItem("/storage/emulated/0/Download", 100),
                CleanupPlanItem("/storage/emulated/0/Download/nested.zip", 50),
                CleanupPlanItem("/storage/emulated/0/DCIM/old.jpg", 25),
            ),
        ).normalized()
        assertEquals(listOf("/storage/emulated/0/Download", "/storage/emulated/0/DCIM/old.jpg"), plan.items.map { it.path })
    }
}
