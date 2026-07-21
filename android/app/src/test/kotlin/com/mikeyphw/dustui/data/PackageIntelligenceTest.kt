package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIntelligenceTest {
    @Test fun `builds package profile with role breakdowns`() {
        val entries = listOf(
            entry("/data/user/0/com.example.app/cache/blob.bin", 600),
            entry("/data/user/0/com.example.app/databases/main.db", 300),
            entry("/storage/emulated/0/Android/data/com.example.app/files/photo.jpg", 200),
            entry("/storage/emulated/0/Android/obb/com.example.app/main.obb", 1_000),
        )
        val profile = PackageIntelligenceAnalyzer.profiles(entries, setOf("com.example.app")).single()
        assertEquals("com.example.app", profile.packageName)
        assertTrue(profile.installed)
        assertEquals(2_100L, profile.totalBytes)
        assertEquals(600L, profile.cacheBytes)
        assertEquals(300L, profile.databaseBytes)
        assertEquals(200L, profile.externalBytes)
        assertEquals(1_000L, profile.obbBytes)
        assertTrue(profile.hasObb)
    }

    @Test fun `marks missing packages as orphaned`() {
        val profile = PackageIntelligenceAnalyzer.profiles(listOf(entry("/data/data/com.old.app/files/state", 10)), emptySet()).single()
        assertFalse(profile.installed)
    }

    private fun entry(path: String, size: Long) = ScanEntryEntity("scan", path, path.substringBeforeLast('/'), path.substringAfterLast('/'), size, size, false, 1, 0L, 1)
}
