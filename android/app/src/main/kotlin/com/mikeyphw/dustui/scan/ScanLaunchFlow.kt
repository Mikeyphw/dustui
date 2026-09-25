package com.mikeyphw.dustui.scan

/**
 * AUD-FIX-10 interactive scan admission protocol.
 *
 * A user-started scan must move through scope selection, privileged preflight,
 * review, and an explicit Start confirmation.  The state machine is pure so
 * unit tests can prove that no UI path can jump directly from scope selection
 * to execution.
 */
enum class ScanLaunchStage { ChooseScope, Preflight, Review, Starting }

data class ScanPreflightSummary(
    val ok: Boolean,
    val code: String,
    val message: String,
    val requestedPath: String,
    val canonicalPath: String? = null,
    val mountPoint: String? = null,
    val filesystem: String? = null,
)

data class ScanLaunchDecision(
    val allowed: Boolean,
    val reasonCode: String,
    val message: String,
    val remediationCodes: List<String> = emptyList(),
)

data class ScanLaunchState(
    val stage: ScanLaunchStage = ScanLaunchStage.ChooseScope,
    val request: ScanRequest? = null,
    val scopeLabel: String? = null,
    val preflight: ScanPreflightSummary? = null,
    val decision: ScanLaunchDecision? = null,
) {
    val canStart: Boolean
        get() = stage == ScanLaunchStage.Review && request != null && preflight?.ok == true && decision?.allowed == true
}

object ScanLaunchFlow {
    fun choose(request: ScanRequest, label: String): ScanLaunchState = ScanLaunchState(
        stage = ScanLaunchStage.Preflight,
        request = request.validated(),
        scopeLabel = label,
    )

    fun resolvePreflight(
        state: ScanLaunchState,
        preflight: ScanPreflightSummary,
        decision: ScanLaunchDecision,
    ): ScanLaunchState {
        require(state.stage == ScanLaunchStage.Preflight) { "scan_preflight_requires_scope_selection" }
        require(state.request != null) { "scan_request_required" }
        require(preflight.requestedPath == state.request.rootPath) { "scan_preflight_scope_mismatch" }
        return state.copy(
            stage = ScanLaunchStage.Review,
            preflight = preflight,
            decision = decision,
        )
    }

    fun beginStart(state: ScanLaunchState): ScanLaunchState {
        require(state.canStart) { "scan_start_requires_successful_preflight_and_review" }
        return state.copy(stage = ScanLaunchStage.Starting)
    }

    fun reset(): ScanLaunchState = ScanLaunchState()
}
