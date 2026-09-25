package com.mikeyphw.dustui.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanLaunchFlowTest {
    private val request = ScanRequest(rootPath = "/storage/emulated/0").validated()

    @Test
    fun audFix10_scopeCannotStartBeforePreflightAndReview() {
        val selected = ScanLaunchFlow.choose(request, "Shared storage")
        assertEquals(ScanLaunchStage.Preflight, selected.stage)
        assertFalse(selected.canStart)
        val blocked = runCatching { ScanLaunchFlow.beginStart(selected) }
        assertTrue(blocked.isFailure)
    }

    @Test
    fun audFix10_failedPreflightRemainsNonExecutableEvenOnReviewStep() {
        val selected = ScanLaunchFlow.choose(request, "Shared storage")
        val reviewed = ScanLaunchFlow.resolvePreflight(
            selected,
            ScanPreflightSummary(false, "root_open_failed", "Unable to open path", request.rootPath),
            ScanLaunchDecision(false, "preflight_failed", "Fix path access before starting", listOf("open_diagnostics")),
        )
        assertEquals(ScanLaunchStage.Review, reviewed.stage)
        assertFalse(reviewed.canStart)
    }

    @Test
    fun audFix10_explicitStartOnlyBecomesExecutableAfterSuccessfulReview() {
        val selected = ScanLaunchFlow.choose(request, "Shared storage")
        val reviewed = ScanLaunchFlow.resolvePreflight(
            selected,
            ScanPreflightSummary(true, "ok", "Path ready", request.rootPath, request.rootPath, "/storage/emulated/0", "fuse"),
            ScanLaunchDecision(true, "allowed", "Available"),
        )
        assertTrue(reviewed.canStart)
        assertEquals(ScanLaunchStage.Starting, ScanLaunchFlow.beginStart(reviewed).stage)
    }
}
