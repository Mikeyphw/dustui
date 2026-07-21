package com.mikeyphw.dustui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScanRequestTest {
    @Test fun roundTripPreservesOptions() {
        val original = ScanRequest(
            scanId = "scan-1",
            rootPath = "/data/user/0",
            maxDepth = 7,
            mode = EntryMode.DIRECTORIES,
            minSize = 4096,
            includeHidden = true,
            excludes = listOf("cache", "*.tmp"),
        ).validated()
        assertEquals(original, ScanRequest.fromJson(original.toJson()))
    }

    @Test fun pseudoFilesystemIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScanRequest(rootPath = "/proc/1").validated() }
    }

    @Test fun relativePathIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScanRequest(rootPath = "downloads").validated() }
    }

    @Test fun lexicalTraversalIntoPseudoFilesystemIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ScanRequest(rootPath = "/data/../proc").validated() }
    }
}
