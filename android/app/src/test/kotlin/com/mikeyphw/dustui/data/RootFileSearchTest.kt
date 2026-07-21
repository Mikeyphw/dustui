package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootFileSearchTest {
    @Test fun `parses extension size and package filters`() {
        val parsed = SearchQueryParser.parse("*.apk >10m package:com.example.app")
        assertEquals("apk", parsed.extension)
        assertEquals(10L * 1024L * 1024L, parsed.minBytes)
        assertEquals("com.example.app", parsed.packageName)
    }

    @Test fun `searches scanned root index by package and extension`() {
        val entries = listOf(
            entry("/data/app/com.example.app/base.apk", 20L * 1024L * 1024L),
            entry("/data/app/com.other.app/base.apk", 30L * 1024L * 1024L),
            entry("/data/data/com.example.app/files/readme.txt", 1L),
        )
        val results = RootFileSearchEngine.search(entries, RootFileSearchRequest(query = "*.apk package:com.example.app >10m"))
        assertEquals(1, results.size)
        assertTrue(results.single().path.contains("com.example.app"))
    }

    private fun entry(path: String, size: Long) = ScanEntryEntity("scan", path, path.substringBeforeLast('/'), path.substringAfterLast('/'), size, size, false, 1, 0L, 1)
}
