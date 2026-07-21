package com.mikeyphw.dustui.scan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NdjsonTailReaderTest {
    @Test fun retainsPartialLineAcrossReads() {
        val file = File.createTempFile("dustui", ".ndjson")
        try {
            file.writeText("{\"type\":\"start\"}")
            NdjsonTailReader(file).use { reader ->
                assertEquals(emptyList<String>(), reader.readAvailable())
                file.appendText("\n{\"type\":\"error\",\"message\":\"x\"}\n")
                assertEquals(2, reader.readAvailable().size)
            }
        } finally {
            file.delete()
        }
    }
}
