package com.mikeyphw.dustui.validation

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.data.CleanupPlan
import com.mikeyphw.dustui.data.CleanupPlanItem
import com.mikeyphw.dustui.root.RootServiceClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Root/device validation for Go manifest/capability + fd-relative mutation safety. */
@RunWith(AndroidJUnit4::class)
class RootedDestructiveSafetyInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val roots = mutableListOf<File>()
    private val mountedTargets = mutableListOf<File>()

    @After fun cleanup() {
        mountedTargets.asReversed().forEach { target -> runCatching { su("umount ${shellQuote(target.absolutePath)}") } }
        roots.asReversed().forEach { root -> root.deleteRecursively() }
    }

    @Test fun goReviewedDeletionDeletesOnlyReviewedFile() = runBlocking {
        val client = rootedClient()
        val root = freshRoot("fd-file")
        val target = File(root, "target.txt").apply { writeText("reviewed") }
        val review = client.reviewCleanup(plan(root, target))
        assertTrue("review failed: $review", review.executable)
        val result = client.executeCleanup(review)
        assertTrue("Go mutation failed: $result", result.ok)
        assertFalse("reviewed file should be gone", target.exists())
    }

    @Test fun fdRelativeDeletionBlocksTopLevelIdentityReplacement() = runBlocking {
        val client = rootedClient()
        val root = freshRoot("identity")
        val target = File(root, "target.txt").apply { writeText("old") }
        val review = client.reviewCleanup(plan(root, target))
        assertTrue(review.executable)
        assertTrue(target.delete())
        target.writeText("new")
        val result = client.executeCleanup(review)
        assertFalse("replacement must not be accepted: $result", result.ok)
        assertTrue("replacement must survive failed reviewed identity check", target.exists())
    }

    @Test fun fdRelativeDeletionBlocksSymlinkLeaf() = runBlocking {
        val client = rootedClient()
        val root = freshRoot("symlink")
        File(root, "real.txt").writeText("safe")
        val link = File(root, "link.txt")
        Os.symlink("real.txt", link.absolutePath)
        val review = client.reviewCleanup(plan(root, link))
        assertFalse("selected symlink must be rejected: $review", review.executable)
        assertTrue(File(root, "real.txt").exists())
        assertTrue(link.exists())
    }

    @Test fun bindMountIntroducedBelowReviewedDirectoryDoesNotDeleteMountedContents() = runBlocking {
        val client = rootedClient()
        val root = freshRoot("bind")
        val source = File(root, "source").apply { mkdirs() }
        val sentinel = File(source, "sentinel.txt").apply { writeText("do not delete") }
        val target = File(root, "target").apply { mkdirs() }
        File(target, "ordinary.txt").writeText("ordinary")
        val mountPoint = File(target, "mounted").apply { mkdirs() }
        val review = client.reviewCleanup(plan(root, target))
        assertTrue("pre-mount review should succeed: $review", review.executable)
        su("mount --bind ${shellQuote(source.absolutePath)} ${shellQuote(mountPoint.absolutePath)}")
        mountedTargets += mountPoint
        val result = client.executeCleanup(review)
        assertTrue("bind-mounted source contents must survive: $result", sentinel.exists())
        assertFalse("new mount child must prevent verified success: $result", result.ok)
    }

    private suspend fun rootedClient(): RootServiceClient = RootServiceClient(context).also {
        assumeTrue("Root + Go root worker are required", it.rootAvailable())
    }

    private fun plan(root: File, target: File): CleanupPlan = CleanupPlan(
        id = UUID.randomUUID().toString(),
        scanId = "instrumented-${UUID.randomUUID()}",
        rootPath = root.absolutePath,
        scanGeneration = 1,
        items = listOf(CleanupPlanItem(path = target.absolutePath, sizeBytes = target.length(), isDirectory = target.isDirectory, modifiedAt = target.lastModified() / 1000L)),
    )

    private fun freshRoot(name: String): File = File(context.cacheDir, "dustui-validation-$name-${System.nanoTime()}").also {
        check(it.mkdirs()) { "Unable to create ${it.absolutePath}" }
        roots += it
    }

    private fun su(command: String): String {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        check(code == 0) { "su command failed ($code): $command\n$output" }
        return output
    }
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
