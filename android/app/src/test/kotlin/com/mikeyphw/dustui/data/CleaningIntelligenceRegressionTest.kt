package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningIntelligenceRegressionTest {
    @Test fun `review binding and protected path policy remain irreversible gatekeepers`() {
        val review = CleanupReview(
            ok = true, operationId = "operation", scanId = "scan-a", rootPath = "/storage/emulated/0", reviewedAt = 1,
            accepted = listOf(CleanupApprovedItem(
                "/storage/emulated/0/Download/old.zip", 10, false,
                CleanupPathFingerprint(1, 2, 3, 10, 4, "tree", 1, "a".repeat(64)),
            )), blocked = emptyList(), plannedBytes = 10,
        )
        assertTrue(review.matches("scan-a", "/storage/emulated/0"))
        assertFalse(review.matches("scan-b", "/storage/emulated/0"))
        assertEquals("Protected Android path", CleanupSafetyPolicy.blockReason("/data/adb/modules", "/data"))
        assertEquals("Path is outside the reviewed scan root", CleanupSafetyPolicy.blockReason("/data/local/tmp/file", "/storage/emulated/0"))
    }

    @Test fun `hard links never become reclaimable automatic duplicate copies`() {
        val group = VerifiedDuplicateGroup(
            sha256 = "hash", partialHash = "edge", sizeBytes = 100,
            pathRecords = listOf(
                verified("/storage/a.bin", "1:10", 10, true, false),
                verified("/storage/a-link.bin", "1:10", 10, true, false),
                verified("/storage/b.bin", "1:20", 20, false, true),
            ),
            physicalCopyCount = 2, hardLinkedCopyCount = 1, reclaimableBytes = 100, safeReclaimableBytes = 0,
        )
        val item = DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST))
        assertEquals(2, item.copies.size)
        assertEquals(1, item.hardLinkedCopies)
        assertEquals(listOf("/storage/b.bin"), item.deletionPaths)
        assertFalse(item.deletionPaths.contains("/storage/a.bin"))
        assertFalse(item.deletionPaths.contains("/storage/a-link.bin"))
        assertEquals(100L, item.plannedBytes)
    }

    @Test fun `uncertain residue and ignored recommendations never join safe batch`() {
        val uncertain = AppResidueGroup(
            packageName = "com.example.unknown", appLabel = null, confidence = AppResidueConfidence.UNCERTAIN,
            locations = listOf(AppResidueLocation(
                path = "/storage/emulated/0/com.example.unknown", sizeBytes = 100, modifiedAt = 1, fileCount = 1,
                userId = null, kind = AppResidueLocationKind.SHARED_PACKAGE_FOLDER,
                confidence = AppResidueConfidence.UNCERTAIN, evidence = emptyList(), userProtected = false,
            )), relatedInstalledPackages = emptySet(),
        )
        assertTrue(uncertain.recommendedDeletionPaths.isEmpty())
        val recommendation = CleanupRecommendation(
            id = "duplicate:test", kind = CleanupRecommendationKind.EXACT_DUPLICATES, title = "Verified duplicate",
            summary = "One extra physical copy", paths = listOf("/storage/emulated/0/a.bin"), reclaimableBytes = 100,
            confidence = CleanupRecommendationConfidence.VERIFIED, risk = CleanupRecommendationRisk.LOW, score = 100,
            ageDays = null, evidence = emptyList(), expectedSha256ByPath = mapOf("/storage/emulated/0/a.bin" to "hash"),
        )
        val page = CleanupRecommendationEngine.evaluate(
            CleanupRecommendationAnalysis("scan", listOf(recommendation), 1),
            CleanupRecommendationFilter(showHidden = true),
            CleanupRecommendationPreferences(ignoredIds = setOf(recommendation.id)),
        )
        assertTrue(page.batchPaths.isEmpty())
        assertEquals(CleanupRecommendationDisposition.IGNORED, page.dispositions.getValue(recommendation.id))
    }

    @Test fun `imported rules and historical comparisons remain informational until reviewed`() {
        val imported = CleanupRule(
            name = "Imported", imported = true, reviewedAt = null, enabled = true,
            conditions = CleanupRuleConditions(includePaths = listOf("/storage/emulated/0/Download")),
        )
        assertFalse(CleanupRuleEngine.validate(imported).valid)
        val newest = session("newest", 3, 300)
        val middle = session("middle", 2, 200)
        val oldest = session("oldest", 1, 100)
        val historical = StorageGrowthEngine.compare(
            middle, listOf(file("middle", "/storage/emulated/0/a.bin", 200)),
            oldest, listOf(file("oldest", "/storage/emulated/0/a.bin", 100)),
            listOf(newest, middle, oldest),
        )
        assertFalse(historical.currentIsLatest)
    }

    @Test fun `interruption and unknown verification never claim completed deletion`() {
        val recovered = OperationRecovery.recover(CleanupOperationEntity("op", "scan", "/storage", "EXECUTING", 1, 100, createdAt = 1), 2)
        assertEquals("INTERRUPTED", recovered.status)
        assertEquals(0L, recovered.deletedBytes)
        val review = CleanupReview(
            true, "op", "scan", "/storage", 1,
            listOf(CleanupApprovedItem("/storage/a", 100, false, CleanupPathFingerprint(1, 2, 3, 100, 1, "tree", 1))),
            emptyList(), 100,
        )
        val report = CleanupVerificationEngine.build(
            review,
            CleanupExecutionResult(true, "op", "COMPLETED", 100, 0, listOf(CleanupItemResult("/storage/a", 100, 100, "DELETED"))),
            1, 2, mapOf("/storage/a" to CleanupPathObservation(CleanupPathPresence.UNKNOWN, "service unavailable")),
            null, null, "1.4.0-rc1",
        )
        assertEquals(0L, report.confirmedDeletedBytes)
        assertEquals(100L, report.failedBytes)
        assertFalse(report.fullyVerified)
    }

    private fun verified(path: String, key: String, inode: Long, hardLinked: Boolean, eligible: Boolean) = DuplicateVerifiedPath(
        path, 1, inode, if (hardLinked) 2 else 1, inode, key, hardLinked, eligible,
        if (eligible) null else "Hard-linked copy",
    )

    private fun session(id: String, completed: Long, bytes: Long) = ScanSessionEntity(
        id, null, "/storage/emulated/0", "{}", "COMPLETED", startedAt = completed, completedAt = completed, totalBytes = bytes,
    )

    private fun file(scanId: String, path: String, bytes: Long) = ScanEntryEntity(
        scanId, path, path.substringBeforeLast('/'), path.substringAfterLast('/'), bytes, bytes, false,
        path.count { it == '/' }, 1, 1,
    )
}
