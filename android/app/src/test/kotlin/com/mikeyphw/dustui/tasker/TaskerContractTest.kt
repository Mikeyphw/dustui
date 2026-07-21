package com.mikeyphw.dustui.tasker

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskerContractTest {
    @Test fun `automation defaults are safe and read only`() {
        assertEquals("/storage/emulated/0", DustTaskerReceiver.DEFAULT_PATH)
        assertEquals("scan", DustTaskerReceiver.ACTION_SCAN)
        assertEquals("cancel", DustTaskerReceiver.ACTION_CANCEL)
    }
}
