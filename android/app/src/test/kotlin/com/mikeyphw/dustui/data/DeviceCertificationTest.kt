package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCertificationTest {
    @Test fun `healthy rooted device is release ready`() {
        val report = DeviceCertification.evaluate(true, 0, "KernelSU", "dustui-scanner/1", true, true, true, true, 1_000_000_000, true, 1)
        assertTrue(report.releaseReady)
        assertEquals(0, report.failures)
    }

    @Test fun `non root uid blocks certification`() {
        val report = DeviceCertification.evaluate(true, 10_000, "Generic su", "scanner", true, true, true, true, 1_000_000_000, true, 1)
        assertFalse(report.releaseReady)
        assertEquals(CertificationStatus.FAIL, report.checks.first { it.id == "root" }.status)
    }

    @Test fun `notification and battery restrictions are warnings`() {
        val report = DeviceCertification.evaluate(true, 0, "Magisk", "scanner", true, true, false, true, 1_000_000_000, false, 1)
        assertTrue(report.releaseReady)
        assertEquals(2, report.warnings)
    }
}
