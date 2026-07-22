package com.mikeyphw.dustui.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiSourceContractTest {
    @Test fun `screen routing uses named destinations instead of boolean toggles`() {
        val source = sourceFile("DustUiApp.kt")
        assertTrue(source.contains("DustDestination"))
        assertFalse(source.contains("showLauncher"))
        assertFalse(source.contains("showInsights"))
    }

    @Test fun `detail rows avoid fixed label columns`() {
        val appSource = sourceFile("DustUiApp.kt")
        val designSource = sourceFile("DustDesign.kt")
        assertFalse(appSource.contains("Modifier.width(110.dp)"))
        assertTrue(designSource.contains("BoxWithConstraints"))
        assertTrue(designSource.contains("MetricRow"))
    }

    @Test fun `explorer distinguishes local and whole scan search`() {
        val source = sourceFile("DustUiApp.kt")
        assertTrue(source.contains("Current folder search"))
        assertTrue(source.contains("Whole scan"))
        assertTrue(source.contains("RootSearchScreen"))
    }

    @Test fun `compact screens keep final sections readable`() {
        val app = sourceFile("DustUiApp.kt")
        assertTrue(app.contains("bottom = 128.dp"))
        assertTrue(app.contains("Oldest large files from the latest scan."))
        assertTrue(app.contains("Scan root"))
        assertFalse(app.contains("label = { Text(\"Whole scan search\") }"))
    }

    @Test fun `remaining audit items are represented in source contract`() {
        val app = sourceFile("DustUiApp.kt")
        val design = sourceFile("DustDesign.kt")
        val theme = sourceFile("Theme.kt")
        assertTrue(app.contains("Text(destination.actionLabel)"))
        assertFalse(app.contains("Text(\"Menu\")"))
        assertTrue(app.contains("SegmentedChoice(\"List\", \"Treemap\""))
        assertTrue(app.contains("MetricGrid("))
        assertTrue(design.contains("actionLabel"))
        assertTrue(design.contains("fun MetricGrid"))
        assertTrue(design.contains("fun SegmentedChoice"))
        assertTrue(theme.contains("letterSpacing = 0.sp"))
    }

    private fun sourceFile(name: String): String {
        val direct = File("src/main/kotlin/com/mikeyphw/dustui/ui/$name")
        if (direct.isFile) return direct.readText()
        return File("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/$name").readText()
    }
}
