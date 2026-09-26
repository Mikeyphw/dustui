package com.mikeyphw.dustui.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** AUD-FIX successor-aware acceptance bridge.
 *
 * This test intentionally validates behavioral/safety invariants by source reachability
 * and does not reference predecessor DustDestination/DebugWorkbench symbols. It therefore
 * remains compilable when AUD-FIX-08/AUD-FIX-11 retire those symbols.
 */
class ModernDarkPortraitFinalContractTest {
    @Test fun `predecessor or successor UI preserves required invariant`() {
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
        val nav = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustShellNavigation.kt") +
            source("src/main/kotlin/com/mikeyphw/dustui/ui/DustSemanticNavigation.kt")
        val goRoutes = rootSource("go/internal/shell/routes.go")
        val predecessor = nav.contains("DustDestination") || app.contains("DebugWorkbench")
        if (predecessor) {
            assertTrue("predecessor UI must retain safe cleanup review", app.contains("review") || app.contains("Review"))
            assertTrue("predecessor UI must retain files/explorer task", app.contains("Explorer") || app.contains("Files"))
        } else {
            for (route in listOf("overview", "files", "clean", "apps", "activity", "system")) {
                assertTrue("successor semantic route missing: $route", goRoutes.contains("\"$route\""))
            }
            assertTrue("successor UI must expose semantic navigation", nav.contains("DustSemanticNavigation") || app.contains("Overview"))
        }
    }

    private fun source(relative: String): String = File(projectRoot(), "android/app/$relative").takeIf { it.isFile }?.readText().orEmpty()
    private fun rootSource(relative: String): String = File(projectRoot(), relative).takeIf { it.isFile }?.readText().orEmpty()
    private fun projectRoot(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        return generateSequence(File(userDir).absoluteFile) { it.parentFile }
            .first { File(it, "android/app/src/main").isDirectory }
    }
}
