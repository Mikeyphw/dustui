package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FinalRcScriptContractTest {
    private val root = locateAndroidRoot()

    private fun locateAndroidRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")) { "user.dir system property is unavailable" })
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile)
            .filterNotNull()
            .first { File(it, "scripts/final-rc-gate.sh").isFile }
    }

    @Test fun `final rc gate collects ship status evidence`() {
        val script = File(root, "scripts/final-rc-gate.sh").readText()
        assertTrue(script.contains("SHIP_STATUS.md"))
        assertTrue(script.contains("KNOWN_ISSUES.md"))
        assertTrue(script.contains("release-candidate"))
        assertTrue(script.contains("device-certification.tar.gz"))
    }

    @Test fun `final rc gate validates scripts by shell parsing`() {
        val releaseGate = File(root, "scripts/release-gate.sh").readText()
        assertTrue(releaseGate.contains("bash -n scripts/final-rc-gate.sh"))
        assertFalse(releaseGate.contains("test -x scripts/final-rc-gate.sh"))
    }
}
