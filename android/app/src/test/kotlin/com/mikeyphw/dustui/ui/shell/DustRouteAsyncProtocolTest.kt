package com.mikeyphw.dustui.ui.shell

import com.mikeyphw.dustui.ui.DustRouteSelection
import com.mikeyphw.dustui.ui.DustSemanticRoute
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DustRouteAsyncProtocolTest {
    @Test
    fun `AUD FIX 09 registry rejects route entity request and fingerprint drift`() {
        val initial = DustRouteSelection(DustSemanticRoute.FilesItem, "/sdcard/A.txt", 9)
        val registry = DustRouteRequestRegistry(initial)
        val active = registry.issue("detail", "path:/sdcard/A.txt")
        assertTrue(registry.accepts("detail", active))

        registry.activate(DustRouteSelection(DustSemanticRoute.FilesItem, "/sdcard/B.txt", 10))
        assertFalse(registry.accepts("detail", active))

        val current = registry.issue("detail", "path:/sdcard/B.txt")
        val lateRequest = current.copy(requestId = "late")
        val wrongFingerprint = current.copy(fingerprint = "path:/sdcard/C.txt")
        assertFalse(registry.accepts("detail", lateRequest))
        assertFalse(registry.accepts("detail", wrongFingerprint))
        assertTrue(registry.accepts("detail", current))
    }

    @Test
    fun `AUD FIX 09 entity switch invalidates package cleanup and activity work`() {
        val registry = DustRouteRequestRegistry(DustRouteSelection(DustSemanticRoute.AppsPackage, "pkg.one", 2))
        val packageRequest = registry.issue("package", "pkg:one")
        registry.activate(DustRouteSelection(DustSemanticRoute.AppsPackage, "pkg.two", 3))
        assertFalse(registry.accepts("package", packageRequest))

        registry.activate(DustRouteSelection(DustSemanticRoute.CleanReview, "manifest-2", 4))
        val review = registry.issue("cleanup-review", "manifest:2")
        registry.activate(DustRouteSelection(DustSemanticRoute.ActivityRun, "op-7", 5))
        assertFalse(registry.accepts("cleanup-review", review))
    }

    @Test
    fun `AUD FIX 09 wire gate is structured and blocked remains distinct`() {
        val gate = DustActionGate.fromWire(
            JSONObject(
                """{
                  "allowed":false,
                  "reasonCode":"scan_stale",
                  "message":"Run a new scan",
                  "remediationActions":[{"code":"start_scan","label":"Run scan","routeKey":"overview/home"}]
                }""".trimIndent(),
            ),
        )
        assertFalse(gate.allowed)
        assertTrue(gate.reasonCode == "scan_stale")
        assertTrue(gate.remediationActions.single().code == "start_scan")
        assertTrue(DustContentState.Blocked != DustContentState.Empty)
    }
}
