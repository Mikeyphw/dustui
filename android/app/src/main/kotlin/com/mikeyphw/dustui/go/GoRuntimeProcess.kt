package com.mikeyphw.dustui.go

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Production Android child-process binding for the app-owned Go runtime. */
class GoRuntimeProcess private constructor(
    private val executable: File,
    private val process: Process,
    private val input: BufferedWriter,
    private val output: BufferedReader,
    private val stderrCapture: BoundedStderrCapture,
    val readiness: JSONObject,
) : Closeable {
    private val lock = ReentrantLock()

    val isAlive: Boolean get() = process.isAlive

    /** Bounded diagnostics only; never expose this text directly to the UI. */
    fun diagnosticStderrTail(): String = stderrCapture.snapshot()

    fun request(request: GoRuntimeRequest): GoRuntimeResponse = lock.withLock {
        if (!process.isAlive) throw transportFailure(GoRuntimeTransportException.Code.DEAD, request)
        val frame = JSONObject()
            .put("protocol", GoRuntimeSpec().protocol)
            .put("type", request.type)
            .put("requestId", request.requestId)
            .put("sequence", request.sequence)
            .put("generation", request.generation)
            .put("payload", JSONObject(request.payloadJson))
        val encoded = frame.toString()
        if (encoded.toByteArray().size > GoRuntimeSpec().maxFrameBytes) {
            throw GoRuntimeRequestException.from(request.type, "request_exceeds_bounded_stream")
                ?: IllegalStateException("Storage engine request exceeded its bounded transfer limit.")
        }
        try {
            input.write(encoded)
            input.newLine()
            input.flush()
        } catch (error: Exception) {
            throw transportFailure(GoRuntimeTransportException.Code.WRITE_FAILED, request, error)
        }
        val line = try {
            output.readLine()
        } catch (error: Exception) {
            throw transportFailure(GoRuntimeTransportException.Code.READ_FAILED, request, error)
        } ?: throw transportFailure(GoRuntimeTransportException.Code.EOF, request)

        if (line.toByteArray().size > GoRuntimeSpec().maxFrameBytes) {
            throw transportFailure(
                GoRuntimeTransportException.Code.PROTOCOL,
                request,
                IllegalStateException("response_exceeds_bounded_stream"),
            )
        }

        val parsed = try {
            val response = JSONObject(line)
            GoRuntimeResponse(
                requestId = response.getString("requestId"),
                sequence = response.getLong("sequence"),
                generation = response.getLong("generation"),
                type = response.getString("type"),
                payloadJson = response.optJSONObject("payload")?.toString() ?: "{}",
            )
        } catch (error: Exception) {
            throw transportFailure(GoRuntimeTransportException.Code.PROTOCOL, request, error)
        }
        if (!parsed.matches(request)) {
            throw transportFailure(
                GoRuntimeTransportException.Code.PROTOCOL,
                request,
                IllegalStateException("stale_or_mismatched_runtime_response"),
            )
        }
        parsed
    }

    private fun transportFailure(
        code: GoRuntimeTransportException.Code,
        request: GoRuntimeRequest? = null,
        cause: Throwable? = null,
    ): GoRuntimeTransportException {
        if (code != GoRuntimeTransportException.Code.PROTOCOL && process.isAlive) {
            runCatching { process.waitFor(25, TimeUnit.MILLISECONDS) }
        }
        val alive = process.isAlive
        return GoRuntimeTransportException(
            code = code,
            processAlive = alive,
            exitCode = if (alive) null else runCatching { process.exitValue() }.getOrNull(),
            stderrTail = stderrCapture.snapshot(waitForDrain = !alive),
            requestType = request?.type,
            requestId = request?.requestId,
            sequence = request?.sequence,
            generation = request?.generation,
            cause = cause,
        )
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
        runCatching { stderrCapture.close() }
    }

    companion object {
        fun start(context: Context): GoRuntimeProcess {
            val executable = File(GoRuntimePathContract.executablePath(context.applicationInfo.nativeLibraryDir))
            if (!executable.isFile) {
                throw GoRuntimeTransportException(
                    code = GoRuntimeTransportException.Code.MISSING_EXECUTABLE,
                    processAlive = false,
                    exitCode = null,
                    stderrTail = "executable=${executable.absolutePath}",
                )
            }
            val stateDir = File(context.noBackupFilesDir, "go-state").apply { mkdirs() }
            val database = File(stateDir, "dustui-go.db")
            val processBuilder = ProcessBuilder(executable.absolutePath, "serve")
            processBuilder.command().add("--db")
            processBuilder.command().add(database.absolutePath)
            val process = processBuilder.start()
            val stderrCapture = BoundedStderrCapture(
                BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)),
            ).also { it.start() }
            val input = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
            val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val readyLine = try {
                output.readLine()
            } catch (error: Exception) {
                val alive = process.isAlive
                val failure = GoRuntimeTransportException(
                    code = GoRuntimeTransportException.Code.READINESS_INVALID,
                    processAlive = alive,
                    exitCode = if (alive) null else runCatching { process.exitValue() }.getOrNull(),
                    stderrTail = stderrCapture.snapshot(waitForDrain = !alive),
                    cause = error,
                )
                process.destroyForcibly()
                runCatching { stderrCapture.close() }
                throw failure
            }
            if (readyLine == null) {
                runCatching { process.waitFor(25, TimeUnit.MILLISECONDS) }
                val alive = process.isAlive
                val failure = GoRuntimeTransportException(
                    code = GoRuntimeTransportException.Code.READINESS_EOF,
                    processAlive = alive,
                    exitCode = if (alive) null else runCatching { process.exitValue() }.getOrNull(),
                    stderrTail = stderrCapture.snapshot(waitForDrain = !alive),
                )
                process.destroyForcibly()
                runCatching { stderrCapture.close() }
                throw failure
            }
            try {
                require(readyLine.toByteArray().size <= GoRuntimeSpec().maxFrameBytes) { "readiness_exceeds_bounded_stream" }
                val readiness = JSONObject(readyLine)
                require(readiness.optString("protocol").isNotBlank()) { "go_runtime_readiness_protocol_missing" }
                require(readiness.optJSONArray("requiredTables") != null) { "go_runtime_readiness_tables_missing" }
                return GoRuntimeProcess(executable, process, input, output, stderrCapture, readiness)
            } catch (error: Exception) {
                val alive = process.isAlive
                val failure = GoRuntimeTransportException(
                    code = GoRuntimeTransportException.Code.READINESS_INVALID,
                    processAlive = alive,
                    exitCode = if (alive) null else runCatching { process.exitValue() }.getOrNull(),
                    stderrTail = stderrCapture.snapshot(waitForDrain = !alive),
                    cause = error,
                )
                process.destroyForcibly()
                runCatching { stderrCapture.close() }
                throw failure
            }
        }
    }
}

private class BoundedStderrCapture(
    private val reader: BufferedReader,
    private val maxLines: Int = 32,
    private val maxChars: Int = 8_192,
) : Closeable {
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val drained = CountDownLatch(1)
    private var chars = 0
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        Thread(
            {
                try {
                    reader.forEachLine { line -> append(line) }
                } finally {
                    drained.countDown()
                }
            },
            "dustui-go-stderr",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun append(line: String) = synchronized(lock) {
        val bounded = if (line.length <= maxChars) line else line.takeLast(maxChars)
        lines.addLast(bounded)
        chars += bounded.length
        while (lines.size > maxLines || chars > maxChars) {
            chars -= lines.removeFirst().length
        }
    }

    fun snapshot(waitForDrain: Boolean = false): String {
        if (waitForDrain) runCatching { drained.await(50, TimeUnit.MILLISECONDS) }
        return synchronized(lock) { lines.joinToString(" | ") }
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { drained.await(50, TimeUnit.MILLISECONDS) }
    }
}
