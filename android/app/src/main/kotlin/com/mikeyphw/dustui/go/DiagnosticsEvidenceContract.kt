package com.mikeyphw.dustui.go

/** AUD-FIX-11 successor contract for the user-facing System → Diagnostics surface. */
object DiagnosticsEvidenceContract {
    const val protocol: String = "dustui.diagnostics.evidence.v1"
    const val canonicalRoute: String = "system/diagnostics"
    const val authority: String = "go-problem-graph"
    const val primaryPresentation: String = "root-cause-remediation"
    val evidenceStates: Set<String> = setOf("missing", "stale", "current", "degraded")
    val privacyClasses: Set<String> = setOf("raw_paths", "hashes", "package_names", "root_device_metadata")
    val supportBundleGuards: Set<String> = setOf(
        "manifest",
        "checksums",
        "selected_evidence_generations",
        "explicit_privacy_mode",
        "no_file_content_by_default",
        "no_automatic_upload",
    )
}
