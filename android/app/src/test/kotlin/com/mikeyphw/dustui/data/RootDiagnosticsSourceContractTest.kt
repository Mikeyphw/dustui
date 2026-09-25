package com.mikeyphw.dustui.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RootDiagnosticsSourceContractTest {
    @Test fun `root service preflights identity path and selinux context`() {
        val aidl = source("src/main/aidl/com/mikeyphw/dustui/root/IDustRootService.aidl")
        val service = source("src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
        assertTrue(aidl.contains("String preflightScan(String requestJson)"))
        assertTrue(service.contains("val uid = Process.myUid()"))
        assertTrue(service.contains("/proc/self/attr/current"))
        assertTrue(service.contains("/proc/self/ns/mnt"))
        assertTrue(service.contains("errnoCode(\"ROOT_OPEN\""))
        assertTrue(service.contains("PERMISSION_DENIED"))
        assertTrue(service.contains("path.toFile().canonicalPath"))
        assertFalse(service.contains("Os.realpath"))
        assertFalse(service.contains("OsConstants.O_DIRECTORY"))
        assertFalse(service.contains("OsConstants.O_CLOEXEC"))
        assertTrue(service.contains("Os.open(path.toString(), OsConstants.O_RDONLY, 0)"))
        assertFalse(service.contains(".put(\"ok\", true)\n            .put(\"uid\""))
    }

    @Test fun `go root scanner treats root open failure as fatal and reports result limits`() {
        val scanner = File(sourceRoot(), "../go/internal/rootworker/scan.go").canonicalFile.readText()
        assertTrue(scanner.contains("ROOT_OPEN_FAILED"))
        assertTrue(scanner.contains("root-preflight"))
        assertTrue(scanner.contains("emittedFiles"))
        assertTrue(scanner.contains("emittedDirectories"))
        assertTrue(scanner.contains("depthLimitedDirectories"))
        assertTrue(scanner.contains("skippedFilesystemEntries"))
        assertTrue(scanner.contains("s.fatal"))
    }

    @Test fun `scan and module screens expose actionable diagnostics instead of zero values`() {
        val foreground = source("src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt")
        val app = source("src/main/kotlin/com/mikeyphw/dustui/ui/DustUiApp.kt")
        val repository = source("src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        assertTrue(foreground.contains("ROOT_SERVICE_NOT_PRIVILEGED"))
        assertTrue(foreground.contains("ZERO_FILES_EMITTED"))
        assertTrue(foreground.contains("MISSING_TERMINAL_EVENT"))
        assertTrue(app.contains("Failure diagnostic"))
        assertTrue(app.contains("Exact scanner and root-service diagnostics retained for this scan."))
        assertTrue(app.contains("Scan /data/adb/modules"))
        assertTrue(app.contains("Current scan does not include modules"))
        assertTrue(repository.contains("ModuleDiagnosticStatus.WRONG_SCAN_ROOT"))
        assertTrue(repository.contains("ModuleDiagnosticStatus.ROOT_ACCESS_FAILED"))
    }

    @Test fun `diagnostics integration uses available coroutine and compose APIs`() {
        val repository = source("src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        val appStorage = source("src/main/kotlin/com/mikeyphw/dustui/ui/AppStorageInspectorScreen.kt")
        val cleaning = source("src/main/kotlin/com/mikeyphw/dustui/ui/CleaningIntelligenceScreens.kt")
        val service = source("src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt")
        val tasker = source("src/main/kotlin/com/mikeyphw/dustui/tasker/DustTaskerReceiver.kt")
        assertTrue(repository.contains("import kotlinx.coroutines.Dispatchers"))
        assertTrue(repository.contains("import androidx.core.net.toUri"))
        assertTrue(repository.contains("Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, \"package:\$packageName\".toUri())"))
        assertTrue(repository.contains("Intent(Intent.ACTION_UNINSTALL_PACKAGE, \"package:\$packageName\".toUri())"))
        assertFalse(repository.contains("Uri.parse(\"package:\$packageName\")"))
        assertTrue(service.contains("preflightFailure(\"INVALID_REQUEST\""))
        assertTrue(service.contains(".toString()"))
        assertFalse(appStorage.contains("import androidx.compose.foundation.layout.weight"))
        assertFalse(cleaning.contains("import androidx.compose.foundation.layout.weight"))
        assertTrue(cleaning.contains("val growthFilter by vm.storageGrowthFilter.collectAsStateWithLifecycle()"))
        assertFalse(cleaning.contains("vm.storageGrowthFilter.value"))
        assertTrue(tasker.contains("import androidx.core.os.bundleOf"))
        assertTrue(tasker.contains("GoAutomationAdapter(context)"))
        assertTrue(tasker.contains("ACTION_STATUS"))
        assertTrue(tasker.contains("RESULT_TERMINAL"))
        assertTrue(tasker.contains("RESULT_OPERATION_ID"))
        assertFalse(tasker.contains("RESULT_STATUS to \"accepted\""))
        assertFalse(tasker.contains("RESULT_STATUS to \"cancel_requested\""))
        assertFalse(tasker.contains("Bundle().apply"))
    }

    private fun sourceRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")) { "user.dir is unavailable" }).absoluteFile) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }

    private fun source(relative: String): String = File(sourceRoot(), "app/$relative").readText()
}
