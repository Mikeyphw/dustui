package com.mikeyphw.dustui.go

/**
 * Structured Android-side failure for the long-lived app-owned Go runtime.
 *
 * [message] is intentionally safe for direct presentation in the UI. Internal
 * sentinel strings, stderr, process exit details, and request identity live in
 * [diagnosticSummary] instead so transport implementation details cannot leak
 * into snackbars while remaining available to Diagnostics.
 */
class GoRuntimeTransportException(
    val code: Code,
    val processAlive: Boolean,
    val exitCode: Int?,
    val stderrTail: String,
    val requestType: String? = null,
    val requestId: String? = null,
    val sequence: Long? = null,
    val generation: Long? = null,
    cause: Throwable? = null,
) : IllegalStateException(code.userMessage, cause) {
    enum class Code(val wireCode: String, val userMessage: String) {
        DEAD(
            "go_runtime_dead_requires_reconcile",
            "Storage engine stopped unexpectedly and needs to reconnect.",
        ),
        EOF(
            "go_runtime_eof_requires_reconcile",
            "Storage engine stopped unexpectedly and needs to reconnect.",
        ),
        WRITE_FAILED(
            "go_runtime_write_failed",
            "Storage engine connection was interrupted and needs to reconnect.",
        ),
        READ_FAILED(
            "go_runtime_read_failed",
            "Storage engine connection was interrupted and needs to reconnect.",
        ),
        PROTOCOL(
            "go_runtime_protocol_invalid",
            "Storage engine returned an invalid response and needs to reconnect.",
        ),
        MISSING_EXECUTABLE(
            "go_runtime_missing",
            "Storage engine is missing from this app build. Reinstall DustUI or open Diagnostics for details.",
        ),
        READINESS_EOF(
            "go_runtime_readiness_eof",
            "Storage engine could not start. Try again or open Diagnostics for details.",
        ),
        READINESS_INVALID(
            "go_runtime_readiness_invalid",
            "Storage engine could not start. Try again or open Diagnostics for details.",
        ),
    }

    val diagnosticSummary: String
        get() = buildString {
            append("code=").append(code.wireCode)
            append(" alive=").append(processAlive)
            exitCode?.let { append(" exitCode=").append(it) }
            requestType?.takeIf(String::isNotBlank)?.let { append(" requestType=").append(it) }
            requestId?.takeIf(String::isNotBlank)?.let { append(" requestId=").append(it) }
            sequence?.let { append(" sequence=").append(it) }
            generation?.let { append(" generation=").append(it) }
            if (stderrTail.isNotBlank()) append(" stderr=").append(stderrTail)
            cause?.message?.takeIf(String::isNotBlank)?.let { append(" cause=").append(it) }
        }
}

/** Raised when the runtime died during a request whose result cannot be replayed safely. */
class GoRuntimeReconciliationRequiredException(
    val failure: GoRuntimeTransportException,
) : IllegalStateException(
    "Storage engine stopped unexpectedly. This action was not replayed because its outcome may be ambiguous. Open Activity or Diagnostics before trying again.",
    failure,
)

/**
 * User-safe wrapper for runtime-internal request failures that were returned as
 * a valid response. Detailed server evidence remains on stderr/Diagnostics.
 */
class GoRuntimeRequestException(
    val code: String,
    val requestType: String,
) : IllegalStateException(userMessage(code)) {
    companion object {
        private val internalCodes = setOf(
            "runtime_internal_error",
            "runtime_response_encode_failed",
            "malformed_runtime_frame",
            "unsupported_session_protocol",
            "frame_identity_required",
            "request_exceeds_bounded_stream",
            "response_exceeds_bounded_stream",
        )

        fun from(type: String, code: String): GoRuntimeRequestException? =
            code.takeIf { it in internalCodes }?.let { GoRuntimeRequestException(it, type) }

        private fun userMessage(code: String): String = when (code) {
            "response_exceeds_bounded_stream", "request_exceeds_bounded_stream" ->
                "Storage engine hit a bounded-transfer limit. Refine the request or open Diagnostics for details."
            "runtime_internal_error", "runtime_response_encode_failed" ->
                "Storage engine hit an internal error while handling this request. Open Diagnostics for details."
            else ->
                "Storage engine protocol error. Retry the action or open Diagnostics for details."
        }
    }
}

/**
 * Runtime replay policy. Only observational/read-only requests may be repeated
 * automatically after process death. Anything that can mutate durable state is
 * fail-closed and requires the caller's normal reconciliation path.
 */
object GoRuntimeRecoveryPolicy {
    private val replaySafeTypes = setOf(
        "domain.ui.action-gate",
        "domain.ui.screen-state",
        "domain.schema.verify",
        "domain.scan.latest",
        "domain.scan.session.get",
        "domain.scan.sessions.completed",
        "domain.scan.entries.page",
        "domain.scan.browse.page",
        "domain.scan.entry.get",
        "domain.scan.entries.by-paths",
        "domain.scan.files.at-least",
        "domain.scan.files.extensions",
        "domain.scan.duplicate.buckets",
        "domain.scan.duplicate.files",
        "domain.scan.search",
        "domain.authority.query",
        "domain.authority.rules.preview",
        "domain.package.snapshot.latest",
        "domain.cleanup.verification.get",
        "domain.cleanup.verification.list",
        "domain.automation.observe",
        "domain.automation.by-request",
        "domain.automation.activity.list",
        "domain.automation.activity.detail",
        "domain.automation.activity.evidence",
        "domain.diagnostics.list",
        "domain.diagnostics.snapshot",
        "domain.product.rules.preview",
        "domain.product.insights",
        "domain.cleanup.operations.list",
    )

    fun mayReplay(type: String): Boolean = type in replaySafeTypes
}
