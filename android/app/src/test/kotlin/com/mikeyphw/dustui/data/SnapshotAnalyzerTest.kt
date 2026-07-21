package com.mikeyphw.dustui.data

import org.junit.Assert.*
import org.junit.Test

class SnapshotAnalyzerTest {
    private fun entry(scan: String, path: String, size: Long) = ScanEntryEntity(scan, path, path.substringBeforeLast('/'), path.substringAfterLast('/'), size, size, false, 1, 0, 1)

    @Test fun `extracts package from private and external paths`() {
        assertEquals("com.example.app", SnapshotAnalyzer.packageFromPath("/data/user/0/com.example.app/cache/a"))
        assertEquals("com.example.app", SnapshotAnalyzer.packageFromPath("/storage/emulated/0/Android/data/com.example.app/files/a"))
    }

    @Test fun `marks missing packages orphaned`() {
        val result = SnapshotAnalyzer.appSummaries(listOf(entry("s", "/data/user/0/com.old.app/cache/a", 10)), emptySet())
        assertTrue(result.single().orphaned)
        assertEquals(10, result.single().cacheBytes)
    }

    @Test fun `computes snapshot additions removals and changes`() {
        val before = listOf(entry("a", "/same", 10), entry("a", "/gone", 20))
        val after = listOf(entry("b", "/same", 15), entry("b", "/new", 30))
        val delta = SnapshotAnalyzer.diff(after, before, "b", "a")
        assertEquals(15, delta.totalDeltaBytes)
        assertEquals(30, delta.addedBytes)
        assertEquals(20, delta.removedBytes)
        assertEquals(1, delta.changedEntries)
    }
}
