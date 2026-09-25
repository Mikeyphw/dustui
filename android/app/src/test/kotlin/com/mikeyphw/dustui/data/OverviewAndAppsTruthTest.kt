package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewAndAppsTruthTest {
    @Test
    fun `AUD_FIX10 overview keeps logical and allocated semantics distinct`() {
        val truth = OverviewStorageSemantics(
            scanId = "scan-a",
            generation = 4,
            rootPath = "/storage/emulated/0",
            integrityStatus = "COMPLETE",
            logicalBytes = 1_000,
            allocatedBytes = 4_096,
            fileCount = 7,
        )
        assertEquals(1_000, truth.logicalBytes)
        assertEquals(4_096, truth.allocatedBytes)
        assertNotEquals(truth.logicalBytes, truth.allocatedBytes)
        assertEquals("scan-a", truth.scanId)
        assertEquals(4, truth.generation)
    }

    @Test
    fun `AUD_FIX10 unavailable package evidence is typed rather than a byte value`() {
        assertTrue(AppStorageEvidenceState.UNAVAILABLE != AppStorageEvidenceState.SUPPORTED)
        assertTrue(AppStorageEvidenceState.PARTIAL != AppStorageEvidenceState.SUPPORTED)
        assertTrue(AppStorageEvidenceState.BLOCKED != AppStorageEvidenceState.SUPPORTED)
        assertEquals(4, AppStorageEvidenceState.entries.size)
    }
}
