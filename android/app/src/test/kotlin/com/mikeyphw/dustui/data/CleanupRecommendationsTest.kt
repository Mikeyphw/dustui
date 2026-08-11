package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupRecommendationsTest {
    @Test fun `verified duplicates are ranked and carry deletion hashes`() {
        val duplicate = DuplicateAnalysisSnapshot.empty("scan", DuplicateThreshold.ONE_MIB.minBytes).copy(
            status = "COMPLETED",
            groups = listOf(duplicateGroup()),
        )
        val analysis = CleanupRecommendationEngine.analyze("scan", duplicateEntries(), duplicate, null, nowSeconds = NOW)
        val item = analysis.items.single { it.kind == CleanupRecommendationKind.EXACT_DUPLICATES }
        assertEquals(CleanupRecommendationConfidence.VERIFIED, item.confidence)
        assertEquals(CleanupRecommendationRisk.LOW, item.risk)
        assertTrue(item.batchEligible)
        assertEquals(setOf("/storage/a.bin"), item.expectedSha256ByPath.keys)
        assertEquals("hash", item.expectedSha256ByPath.getValue("/storage/a.bin"))
    }

    @Test fun `high confidence residue is batch eligible but uncertain residue is absent`() {
        val high = residueGroup("com.example.gone", AppResidueConfidence.HIGH, "/storage/emulated/0/Android/data/com.example.gone")
        val uncertain = residueGroup("com.example.maybe", AppResidueConfidence.UNCERTAIN, "/storage/emulated/0/com.example.maybe")
        val residue = AppResidueAnalysis("scan", true, setOf(0), listOf(high, uncertain), 0, 1)
        val entries = listOf(directory(high.locations.single().path, 500L * MIB), directory(uncertain.locations.single().path, 100L * MIB))
        val analysis = CleanupRecommendationEngine.analyze("scan", entries, null, residue, nowSeconds = NOW)
        assertTrue(analysis.items.any { it.id == "residue:com.example.gone" && it.batchEligible })
        assertFalse(analysis.items.any { it.id == "residue:com.example.maybe" })
    }

    @Test fun `generic old files require explicit review and never join batch selection`() {
        val old = file("/storage/old-video.mp4", 2L * GIB, ageDays = 400)
        val analysis = CleanupRecommendationEngine.analyze("scan", listOf(old), null, null, nowSeconds = NOW)
        val item = analysis.items.single()
        assertEquals(CleanupRecommendationKind.LARGE_OLD_FILE, item.kind)
        assertEquals(CleanupRecommendationConfidence.MEDIUM, item.confidence)
        assertFalse(item.batchEligible)
        val page = CleanupRecommendationEngine.evaluate(analysis, CleanupRecommendationFilter(), CleanupRecommendationPreferences())
        assertTrue(page.batchPaths.isEmpty())
    }

    @Test fun `residue ownership suppresses overlapping generic recommendations`() {
        val root = "/storage/emulated/0/Android/data/com.example.gone"
        val residue = AppResidueAnalysis(
            "scan",
            true,
            setOf(0),
            listOf(residueGroup("com.example.gone", AppResidueConfidence.HIGH, root)),
            0,
            1,
        )
        val entries = listOf(directory(root, 2L * GIB), file("$root/archive.zip", GIB, ageDays = 400))
        val analysis = CleanupRecommendationEngine.analyze("scan", entries, null, residue, nowSeconds = NOW)
        assertEquals(1, analysis.items.size)
        assertEquals(CleanupRecommendationKind.APP_RESIDUE, analysis.items.single().kind)
    }

    @Test fun `recommendation claimed paths use bidirectional component overlap`() {
        val root = "/storage/emulated/0/Android/data/com.example.gone"
        val residue = AppResidueAnalysis(
            "scan",
            true,
            setOf(0),
            listOf(residueGroup("com.example.gone", AppResidueConfidence.HIGH, root)),
            0,
            1,
        )
        val entries = listOf(
            directory(root, 2L * GIB),
            file("$root/archive.zip", GIB, ageDays = 400),
            file("/storage/emulated/0/Android/data/com.example.gone-old/archive.zip", GIB, ageDays = 400),
        )
        val analysis = CleanupRecommendationEngine.analyze("scan", entries, null, residue, nowSeconds = NOW)
        assertEquals(2, analysis.items.size)
        assertTrue(analysis.items.any { it.kind == CleanupRecommendationKind.APP_RESIDUE && it.paths == listOf(root) })
        assertTrue(analysis.items.any { "/storage/emulated/0/Android/data/com.example.gone-old/archive.zip" in it.paths })
        assertFalse(analysis.items.any { "$root/archive.zip" in it.paths })
    }

    @Test fun `rank score is deterministic and exposes its point breakdown`() {
        val entry = file("/storage/Download/old.apk", 700L * MIB, ageDays = 120)
        val first = CleanupRecommendationEngine.analyze("scan", listOf(entry), null, null, nowSeconds = NOW).items.single()
        val second = CleanupRecommendationEngine.analyze("scan", listOf(entry), null, null, nowSeconds = NOW).items.single()
        assertEquals(first.score, second.score)
        assertTrue(first.evidence.any { it.code == "RANK_BREAKDOWN" && it.detail.contains("size +") })
    }

    @Test fun `dismissed and ignored items remain reviewable through show hidden`() {
        val analysis = CleanupRecommendationEngine.analyze(
            "scan",
            listOf(
                file("/storage/Download/old.apk", 700L * MIB, ageDays = 120),
                file("/storage/Backup/archive.zip", 800L * MIB, ageDays = 400),
            ),
            null,
            null,
            nowSeconds = NOW,
        )
        val first = analysis.items[0].id
        val second = analysis.items[1].id
        val preferences = CleanupRecommendationPreferences(setOf(first), setOf(second))
        val hidden = CleanupRecommendationEngine.evaluate(analysis, CleanupRecommendationFilter(), preferences)
        assertTrue(hidden.items.isEmpty())
        assertEquals(2, hidden.hiddenCount)
        val shown = CleanupRecommendationEngine.evaluate(analysis, CleanupRecommendationFilter(showHidden = true), preferences)
        assertEquals(2, shown.items.size)
        assertEquals(CleanupRecommendationDisposition.DISMISSED, shown.dispositions.getValue(first))
        assertEquals(CleanupRecommendationDisposition.IGNORED, shown.dispositions.getValue(second))
    }

    @Test fun `page batching includes only active safe recommendations`() {
        val duplicate = DuplicateAnalysisSnapshot.empty("scan", DuplicateThreshold.ONE_MIB.minBytes).copy(
            status = "COMPLETED",
            groups = listOf(duplicateGroup()),
        )
        val generic = file("/storage/Download/old.apk", 700L * MIB, ageDays = 120)
        val analysis = CleanupRecommendationEngine.analyze("scan", duplicateEntries() + generic, duplicate, null, nowSeconds = NOW)
        val page = CleanupRecommendationEngine.evaluate(analysis, CleanupRecommendationFilter(), CleanupRecommendationPreferences())
        assertEquals(listOf("/storage/a.bin"), page.batchPaths)
        assertEquals("hash", page.batchHashes.getValue("/storage/a.bin"))
    }

    private fun duplicateEntries() = listOf(
        file("/storage/a.bin", 1_000L, ageDays = 10),
        file("/storage/b.bin", 1_000L, ageDays = 20),
    )

    private fun duplicateGroup() = VerifiedDuplicateGroup(
        sha256 = "hash",
        partialHash = "partial",
        sizeBytes = 1_000,
        pathRecords = listOf(
            duplicatePath("a", "/storage/a.bin", modifiedAt = 10),
            duplicatePath("b", "/storage/b.bin", modifiedAt = 20),
        ),
        physicalCopyCount = 2,
        hardLinkedCopyCount = 0,
        reclaimableBytes = 1_000,
        safeReclaimableBytes = 1_000,
    )

    private fun duplicatePath(key: String, path: String, modifiedAt: Long) = DuplicateVerifiedPath(
        path = path,
        deviceId = 1,
        inode = key.hashCode().toLong(),
        linkCount = 1,
        modifiedAt = modifiedAt,
        copyKey = key,
        hardLinked = false,
        autoDeleteEligible = true,
    )

    private fun residueGroup(packageName: String, confidence: AppResidueConfidence, path: String): AppResidueGroup {
        val location = AppResidueLocation(
            path = path,
            sizeBytes = 500L * MIB,
            modifiedAt = NOW - 400L * DAY,
            fileCount = 8,
            userId = 0,
            kind = AppResidueLocationKind.EXTERNAL_DATA,
            confidence = confidence,
            evidence = emptyList(),
            userProtected = false,
        )
        return AppResidueGroup(packageName, null, confidence, listOf(location), emptySet())
    }

    private fun directory(path: String, size: Long) = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/').ifBlank { "/" },
        name = path.substringAfterLast('/'),
        sizeBytes = size,
        allocatedBytes = size,
        isDirectory = true,
        depth = path.count { it == '/' },
        modifiedAt = NOW - 400L * DAY,
        fileCount = 8,
    )

    private fun file(path: String, size: Long, ageDays: Long) = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/').ifBlank { "/" },
        name = path.substringAfterLast('/'),
        sizeBytes = size,
        allocatedBytes = size,
        isDirectory = false,
        depth = path.count { it == '/' },
        modifiedAt = NOW - ageDays * DAY,
        fileCount = 1,
    )

    private companion object {
        const val MIB = 1024L * 1024L
        const val GIB = 1024L * MIB
        const val DAY = 24L * 60L * 60L
        const val NOW = 2_000_000_000L
    }
}
