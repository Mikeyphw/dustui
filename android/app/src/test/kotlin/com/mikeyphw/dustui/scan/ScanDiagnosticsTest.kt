package com.mikeyphw.dustui.scan

import com.mikeyphw.dustui.data.ScanDiagnosticIssue
import com.mikeyphw.dustui.data.ScanDiagnosticSeverity
import com.mikeyphw.dustui.data.ScanDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDiagnosticsTest {
    @Test fun `diagnostics preserve root identity counters and exact issue context`() {
        val original = ScanDiagnostics(
            scanId = "scan-1",
            rootPath = "/data/data/com.termux",
            rootServiceOk = true,
            rootUid = 0,
            rootGid = 0,
            rootPid = 42,
            rootBackend = "KernelSU",
            scannerVersion = "dustui-native-root/3",
            selinuxEnforcing = true,
            selinuxContext = "u:r:su:s0",
            mountNamespace = "mnt:[4026533000]",
            preflightOk = true,
            canonicalRoot = "/data/user/0/com.termux",
            rootOpened = true,
            rootHasChildren = true,
            emittedEntries = 10,
            emittedFiles = 8,
            emittedDirectories = 2,
            truncated = true,
            issues = listOf(
                ScanDiagnosticIssue(
                    code = "DIRECTORY_OPEN_FAILED",
                    severity = ScanDiagnosticSeverity.WARNING,
                    phase = "traversal",
                    message = "Permission denied",
                    path = "/data/data/com.termux/files",
                    errno = 13,
                    occurredAt = 7,
                ),
            ),
        )
        val restored = ScanDiagnostics.fromJson(original.toJson())
        assertEquals(0, restored.rootUid)
        assertEquals(8, restored.emittedFiles)
        assertTrue(restored.truncated)
        assertEquals("DIRECTORY_OPEN_FAILED", restored.issues.single().code)
        assertEquals(13, restored.issues.single().errno)
    }
}
