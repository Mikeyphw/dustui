package com.mikeyphw.dustui.scan

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

class NdjsonTailReader(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private var offset = 0L
    private var carry = ByteArray(0)

    fun readAvailable(): List<String> {
        val remaining = input.length() - offset
        if (remaining <= 0) return emptyList()
        require(remaining <= Int.MAX_VALUE) { "Single scanner chunk is too large" }
        val bytes = ByteArray(remaining.toInt())
        input.seek(offset)
        input.readFully(bytes)
        offset += bytes.size

        val combined = ByteArray(carry.size + bytes.size)
        carry.copyInto(combined)
        bytes.copyInto(combined, carry.size)
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
}
