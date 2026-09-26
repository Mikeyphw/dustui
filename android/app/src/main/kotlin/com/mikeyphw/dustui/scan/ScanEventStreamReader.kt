package com.mikeyphw.dustui.scan

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.InputStream

/**
 * Bounded line reader for the direct root-worker pipe.
 *
 * The scanner protocol remains line-delimited JSON, but the Android process no longer
 * persists/tails an NDJSON state-transfer file. EOF is authoritative pipe completion.
 */
class ScanEventStreamReader internal constructor(
    input: InputStream,
) : Closeable {
    private val reader = input.bufferedReader(Charsets.UTF_8, BUFFER_BYTES)
    private var eof = false

    constructor(descriptor: ParcelFileDescriptor) : this(ParcelFileDescriptor.AutoCloseInputStream(descriptor))

    fun readAvailable(): List<String> {
        if (eof) return emptyList()
        val first = reader.readLine() ?: run {
            eof = true
            return emptyList()
        }
        require(first.toByteArray(Charsets.UTF_8).size <= MAX_LINE_BYTES) {
            "Scanner emitted a protocol line larger than $MAX_LINE_BYTES bytes"
        }
        val lines = mutableListOf(first)
        while (reader.ready() && lines.size < MAX_LINES_PER_READ) {
            val line = reader.readLine()
            if (line == null) {
                eof = true
                break
            }
            require(line.toByteArray(Charsets.UTF_8).size <= MAX_LINE_BYTES) {
                "Scanner emitted a protocol line larger than $MAX_LINE_BYTES bytes"
            }
            if (line.isNotBlank()) lines += line
        }
        return lines.filter(String::isNotBlank)
    }

    override fun close() = reader.close()

    private companion object {
        const val BUFFER_BYTES = 128 * 1024
        const val MAX_LINE_BYTES = 1024 * 1024
        const val MAX_LINES_PER_READ = 2048
    }
}
