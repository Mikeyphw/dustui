package com.mikeyphw.dustui.data

/** Reconciles cleanup rows left in an executing state after process or device interruption. */
object OperationRecovery {
    fun recover(operation: CleanupOperationEntity, now: Long): CleanupOperationEntity =
        if (operation.status == "EXECUTING") {
            operation.copy(
                status = "INTERRUPTED",
                completedAt = operation.completedAt ?: now,
                message = operation.message ?: "Operation was interrupted before completion; inspect quarantine before retrying.",
            )
        } else operation
}
