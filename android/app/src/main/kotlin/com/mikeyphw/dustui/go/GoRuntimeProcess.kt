package com.mikeyphw.dustui.go

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Production Android child-process binding for the app-owned Go runtime. */
class GoRuntimeProcess private constructor(
    private val executable: File,
    private val process: Process,
    private val input: BufferedWriter,
    private val output: BufferedReader,
    val readiness: JSONObject,
) : Closeable {
    private val lock = ReentrantLock()

    val isAlive: Boolean get() = process.isAlive

    fun request(request: GoRuntimeRequest): GoRuntimeResponse = lock.withLock {
        check(process.isAlive) { "go_runtime_dead_requires_reconcile" }
        val frame = JSONObject()
            .put("protocol", GoRuntimeSpec().protocol)
            .put("type", request.type)
            .put("requestId", request.requestId)
            .put("sequence", request.sequence)
            .put("generation", request.generation)
            .put("payload", JSONObject(request.payloadJson))
        val encoded = frame.toString()
        require(encoded.toByteArray().size <= GoRuntimeSpec().maxFrameBytes) { "payload_exceeds_bounded_stream" }
        input.write(encoded)
        input.newLine()
        input.flush()
        val line = output.readLine() ?: error("go_runtime_eof_requires_reconcile")
        require(line.toByteArray().size <= GoRuntimeSpec().maxFrameBytes) { "response_exceeds_bounded_stream" }
        val response = JSONObject(line)
        val parsed = GoRuntimeResponse(
            requestId = response.getString("requestId"),
            sequence = response.getLong("sequence"),
            generation = response.getLong("generation"),
            type = response.getString("type"),
            payloadJson = response.optJSONObject("payload")?.toString() ?: "{}",
        )
        check(parsed.matches(request)) { "stale_or_mismatched_runtime_response" }
        parsed
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
        process.destroy()
        if (process.isAlive) process.destroyForcibly()
    }

    companion object {
        fun start(context: Context): GoRuntimeProcess {
            val executable = File(GoRuntimePathContract.executablePath(context.applicationInfo.nativeLibraryDir))
            require(executable.isFile) { "go_runtime_missing:${executable.absolutePath}" }
            val process = ProcessBuilder(executable.absolutePath, "serve")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
            val input = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
            val output = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val readyLine = output.readLine() ?: run {
                process.destroyForcibly()
                error("go_runtime_readiness_eof")
            }
            require(readyLine.toByteArray().size <= GoRuntimeSpec().maxFrameBytes) { "readiness_exceeds_bounded_stream" }
            val readiness = JSONObject(readyLine)
            require(readiness.optString("protocol").isNotBlank()) { "go_runtime_readiness_protocol_missing" }
            require(readiness.optJSONArray("requiredTables") != null) { "go_runtime_readiness_tables_missing" }
            return GoRuntimeProcess(executable, process, input, output, readiness)
        }
    }
}
