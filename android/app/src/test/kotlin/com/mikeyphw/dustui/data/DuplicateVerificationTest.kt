package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateVerificationTest {
    @Test fun `report totals verified reclaimable bytes`() {
        val report = DuplicateVerificationReport(listOf(
            VerifiedDuplicateGroup("a", 10, listOf("/a", "/b"), 10),
            VerifiedDuplicateGroup("b", 20, listOf("/c", "/d", "/e"), 40),
        ), 5, 0)
        assertEquals(50L, report.reclaimableBytes)
    }

    @Test fun `empty report has no reclaimable bytes`() {
        assertEquals(0L, DuplicateVerificationReport(emptyList(), 0, 2).reclaimableBytes)
    }
}
