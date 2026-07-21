package com.mikeyphw.dustui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEventParserTest {
    @Test fun parsesEntry() {
        val event = ScanEventParser.parse(
            """{"type":"entry","path":"/data/a","parentPath":"/data","name":"a","sizeBytes":10,"allocatedBytes":4096,"directory":false,"depth":2,"modifiedAt":42,"fileCount":1}""",
        )
        assertTrue(event is ScanEvent.Entry)
        event as ScanEvent.Entry
        assertEquals("/data/a", event.path)
        assertEquals(10, event.sizeBytes)
        assertEquals(4096, event.allocatedBytes)
    }

    @Test fun parsesCompletion() {
        val event = ScanEventParser.parse(
            """{"type":"complete","totalBytes":100,"scannedItems":4,"scannedBytes":80,"warnings":1}""",
        ) as ScanEvent.Complete
        assertEquals(100, event.totalBytes)
        assertEquals(4, event.scannedItems)
    }
}
