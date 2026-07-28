package com.mikeyphw.dustui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModernDarkPortraitFinalContractTest {
    @Test fun `phase 8 seals modern compact navigation contracts`() {
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")

        assertEquals(
            listOf(
                DustDestination.INSIGHTS,
                DustDestination.EXPLORER,
                DustDestination.RECOMMENDATIONS,
                DustDestination.MORE,
            ),
            DustNavigationModel.compactDestinations,
        )
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.compactSelection(DustDestination.SCAN))
        assertEquals(DustDestination.EXPLORER, DustNavigationModel.compactSelection(DustDestination.SEARCH))
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.compactNavigationTarget(DustDestination.INSIGHTS, hasScanData = false))
        assertFalse(DustNavigationModel.compactDestinations.contains(DustDestination.SCAN))
        assertFalse(DustNavigationModel.compactDestinations.contains(DustDestination.SEARCH))
        assertTrue(app.contains("ScanLauncherSheet("))
        assertTrue(app.contains("HomeDashboardScreen("))
        assertTrue(app.contains("ToolsHubScreen("))
    }

    @Test fun `phase 8 seals diagnostics and cleanup safety wording`() {
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
        val cleanup = source("src/main/kotlin/com/mikeyphw/dustui/ui/CleanupRecommendationsScreen.kt")
        val uiContract = File(projectRoot(), "UI_UX_CONTRACT.md").readText()
        val modernDoc = File(projectRoot(), "docs/ANDROID_MODERN_DARK_PORTRAIT_UI.md").readText()
        val sealDoc = File(projectRoot(), "docs/ANDROID_MODERN_DARK_PORTRAIT_FINAL_SEAL.md").readText()

        assertTrue(app.contains("Current scan does not include modules"))
        assertTrue(app.contains("Scan /data/adb/modules to fill module values"))
        assertTrue(app.contains("Protected mounted targets remain evidence, not deletion targets."))
        assertTrue(cleanup.contains("Permanent deletion stays review-first"))
        assertTrue(cleanup.contains("Stale cleanup locked"))
        assertTrue(uiContract.contains("Modern dark portrait final qualification contract"))
        assertTrue(modernDoc.contains("Phase 8 — Final contract seal"))
        assertTrue(sealDoc.contains("Current scan does not include modules"))
        assertFalse(modernDoc.contains("Current scan excludes modules"))
        assertFalse(uiContract.contains("five core destinations"))
        assertFalse(uiContract.contains("primary workbenches return to Scan"))
    }

    @Test fun `phase 8 keeps final validation audit runnable`() {
        val script = File(projectRoot(), "android/scripts/validate-modern-dark-portrait-ui.py")
        val validation = File(projectRoot(), "VALIDATION.md").readText()
        val checklist = File(projectRoot(), "RELEASE_CHECKLIST.md").readText()

        assertTrue(script.isFile)
        assertTrue(script.readText().contains("Modern dark portrait final audit passed."))
        assertTrue(validation.contains("Android modern dark portrait final validation"))
        assertTrue(validation.contains(":app:assembleDebug"))
        assertTrue(validation.contains(":app:testDebugUnitTest"))
        assertTrue(validation.contains(":app:lintDebug"))
        assertTrue(checklist.contains("Modern dark portrait final seal validation"))
    }

    private fun source(relative: String): String = File(projectRoot(), "android/app/$relative").readText()

    private fun projectRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "android/app/src/main").isDirectory }
}
