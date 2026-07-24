package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFileWorkbenchTest {
    private val now = 2_000_000_000L
    private val mib = 1024L * 1024L

    @Test fun `default workbench excludes directories and small files`() {
        val page = LargeFileWorkbenchEngine.evaluate(
            listOf(
                entry("/scan/big.mkv", 700 * mib, modifiedAt = now - 10),
                entry("/scan/small.mkv", 20 * mib, modifiedAt = now - 10),
                entry("/scan/folder", 2_000 * mib, directory = true, modifiedAt = now - 10),
            ),
            LargeFileFilter(),
            now,
        )
        assertEquals(listOf("/scan/big.mkv"), page.pageItems.map { it.entry.path })
    }

    @Test fun `presets enforce size age and category contracts`() {
        val entries = listOf(
            entry("/scan/old.zip", 120 * mib, modifiedAt = now - 120L * 86_400L),
            entry("/scan/new.zip", 120 * mib, modifiedAt = now - 2L * 86_400L),
            entry("/scan/movie.mkv", 900 * mib, modifiedAt = now - 2L * 86_400L),
            entry("/scan/app.apk", 40 * mib, modifiedAt = now - 2L * 86_400L),
        )
        val oldArchives = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(preset = LargeFilePreset.OLD_ARCHIVES), now)
        val videos = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(preset = LargeFilePreset.LARGE_VIDEOS), now)
        val installers = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(preset = LargeFilePreset.INSTALLERS), now)
        assertEquals(listOf("/scan/old.zip"), oldArchives.pageItems.map { it.entry.path })
        assertEquals(listOf("/scan/movie.mkv"), videos.pageItems.map { it.entry.path })
        assertEquals(listOf("/scan/app.apk"), installers.pageItems.map { it.entry.path })
    }

    @Test fun `query and category filters match path and package owner`() {
        val entries = listOf(
            entry("/data/user/0/com.example.player/files/movie.mkv", 600 * mib),
            entry("/scan/archive.zip", 600 * mib),
        )
        val byPackage = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(query = "example.player"), now)
        val byCategory = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(category = "Archives"), now)
        assertEquals(listOf("/data/user/0/com.example.player/files/movie.mkv"), byPackage.pageItems.map { it.entry.path })
        assertEquals(listOf("/scan/archive.zip"), byCategory.pageItems.map { it.entry.path })
    }

    @Test fun `paging is stable and does not overlap`() {
        val entries = (0 until 25).map { index -> entry("/scan/file-$index.bin", (100 + index).toLong() * mib) }
        val first = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(pageSize = 10, page = 0), now)
        val second = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(pageSize = 10, page = 1), now)
        assertEquals(3, first.pageCount)
        assertEquals(10, first.pageItems.size)
        assertEquals(10, second.pageItems.size)
        assertTrue(first.pageItems.map { it.entry.path }.intersect(second.pageItems.map { it.entry.path }.toSet()).isEmpty())
    }

    @Test fun `grouping preserves page bytes and exact membership`() {
        val entries = listOf(
            entry("/scan/a/movie.mkv", 700 * mib),
            entry("/scan/a/photo.jpg", 200 * mib),
            entry("/scan/b/archive.zip", 300 * mib),
        )
        val page = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(group = LargeFileGroup.CATEGORY), now)
        assertEquals(page.pageItems.sumOf { it.entry.sizeBytes }, page.sections.sumOf { it.totalBytes })
        assertEquals(page.pageItems.map { it.entry.path }.toSet(), page.sections.flatMap { it.items }.map { it.entry.path }.toSet())
        assertTrue(page.sections.any { it.label == "Video" })
    }

    @Test fun `sort modes expose oldest newest path and package ordering`() {
        val entries = listOf(
            entry("/data/user/0/com.zeta/files/b.bin", 200 * mib, modifiedAt = now - 100L * 86_400L),
            entry("/data/user/0/com.alpha/files/a.bin", 300 * mib, modifiedAt = now - 5L * 86_400L),
        )
        val oldest = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(sort = LargeFileSort.OLDEST), now)
        val newest = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(sort = LargeFileSort.NEWEST), now)
        val owner = LargeFileWorkbenchEngine.evaluate(entries, LargeFileFilter(sort = LargeFileSort.PACKAGE), now)
        assertTrue(oldest.pageItems.first().entry.path.contains("com.zeta"))
        assertTrue(newest.pageItems.first().entry.path.contains("com.alpha"))
        assertTrue(owner.pageItems.first().entry.path.contains("com.alpha"))
        assertFalse(owner.pageItems.first().packageName.isNullOrBlank())
    }

    private fun entry(
        path: String,
        size: Long,
        directory: Boolean = false,
        modifiedAt: Long = now - 86_400L,
    ) = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/').ifBlank { "/" },
        name = path.substringAfterLast('/'),
        sizeBytes = size,
        allocatedBytes = size,
        isDirectory = directory,
        depth = path.count { it == '/' },
        modifiedAt = modifiedAt,
        fileCount = if (directory) 1 else 0,
    )
}
