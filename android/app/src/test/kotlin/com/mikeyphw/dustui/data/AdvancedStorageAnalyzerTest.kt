package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedStorageAnalyzerTest {
    private fun entry(path: String, size: Long, modified: Long = 1L) = ScanEntryEntity("scan", path, path.substringBeforeLast('/'), path.substringAfterLast('/'), size, size, false, 1, modified, 1)

    @Test fun `finds duplicate candidates and estimated savings`() {
        val report = AdvancedStorageAnalyzer.duplicateCandidates(listOf(entry("/a/movie.mkv", 2_000_000), entry("/b/copy.mkv", 2_000_000), entry("/c/other.zip", 2_000_000)))
        assertEquals(1, report.size)
        assertEquals(2_000_000, report.single().estimatedSavingsBytes)
    }

    @Test fun `aggregates root module footprints`() {
        val modules = AdvancedStorageAnalyzer.moduleFootprints(listOf(entry("/data/adb/modules/foo/system/a", 10), entry("/data/adb/modules/foo/module.prop", 5), entry("/data/adb/modules/bar/x", 7)))
        assertEquals("foo", modules.first().moduleId)
        assertEquals(15, modules.first().totalBytes)
    }

    @Test fun `parses only bind and overlay topology`() {
        val mounts = AdvancedStorageAnalyzer.parseMounts("overlay /system overlay ro,relatime 0 0\n/dev/block/x /data ext4 rw,relatime 0 0\n/data/src /mnt/bind none rw,bind 0 0")
        assertEquals(2, mounts.size)
        assertTrue(mounts.any { it.overlay })
        assertTrue(mounts.any { it.bind })
    }
}
