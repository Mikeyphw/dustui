package com.mikeyphw.dustui.data

import com.mikeyphw.dustui.ui.DustRouteResolution
import com.mikeyphw.dustui.ui.DustRouteSelection
import com.mikeyphw.dustui.ui.DustSemanticNavigation
import com.mikeyphw.dustui.ui.DustSemanticRoute
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDiagnosticsProductUxTest {
    private fun run(
        id: String,
        state: String = "running",
        active: Boolean = true,
        history: Boolean = false,
        recovery: Boolean = false,
        truth: String = "pending",
    ) = ActivityRun(
        operationId = id,
        requestId = "request-$id",
        kind = "scan",
        sources = listOf("/storage/emulated/0"),
        state = state,
        active = active,
        history = history,
        recoveryRequired = recovery,
        terminalTruth = truth,
        progressJson = "{}",
        resultJson = "{}",
        verificationJson = "{}",
        retryGate = if (active) "blocked" else "allowed",
        cancelGate = if (active) "allowed" else "blocked",
        reconcileGate = if (recovery) "allowed" else "blocked",
        evidenceCorrelation = "evidence:$id",
        updatedAt = 1_700_000_000_000,
    )

    @Test fun `AUD FIX11 Activity filters durable lifecycle truth`() {
        val active = run("active")
        val recovery = run("recovery", state = "recovery_required", active = false, history = true, recovery = true, truth = "recovery_required")
        val failed = run("failed", state = "failed", active = false, history = true, truth = "failure")
        val completed = run("completed", state = "completed", active = false, history = true, truth = "success")

        assertTrue(ActivityFilter(ActivityStateFilter.Active).accepts(active))
        assertTrue(ActivityFilter(ActivityStateFilter.NeedsAttention).accepts(recovery))
        assertTrue(ActivityFilter(ActivityStateFilter.Failed).accepts(failed))
        assertTrue(ActivityFilter(ActivityStateFilter.Completed).accepts(completed))
        assertFalse(ActivityFilter(ActivityStateFilter.Completed).accepts(recovery))
        assertEquals("Recovery required", recovery.statusLabel)
    }

    @Test fun `AUD FIX11 diagnostics remediation preserves exact Activity entity identity`() {
        val problem = JSONObject(
            """{
              "id":"operation_recovery_required",
              "reason":"Operation needs recovery",
              "recoveryRequired":true,
              "operationId":"op-42",
              "guidance":"Review and reconcile the operation.",
              "risk":"medium",
              "remediationActions":[{
                "code":"OPEN_ACTIVITY_RUN",
                "label":"Review operation",
                "route":"activity/run",
                "entityId":"op-42",
                "destructive":false
              }]
            }""",
        ).toDiagnosticProblem()

        val action = problem.remediationActions.single()
        assertEquals("activity/run", action.route)
        assertEquals("op-42", action.entityId)
        assertTrue(problem.recoveryRequired)
    }

    @Test fun `AUD FIX11 Activity run deep link round trips operation correlation`() {
        val selection = DustRouteSelection(DustSemanticRoute.ActivityRun, "op-42", 9)
        val deepLink = DustSemanticNavigation.deepLink(selection)
        val resolved = DustSemanticNavigation.resolveDeepLink(deepLink, 10)
        assertTrue(resolved is DustRouteResolution.Resolved)
        val restored = (resolved as DustRouteResolution.Resolved).selection
        assertEquals(DustSemanticRoute.ActivityRun, restored.route)
        assertEquals("op-42", restored.entityId)
        assertEquals(10, restored.generation)
    }
}
