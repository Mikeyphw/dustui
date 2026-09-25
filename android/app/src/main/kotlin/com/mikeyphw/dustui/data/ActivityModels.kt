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
}

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
