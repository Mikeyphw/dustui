package com.mikeyphw.dustui.go

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class GoChildLaunchIsolationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedGoChildStartsWithSanitizedPrivateLaunchContract() {
        val executable = File(GoRuntimePathContract.executablePath(context.applicationInfo.nativeLibraryDir))
        assertTrue("packaged Go runtime is missing: ${executable.absolutePath}", executable.isFile)

        val work = File(context.noBackupFilesDir, "go-launch-contract-test").apply { mkdirs() }
        val builder = ProcessBuilder(executable.absolutePath, "launch-contract")
        builder.environment()["TEST_TMPDIR"] = "/data/local/tests"
        builder.environment()["LD_LIBRARY_PATH"] = "/data/local/tests/lib64"
        GoChildProcessPolicy.apply(
            builder = builder,
            workingDirectory = work,
            homeDirectory = context.filesDir,
            temporaryDirectory = context.cacheDir,
        )

        val process = builder.start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        assertTrue("launch-contract timed out", process.waitFor(10, TimeUnit.SECONDS))
        assertEquals("launch-contract failed: $stderr", 0, process.exitValue())

        val report = JSONObject(stdout)
        assertEquals(work.absolutePath, report.getString("workingDirectory"))
        assertEquals(context.filesDir.absolutePath, report.getString("homeDirectory"))
        assertEquals(context.cacheDir.absolutePath, report.getString("temporaryDirectory"))
        assertTrue(report.getBoolean("androidChild"))
        assertFalse(report.getBoolean("shellTestPathPresent"))
        assertEquals(0, report.getJSONArray("unexpectedEnvironment").length())
        assertTrue(report.getString("goMaxProcs").toIntOrNull()?.let { it >= 1 } == true)
        assertTrue(report.getString("goVersion").startsWith("go"))

        // Exercise the actual long-lived production startup path as well.
        GoRuntimeProcess.start(context).use { runtime ->
            assertTrue(runtime.isAlive)
            assertTrue(runtime.readiness.optString("protocol").isNotBlank())
        }
    }
}
