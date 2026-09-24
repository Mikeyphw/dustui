package com.mikeyphw.dustui.go

/**
 * OV11 reconnect scaffold: UI, WorkManager, Tasker, notifications, and recovery
 * must observe the same durable Go operation. Android buildability remains
 * intentionally suspended until OV13, so this file is a thin contract only.
 */
object AutomationActivityContract {
    const val Protocol = "dustui.automation.activity.v1"
    const val Phase = "OV11"
    const val Authority = "go-automation-activity"
    val ThinAdapters = listOf("ui", "workmanager", "tasker", "notification", "recovery")
    val NonDestructiveAutomation = listOf("scan", "status", "cancel", "inspect")
    val ForbiddenSuccessAliases = listOf("accepted", "queued", "running")
    val NotificationGuards = listOf("nonce", "expiry", "single_use", "operation_binding", "replay_rejection")
}

data class AutomationAdmissionContract(
    val requestId: String,
    val source: String,
    val operation: String,
    val generation: Long,
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
