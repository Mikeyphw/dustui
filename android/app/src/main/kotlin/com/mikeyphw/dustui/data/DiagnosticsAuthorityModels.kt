package com.mikeyphw.dustui.data

data class DiagnosticRemediationAction(
    val code: String,
    val label: String,
    val route: String?,
    val destructive: Boolean,
)

data class DiagnosticProblem(
    val id: String,
    val reason: String,
    val recoveryRequired: Boolean,
    val operationId: String?,
    val rootCauseId: String?,
    val guidance: String,
    val risk: String,
    val remediationActions: List<DiagnosticRemediationAction>,
)

data class DiagnosticEvidenceGeneration(
    val subject: String,
    val generation: Long,
    val hash: String,
    val health: String,
    val reason: String?,
)

data class DiagnosticsAuthoritySnapshot(
    val protocol: String,
    val generatedAtMillis: Long,
    val evidence: List<DiagnosticEvidenceGeneration>,
    val problems: List<DiagnosticProblem>,
    val supportExportState: String,
    val maxEventsPerSession: Int,
    val maxSessions: Int,
    val maxSupportBundles: Int,
)
