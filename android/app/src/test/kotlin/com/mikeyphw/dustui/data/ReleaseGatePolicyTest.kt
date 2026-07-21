package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ReleaseGatePolicyTest {
    @Test fun `tasker contract remains read only`() {
        assertTrue(ReleaseGatePolicy.isAllowedTaskerAction("scan"))
        assertTrue(ReleaseGatePolicy.isAllowedTaskerAction("cancel"))
        assertFalse(ReleaseGatePolicy.isAllowedTaskerAction("cleanup"))
        assertFalse(ReleaseGatePolicy.isAllowedTaskerAction("shell"))
    }

    @Test fun `restore manifest rejects traversal and forged vaults`() {
        assertTrue(ReleaseGatePolicy.validateRestoreEntry("/data/user/0/app/cache", "/tmp/fake") != null)
        assertTrue(ReleaseGatePolicy.validateRestoreEntry("/data/../system", "/data/.dustui-quarantine/x") != null)
        assertNull(ReleaseGatePolicy.validateRestoreEntry("/data/user/0/app/cache", "/data/.dustui-quarantine/op/cache"))
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
