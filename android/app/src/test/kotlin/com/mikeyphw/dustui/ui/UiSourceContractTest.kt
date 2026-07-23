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
        assertTrue(app.contains("bottom: Int = 128"))
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

    @Test fun `explorer uses one full screen scroll surface`() {
        val app = sourceFile("DustUiApp.kt")
        assertTrue(app.contains("contentPadding = dustScreenPadding(horizontal = 0, bottom = if (selectionMode) 208 else 128)"))
        assertTrue(app.contains("contentPadding = PaddingValues(end = 56.dp)"))
        assertFalse(app.contains("Column(modifier) {\n        SessionCard(session"))
        assertFalse(app.contains("LazyColumn(Modifier.fillMaxSize(), contentPadding = dustScreenPadding(horizontal = 12)"))
    }

    @Test fun `cleanup execution remains bound to the reviewed scan and items`() {
        val viewModel = sourceFile("MainViewModel.kt")
        val repository = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        val cleanup = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/CleanupModels.kt")
        val app = sourceFile("DustUiApp.kt")
        assertTrue(viewModel.contains("resetScanScopedState(session)"))
        assertTrue(viewModel.contains("review.matches(session.id, session.rootPath)"))
        assertTrue(viewModel.contains("repository.executeCleanup(review)"))
        assertTrue(repository.contains("items = review.accepted"))
        assertTrue(cleanup.contains("val scanId: String"))
        assertTrue(cleanup.contains("val rootPath: String"))
        assertTrue(app.contains("Items to quarantine"))
        assertTrue(app.contains("items(review.accepted"))
    }

    @Test fun `android navigation compact charts and package selection follow the audit contract`() {
        val app = sourceFile("DustUiApp.kt")
        val charts = sourceFile("StorageCharts.kt")
        val treemap = sourceFile("Treemap.kt")
        val design = sourceFile("DustDesign.kt")
        val activity = androidSource("src/main/kotlin/com/mikeyphw/dustui/MainActivity.kt")
        assertTrue(app.contains("BackHandler("))
        assertTrue(app.contains("destination == DustDestination.SEARCH && currentSession != null"))
        assertTrue(app.contains("destination == DustDestination.PACKAGES && currentSession != null"))
        assertTrue(app.contains("maxWidth >= 840.dp && maxHeight >= 520.dp"))
        assertTrue(app.contains("showLargestChart"))
        assertTrue(app.contains("Showing \${matchingPackages.size} of \${packageProfiles.size} packages"))
        assertFalse(app.contains("packageProfiles.take(24)"))
        assertTrue(charts.contains("val compact = maxWidth < 480.dp"))
        assertTrue(charts.contains("heightIn(min = 48.dp)"))
        assertTrue(charts.contains("No measured storage data"))
        assertTrue(treemap.contains("Every visual block has a matching accessible row"))
        assertTrue(treemap.contains("clickable(role = Role.Button)"))
        assertTrue(design.contains("val columns = if (maxWidth < 420.dp) 1 else 2"))
        assertTrue(activity.indexOf("setContent") < activity.indexOf("notificationPermission.launch"))
    }

    private fun sourceFile(name: String): String = androidSource("src/main/kotlin/com/mikeyphw/dustui/ui/$name")

    private fun androidSource(relative: String): String {
        val direct = File(relative)
        if (direct.isFile) return direct.readText()
        return File("android/app/$relative").readText()
    }
}
