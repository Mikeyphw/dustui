package com.mikeyphw.dustui.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.mikeyphw.dustui.scan.ScanRequest
import com.mikeyphw.dustui.data.CleanupBlockedItem
import com.mikeyphw.dustui.data.CleanupExecutionResult
import com.mikeyphw.dustui.data.CleanupPlan
import com.mikeyphw.dustui.data.CleanupPlanItem
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.QuarantineEntryEntity
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RootServiceClient(private val context: Context) {
    private val bindMutex = Mutex()
    private val callMutex = Mutex()
    @Volatile private var remote: IDustRootService? = null
    @Volatile private var activeConnection: ServiceConnection? = null

    suspend fun rootAvailable(): Boolean = withContext(Dispatchers.IO) {
        withTimeoutOrNull(ROOT_TIMEOUT_MS) { runCatching { Shell.getShell().isRoot }.getOrDefault(false) } ?: false
    }

    suspend fun startScan(request: ScanRequest, output: ParcelFileDescriptor): RootScanState =
        call(START_TIMEOUT_MS) { it.startScan(request.toJson(), output) }

    suspend fun scanState(scanId: String): RootScanState = call { it.scanState(scanId) }
    suspend fun cancelScan(scanId: String): RootScanState = call { it.cancelScan(scanId) }

    suspend fun reviewCleanup(plan: CleanupPlan): CleanupReview = cleanupCall { it.reviewCleanup(plan.normalized().toJson()) }.let(::parseReview)
    suspend fun executeCleanup(plan: CleanupPlan): CleanupExecutionResult = cleanupCall(60_000L) { it.executeCleanup(plan.normalized().toJson()) }.let(::parseExecution)
    suspend fun restoreCleanup(operationId: String, rootPath: String): CleanupExecutionResult = cleanupCall(60_000L) { it.restoreCleanup(operationId, rootPath) }.let(::parseExecution)

    private suspend fun cleanupCall(timeoutMs: Long = CALL_TIMEOUT_MS, block: (IDustRootService) -> String): String = withTimeout(timeoutMs) {
        callMutex.withLock { withContext(Dispatchers.IO) { block(service()) } }
    }

    private fun parseReview(raw: String): CleanupReview {
        val json = JSONObject(raw)
        val accepted = json.optJSONArray("accepted")
        val blocked = json.optJSONArray("blocked")
        return CleanupReview(
            ok = json.optBoolean("ok"),
            operationId = json.optString("operationId"),
            accepted = buildList { if (accepted != null) for (index in 0 until accepted.length()) accepted.getJSONObject(index).let { add(CleanupPlanItem(it.getString("path"), it.optLong("sizeBytes"))) } },
            blocked = buildList { if (blocked != null) for (index in 0 until blocked.length()) blocked.getJSONObject(index).let { add(CleanupBlockedItem(it.getString("path"), it.getString("reason"))) } },
            plannedBytes = json.optLong("plannedBytes"),
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    private fun parseExecution(raw: String): CleanupExecutionResult {
        val json = JSONObject(raw)
        val entries = json.optJSONArray("entries")
        return CleanupExecutionResult(
            ok = json.optBoolean("ok"),
            operationId = json.optString("operationId"),
            status = json.optString("status"),
            reclaimedBytes = json.optLong("reclaimedBytes"),
            entries = buildList { if (entries != null) for (index in 0 until entries.length()) entries.getJSONObject(index).let { add(QuarantineEntryEntity(it.getString("id"), it.getString("operationId"), it.getString("originalPath"), it.getString("quarantinePath"), it.optLong("sizeBytes"))) } },
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    private suspend fun call(
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: (IDustRootService) -> String,
    ): RootScanState = try {
        withTimeout(timeoutMs) {
            callMutex.withLock {
                withContext(Dispatchers.IO) {
                    val service = service()
                    runCatching { RootScanState.fromJson(block(service)) }.getOrElse { error ->
                        remote = null
                        throw error
                    }
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        remote = null
        RootScanState(false, message = "Root service call timed out")
    }

    private suspend fun service(): IDustRootService = remote ?: bindMutex.withLock {
        remote ?: bindService().also { remote = it }
    }

    private suspend fun bindService(): IDustRootService = withTimeout(BIND_TIMEOUT_MS) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val intent = Intent(context, DustRootService::class.java)
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        val service = IDustRootService.Stub.asInterface(binder)
                        remote = service
                        activeConnection = this
                        if (continuation.isActive) continuation.resume(service)
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        if (activeConnection === this) activeConnection = null
                        remote = null
                    }
                    override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                    override fun onNullBinding(name: ComponentName) {
                        onServiceDisconnected(name)
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("Root service returned no binder"),
                        )
                    }
                }
                continuation.invokeOnCancellation { runCatching { RootService.unbind(connection) } }
                runCatching { RootService.bind(intent, connection) }
                    .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
            }
        }
    }

    data class RootScanState(
        val ok: Boolean,
        val scanId: String = "",
        val status: String = "",
        val exitCode: Int? = null,
        val error: String? = null,
        val message: String? = null,
    ) {
        val terminal: Boolean get() = status in setOf("completed", "cancelled", "failed")

        companion object {
            fun fromJson(raw: String): RootScanState {
                val json = JSONObject(raw)
                return RootScanState(
                    ok = json.optBoolean("ok", false),
                    scanId = json.optString("scanId"),
                    status = json.optString("status"),
                    exitCode = if (json.has("exitCode") && !json.isNull("exitCode")) json.getInt("exitCode") else null,
                    error = json.optString("error").takeIf(String::isNotBlank),
                    message = json.optString("message").takeIf(String::isNotBlank),
                )
            }
        }
    }

    private companion object {
        const val ROOT_TIMEOUT_MS = 8_000L
        const val BIND_TIMEOUT_MS = 20_000L
        const val START_TIMEOUT_MS = 30_000L
        const val CALL_TIMEOUT_MS = 10_000L
    }
}
