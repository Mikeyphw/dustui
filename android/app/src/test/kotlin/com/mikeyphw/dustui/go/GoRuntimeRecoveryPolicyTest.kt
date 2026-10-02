package com.mikeyphw.dustui.go

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class GoRuntimeRecoveryPolicyTest {
    @Test
    fun observationalStorageRequestsMayReplayAfterRuntimeDeath() {
        listOf(
            "domain.scan.latest",
            "domain.scan.browse.page",
            "domain.authority.query",
            "domain.product.insights",
            "domain.cleanup.operations.list",
            "domain.diagnostics.snapshot",
        ).forEach { type ->
            assertTrue("expected read-only replay for $type", GoRuntimeRecoveryPolicy.mayReplay(type))
        }
    }

    @Test
    fun mutationsFailClosedInsteadOfBlindReplay() {
        listOf(
            "domain.scan.session.upsert",
            "domain.scan.entries.delete",
            "domain.package.snapshot.replace",
            "domain.cleanup.operation.claim",
            "domain.cleanup.verification.reverify",
            "domain.automation.advance",
            "domain.automation.reconcile",
            "domain.diagnostics.record",
            "domain.support.bundle",
        ).forEach { type ->
            assertFalse("mutation must not auto-replay: $type", GoRuntimeRecoveryPolicy.mayReplay(type))
        }
    }

    @Test
    fun internalRuntimeSentinelsNeverBecomeUserFacingMessages() {
        val failure = GoRuntimeTransportException(
            code = GoRuntimeTransportException.Code.EOF,
            processAlive = false,
            exitCode = 17,
            stderrTail = "fatal: simulated child failure",
            requestType = "domain.scan.latest",
            requestId = "req-17",
            sequence = 42,
            generation = 7,
        )

        assertFalse(failure.message.orEmpty().contains("go_runtime_"))
        assertTrue(failure.diagnosticSummary.contains("go_runtime_eof_requires_reconcile"))
        assertTrue(failure.diagnosticSummary.contains("exitCode=17"))
        assertTrue(failure.diagnosticSummary.contains("requestType=domain.scan.latest"))
        assertTrue(failure.diagnosticSummary.contains("requestId=req-17"))
        assertTrue(failure.diagnosticSummary.contains("sequence=42"))
        assertTrue(failure.diagnosticSummary.contains("generation=7"))
        assertTrue(failure.diagnosticSummary.contains("simulated child failure"))

        val reconcile = GoRuntimeReconciliationRequiredException(failure)
        assertFalse(reconcile.message.orEmpty().contains("go_runtime_"))
        assertTrue(reconcile.message.orEmpty().contains("not replayed"))
    }

    @Test
    fun internalServerErrorsAreMappedToUserSafeMessages() {
        listOf(
            "runtime_internal_error",
            "runtime_response_encode_failed",
            "malformed_runtime_frame",
            "unsupported_session_protocol",
            "frame_identity_required",
            "request_exceeds_bounded_stream",
            "response_exceeds_bounded_stream",
        ).forEach { code ->
            val failure = GoRuntimeRequestException.from("domain.scan.latest", code)
            assertNotNull("expected safe mapping for $code", failure)
            assertFalse(failure!!.message.orEmpty().contains(code))
        }
    }
}
