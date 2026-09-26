package com.mikeyphw.dustui.data

enum class CleaningQualificationArea(val label: String) {
    BUILD("Build and diagnostics"),
    DATABASE("Database compatibility"),
    DELETION_SAFETY("Deletion safety"),
    DUPLICATES("Duplicate verification"),
    RESIDUE("App residue"),
    RULES("Cleanup rules"),
    HISTORY("Storage history"),
    REPORTS("Deletion reports"),
    RECOVERY("Process recovery"),
    ACCESSIBILITY("Accessibility"),
    PERFORMANCE("Performance"),
    PRODUCT_CONTRACT("Product contract"),
}

enum class CleaningQualificationSeverity { BLOCKER, WARNING }

data class CleaningQualificationCheck(
    val id: String,
    val area: CleaningQualificationArea,
    val severity: CleaningQualificationSeverity,
    val passed: Boolean,
    val detail: String,
)

data class CleaningQualificationEvidence(
    val testsPassed: Int,
    val testsFailed: Int,
    val lintErrors: Int,
    val lintWarnings: Int,
    val databaseSchemaPassed: Boolean,
    val protectedPathSuitePassed: Boolean,
    val fingerprintSuitePassed: Boolean,
    val duplicateHardLinkSuitePassed: Boolean,
    val residueOwnershipSuitePassed: Boolean,
    val cleanupRuleSuitePassed: Boolean,
    val historicalPathSuitePassed: Boolean,
    val reportRedactionSuitePassed: Boolean,
    val interruptedOperationSuitePassed: Boolean,
    val accessibilitySuitePassed: Boolean,
    val largeDatasetSuitePassed: Boolean,
    val surfacedPathsActionable: Boolean,
    val quarantineRuntimeReferences: Int,
    val sampleDatasetEntries: Int = 0,
    val sampleDurationMillis: Long = 0,
)

data class CleaningQualificationReport(val checks: List<CleaningQualificationCheck>) {
    val blockers: Int get() = checks.count { !it.passed && it.severity == CleaningQualificationSeverity.BLOCKER }
    val warnings: Int get() = checks.count { !it.passed && it.severity == CleaningQualificationSeverity.WARNING }
    val ready: Boolean get() = blockers == 0
    fun summary(): String = "Cleaning intelligence gate: ${if (ready) "passed" else "failed"} ($blockers blockers, $warnings warnings)"

    companion object {
        fun notEvaluated(): CleaningQualificationReport = CleaningQualificationReport(
            listOf(
                CleaningQualificationCheck(
                    id = "not-evaluated",
                    area = CleaningQualificationArea.BUILD,
                    severity = CleaningQualificationSeverity.BLOCKER,
                    passed = false,
                    detail = "Cleaning intelligence qualification evidence was not evaluated",
                ),
            ),
        )
    }
}

object CleaningIntelligenceQualification {
    const val MIN_LARGE_DATASET_ENTRIES = 10_000
    const val MAX_LARGE_DATASET_DURATION_MILLIS = 15_000L

    fun evaluate(evidence: CleaningQualificationEvidence): CleaningQualificationReport = CleaningQualificationReport(
        listOf(
            blocker("tests", CleaningQualificationArea.BUILD, evidence.testsPassed > 0 && evidence.testsFailed == 0,
                "${evidence.testsPassed} passed, ${evidence.testsFailed} failed"),
            blocker("lint", CleaningQualificationArea.BUILD, evidence.lintErrors == 0 && evidence.lintWarnings == 0,
                "${evidence.lintErrors} errors, ${evidence.lintWarnings} warnings"),
            blocker("database-schema", CleaningQualificationArea.DATABASE, evidence.databaseSchemaPassed,
                "Go-owned SQLite schema and compatibility contract"),
            blocker("protected-paths", CleaningQualificationArea.DELETION_SAFETY, evidence.protectedPathSuitePassed,
                "Protected path, scan-root, outside-root, symlink, and mount-boundary cases"),
            blocker("fingerprints", CleaningQualificationArea.DELETION_SAFETY, evidence.fingerprintSuitePassed,
                "Changed content, replacement inode, and reviewed SHA-256 cases"),
            blocker("hard-links", CleaningQualificationArea.DUPLICATES, evidence.duplicateHardLinkSuitePassed,
                "Hard links do not inflate reclaimable bytes or automatic deletion"),
            blocker("residue-ownership", CleaningQualificationArea.RESIDUE, evidence.residueOwnershipSuitePassed,
                "Multi-user and uncertain package ownership stay conservative"),
            blocker("cleanup-rules", CleaningQualificationArea.RULES, evidence.cleanupRuleSuitePassed,
                "Imported rule review, safe regex, protected exclusions, and exact preview"),
            blocker("historical-paths", CleaningQualificationArea.HISTORY, evidence.historicalPathSuitePassed,
                "Historical snapshots never become current deletion targets"),
            blocker("report-redaction", CleaningQualificationArea.REPORTS, evidence.reportRedactionSuitePassed,
                "Exported reports redact credentials and retain useful status labels"),
            blocker("operation-recovery", CleaningQualificationArea.RECOVERY, evidence.interruptedOperationSuitePassed,
                "Interrupted analysis and deletion recover without claiming success"),
            blocker("accessibility", CleaningQualificationArea.ACCESSIBILITY, evidence.accessibilitySuitePassed,
                "Compact, large-font, TalkBack, switch, and chart-list parity contracts"),
            blocker(
                "large-dataset",
                CleaningQualificationArea.PERFORMANCE,
                evidence.largeDatasetSuitePassed && evidence.sampleDatasetEntries >= MIN_LARGE_DATASET_ENTRIES &&
                    evidence.sampleDurationMillis in 0..MAX_LARGE_DATASET_DURATION_MILLIS,
                "${evidence.sampleDatasetEntries} entries in ${evidence.sampleDurationMillis} ms",
            ),
            blocker("actionable-paths", CleaningQualificationArea.PRODUCT_CONTRACT, evidence.surfacedPathsActionable,
                "Every surfaced current file has inspect, reveal, select, or reviewed deletion actions"),
            blocker("no-quarantine-runtime", CleaningQualificationArea.PRODUCT_CONTRACT, evidence.quarantineRuntimeReferences == 0,
                "${evidence.quarantineRuntimeReferences} active quarantine runtime reference(s)"),
        ),
    )

    private fun blocker(id: String, area: CleaningQualificationArea, passed: Boolean, detail: String) =
        CleaningQualificationCheck(id, area, CleaningQualificationSeverity.BLOCKER, passed, detail)
}
