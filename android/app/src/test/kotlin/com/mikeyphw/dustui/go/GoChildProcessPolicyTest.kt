package com.mikeyphw.dustui.go

import java.nio.file.Files
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoChildProcessPolicyTest {
    @Test
    fun appPrivatePolicySanitizesInheritedEnvironmentAndPinsRuntimeInputs() {
        val root = Files.createTempDirectory("dustui-go-child-policy").toFile()
        val work = root.resolve("state").apply { mkdirs() }
        val home = root.resolve("home").apply { mkdirs() }
        val temp = root.resolve("cache").apply { mkdirs() }
        val builder = ProcessBuilder("ignored")
        builder.environment()["TEST_TMPDIR"] = "/data/local/tests"
        builder.environment()["LD_LIBRARY_PATH"] = "/data/local/tests/lib64"
        builder.environment()["UNRELATED_PARENT_SECRET"] = "must-not-cross-boundary"

        GoChildProcessPolicy.apply(builder, work, home, temp)

        assertEquals(work.absolutePath, builder.directory().absolutePath)
        val env = builder.environment()
        assertEquals(work.absolutePath, env["PWD"])
        assertEquals(home.absolutePath, env["HOME"])
        assertEquals(temp.absolutePath, env["TMPDIR"])
        assertEquals(temp.absolutePath, env["TMP"])
        assertEquals(temp.absolutePath, env["TEMP"])
        assertEquals("/system/bin:/system/xbin", env["PATH"])
        assertEquals("/system", env["ANDROID_ROOT"])
        assertEquals("/data", env["ANDROID_DATA"])
        assertEquals("1", env[GoChildProcessPolicy.ANDROID_CHILD_MARKER])
        assertTrue(env["GOMAXPROCS"].orEmpty().toIntOrNull()?.let { it >= 1 } == true)
        assertFalse("TEST_TMPDIR" in env)
        assertFalse("LD_LIBRARY_PATH" in env)
        assertFalse("UNRELATED_PARENT_SECRET" in env)
        assertTrue(env.values.none { it.contains("/data/local/tests") || it.contains("/data/nativetest") })
    }
}
