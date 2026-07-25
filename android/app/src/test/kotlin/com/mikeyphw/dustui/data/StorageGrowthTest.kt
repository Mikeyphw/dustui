package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageGrowthTest {
    @Test fun `comparison uses leaf files and keeps removed paths historical`() {
        val previous = session("old", 1_000, 1)
        val current = session("new", 1_500, 2)
        val oldEntries = listOf(directory("/storage"), file("/storage/a.bin", 100), file("/storage/removed.bin", 200))
        val newEntries = listOf(directory("/storage"), file("/storage/a.bin", 300), file("/storage/new.bin", 400))
        val analysis = StorageGrowthEngine.compare(current, newEntries, previous, oldEntries)
        assertEquals(3, analysis.items.size)
        assertEquals(StorageGrowthKind.GROWN, analysis.items.first { it.path.endsWith("a.bin") }.kind)
        assertEquals(StorageGrowthKind.ADDED, analysis.items.first { it.path.endsWith("new.bin") }.kind)
        val removed = analysis.items.first { it.path.endsWith("removed.bin") }
        assertEquals(StorageGrowthKind.REMOVED, removed.kind)
        assertFalse(removed.actionable)
        assertTrue(analysis.items.first { it.path.endsWith("new.bin") }.actionable)
    }

    @Test fun `all six grouping modes produce deterministic contributors`() {
        val analysis = StorageGrowthEngine.compare(
            session("new", 900 * MIB, 2),
            listOf(file("/data/adb/modules/demo/system/lib/a.so", 300 * MIB), file("/storage/emulated/0/DCIM/b.jpg", 400 * MIB)),
            session("old", 100 * MIB, 1),
            listOf(file("/data/adb/modules/demo/system/lib/a.so", 100 * MIB)),
        )
        StorageGrowthGroup.entries.forEach { group ->
            val page = StorageGrowthEngine.evaluate(analysis, StorageGrowthFilter(group = group), emptySet())
            assertTrue("Expected contributors for $group", page.groups.isNotEmpty())
        }
        assertTrue(analysis.items.any { it.groupModule == "demo" })
        assertTrue(analysis.items.any { it.groupExtension == ".so" })
        assertTrue(analysis.items.any { it.groupMount == "/data/adb/modules" })
    }

    @Test fun `timeline is ordered and carries period deltas`() {
        val timeline = StorageGrowthEngine.timeline(listOf(session("third", 800, 3), session("first", 100, 1), session("second", 350, 2)))
        assertEquals(listOf("first", "second", "third"), timeline.map { it.sessionId })
        assertEquals(null, timeline.first().deltaFromPreviousBytes)
        assertEquals(250L, timeline[1].deltaFromPreviousBytes)
        assertEquals(450L, timeline[2].deltaFromPreviousBytes)
    }

    @Test fun `specialized insights identify large threshold log module mount and directory growth`() {
        val old = listOf(
            file("/data/adb/modules/demo/logs/runtime.log", 10 * MIB),
            file("/storage/emulated/0/Download/growing.bin", 90 * MIB),
        )
        val current = listOf(
            file("/data/adb/modules/demo/logs/runtime.log", 250 * MIB),
            file("/storage/emulated/0/Download/growing.bin", 150 * MIB),
            file("/storage/emulated/0/Download/new.iso", 500 * MIB),
        )
        val analysis = StorageGrowthEngine.compare(session("new", 900 * MIB, 2), current, session("old", 100 * MIB, 1), old)
        val kinds = analysis.insights.mapTo(mutableSetOf()) { it.kind }
        assertTrue(StorageGrowthInsightKind.NEW_LARGE_FILE in kinds)
        assertTrue(StorageGrowthInsightKind.CROSSED_LARGE_THRESHOLD in kinds)
        assertTrue(StorageGrowthInsightKind.SUDDEN_LOG_OR_CACHE in kinds)
        assertTrue(StorageGrowthInsightKind.MODULE_GROWTH in kinds)
        assertTrue(StorageGrowthInsightKind.MOUNT_GROWTH in kinds)
        assertTrue(StorageGrowthInsightKind.FASTEST_GROWING_DIRECTORY in kinds)
    }

    @Test fun `growth page hides understood entries and pages deterministically`() {
        val analysis = StorageGrowthEngine.compare(
            session("new", 900, 2), listOf(file("/storage/Download/a.apk", 500), file("/storage/DCIM/b.jpg", 400)),
            session("old", 100, 1), listOf(file("/storage/Download/a.apk", 100)),
        )
        val first = analysis.items.first()
        val hidden = setOf(StorageGrowthEngine.identity(first, analysis))
        val page = StorageGrowthEngine.evaluate(analysis, StorageGrowthFilter(group = StorageGrowthGroup.CATEGORY, pageSize = 1), hidden)
        assertEquals(1, page.hiddenUnderstood)
        assertEquals(1, page.items.size)
        val shown = StorageGrowthEngine.evaluate(analysis, StorageGrowthFilter(showUnderstood = true, pageSize = 1), hidden)
        assertEquals(2, shown.pageCount)
    }

    @Test fun `historical current endpoints are not marked latest`() {
        val newest = session("newest", 1_000, 3)
        val historicalCurrent = session("middle", 800, 2)
        val older = session("old", 500, 1)
        val analysis = StorageGrowthEngine.compare(
            historicalCurrent, listOf(file("/storage/a.bin", 800)),
            older, listOf(file("/storage/a.bin", 500)),
            timelineSessions = listOf(newest, historicalCurrent, older),
        )
        assertFalse(analysis.currentIsLatest)
        val latest = StorageGrowthEngine.compare(
            newest, listOf(file("/storage/a.bin", 1_000)),
            historicalCurrent, listOf(file("/storage/a.bin", 800)),
            timelineSessions = listOf(newest, historicalCurrent, older),
        )
        assertTrue(latest.currentIsLatest)
    }

    private fun session(id: String, total: Long, completed: Long) = ScanSessionEntity(id, null, "/storage", "{}", "COMPLETED", startedAt = completed, completedAt = completed, totalBytes = total)
    private fun file(path: String, bytes: Long) = ScanEntryEntity("scan", path, path.substringBeforeLast('/'), path.substringAfterLast('/'), bytes, bytes, false, path.count { it == '/' }, 1, 1)
    private fun directory(path: String) = ScanEntryEntity("scan", path, "/", path.substringAfterLast('/'), 10_000, 10_000, true, 1, 1, 2)
    private companion object { const val MIB = 1024L * 1024L }
}
