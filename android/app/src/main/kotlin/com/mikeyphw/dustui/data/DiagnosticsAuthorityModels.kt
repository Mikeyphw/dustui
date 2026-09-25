package com.mikeyphw.dustui.data

import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticRemediationAction(
    val code: String,
    val label: String,
    val route: String?,
    val entityId: String?,
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
) {
    val unhealthyEvidence: List<DiagnosticEvidenceGeneration>
        get() = evidence.filterNot { it.health.equals("current", true) }
    val recoveryProblems: List<DiagnosticProblem>
        get() = problems.filter { it.recoveryRequired }
}

internal fun JSONObject.toDiagnosticProblem(): DiagnosticProblem {
    val actionsJson = optJSONArray("remediationActions") ?: JSONArray()
    val actions = buildList {
        for (j in 0 until actionsJson.length()) {
            val action = actionsJson.optJSONObject(j) ?: continue
            add(
                DiagnosticRemediationAction(
                    code = action.optString("code"),
                    label = action.optString("label"),
                    route = action.optString("route").ifBlank { null },
                    entityId = action.optString("entityId").ifBlank { null },
                    destructive = action.optBoolean("destructive"),
                ),
            )
        }
    }
    return DiagnosticProblem(
        id = optString("id"),
        reason = optString("reason"),
        recoveryRequired = optBoolean("recoveryRequired"),
        operationId = optString("operationId").ifBlank { null },
        rootCauseId = optString("rootCauseId").ifBlank { null },
        guidance = optString("guidance"),
        risk = optString("risk"),
        remediationActions = actions,
    )
}
