package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.DUST_DEBUG_EVENT_LIMIT
import com.mikeyphw.dustui.data.DebugEventStore
import com.mikeyphw.dustui.data.DebugExportBundleWriter
import com.mikeyphw.dustui.data.DebugExportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebugWorkbenchFinalContractTest {
    @Test fun `phase 14 seals the debug route as scan independent tools state`() {
        assertTrue(DustDestination.DEBUG in DustNavigationModel.wideDestinations)
        assertTrue(DustDestination.DEBUG in DustNavigationModel.moreDestinations)
        assertFalse(DustDestination.DEBUG in DustNavigationModel.compactDestinations)
        assertFalse(DustNavigationModel.needsScanData(DustDestination.DEBUG))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.DEBUG))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.DEBUG, wide = false))
    }

    @Test fun `phase 14 keeps timeline bounded and safe-mode path redaction locked`() {
        assertEquals(500, DUST_DEBUG_EVENT_LIMIT)
        val store = DebugEventStore(limit = 1)
        store.record(
            category = com.mikeyphw.dustui.data.DebugCategory.Root,
            severity = com.mikeyphw.dustui.data.DebugSeverity.Warning,
            name = "root_probe",
            message = "Root probe recorded",
            evidence = mapOf("rootPath" to "/data/user/0/private", "token" to "secret-token"),
        )
        assertEquals(1, store.events.value.size)
        assertEquals("/data/…", store.events.value.single().evidence["rootPath"])
        assertEquals("<redacted>", store.events.value.single().evidence["token"])
        assertEquals("/data/…", DebugExportBundleWriter.valueForMode("/data/user/0/private", DebugExportMode.Safe))
        assertEquals("/data/adb/modules/…", DebugExportBundleWriter.valueForMode("/data/adb/modules/example/module.prop", DebugExportMode.Safe))
    }

    @Test fun `phase 14 locks the full debug roadmap contracts`() {
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
        val vm = source("src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
        val model = source("src/main/kotlin/com/mikeyphw/dustui/data/DebugEvents.kt")
        val export = source("src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt")
        val docs = doc("ANDROID_DEBUG_WORKBENCH.md")
        val seal = doc("ANDROID_DEBUG_WORKBENCH_FINAL_SEAL.md")
        val script = repoSource("android/scripts/validate-debug-workbench-contract.py")

        assertTrue(model.contains("const val DUST_DEBUG_EVENT_LIMIT = 500"))
        assertTrue(model.contains("enum class DebugProbeTarget"))
        assertTrue(vm.contains("val debugEvents: StateFlow<List<DebugEvent>>"))
        assertTrue(vm.contains("fun runAllDebugProbes()"))
        assertTrue(vm.contains("fun exportDebugBundle(mode: DebugExportMode"))
        assertTrue(vm.contains("val debugOverlayVisible: StateFlow<Boolean>"))
        assertTrue(app.contains("DebugWorkbenchScreen("))
        assertTrue(app.contains("Live state"))
        assertTrue(app.contains("Trace timeline"))
        assertTrue(app.contains("Diagnostic probes"))
        assertTrue(app.contains("Export debug bundle"))
        assertTrue(app.contains("DebugOverlayHud("))
        assertTrue(app.contains("private fun DisabledReasonNotice"))
        assertTrue(export.contains("noFileContents"))
        assertTrue(export.contains("dustui-debug-report.json"))
        assertFalse(export.contains("FileInputStream("))
        assertFalse(export.contains("readBytes()"))
        assertTrue(docs.contains("Phase 14 · Final debug contract seal"))
        assertTrue(seal.contains("Non-negotiable safety contracts"))
        assertTrue(script.contains("Debug Workbench final contract audit passed."))
    }

    private fun source(relative: String): String = repoSource("android/app/$relative")

    private fun doc(name: String): String = repoSource("docs/$name")

    private fun repoSource(relative: String): String = File(projectRoot(), relative).readText()

    private fun projectRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        return generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .first { File(it, "android/app/src/main").isDirectory }
    }
}
