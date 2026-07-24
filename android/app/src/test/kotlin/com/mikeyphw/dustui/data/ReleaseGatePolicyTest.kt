package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReleaseGatePolicyTest {
    @Test fun `tasker contract remains read only`() {
        assertTrue(ReleaseGatePolicy.isAllowedTaskerAction("scan"))
        assertTrue(ReleaseGatePolicy.isAllowedTaskerAction("cancel"))
        assertFalse(ReleaseGatePolicy.isAllowedTaskerAction("cleanup"))
        assertFalse(ReleaseGatePolicy.isAllowedTaskerAction("delete"))
        assertFalse(ReleaseGatePolicy.isAllowedTaskerAction("shell"))
    }

    @Test fun `fingerprint detects replacement`() {
        val path = Files.createTempFile("dustui-fingerprint", ".tmp").toFile()
        path.writeText("first")
        val fingerprint = ReleaseGatePolicy.fingerprint(path)
        assertTrue(fingerprint.stillMatches(path))
        path.writeText("replacement")
        assertFalse(fingerprint.stillMatches(path))
        path.delete()
    }
}
