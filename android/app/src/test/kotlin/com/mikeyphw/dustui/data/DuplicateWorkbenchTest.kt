package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateWorkbenchTest {
    @Test fun `newest strategy keeps newest physical copy`() {
        val group = group(
            copy("old", "/a/old.bin", modifiedAt = 10),
            copy("new", "/b/new.bin", modifiedAt = 20),
            copy("middle", "/c/middle.bin", modifiedAt = 15),
        )
        val item = DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST))
        assertEquals("new", item.keepCopyKey)
        assertEquals(setOf("/a/old.bin", "/c/middle.bin"), item.deletionPaths.toSet())
        assertEquals(2_000L, item.plannedBytes)
    }

    @Test fun `hard linked copies are never auto selected and can anchor retained content`() {
        val group = group(
            copy("hard", "/links/a.bin", modifiedAt = 5, linkCount = 2, hardLinked = true, eligible = false),
            copy("normal", "/data/copy.bin", modifiedAt = 10),
        )
        val item = DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST))
        assertEquals("hard", item.keepCopyKey)
        assertEquals(listOf("/data/copy.bin"), item.deletionPaths)
        assertEquals(1, item.hardLinkedCopies)
    }

    @Test fun `protected copy anchors content and is never selected`() {
        val group = group(
            copy("protected", "/system/protected.bin", eligible = false, blockReason = "Protected Android path"),
            copy("user", "/storage/user.bin"),
        )
        val item = DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST))
        assertEquals("protected", item.keepCopyKey)
        assertEquals(listOf("/storage/user.bin"), item.deletionPaths)
        assertEquals("Protected Android path", item.copies.first { it.key == "protected" }.deletionBlockReason)
    }

    @Test fun `manual strategy selects nothing until a retained copy is chosen`() {
        val group = group(copy("a", "/a"), copy("b", "/b"))
        val filter = DuplicateFilter(keepStrategy = DuplicateKeepStrategy.MANUAL)
        assertTrue(DuplicateWorkbenchEngine.groupItem(group, filter).deletionPaths.isEmpty())
        assertEquals(listOf("/b"), DuplicateWorkbenchEngine.groupItem(group, filter, "a").deletionPaths)
    }

    @Test fun `preferred directory wins when a copy is inside it`() {
        val group = group(copy("downloads", "/storage/Downloads/a.zip"), copy("backup", "/storage/Backup/a.zip"))
        val item = DuplicateWorkbenchEngine.groupItem(
            group,
            DuplicateFilter(
                keepStrategy = DuplicateKeepStrategy.PREFERRED_DIRECTORY,
                preferredDirectory = "/storage/Backup",
            ),
        )
        assertEquals("backup", item.keepCopyKey)
        assertEquals(listOf("/storage/Downloads/a.zip"), item.deletionPaths)
    }

    @Test fun `query paging and totals stay deterministic`() {
        val groups = (0 until 25).map { index ->
            group(copy("$index-a", "/set-$index/a.bin"), copy("$index-b", "/set-$index/b.bin"), hash = "hash-$index")
        }
        val snapshot = DuplicateAnalysisSnapshot.empty("scan", DuplicateThreshold.ONE_MIB.minBytes).copy(
            status = "COMPLETED",
            groups = groups,
        )
        val page = DuplicateWorkbenchEngine.evaluate(snapshot, DuplicateFilter(page = 1, pageSize = 10))
        assertEquals(25, page.totalGroups)
        assertEquals(10, page.items.size)
        assertEquals(3, page.pageCount)
        assertEquals(1, page.pageIndex)
        assertFalse(page.items.first().group.sha256.isBlank())
    }

    @Test fun `different threshold does not reuse persisted results`() {
        val snapshot = DuplicateAnalysisSnapshot.empty("scan", DuplicateThreshold.ONE_MIB.minBytes).copy(
            status = "COMPLETED",
            groups = listOf(group(copy("a", "/a"), copy("b", "/b"))),
        )
        val page = DuplicateWorkbenchEngine.evaluate(snapshot, DuplicateFilter(threshold = DuplicateThreshold.TEN_MIB))
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.totalGroups)
    }

    private fun group(vararg copies: DuplicateVerifiedPath, hash: String = "hash") = VerifiedDuplicateGroup(
        sha256 = hash,
        partialHash = "partial-$hash",
        sizeBytes = 1_000,
        pathRecords = copies.toList(),
        physicalCopyCount = copies.map { it.copyKey }.distinct().size,
        hardLinkedCopyCount = copies.groupBy { it.copyKey }.values.count { owned -> owned.any { it.hardLinked } },
        reclaimableBytes = 1_000L * (copies.map { it.copyKey }.distinct().size - 1).coerceAtLeast(0),
        safeReclaimableBytes = 1_000L,
    )

    private fun copy(
        key: String,
        path: String,
        modifiedAt: Long = 1,
        linkCount: Long = 1,
        hardLinked: Boolean = false,
        eligible: Boolean = true,
        blockReason: String? = null,
    ) = DuplicateVerifiedPath(
        path = path,
        deviceId = 1,
        inode = key.hashCode().toLong(),
        linkCount = linkCount,
        modifiedAt = modifiedAt,
        copyKey = key,
        hardLinked = hardLinked,
        autoDeleteEligible = eligible,
        deletionBlockReason = blockReason,
    )
}
