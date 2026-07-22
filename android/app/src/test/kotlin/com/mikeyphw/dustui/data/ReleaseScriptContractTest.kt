package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseScriptContractTest {
    private val root = locateAndroidRoot()

    private fun locateAndroidRoot(): File {
        val cwd = File(System.getProperty("user.dir"))
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile)
            .filterNotNull()
            .first { File(it, "scripts/release-gate.sh").isFile }
    }

    @Test fun `release scripts avoid executable bit assumptions`() {
        val releaseGate = File(root, "scripts/release-gate.sh").readText()
        assertFalse(releaseGate.contains("test -x scripts/device-certify.sh"))
        assertTrue(releaseGate.contains("bash -n scripts/device-certify.sh"))
        assertTrue(releaseGate.contains("bash -n scripts/install-smoke.sh"))
    }

    @Test fun `package rc script writes manifest checksums and unsigned warning`() {
        val script = File(root, "scripts/package-rc.sh").readText()
        assertTrue(script.contains("manifest.json"))
        assertTrue(script.contains("SHA256SUMS"))
        assertTrue(script.contains("release-unsigned"))
        assertTrue(script.contains("distribution signing is still required"))
    }

    @Test fun `install smoke collects launch and fatal exception evidence`() {
        val script = File(root, "scripts/install-smoke.sh").readText()
        assertTrue(script.contains("adb_bin"))
        assertTrue(script.contains("install -r"))
        assertTrue(script.contains("monkey -p"))
        assertTrue(script.contains("FATAL EXCEPTION"))
    }
}
