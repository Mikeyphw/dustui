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
        assertTrue(source.contains("Whole scan search"))
        assertTrue(source.contains("RootSearchScreen"))
    }

    private fun sourceFile(name: String): String {
        val direct = File("src/main/kotlin/com/mikeyphw/dustui/ui/$name")
        if (direct.isFile) return direct.readText()
        return File("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/$name").readText()
    }
}
