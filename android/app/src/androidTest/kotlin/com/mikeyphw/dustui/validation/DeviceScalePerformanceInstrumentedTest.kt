package com.mikeyphw.dustui.validation

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.root.RootServiceClient
import com.mikeyphw.dustui.scan.EntryMode
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.max

/** Device-scale validation for the production Go root scanner and NDJSON stream. */
@RunWith(AndroidJUnit4::class)
class DeviceScalePerformanceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val roots = mutableListOf<File>()
    private val files = mutableListOf<File>()

    @After fun cleanup() {
        files.asReversed().forEach { it.delete() }
        roots.asReversed().forEach { it.deleteRecursively() }
    }

    @Test fun goRootScannerHandlesAtLeastConfiguredLargeDatasetWithoutTruncation() = runBlocking {
        val client = RootServiceClient(context)
        assumeTrue("Root/Go worker is required for device-scale validation", client.rootAvailable())
        val entries = args.getString("dustui.entries")?.toIntOrNull()?.coerceAtLeast(1) ?: 100_000
        val maxMillis = args.getString("dustui.maxMillis")?.toLongOrNull()?.coerceAtLeast(1_000L) ?: 240_000L
        val root = createDataset(entries)
        val output = File.createTempFile("dustui-scale", ".ndjson", context.cacheDir).also(files::add)
        val pipe = ParcelFileDescriptor.createPipe()
        val scanId = "scale-${UUID.randomUUID()}"
        val request = ScanRequest(
            scanId = scanId,
            rootPath = root.absolutePath,
            maxDepth = 8,
            includeHidden = true,
            apparentSize = true,
            sameFilesystem = true,
            mode = EntryMode.ALL,
            minSize = 0,
            maxEntries = entries.toLong() + 10_000L,
            excludes = emptyList(),
        )
        val started = SystemClock.elapsedRealtime()

        val startState = try {
            client.startScan(request, pipe[1])
        } finally {
            /*
             * startScan() sends the write descriptor across Binder. The root
             * service duplicates/owns its descriptor before returning.
             *
             * The caller MUST close its own write end here. If it remains open,
             * the read side can never observe EOF after the Go worker exits and
             * input.copyTo() waits forever.
             */
            runCatching { pipe[1].close() }
        }

        assertTrue("Go root scan did not start: $startState", startState.ok)

        val reader = Executors.newSingleThreadExecutor()
        try {
            val readFuture = reader.submit<Long> {
                ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                    output.outputStream().use { sink ->
                        input.copyTo(sink)
                    }
                }
            }

            val alreadyElapsed = SystemClock.elapsedRealtime() - started
            val remainingMillis = (maxMillis - alreadyElapsed).coerceAtLeast(1L)

            try {
                readFuture.get(remainingMillis, TimeUnit.MILLISECONDS)
            } catch (error: TimeoutException) {
                /*
                 * Closing the local read descriptor guarantees a blocked pipe
                 * read is released even if thread interruption alone cannot
                 * interrupt the underlying Android/Linux read().
                 */
                runCatching { pipe[0].close() }
                readFuture.cancel(true)

                try {
                    client.cancelScan(scanId)
                } catch (_: Throwable) {
                    // Preserve the primary timeout failure.
                }

                throw AssertionError(
                    "Go root scan did not close its NDJSON stream within ${maxMillis}ms",
                    error,
                )
            }
        } finally {
            reader.shutdownNow()
            runCatching { pipe[0].close() }
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        val complete = output.useLines { lines -> lines.lastOrNull { it.contains("\"type\":\"complete\"") } }?.let(::JSONObject)
        assertTrue("Go root scanner did not emit a complete event", complete != null)
        assertFalse("large validation scan must not truncate at $entries entries", complete!!.optBoolean("truncated"))
        assertTrue("scanner emitted too few entries: ${complete.optLong("emittedEntries")}", complete.optLong("emittedEntries") >= entries.toLong())
        assertTrue("$entries entry scan exceeded ${maxMillis}ms: ${elapsed}ms", elapsed <= maxMillis)
    }

    private fun createDataset(entries: Int): File {
        val root = File(context.cacheDir, "dustui-scale-${System.nanoTime()}")
        check(root.mkdirs()) { "Unable to create ${root.absolutePath}" }
        roots += root
        val buckets = max(1, entries / 1_000)
        repeat(buckets) { index -> File(root, "bucket-$index").mkdirs() }
        repeat(entries) { index -> File(File(root, "bucket-${index % buckets}"), "file-$index.bin").writeText("dustui-scale-$index") }
        return root
    }
}
