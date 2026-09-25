package com.mikeyphw.dustui.data

import android.content.Context
import com.mikeyphw.dustui.scan.ScanForegroundService
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/** Thin Android adapter over the durable Go automation/activity authority. */
class GoAutomationAdapter(
    context: Context,
    suppliedStore: GoDomainStore? = null,
) : java.io.Closeable {
    private val appContext = context.applicationContext
    private val ownsStore = suppliedStore == null
    private val store = suppliedStore ?: GoDomainStore(appContext)

    suspend fun admitOperation(
        requestId: String,
        source: String,
        kind: String,
        generation: Long,
        fingerprint: String,
    ): DurableOperation {
        val payload = store.authorityRequest(
            "domain.automation.admit",
            generation.coerceAtLeast(1L),
            JSONObject()
                .put("requestId", requestId)
                .put("source", source)
                .put("kind", kind)
                .put("generation", generation.coerceAtLeast(1L))
                .put("fingerprint", fingerprint),
        )
        return payload.getJSONObject("admission").toDurableOperation().also {
            check(it.acceptedNotSuccess) { "automation admission must not masquerade as success" }
        }
    }

    suspend fun admitScan(request: ScanRequest, source: String): DurableOperation = admitOperation(
        requestId = request.scanId,
        source = source,
        kind = "scan",
        generation = 1L,
        fingerprint = request.toJson().sha256(),
    )


    suspend fun observe(operationId: String): DurableOperation = store.authorityRequest(
        "domain.automation.observe",
        1,
        JSONObject().put("operationId", operationId),
    ).getJSONObject("operation").toDurableOperation()

    suspend fun operationForRequest(requestId: String): DurableOperation = store.authorityRequest(
        "domain.automation.by-request",
        1,
        JSONObject().put("requestId", requestId),
    ).getJSONObject("operation").toDurableOperation()

    suspend fun report(operationId: String, state: String, reason: String, message: String): DurableOperation = store.authorityRequest(
        "domain.automation.advance",
        1,
        JSONObject()
            .put("operationId", operationId)
            .put("state", state)
            .put("reason", reason)
            .put("message", message),
    ).getJSONObject("operation").toDurableOperation()

    suspend fun activity(limit: Int = 100): List<ActivityRun> = store.authorityRequest(
        "domain.automation.activity.list",
        1,
        JSONObject().put("limit", limit.coerceIn(1, 500)),
    ).getJSONArray("runs").let { array ->
        buildList { for (i in 0 until array.length()) add(array.getJSONObject(i).toActivityRun()) }
    }

    suspend fun activityDetail(operationId: String): ActivityRun = store.authorityRequest(
        "domain.automation.activity.detail",
        1,
        JSONObject().put("operationId", operationId),
    ).getJSONObject("run").toActivityRun()

    suspend fun activityEvidence(operationId: String): ActivityEvidence {
        val value = store.authorityRequest(
            "domain.automation.activity.evidence",
            1,
            JSONObject().put("operationId", operationId),
        ).getJSONObject("evidence")
        val cleanup = value.optJSONObject("cleanupVerification")?.let(GoProductAuthorityMapper::verificationReport)
        val problemsJson = value.optJSONArray("problems")
        val problems = buildList {
            if (problemsJson != null) for (index in 0 until problemsJson.length()) {
                problemsJson.optJSONObject(index)?.let { add(it.toDiagnosticProblem()) }
            }
        }
        return ActivityEvidence(
            operationId = value.getString("operationId"),
            evidenceCorrelation = value.optString("evidenceCorrelation"),
            run = value.getJSONObject("run").toActivityRun(),
            cleanupVerification = cleanup,
            verificationGeneration = value.optLong("verificationGeneration").takeIf { it > 0 },
            problems = problems,
        )
    }

    suspend fun requestCancel(operationId: String, source: String): Boolean = store.authorityRequest(
        "domain.automation.cancel",
        1,
        JSONObject().put("operationId", operationId).put("source", source),
    ).getJSONObject("action").getBoolean("allowed")

    suspend fun cancelOperation(operationId: String, source: String): Boolean {
        val current = observe(operationId)
        if (current.kind != "scan") return false
        val allowed = requestCancel(operationId, source)
        if (allowed) {
            ScanForegroundService.cancel(appContext, current.requestId, current.operationId, source)
        }
        return allowed
    }

    suspend fun reconcile(operationId: String): DurableOperation {
        store.authorityRequest("domain.automation.reconcile", 1, JSONObject().put("operationId", operationId))
        return observe(operationId)
    }

    suspend fun retry(operationId: String, source: String, newRequestId: String = UUID.randomUUID().toString()): DurableOperation {
        val current = observe(operationId)
        require(current.kind == "scan") { "automation_retry_kind_unsupported" }
        val previousSession = store.session(current.requestId) ?: error("automation_retry_scan_session_missing")
        val retryRequest = ScanRequest.fromJson(previousSession.optionsJson)
            .copy(scanId = newRequestId)
            .validated()
        val admitted = store.authorityRequest(
            "domain.automation.retry",
            current.generation + 1,
            JSONObject()
                .put("operationId", operationId)
                .put("newRequestId", newRequestId)
                .put("source", source)
                .put("newGeneration", current.generation + 1),
        ).getJSONObject("admission").toDurableOperation()
        ScanForegroundService.start(
            appContext,
            retryRequest,
            operationId = admitted.operationId,
            automationSource = source,
        )
        return admitted
    }

    suspend fun notificationCancelCapability(operationId: String): NotificationCapability {
        val obj = store.authorityRequest(
            "domain.automation.notification.create",
            1,
            JSONObject().put("operationId", operationId).put("action", "cancel").put("ttlMs", NOTIFICATION_TTL_MS),
        ).getJSONObject("capability")
        return NotificationCapability(
            nonce = obj.getString("nonce"),
            operationId = obj.getString("operationId"),
            requestId = obj.getString("requestId"),
            action = obj.getString("action"),
            expiresAtMs = obj.getLong("expiresAtMs"),
            operationBinding = obj.getString("operationBinding"),
        )
    }

    suspend fun claimNotificationCancel(capability: NotificationCapability): NotificationClaim {
        val obj = store.authorityRequest(
            "domain.automation.notification.claim",
            1,
            JSONObject()
                .put("nonce", capability.nonce)
                .put("operationId", capability.operationId)
                .put("action", capability.action)
                .put("operationBinding", capability.operationBinding)
                .put("nowMs", System.currentTimeMillis()),
        ).getJSONObject("claim")
        return NotificationClaim(
            accepted = obj.getBoolean("accepted"),
            reason = obj.getString("reason"),
            operationId = obj.optString("operationId").takeIf(String::isNotBlank),
            requestId = obj.optString("requestId").takeIf(String::isNotBlank),
        )
    }

    suspend fun startScan(request: ScanRequest, source: String, foregroundWorker: Boolean = false): DurableOperation {
        val admitted = admitScan(request, source)
        ScanForegroundService.start(
            appContext,
            request,
            launchedFromForegroundWorker = foregroundWorker,
            operationId = admitted.operationId,
            automationSource = source,
        )
        return admitted
    }

    suspend fun runScanToTerminal(request: ScanRequest, source: String, foregroundWorker: Boolean = false): DurableOperation {
        val admitted = startScan(request, source, foregroundWorker)
        return waitForTerminal(admitted.operationId)
    }

    suspend fun requestScanCancel(scanId: String, source: String): DurableOperation {
        val operation = operationForRequest(scanId)
        if (!cancelOperation(operation.operationId, source)) return observe(operation.operationId)
        return observe(operation.operationId)
    }

    suspend fun cancelScanToTerminal(scanId: String, source: String): DurableOperation {
        val requested = requestScanCancel(scanId, source)
        if (requested.terminal) return requested
        return waitForTerminal(requested.operationId, CANCEL_TIMEOUT_MS)
    }

    suspend fun waitForTerminal(operationId: String, timeoutMs: Long = TERMINAL_TIMEOUT_MS): DurableOperation {
        val deadline = System.currentTimeMillis() + timeoutMs
        var latest = observe(operationId)
        while (!latest.terminal && System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            latest = observe(operationId)
        }
        if (latest.terminal) return latest
        return reconcile(operationId)
    }

    override fun close() {
        if (ownsStore) store.close()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val PROTOCOL = "dustui.automation.activity.v2"
        private const val POLL_MS = 500L
        private const val TERMINAL_TIMEOUT_MS = 6L * 60L * 60L * 1000L
        private const val CANCEL_TIMEOUT_MS = 45_000L
        private const val NOTIFICATION_TTL_MS = 15L * 60L * 1000L
    }
}
