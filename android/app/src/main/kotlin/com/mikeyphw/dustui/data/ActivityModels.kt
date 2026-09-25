package com.mikeyphw.dustui.data

import org.json.JSONArray
import org.json.JSONObject

data class DurableOperation(
    val operationId: String,
    val requestId: String,
    val sources: List<String>,
    val kind: String,
    val generation: Long,
    val state: String,
    val terminalTruth: String,
    val lastSequence: Long,
    val recoveryRequired: Boolean,
    val evidenceCorrelation: String,
    val acceptedNotSuccess: Boolean,
) {
    val terminal: Boolean get() = terminalTruth != "pending"
    val successful: Boolean get() = terminalTruth == "success"
}

data class ActivityRun(
    val operationId: String,
    val requestId: String,
    val kind: String,
    val sources: List<String>,
    val state: String,
    val active: Boolean,
    val history: Boolean,
    val recoveryRequired: Boolean,
    val terminalTruth: String,
    val progressJson: String,
    val resultJson: String,
    val verificationJson: String,
    val retryGate: String,
    val cancelGate: String,
    val reconcileGate: String,
    val evidenceCorrelation: String,
    val updatedAt: Long,
) {
    val terminal: Boolean get() = history && !active && terminalTruth != "pending"
    val canRetry: Boolean get() = retryGate == "allowed"
    val canCancel: Boolean get() = cancelGate == "allowed"
    val canReconcile: Boolean get() = reconcileGate == "allowed"
    val needsAttention: Boolean get() = recoveryRequired || terminalTruth in setOf("failure", "interrupted", "recovery_required")
    val statusLabel: String get() = when {
        recoveryRequired -> "Recovery required"
        active && state.equals("cancelling", true) -> "Cancelling"
        active -> "In progress"
        terminalTruth == "success" -> "Completed"
        terminalTruth == "cancelled" -> "Cancelled"
        terminalTruth == "failure" -> "Failed"
        terminalTruth == "interrupted" -> "Interrupted"
        else -> state.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

enum class ActivityStateFilter(val label: String) {
    All("All"),
    Active("Active"),
    NeedsAttention("Needs attention"),
    Failed("Failed"),
    Completed("Completed"),
}

data class ActivityFilter(
    val state: ActivityStateFilter = ActivityStateFilter.All,
    val kind: String? = null,
) {
    fun accepts(run: ActivityRun): Boolean {
        val stateMatches = when (state) {
            ActivityStateFilter.All -> true
            ActivityStateFilter.Active -> run.active
            ActivityStateFilter.NeedsAttention -> run.needsAttention
            ActivityStateFilter.Failed -> run.terminalTruth in setOf("failure", "interrupted", "recovery_required")
            ActivityStateFilter.Completed -> run.terminal && !run.needsAttention
        }
        return stateMatches && (kind == null || run.kind == kind)
    }
}

data class ActivityEvidence(
    val operationId: String,
    val evidenceCorrelation: String,
    val run: ActivityRun,
    val cleanupVerification: CleanupVerificationReport?,
    val verificationGeneration: Long?,
    val problems: List<DiagnosticProblem>,
)

data class NotificationCapability(
    val nonce: String,
    val operationId: String,
    val requestId: String,
    val action: String,
    val expiresAtMs: Long,
    val operationBinding: String,
)

data class NotificationClaim(
    val accepted: Boolean,
    val reason: String,
    val operationId: String?,
    val requestId: String?,
)

internal fun JSONObject.toDurableOperation() = DurableOperation(
    operationId = getString("operationId"),
    requestId = getString("requestId"),
    sources = optJSONArray("sources").strings(),
    kind = getString("kind"),
    generation = getLong("generation"),
    state = getString("state"),
    terminalTruth = getString("terminalTruth"),
    lastSequence = getLong("lastSequence"),
    recoveryRequired = optBoolean("recoveryRequired"),
    evidenceCorrelation = optString("evidenceCorrelation"),
    acceptedNotSuccess = optBoolean("acceptedNotSuccess", true),
)

internal fun JSONObject.toActivityRun() = ActivityRun(
    operationId = getString("operationId"),
    requestId = getString("requestId"),
    kind = getString("kind"),
    sources = optJSONArray("sources").strings(),
    state = getString("state"),
    active = getBoolean("active"),
    history = getBoolean("history"),
    recoveryRequired = getBoolean("recoveryRequired"),
    terminalTruth = getString("terminalTruth"),
    progressJson = optJSONObject("progress")?.toString() ?: "{}",
    resultJson = optJSONObject("result")?.toString() ?: "{}",
    verificationJson = optJSONObject("verification")?.toString() ?: "{}",
    retryGate = getString("retryGate"),
    cancelGate = getString("cancelGate"),
    reconcileGate = getString("reconcileGate"),
    evidenceCorrelation = getString("evidenceCorrelation"),
    updatedAt = getLong("updatedAtMs"),
)

private fun JSONArray?.strings(): List<String> = this?.let { array ->
    buildList { for (i in 0 until array.length()) add(array.getString(i)) }
}.orEmpty()
