package com.mikeyphw.dustui.root

import android.content.Context
import android.os.ParcelFileDescriptor
import com.mikeyphw.dustui.go.GoChildProcessPolicy
import com.mikeyphw.dustui.go.GoRuntimePathContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fixed root-side launcher for the packaged Go executable.
 *
 * The privileged Android service remains a platform adapter only: it owns binder,
 * package visibility and process lifetime, while scan/review/mutation authority is
 * implemented by the Go worker commands.
 */
object GoRootWorker {
    private const val MAX_COMMAND_OUTPUT_BYTES = 8 * 1024 * 1024
    private const val MAX_STDERR_BYTES = 256 * 1024

    fun version(context: Context): String = call(
        context = context,
        command = "root-worker-version",
        inputJson = "",
        timeoutMs = 5_000L,
    ).output.trim()

    fun runScan(
        context: Context,
        requestJson: String,
        ownedOutputFd: Int,
        onProcess: (Process) -> Unit,
    ): WorkerResult {
        val process = start(context, "root-scan")
        onProcess(process)
        val stderr = boundedDrain(process.errorStream, "DustUI-GoRoot-scan-stderr", MAX_STDERR_BYTES)
        var outputFdOwned = true
        return try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(requestJson)
                writer.newLine()
            }
            val descriptor = ParcelFileDescriptor.adoptFd(ownedOutputFd)
            outputFdOwned = false
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                process.inputStream.use { input -> input.copyTo(output, 128 * 1024) }
            }
            val exit = process.waitFor()
            stderr.join(2_000L)
            WorkerResult(exit == 0 || exit == 130, exit, "", drainText(stderr))
        } catch (error: Throwable) {
            if (outputFdOwned) runCatching { ParcelFileDescriptor.adoptFd(ownedOutputFd).close() }
            if (process.isAlive) process.destroyForcibly()
            WorkerResult(false, -1, "", error.message ?: error.javaClass.simpleName)
        }
    }

    fun verifyDuplicates(
        context: Context,
        requestJson: String,
        onProcess: (Process) -> Unit,
    ): WorkerResult = call(
        context = context,
        command = "root-duplicates-verify",
        inputJson = requestJson,
        timeoutMs = 30 * 60 * 1000L,
        onProcess = onProcess,
    )

    fun reviewCleanup(context: Context, planJson: String): WorkerResult = call(
        context = context,
        command = "root-cleanup-review",
        inputJson = planJson,
        timeoutMs = 90_000L,
    )

    fun executeCleanup(
        context: Context,
        executionJson: String,
        onProcess: (Process) -> Unit,
    ): WorkerResult = call(
        context = context,
        command = "root-cleanup-execute",
        inputJson = executionJson,
        timeoutMs = 120_000L,
        onProcess = onProcess,
    )

    fun reconcileCleanup(context: Context, requestJson: String): WorkerResult = call(
        context = context,
        command = "root-cleanup-reconcile",
        inputJson = requestJson,
        timeoutMs = 10_000L,
    )

    private fun call(
        context: Context,
        command: String,
        inputJson: String,
        timeoutMs: Long,
        onProcess: ((Process) -> Unit)? = null,
    ): WorkerResult {
        val process = start(context, command)
        onProcess?.invoke(process)
        val stderr = boundedDrain(process.errorStream, "DustUI-GoRoot-$command-stderr", MAX_STDERR_BYTES)
        return try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (inputJson.isNotEmpty()) {
                    writer.write(inputJson)
                    writer.newLine()
                }
            }
            val stdout = boundedDrain(process.inputStream, "DustUI-GoRoot-$command-stdout", MAX_COMMAND_OUTPUT_BYTES)
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
            }
            stdout.join(2_000L)
            stderr.join(2_000L)
            val output = drainText(stdout)
            if (stdout.overflowed) {
                WorkerResult(false, 75, output, "Go root worker response exceeded $MAX_COMMAND_OUTPUT_BYTES bytes")
            } else if (!finished) {
                WorkerResult(false, 124, output, "Go root worker timed out; ${drainText(stderr)}")
            } else {
                val exit = process.exitValue()
                WorkerResult(exit == 0, exit, output, drainText(stderr))
            }
        } catch (error: Throwable) {
            if (process.isAlive) process.destroyForcibly()
            WorkerResult(false, -1, "", error.message ?: error.javaClass.simpleName)
        }
    }

    private fun start(context: Context, command: String): Process {
        val executable = File(GoRuntimePathContract.executablePath(context.applicationInfo.nativeLibraryDir))
        require(executable.isFile) { "go_root_worker_missing:${executable.absolutePath}" }
        val workingDirectory = File(context.noBackupFilesDir, "go-root-worker").apply { mkdirs() }
        val builder = ProcessBuilder(executable.absolutePath, command)
        GoChildProcessPolicy.apply(
            builder = builder,
            workingDirectory = workingDirectory,
            homeDirectory = context.filesDir,
            temporaryDirectory = context.cacheDir,
        )
        return builder.start()
    }

    private class Drain(val buffer: ByteArrayOutputStream, val thread: Thread, @Volatile var overflowed: Boolean = false) {
        fun join(timeoutMs: Long) = thread.join(timeoutMs)
    }

    private fun boundedDrain(input: java.io.InputStream, name: String, maxBytes: Int): Drain {
        val output = ByteArrayOutputStream()
        lateinit var drain: Drain
        val thread = Thread({
            input.use { stream ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    val remaining = maxBytes - output.size()
                    if (remaining > 0) output.write(buffer, 0, minOf(count, remaining))
                    if (count > remaining) drain.overflowed = true
                }
            }
        }, name).apply { isDaemon = true }
        drain = Drain(output, thread)
        thread.start()
        return drain
    }

    private fun drainText(drain: Drain): String = drain.buffer.toString(Charsets.UTF_8.name()).trim()


    data class WorkerResult(
        val ok: Boolean,
        val exitCode: Int,
        val output: String,
        val error: String,
    )
}
