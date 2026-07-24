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
        assertTrue(repository.contains("rootClient.executeCleanup(review)"))
        assertTrue(cleanup.contains("val scanId: String"))
        assertTrue(cleanup.contains("val rootPath: String"))
        assertTrue(app.contains("Exact paths to delete"))
        assertTrue(app.contains("items(review.accepted"))
        assertTrue(app.contains("I understand this deletion is irreversible."))
        assertTrue(app.contains("Delete permanently"))
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


    @Test fun `surfaced file paths are actionable across workbenches`() {
        val app = sourceFile("DustUiApp.kt")
        val viewModel = sourceFile("MainViewModel.kt")
        assertTrue(app.contains("private fun ActionablePathCard"))
        assertTrue(app.contains("Delete permanently…"))
        assertTrue(app.contains("selectedPaths: Set<String>"))
        assertTrue(app.contains("advanced.largeOldFiles.take(8).forEach"))
        assertTrue(app.contains("items(results.take(80)"))
        assertTrue(app.contains("items(profile.largestPaths.take(8)"))
        assertTrue(app.contains("items(module.largestPaths.take(3)"))
        assertTrue(app.contains("items(conflict.sourcePaths"))
        assertTrue(app.contains("Actions apply to module source files, never the protected system target."))
        assertTrue(viewModel.contains("fun reviewPaths(paths: Collection<String>)"))
        assertTrue(viewModel.contains("fun revealPath(path: String)"))
        assertTrue(viewModel.contains("fun showPathDetails(path: String)"))
    }

    @Test fun `storage doughnut is an actionable accessible file surface`() {
        val app = sourceFile("DustUiApp.kt")
        val charts = sourceFile("StorageCharts.kt")
        val viewModel = sourceFile("MainViewModel.kt")
        val analytics = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/VisualAnalytics.kt")
        assertTrue(charts.contains("detectTapGestures"))
        assertTrue(charts.contains("Tap a doughnut slice to open its files"))
        assertTrue(charts.contains("selectedSlice.count"))
        assertTrue(charts.contains("contentDescription = \"\${slice.label}"))
        assertTrue(charts.contains("\${slice.count} files"))
        assertTrue(app.contains("private fun StorageCompositionActions"))
        assertTrue(app.contains("private fun CategoryFilesSheet"))
        assertTrue(app.contains("Other includes \${slice.sourceLabels.size} categories"))
        assertTrue(app.contains("vm.openCategoryFiles(slice.label, slice.sourceLabels)"))
        assertTrue(app.contains("vm.selectCategoryPaths(slice.sourceLabels)"))
        assertTrue(app.contains("vm.reviewCategoryPaths(slice.sourceLabels)"))
        assertTrue(app.contains("These results are stale after deletion"))
        assertTrue(viewModel.contains("fun openCategoryFiles"))
        assertTrue(viewModel.contains("fun selectCategoryPaths"))
        assertTrue(viewModel.contains("fun reviewCategoryPaths"))
        assertTrue(viewModel.contains("Run a new scan before planning another deletion"))
        assertTrue(analytics.contains("val sourceLabels: Set<String>"))
    }


    @Test fun `large file workbench is filtered grouped paged and deletion bound`() {
        val app = sourceFile("DustUiApp.kt")
        val viewModel = sourceFile("MainViewModel.kt")
        val design = sourceFile("DustDesign.kt")
        val model = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/LargeFileWorkbench.kt")
        val dao = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt")
        val repository = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        assertTrue(design.contains("LARGE_FILES"))
        assertTrue(app.contains("private fun LargeFileWorkbenchScreen"))
        assertTrue(app.contains("Find by name, path, or package"))
        assertTrue(app.contains("Select page"))
        assertTrue(app.contains("Plan page"))
        assertTrue(app.contains("largeFileMetadata(item)"))
        assertTrue(viewModel.contains("LargeFileWorkbenchEngine.evaluate"))
        assertTrue(viewModel.contains("fun selectLargeFilePage"))
        assertTrue(viewModel.contains("fun reviewLargeFilePage"))
        assertTrue(viewModel.contains("reviewPaths(paths, hashes)"))
        assertTrue(model.contains("enum class LargeFilePreset"))
        assertTrue(model.contains("enum class LargeFileGroup"))
        assertTrue(model.contains("val pageSize: Int = 40"))
        assertTrue(model.contains("filterNot { it.isDirectory }"))
        assertTrue(model.contains("EntryIntelligenceResolver.packageFromPath"))
        assertTrue(dao.contains("filesAtLeastOnce"))
        assertTrue(dao.contains("installerFilesOnce"))
        assertTrue(dao.contains("archiveFilesOnce"))
        assertTrue(repository.contains("suspend fun largeFileEntries"))
        assertFalse(viewModel.contains("combine(allEntries, _largeFileFilter)"))
    }

    @Test fun `duplicate workbench verifies exact content handles hard links and plans safely`() {
        val app = sourceFile("DustUiApp.kt")
        val viewModel = sourceFile("MainViewModel.kt")
        val design = sourceFile("DustDesign.kt")
        val model = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/DuplicateWorkbench.kt")
        val store = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/DuplicateAnalysisStore.kt")
        val dao = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/ScanDao.kt")
        val repository = androidSource("src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        val service = androidSource("src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
        assertTrue(design.contains("DUPLICATES"))
        assertTrue(app.contains("private fun DuplicateWorkbenchScreen"))
        assertTrue(app.contains("complete SHA-256 hashes prove exact content matches"))
        assertTrue(app.contains("Keep this physical copy"))
        assertTrue(app.contains("Hard-linked copies are shown but never auto-selected"))
        assertTrue(app.contains("Select recommended"))
        assertTrue(app.contains("Plan deletion"))
        assertTrue(viewModel.contains("fun startDuplicateAnalysis"))
        assertTrue(viewModel.contains("fun cancelDuplicateAnalysis"))
        assertTrue(viewModel.contains("fun chooseDuplicateKeep"))
        assertTrue(viewModel.contains("reviewPaths(paths)"))
        assertTrue(model.contains("enum class DuplicateKeepStrategy"))
        assertTrue(model.contains("DuplicateKeepStrategy.MANUAL"))
        assertTrue(model.contains("protectedCopies.isNotEmpty()"))
        assertTrue(model.contains("eligibleCopies.filterNot"))
        assertTrue(store.contains("duplicate-analysis"))
        assertTrue(store.contains("output.fd.sync()"))
        assertTrue(dao.contains("duplicateSizeBuckets"))
        assertTrue(dao.contains("GROUP BY sizeBytes"))
        assertTrue(repository.contains("Resume continues with the next size bucket"))
        assertTrue(repository.contains("duplicateStore.save(snapshot)"))
        assertTrue(service.contains("hashFileEdges"))
        assertTrue(service.contains("hashWholeFile"))
        assertTrue(service.contains("st_nlink"))
        assertTrue(service.contains("physicalCopies.size < 2"))
        assertTrue(service.contains("File changed while hashing"))
        assertTrue(service.contains("File content changed since duplicate verification"))
        assertTrue(service.contains("contentSha256"))
        assertTrue(viewModel.contains("_selectedContentHashes"))
        assertTrue(repository.contains("expectedSha256ByPath"))
        assertFalse(service.contains("groupBy { it.sizeBytes to extensionOf"))
    }

    @Test fun `permanent deletion replaces quarantine runtime behavior`() {
        val app = sourceFile("DustUiApp.kt")
        val rootService = androidSource("src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
        val aidl = androidSource("src/main/aidl/com/mikeyphw/dustui/root/IDustRootService.aidl")
        val charts = sourceFile("StorageCharts.kt")
        assertFalse(app.contains("Quarantine"))
        assertFalse(app.contains("Restore"))
        assertFalse(aidl.contains("restoreCleanup"))
        assertTrue(rootService.contains("Path contents changed after review"))
        assertTrue(rootService.contains("treeDigest"))
        assertTrue(rootService.contains("Files.walkFileTree"))
        assertFalse(charts.contains("onSelect?.invoke"))
        assertFalse(app.contains("moduleReport?.modules"))
        assertFalse(app.contains("moduleReport?.overlayConflicts"))
        assertTrue(app.contains("val report = moduleReport"))
    }

    private fun sourceFile(name: String): String = androidSource("src/main/kotlin/com/mikeyphw/dustui/ui/$name")

    private fun androidSource(relative: String): String {
        val direct = File(relative)
        if (direct.isFile) return direct.readText()
        return File("android/app/$relative").readText()
    }
}
