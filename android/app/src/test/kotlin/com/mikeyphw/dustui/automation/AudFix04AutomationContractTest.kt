package com.mikeyphw.dustui.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudFix04AutomationContractTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").canonicalFile) { it.parentFile }
            .first { File(it, ".devtool.toml").isFile && File(it, "go/internal/state").isDirectory }
    }

    private fun read(path: String) = File(repoRoot, path).readText()

    @Test fun `workmanager and tasker report durable terminal truth`() {
        val worker = read("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScheduledScanWorker.kt")
        val tasker = read("android/app/src/main/kotlin/com/mikeyphw/dustui/tasker/DustTaskerReceiver.kt")
        assertTrue(worker.contains("runScanToTerminal"))
        assertTrue(worker.contains("terminal.terminalTruth"))
        assertTrue(worker.contains("KEY_OPERATION_ID"))
        assertFalse(worker.contains("STATUS_LAUNCHED_NOT_TERMINAL"))
        assertTrue(tasker.contains("goAsync()"))
        assertTrue(tasker.contains("PendingResult"))
        assertTrue(tasker.contains("adapter.startScan"))
        assertTrue(tasker.contains("ACTION_STATUS"))
        assertTrue(tasker.contains("requestScanCancel"))
        assertTrue(tasker.contains("RESULT_TERMINAL"))
        assertTrue(tasker.contains("RESULT_PENDING"))
        assertFalse(tasker.contains("RESULT_STATUS to \"accepted\""))
        assertFalse(tasker.contains("RESULT_STATUS to \"cancel_requested\""))
    }

    @Test fun `notification cancellation is go capability bound and replay resistant`() {
        val service = read("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScanForegroundService.kt")
        val adapter = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoAutomationAdapter.kt")
        listOf("EXTRA_NOTIFICATION_NONCE", "EXTRA_NOTIFICATION_BINDING", "SOURCE_NOTIFICATION").forEach { assertTrue(service.contains(it)) }
        assertTrue(service.contains("claimNotificationCancel"))
        assertTrue(service.contains("requestCancel"))
        assertTrue(service.contains("AUTOMATION_OPERATION_BINDING_MISMATCH"))
        assertTrue(service.contains("observed.requestId != request.scanId"))
        assertTrue(adapter.contains("domain.automation.notification.create"))
        assertTrue(adapter.contains("domain.automation.notification.claim"))
    }

    @Test fun `activity actions are exposed from durable go authority`() {
        val adapter = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/GoAutomationAdapter.kt")
        for (operation in listOf(
            "domain.automation.activity.list",
            "domain.automation.activity.detail",
            "domain.automation.cancel",
            "domain.automation.retry",
            "domain.automation.reconcile",
        )) assertTrue("missing $operation", adapter.contains(operation))
        assertTrue(adapter.contains("DurableOperation"))
        assertTrue(adapter.contains("ActivityRun"))
        assertTrue(adapter.contains("automation_retry_kind_unsupported"))
        assertTrue(adapter.contains("ScanRequest.fromJson(previousSession.optionsJson)"))
        assertTrue(adapter.contains("operationId = admitted.operationId"))
        val repository = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        val viewModel = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
        assertTrue(repository.contains("suspend fun activityRuns"))
        assertTrue(repository.contains("suspend fun activityRun"))
        assertTrue(repository.contains("suspend fun cancelActivityRun"))
        assertTrue(repository.contains("cancelOperation(operationId, \"ui\")"))
        assertTrue(repository.contains("suspend fun reconcileActivityRun"))
        assertTrue(repository.contains("suspend fun retryActivityRun"))
        assertTrue(viewModel.contains("val activityRuns: StateFlow<List<ActivityRun>>"))
        assertTrue(viewModel.contains("val selectedActivityRun: StateFlow<ActivityRun?>"))
        assertTrue(viewModel.contains("fun refreshActivityRuns"))
    }

    @Test fun `activity truth includes scan analysis and cleanup without inventing cleanup automation gates`() {
        val go = read("go/internal/state/automation_authority.go")
        val cleanup = read("go/internal/state/android_domain.go")
        assertTrue(go.contains("\"analysis\""))
        assertTrue(go.contains("android_cleanup_operations c"))
        assertTrue(cleanup.contains("syncCleanupActivity"))
        assertTrue(cleanup.contains("blocked_cleanup_review_required"))
        assertTrue(cleanup.contains("blocked_cleanup_owned"))
        assertTrue(cleanup.contains("blocked_cleanup_recovery_surface"))
        val repository = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        assertTrue(repository.contains("admitAnalysis"))
        assertTrue(repository.contains("duplicate_analysis_started"))
        assertTrue(repository.contains("duplicate_analysis_complete"))
        assertTrue(repository.contains("duplicate_analysis_cancelled"))
        assertTrue(repository.contains("rootClient.verifyDuplicates(durable.operationId"))
        assertTrue(repository.contains("it.kind == \"scan\" || it.kind == \"analysis\""))
        val legacyContract = read("go/internal/automation/automation.go")
        assertFalse(legacyContract.contains("type Controller struct"))
        assertFalse(legacyContract.contains("NewController"))
        assertTrue(legacyContract.contains("single_durable_sqlite_authority"))
    }
}
