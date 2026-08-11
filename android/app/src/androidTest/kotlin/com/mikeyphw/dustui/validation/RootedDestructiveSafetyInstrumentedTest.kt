package com.mikeyphw.dustui.validation

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.root.NativeDustScanner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Device/root validation for the fd-relative destructive cleanup boundary.
 *
 * The non-root cases prove that mutation goes through the native reviewed-object
 * path. The bind-mount case requires a disposable rooted emulator/device.
 */
@RunWith(AndroidJUnit4::class)
class RootedDestructiveSafetyInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roots = mutableListOf<File>()
    private val mountedTargets = mutableListOf<File>()

    @After
    fun cleanup() {
        mountedTargets.asReversed().forEach { target ->
            runCatching { su("umount ${shellQuote(target.absolutePath)}") }
        }
        roots.asReversed().forEach { root -> root.deleteRecursively() }
    }

    @Test
    fun fdRelativeDeletionDeletesOnlyReviewedFile() {
        val root = freshRoot("fd-file")
        val target = File(root, "target.txt").apply { writeText("reviewed") }
        val stat = Os.lstat(target.absolutePath)

        val result = deleteReviewed(root, "target.txt", stat)

        assertTrue(result.toString(), result.optBoolean("success"))
        assertFalse("reviewed file should be gone", target.exists())
    }

    @Test
    fun fdRelativeDeletionBlocksTopLevelIdentityReplacement() {
        val root = freshRoot("identity")
        val target = File(root, "target.txt").apply { writeText("old") }
        val reviewed = Os.lstat(target.absolutePath)
        assertTrue(target.delete())
        File(root, "target.txt").writeText("new")

        val result = deleteReviewed(root, "target.txt", reviewed)

        assertFalse(result.toString(), result.optBoolean("success"))
        assertTrue("replacement must survive failed reviewed identity check", target.exists())
    }

    @Test
    fun fdRelativeDeletionBlocksSymlinkLeaf() {
        val root = freshRoot("symlink")
        File(root, "real.txt").writeText("safe")
        Os.symlink("real.txt", File(root, "link.txt").absolutePath)
        val reviewed = Os.lstat(File(root, "link.txt").absolutePath)

        val result = deleteReviewed(root, "link.txt", reviewed)

        assertFalse(result.toString(), result.optBoolean("success"))
        assertTrue("symlink target must survive", File(root, "real.txt").exists())
        assertTrue("symlink itself should not be removed by reviewed deletion", File(root, "link.txt").exists())
    }

    @Test
    fun bindMountIntroducedBelowReviewedDirectoryDoesNotDeleteMountedContents() {
        assumeTrue("Root is required for bind-mount destructive safety validation", hasRoot())
        val root = freshRoot("bind")
        val source = File(root, "source").apply { mkdirs() }
        val sentinel = File(source, "sentinel.txt").apply { writeText("do not delete") }
        val target = File(root, "target").apply { mkdirs() }
        File(target, "ordinary.txt").writeText("ordinary")
        val mountPoint = File(target, "mounted").apply { mkdirs() }
        val reviewed = Os.lstat(target.absolutePath)

        su("mount --bind ${shellQuote(source.absolutePath)} ${shellQuote(mountPoint.absolutePath)}")
        mountedTargets += mountPoint

        val result = deleteReviewed(root, "target", reviewed)

        assertTrue("bind-mounted source contents must survive: $result", sentinel.exists())
        assertFalse("deleting a reviewed directory with a new mount child must not report clean success: $result", result.optBoolean("success"))
    }

    private fun freshRoot(name: String): File {
        val root = File(context.cacheDir, "dustui-validation-$name-${System.nanoTime()}")
        check(root.mkdirs()) { "Unable to create ${root.absolutePath}" }
        roots += root
        return root
    }

    private fun deleteReviewed(root: File, relative: String, stat: android.system.StructStat): JSONObject {
        return JSONObject(
            NativeDustScanner.deleteReviewedPath(
                UUID.randomUUID().toString(),
                root.absolutePath,
                relative,
                stat.st_dev,
                stat.st_ino,
                stat.st_mode,
                stat.st_size,
                stat.st_mtime,
            ),
        )
    }

    private fun hasRoot(): Boolean = runCatching { su("id -u").trim() == "0" }.getOrDefault(false)

    private fun su(command: String): String {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        check(code == 0) { "su command failed ($code): $command\n$output" }
        return output
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
