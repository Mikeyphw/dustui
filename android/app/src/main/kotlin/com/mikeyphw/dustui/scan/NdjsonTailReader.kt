package com.mikeyphw.dustui.scan

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class NdjsonTailReader(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private var offset = 0L
    private var carry = ByteArray(0)
    private val readBuffer = ByteArray(MAX_CHUNK_BYTES)

    /** Reads at most one bounded chunk and keeps partial lines for the next call. */
    fun readAvailable(): List<String> {
        val remaining = input.length() - offset
        if (remaining <= 0) return emptyList()
        val requested = minOf(remaining, MAX_CHUNK_BYTES.toLong()).toInt()
        input.seek(offset)
        val read = input.read(readBuffer, 0, requested)
        if (read <= 0) return emptyList()
        offset += read.toLong()

        val combinedSize = carry.size + read
        require(combinedSize <= MAX_LINE_BYTES) { "Scanner emitted an NDJSON line larger than $MAX_LINE_BYTES bytes" }
        val combined = ByteArray(combinedSize)
        carry.copyInto(combined)
        readBuffer.copyInto(combined, carry.size, 0, read)

        val lines = mutableListOf<String>()
        var start = 0
        combined.forEachIndexed { index, byte ->
            if (byte == '\n'.code.toByte()) {
                if (index > start) {
                    lines += String(combined, start, index - start, StandardCharsets.UTF_8).trimEnd('\r')
                }
                start = index + 1
            }
        }
        carry = combined.copyOfRange(start, combined.size)
        return lines.filter(String::isNotBlank)
    }

    override fun close() = input.close()

    private companion object {
        const val MAX_CHUNK_BYTES = 256 * 1024
        const val MAX_LINE_BYTES = 1024 * 1024
    }
}
