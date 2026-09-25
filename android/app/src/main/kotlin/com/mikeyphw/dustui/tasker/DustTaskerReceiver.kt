package com.mikeyphw.dustui.tasker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import com.mikeyphw.dustui.data.DurableOperation
import com.mikeyphw.dustui.data.GoAutomationAdapter
import com.mikeyphw.dustui.scan.ScanDepthPolicy
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Locale/Tasker-compatible thin adapter over DustUI's durable Go operation authority. */
class DustTaskerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_SETTING) return
        val plugin = intent.getBundleExtra(EXTRA_BUNDLE) ?: Bundle.EMPTY
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (plugin.getString(KEY_ACTION, ACTION_SCAN)) {
                    ACTION_SCAN -> runScan(context, plugin, pending)
                    ACTION_STATUS -> runStatus(context, plugin, pending)
                    ACTION_CANCEL -> runCancel(context, plugin, pending)
                    else -> pending.fail("unknown_action")
                }
            } catch (t: Throwable) {
                pending.fail("recovery_required", t.message)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun runScan(context: Context, plugin: Bundle, pending: PendingResult) {
        val request = runCatching {
            ScanRequest(
                rootPath = plugin.getString(KEY_PATH, DEFAULT_PATH).orEmpty().trim(),
                maxDepth = plugin.getInt(KEY_DEPTH, DEFAULT_DEPTH).coerceIn(ScanDepthPolicy.UNLIMITED_DEPTH, ScanDepthPolicy.MAX_ALLOWED_DEPTH),
                presetId = "tasker",
            ).validated()
        }.getOrElse {
            pending.fail("invalid_request", it.message)
            return
        }
        GoAutomationAdapter(context).use { adapter ->
            // A BroadcastReceiver PendingResult is not a long-running job handle. Start the
            // durable operation, return its non-terminal truth, and let ACTION_STATUS poll it.
            val operation = adapter.startScan(request, source = SOURCE_TASKER)
            pending.report(operation, request.rootPath)
        }
    }

    private suspend fun runStatus(context: Context, plugin: Bundle, pending: PendingResult) {
        val scanId = plugin.getString(KEY_SCAN_ID)?.takeIf(String::isNotBlank) ?: run {
            pending.fail("missing_scan_id")
            return
        }
        GoAutomationAdapter(context).use { adapter ->
            pending.report(adapter.operationForRequest(scanId))
        }
    }

    private suspend fun runCancel(context: Context, plugin: Bundle, pending: PendingResult) {
        val scanId = plugin.getString(KEY_SCAN_ID)?.takeIf(String::isNotBlank) ?: run {
            pending.fail("missing_scan_id")
            return
        }
        GoAutomationAdapter(context).use { adapter ->
            pending.report(adapter.requestScanCancel(scanId, source = SOURCE_TASKER))
        }
    }

    private fun PendingResult.report(operation: DurableOperation, path: String? = null) {
        resultCode = when {
            operation.terminalTruth == "success" -> Activity.RESULT_OK
            operation.terminal -> Activity.RESULT_CANCELED
            else -> RESULT_PENDING
        }
        resultExtras = bundleOf(
            RESULT_STATUS to if (operation.terminal) operation.terminalTruth else operation.state,
            RESULT_TERMINAL to operation.terminal,
            RESULT_SCAN_ID to operation.requestId,
            RESULT_OPERATION_ID to operation.operationId,
            RESULT_PATH to path,
        )
    }

    private fun PendingResult.fail(status: String, message: String? = null) {
        resultCode = Activity.RESULT_CANCELED
        resultExtras = bundleOf(RESULT_STATUS to status, RESULT_TERMINAL to true, RESULT_MESSAGE to message)
    }

    companion object {
        const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
        const val KEY_ACTION = "dustui_action"
        const val KEY_PATH = "dustui_path"
        const val KEY_DEPTH = "dustui_depth"
        const val KEY_SCAN_ID = "dustui_scan_id"
        const val ACTION_SCAN = "scan"
        const val ACTION_STATUS = "status"
        const val ACTION_CANCEL = "cancel"
        const val RESULT_STATUS = "dustui_result_status"
        const val RESULT_TERMINAL = "dustui_result_terminal"
        const val RESULT_SCAN_ID = "dustui_result_scan_id"
        const val RESULT_OPERATION_ID = "dustui_result_operation_id"
        const val RESULT_PATH = "dustui_result_path"
        const val RESULT_MESSAGE = "dustui_result_message"
        const val SOURCE_TASKER = "tasker"
        const val DEFAULT_PATH = "/storage/emulated/0"
        const val DEFAULT_DEPTH = ScanDepthPolicy.DEFAULT_MAX_DEPTH
        const val RESULT_PENDING = Activity.RESULT_FIRST_USER
    }
}
