package com.mikeyphw.dustui.go

/**
 * OV10 reconnect scaffold: cleanup intelligence is owned by Go.
 * Android Clean routes may render duplicate/residue/rule/recommendation outputs,
 * but must not derive safety labels or authorize deletion locally.
 */
object CleanupIntelligenceContract {
    const val Protocol = "dustui.cleanup.intelligence.v1"
    const val Phase = "OV10"
    const val Authority = "go-cleanup-intelligence"
    val Domains = listOf("duplicates", "residue", "recommendations", "rules")
    val ForbiddenAndroidPolicies = listOf(
        "bare_path_verified_safe_label",
        "regex_only_authorization",
        "local_duplicate_keep_selection",
        "local_residue_package_state_guess",
    )
}
