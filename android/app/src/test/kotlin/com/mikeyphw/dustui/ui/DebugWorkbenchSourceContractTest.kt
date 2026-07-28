package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.DebugCategory
import com.mikeyphw.dustui.data.DebugEventStore
import com.mikeyphw.dustui.data.DebugSeverity
import com.mikeyphw.dustui.data.DebugProbeTarget
import com.mikeyphw.dustui.data.DebugProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebugWorkbenchSourceContractTest {
    @Test fun `phase 9 debug event store is bounded and redacts sensitive evidence`() {
        val store = DebugEventStore(limit = 2)
        store.record(DebugCategory.App, DebugSeverity.Info, "first", "First event")
        store.record(DebugCategory.Scan, DebugSeverity.Warning, "second", "Second event", mapOf("rootPath" to "/data/user/0/private"))
        store.record(DebugCategory.Root, DebugSeverity.Error, "third", "Third event", mapOf("authorization" to "secret-token"))

        val events = store.events.value
        assertEquals(2, events.size)
        assertEquals("second", events.first().name)
        assertEquals("/data/…", events.first().evidence["rootPath"])
        assertEquals("<redacted>", events.last().evidence["authorization"])
    }

    @Test fun `phase 9 records structured events from root scan search and cleanup flows`() {
        val vm = source("src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
        val model = source("src/main/kotlin/com/mikeyphw/dustui/data/DebugEvents.kt")

        assertTrue(model.contains("const val DUST_DEBUG_EVENT_LIMIT = 500"))
        assertTrue(model.contains("enum class DebugCategory"))
        assertTrue(model.contains("enum class DebugSeverity"))
        assertTrue(model.contains("class DebugEventStore"))
        assertTrue(vm.contains("val debugEvents: StateFlow<List<DebugEvent>>"))
        assertTrue(vm.contains("recordDebugEvent(DebugCategory.Root"))
        assertTrue(vm.contains("recordDebugEvent(DebugCategory.Scan"))
        assertTrue(vm.contains("recordDebugEvent(DebugCategory.Search"))
        assertTrue(vm.contains("recordDebugEvent(DebugCategory.Recommendation"))
        assertTrue(vm.contains("recordDebugEvent(DebugCategory.Deletion"))
        assertTrue(vm.contains("fun clearDebugEvents()"))
    }

    @Test fun `phase 10 exposes debug workbench from tools without scan data`() {
        val design = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustDesign.kt")
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")

        assertTrue(design.contains("DEBUG(\"Debug workbench\", \"Debug\""))
        assertTrue(design.contains("DustDestination.DEBUG"))
        assertTrue(design.contains("DustDestination.SCAN, DustDestination.INSIGHTS, DustDestination.CLEANUP, DustDestination.DEBUG"))
        assertTrue(app.contains("DebugWorkbenchScreen("))
        assertTrue(app.contains("title = \"Debug workbench\""))
        assertTrue(app.contains("Live state"))
        assertTrue(app.contains("Trace timeline"))
        assertTrue(app.contains("Warnings/errors"))
        assertTrue(app.contains("Clear timeline"))
        assertTrue(app.contains("DebugCategory.entries"))
        assertFalse(app.contains("Debug workbench is unavailable until scan"))
    }


    @Test fun `phase 11 diagnostic probes define evidence only targets`() {
        val targets = DebugProbeTarget.entries.map { it.name }

        assertTrue(targets.contains("RootService"))
        assertTrue(targets.contains("DataRootAccess"))
        assertTrue(targets.contains("ModuleRootAccess"))
        assertTrue(targets.contains("ActiveScan"))
        assertTrue(targets.contains("ZeroFileScan"))
        assertTrue(targets.contains("ModuleScope"))
        assertTrue(targets.contains("DeletionReadiness"))
        assertTrue(targets.contains("SearchReadiness"))
        assertEquals("Pass", DebugProbeStatus.Pass.name)
    }

    @Test fun `phase 11 debug workbench exposes manual probes and records results`() {
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
        val vm = source("src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
        val model = source("src/main/kotlin/com/mikeyphw/dustui/data/DebugEvents.kt")

        assertTrue(model.contains("enum class DebugProbeTarget"))
        assertTrue(model.contains("data class DebugProbeResult"))
        assertTrue(vm.contains("val debugProbeResults: StateFlow<List<DebugProbeResult>>"))
        assertTrue(vm.contains("fun runAllDebugProbes()"))
        assertTrue(vm.contains("fun runDebugProbe(target: DebugProbeTarget)"))
        assertTrue(vm.contains("preflightProbe(DebugProbeTarget.DataRootAccess, \"/data\")"))
        assertTrue(vm.contains("preflightProbe(DebugProbeTarget.ModuleRootAccess, \"/data/adb/modules\")"))
        assertTrue(vm.contains("DeletionReadiness"))
        assertTrue(app.contains("Diagnostic probes"))
        assertTrue(app.contains("Evidence-only probes"))
        assertTrue(app.contains("Run all probes"))
        assertTrue(app.contains("DebugProbeCard("))
        assertFalse(app.contains("executeCleanup(review)"))
    }

    private fun source(relative: String): String = File(projectRoot(), "android/app/$relative").readText()

    private fun projectRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        return generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .first { File(it, "android/app/src/main").isDirectory }
    }
}
