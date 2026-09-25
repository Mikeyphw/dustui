package com.mikeyphw.dustui.go

/** AUD-FIX-04 durable automation/activity contract consumed by thin Android adapters. */
object AutomationActivityContract {
    const val Protocol = "dustui.automation.activity.v2"
    const val Phase = "AUD-FIX-04"
    const val Authority = "go-sqlite-automation-activity"
    val ThinAdapters = listOf("ui", "workmanager", "tasker", "notification", "recovery")
    val Lifecycle = listOf("accepted", "running", "success", "failure", "cancelled", "recovery_required")
    val ForbiddenSuccessAliases = listOf("accepted", "queued", "running", "cancelling")
    val NotificationGuards = listOf("nonce", "expiry", "single_use", "operation_binding", "replay_rejection")
    val ActivityActions = listOf("detail", "cancel", "retry", "reconcile")
}

data class AutomationAdmissionContract(
    val requestId: String,
    val source: String,
    val operation: String,
    val generation: Long,
    val acceptedNotSuccess: Boolean,
)

data class ActivityRunContract(
    val operationId: String,
    val state: String,
    val terminalTruth: String,
    val recoveryRequired: Boolean,
    val canRetry: Boolean,
    val canCancel: Boolean,
    val canReconcile: Boolean,
    val evidenceCorrelation: String,
)
