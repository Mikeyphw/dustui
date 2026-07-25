package com.mikeyphw.dustui.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CleaningAccessibilityContractTest {
    private val androidRoot = locateAndroidRoot()

    @Test fun `charts and treemap expose equivalent accessible actions`() {
        val charts = source("StorageCharts.kt")
        val treemap = source("Treemap.kt")
        assertTrue(charts.contains("heightIn(min = 48.dp)"))
        assertTrue(charts.contains("role = Role.Button"))
        assertTrue(charts.contains("selected = selectedLabel == slice.label"))
        assertTrue(charts.contains("${'$'}{slice.count} files"))
        assertTrue(treemap.contains("heightIn(min = 48.dp)"))
        assertTrue(treemap.contains("clickable(role = Role.Button)"))
        assertTrue(treemap.contains("Open ${'$'}{entry.name}"))
    }

    @Test fun `compact and large font layouts remain adaptive`() {
        val app = source("DustUiApp.kt")
        val design = source("DustDesign.kt")
        val charts = source("StorageCharts.kt")
        assertTrue(app.contains("BoxWithConstraints"))
        assertTrue(design.contains("BoxWithConstraints"))
        assertTrue(design.contains("MetricGrid"))
        assertTrue(charts.contains("maxWidth < 480.dp"))
    }

    @Test fun `historical charts retain an accessible list path`() {
        val screens = source("CleaningIntelligenceScreens.kt")
        assertTrue(screens.contains("Accessible timeline points"))
        assertTrue(screens.contains("Storage timeline"))
        assertTrue(screens.contains("contentDescription = \"Storage timeline."))
    }

    @Test fun `every destructive surface remains review first`() {
        listOf(
            "DustUiApp.kt", "AppStorageInspectorScreen.kt", "CleanupRecommendationsScreen.kt", "CleaningIntelligenceScreens.kt",
        ).forEach { name -> assertFalse("$name must not execute deletion directly", source(name).contains("executeCleanup(")) }
    }

    private fun source(name: String): String = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/ui/$name").readText()
    private fun locateAndroidRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
            .first { File(it, "app/src/main/kotlin/com/mikeyphw/dustui/ui/StorageCharts.kt").isFile }
    }
}
