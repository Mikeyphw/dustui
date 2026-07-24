package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResidueTest {
    @Test fun `managed package directory absent across all users is high confidence`() {
        val analysis = analyze(entry("/storage/emulated/0/Android/data/com.example.gone"))
        val group = analysis.groups.single()
        assertEquals(AppResidueConfidence.HIGH, group.confidence)
        assertEquals(listOf("/storage/emulated/0/Android/data/com.example.gone"), group.recommendedDeletionPaths)
    }

    @Test fun `package installed for another user is excluded`() {
        val inventory = InstalledPackageInventory(mapOf(0 to emptySet(), 10 to setOf("com.example.gone")), complete = true)
        val analysis = AppResidueAnalyzer.analyze("scan", listOf(entry("/storage/emulated/0/Android/data/com.example.gone")), inventory, emptySet())
        assertTrue(analysis.groups.isEmpty())
    }

    @Test fun `incomplete package visibility keeps managed folder uncertain`() {
        val inventory = InstalledPackageInventory.currentUserOnly(emptySet(), "user query failed")
        val group = AppResidueAnalyzer.analyze(
            "scan",
            listOf(entry("/storage/emulated/0/Android/media/com.example.gone")),
            inventory,
            emptySet(),
        ).groups.single()
        assertEquals(AppResidueConfidence.UNCERTAIN, group.confidence)
        assertTrue(group.recommendedDeletionPaths.isEmpty())
    }

    @Test fun `known export folder is probable but never preselected`() {
        val group = analyze(entry("/storage/emulated/0/Backups/com.example.gone")).groups.single()
        assertEquals(AppResidueConfidence.PROBABLE, group.confidence)
        assertTrue(group.recommendedDeletionPaths.isEmpty())
    }

    @Test fun `package named shared folder remains uncertain`() {
        val group = analyze(entry("/storage/emulated/0/com.example.gone")).groups.single()
        assertEquals(AppResidueConfidence.UNCERTAIN, group.confidence)
    }

    @Test fun `related installed package downgrades managed residue`() {
        val inventory = InstalledPackageInventory(mapOf(0 to setOf("com.example.current")), complete = true)
        val group = AppResidueAnalyzer.analyze(
            "scan",
            listOf(entry("/storage/emulated/0/Android/data/com.example.old")),
            inventory,
            emptySet(),
        ).groups.single()
        assertEquals(AppResidueConfidence.PROBABLE, group.confidence)
        assertTrue(group.relatedInstalledPackages.contains("com.example.current"))
        assertTrue(group.recommendedDeletionPaths.isEmpty())
    }

    @Test fun `user protected folder is never recommended`() {
        val path = "/storage/emulated/0/Android/obb/com.example.gone"
        val group = analyze(entry(path), protected = setOf(path)).groups.single()
        assertEquals(1, group.protectedCount)
        assertTrue(group.recommendedDeletionPaths.isEmpty())
    }

    @Test fun `legacy private alias is dropped when user zero path exists`() {
        val analysis = analyze(
            entry("/data/data/com.example.gone"),
            entry("/data/user/0/com.example.gone"),
        )
        assertEquals(listOf("/data/user/0/com.example.gone"), analysis.groups.single().locations.map { it.path })
    }

    @Test fun `workbench filters and pages deterministically`() {
        val groups = (0 until 15).map { index ->
            AppResidueGroup(
                packageName = "com.example.app$index",
                appLabel = null,
                confidence = if (index % 2 == 0) AppResidueConfidence.HIGH else AppResidueConfidence.PROBABLE,
                locations = listOf(
                    AppResidueLocation(
                        path = "/storage/emulated/0/Android/data/com.example.app$index",
                        sizeBytes = index.toLong(),
                        modifiedAt = index.toLong(),
                        fileCount = 1,
                        userId = 0,
                        kind = AppResidueLocationKind.EXTERNAL_DATA,
                        confidence = if (index % 2 == 0) AppResidueConfidence.HIGH else AppResidueConfidence.PROBABLE,
                        evidence = emptyList(),
                        userProtected = false,
                    ),
                ),
                relatedInstalledPackages = emptySet(),
            )
        }
        val analysis = AppResidueAnalysis("scan", true, setOf(0), groups, 0, 1)
        val page = AppResidueWorkbenchEngine.evaluate(
            analysis,
            AppResidueFilter(confidence = AppResidueConfidence.HIGH, page = 1, pageSize = 3),
        )
        assertEquals(8, page.totalGroups)
        assertEquals(3, page.items.size)
        assertTrue(page.items.zipWithNext().all { (first, second) -> first.totalBytes >= second.totalBytes })
        assertFalse(page.recommendedPaths.isEmpty())
    }

    private fun analyze(vararg entries: ScanEntryEntity, protected: Set<String> = emptySet()): AppResidueAnalysis =
        AppResidueAnalyzer.analyze(
            scanId = "scan",
            entries = entries.toList(),
            inventory = InstalledPackageInventory(mapOf(0 to emptySet(), 10 to emptySet()), complete = true),
            protectedPaths = protected,
        )

    private fun entry(path: String, size: Long = 1024L): ScanEntryEntity = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/').ifBlank { "/" },
        name = path.substringAfterLast('/'),
        sizeBytes = size,
        allocatedBytes = size,
        isDirectory = true,
        depth = path.count { it == '/' },
        modifiedAt = 100,
        fileCount = 4,
    )
}
