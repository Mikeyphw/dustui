package com.mikeyphw.dustui.data

import java.util.Locale

enum class RcDecision {
    SHIP,
    SHIP_WITH_WARNINGS,
    DO_NOT_SHIP,
}

enum class RcIssueSeverity {
    BLOCKER,
    WARNING,
    INFO,
}

data class RcKnownIssue(
    val id: String,
    val title: String,
    val severity: RcIssueSeverity,
)

data class FinalRcEvidence(
    val releaseGatePassed: Boolean,
    val testsPassed: Int,
    val testsFailed: Int,
    val diagnosticsWarnings: Int,
    val packagingReport: ReleasePackagingReport,
    val installSmokePassed: Boolean,
    val deviceCertificationPassed: Boolean,
    val changelogReady: Boolean,
    val checklistReady: Boolean,
    val knownIssuesReviewed: Boolean,
    val cleaningQualificationReport: CleaningQualificationReport = CleaningQualificationReport.notEvaluated(),
    val knownIssues: List<RcKnownIssue> = emptyList(),
)

data class FinalRcIssue(
    val id: String,
    val severity: RcIssueSeverity,
    val message: String,
)

data class FinalRcReport(
    val versionName: String,
    val decision: RcDecision,
    val issues: List<FinalRcIssue>,
) {
    val blockers: Int
        get() = issues.count { it.severity == RcIssueSeverity.BLOCKER }

    val warnings: Int
        get() = issues.count { it.severity == RcIssueSeverity.WARNING }

    val shipReady: Boolean
        get() = decision == RcDecision.SHIP

    fun statusLine(): String {
        val normalized = decision.name.lowercase(Locale.US).replace('_', '-')
        return "DustUI $versionName final RC status: $normalized ($blockers blockers, $warnings warnings)"
    }
}

object FinalRcReadiness {
    fun evaluate(versionName: String, evidence: FinalRcEvidence): FinalRcReport {
        val issues = mutableListOf<FinalRcIssue>()
        if (!evidence.releaseGatePassed) {
            issues += FinalRcIssue("release-gate", RcIssueSeverity.BLOCKER, "Release gate did not pass")
        }
        if (evidence.testsFailed > 0) {
            issues += FinalRcIssue("tests", RcIssueSeverity.BLOCKER, "${evidence.testsFailed} tests failed")
        }
        if (evidence.testsPassed <= 0) {
            issues += FinalRcIssue("tests-missing", RcIssueSeverity.BLOCKER, "No passing test evidence was recorded")
        }
        if (evidence.diagnosticsWarnings > 0) {
            issues += FinalRcIssue("diagnostics", RcIssueSeverity.BLOCKER, "Diagnostics still report ${evidence.diagnosticsWarnings} warning(s)")
        }
        if (!evidence.packagingReport.ready) {
            issues += FinalRcIssue("packaging", RcIssueSeverity.BLOCKER, "Release packaging has ${evidence.packagingReport.blockers} blocker(s)")
        }
        if (!evidence.changelogReady) {
            issues += FinalRcIssue("changelog", RcIssueSeverity.BLOCKER, "Changelog is missing or incomplete")
        }
        if (!evidence.checklistReady) {
            issues += FinalRcIssue("checklist", RcIssueSeverity.BLOCKER, "Release checklist is missing or incomplete")
        }
        if (!evidence.knownIssuesReviewed) {
            issues += FinalRcIssue("known-issues-review", RcIssueSeverity.BLOCKER, "Known issues were not reviewed")
        }
        if (!evidence.cleaningQualificationReport.ready) {
            issues += FinalRcIssue(
                "cleaning-intelligence",
                RcIssueSeverity.BLOCKER,
                evidence.cleaningQualificationReport.summary(),
            )
        }
        evidence.knownIssues.filter { it.severity == RcIssueSeverity.BLOCKER }.forEach { knownIssue ->
            issues += FinalRcIssue("known-issue-${knownIssue.id}", RcIssueSeverity.BLOCKER, knownIssue.title)
        }
        evidence.knownIssues.filter { it.severity == RcIssueSeverity.WARNING }.forEach { knownIssue ->
            issues += FinalRcIssue("known-issue-${knownIssue.id}", RcIssueSeverity.WARNING, knownIssue.title)
        }
        evidence.packagingReport.issues.filter { it.severity == ReleaseBlockerSeverity.WARNING }.forEach { packagingIssue ->
            issues += FinalRcIssue("packaging-${packagingIssue.id}", RcIssueSeverity.WARNING, packagingIssue.message)
        }
        if (!evidence.installSmokePassed) {
            issues += FinalRcIssue("install-smoke", RcIssueSeverity.WARNING, "Install and launch smoke evidence is missing")
        }
        if (!evidence.deviceCertificationPassed) {
            issues += FinalRcIssue("device-certification", RcIssueSeverity.WARNING, "Root-device certification evidence is missing")
        }
        val decision = when {
            issues.any { it.severity == RcIssueSeverity.BLOCKER } -> RcDecision.DO_NOT_SHIP
            issues.any { it.severity == RcIssueSeverity.WARNING } -> RcDecision.SHIP_WITH_WARNINGS
            else -> RcDecision.SHIP
        }
        return FinalRcReport(versionName, decision, issues.sortedWith(compareBy<FinalRcIssue> { it.severity.ordinal }.thenBy { it.id }))
    }
}
