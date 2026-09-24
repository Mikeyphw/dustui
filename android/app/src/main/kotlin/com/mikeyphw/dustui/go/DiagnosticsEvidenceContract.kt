package com.mikeyphw.dustui.go

/**
 * OV12 reconnect scaffold only. Android buildability is intentionally suspended
 * until OV13; this file records the contract the future shell must consume.
 */
object DiagnosticsEvidenceContract {
    const val protocol: String = "dustui.diagnostics.evidence.v1"
    const val canonicalRoute: String = "System/Diagnostics"
    const val androidBuildability: String = "intentionally-suspended-until-OV13"
    val evidenceStates: Set<String> = setOf("missing", "stale", "current", "degraded")
    val privacyClasses: Set<String> = setOf("raw_paths", "hashes", "package_names", "root_device_metadata")
    val supportBundleGuards: Set<String> = setOf(
        "manifest",
        "checksums",
        "selected_evidence_generations",
        "explicit_privacy_mode",
        "no_file_content_by_default",
        "no_automatic_upload"
    )
}
