package com.mikeyphw.dustui.validation

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.root.NativeDustScanner
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Device-scale validation for the native scanner and scan-stream path.
 *
 * Run with:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.mikeyphw.dustui.validation.DeviceScalePerformanceInstrumentedTest \
 *     -Pandroid.testInstrumentationRunnerArguments.dustui.entries=100000
 */
@RunWith(AndroidJUnit4::class)
class DeviceScalePerformanceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val roots = mutableListOf<File>()
    private val files = mutableListOf<File>()

    @After
    fun cleanup() {
        files.asReversed().forEach { it.delete() }
        roots.asReversed().forEach { it.deleteRecursively() }
    }

    @Test
    fun nativeScannerHandlesAtLeastConfiguredLargeDatasetWithoutTruncation() {
        val entries = args.getString("dustui.entries")?.toIntOrNull()?.coerceAtLeast(1) ?: 100_000
        val maxMillis = args.getString("dustui.maxMillis")?.toLongOrNull()?.coerceAtLeast(1_000L) ?: 240_000L
        val root = createDataset(entries)
        val output = File.createTempFile("dustui-scale", ".ndjson", context.cacheDir)
        files += output
        val pfd = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE,
        )

        val started = SystemClock.elapsedRealtime()
        val result = NativeDustScanner.scan(
            scanId = "scale-${UUID.randomUUID()}",
            rootPath = root.absolutePath,
            outputFd = pfd.fd,
            maxDepth = 8,
            includeHidden = true,
            apparentSize = true,
            sameFilesystem = true,
            mode = 0,
            minSize = 0,
            maxEntries = entries.toLong() + 10_000L,
            excludes = emptyArray(),
        )
        pfd.close()
        val elapsed = SystemClock.elapsedRealtime() - started
        val complete = output.readLines().lastOrNull { it.contains("\"type\":\"complete\"") }?.let(::JSONObject)

        assertEquals("native scanner returned non-zero; output tail=${output.readLines().takeLast(3)}", 0, result)
        assertTrue("scanner did not emit a complete event", complete != null)
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
        repeat(entries) { index ->
            val bucket = File(root, "bucket-${index % buckets}")
            File(bucket, "file-$index.bin").writeText("dustui-scale-$index")
        }
        return root
    }
}
