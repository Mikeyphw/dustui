package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationRecoveryTest {
    @Test fun `executing operation becomes interrupted`() {
        val operation = CleanupOperationEntity("id", "scan", "/storage", "EXECUTING", 1, 42, createdAt = 1)
        val recovered = OperationRecovery.recover(operation, 99)
        assertEquals("INTERRUPTED", recovered.status)
        assertEquals(99L, recovered.completedAt)
        assertTrue(recovered.message.orEmpty().contains("interrupted"))
    }

    @Test fun `terminal operation is preserved`() {
        val operation = CleanupOperationEntity("id", "scan", "/storage", "COMPLETED", 1, 42, createdAt = 1, completedAt = 2)
        assertEquals(operation, OperationRecovery.recover(operation, 99))
    }
}
