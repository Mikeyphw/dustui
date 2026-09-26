package com.mikeyphw.dustui.scan

import java.io.ByteArrayInputStream
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanEventStreamReaderTest {
    @Test
    fun readsLineDelimitedProtocolWithoutFileTailing() {
        val payload = "{\"type\":\"start\"}\n{\"type\":\"complete\"}\n".toByteArray()
        ScanEventStreamReader(ByteArrayInputStream(payload)).use { reader ->
            assertEquals(
                listOf("{\"type\":\"start\"}", "{\"type\":\"complete\"}"),
                reader.readAvailable(),
            )
            assertTrue(reader.readAvailable().isEmpty())
        }
    }

    @Test
    fun ignoresBlankProtocolLines() {
        val payload = "\n{\"type\":\"progress\"}\n\n".toByteArray()
        ScanEventStreamReader(ByteArrayInputStream(payload)).use { reader ->
            val first = reader.readAvailable()
            val second = reader.readAvailable()
            assertEquals(listOf("{\"type\":\"progress\"}"), first + second)
        }
    }
}
