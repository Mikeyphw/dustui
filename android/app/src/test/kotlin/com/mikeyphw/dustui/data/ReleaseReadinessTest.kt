package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseReadinessTest {
    @Test fun `green evidence can ship final rc`() {
        val report = FinalRcReadiness.evaluate("1.4.0-rc1", evidence())
        assertEquals(RcDecision.SHIP, report.decision)
        assertTrue(report.shipReady)
        assertEquals(0, report.blockers)
    }

    @Test fun `test failure blocks final rc`() {
        val report = FinalRcReadiness.evaluate("1.4.0-rc1", evidence(testsFailed = 1))
        assertEquals(RcDecision.DO_NOT_SHIP, report.decision)
        assertFalse(report.shipReady)
        assertTrue(report.issues.any { it.id == "tests" })
    }

    @Test fun `unsigned release remains warning when packaging is otherwise ready`() {
        val packaging = ReleasePackaging.evaluate(
            versionName = "1.4.0-rc1",
            artifacts = listOf(
                ReleaseApkArtifact("app-debug.apk", 10, "a", ApkKind.DEBUG),
                ReleaseApkArtifact("app-release-unsigned.apk", 20, "b", ApkKind.RELEASE_UNSIGNED),
            ),
            certificationReady = true,
        )
        val report = FinalRcReadiness.evaluate("1.4.0-rc1", evidence(packagingReport = packaging))
        assertEquals(RcDecision.SHIP_WITH_WARNINGS, report.decision)
        assertFalse(report.shipReady)
        assertEquals(1, report.warnings)
    }

    @Test fun `known blocker prevents release`() {
        val report = FinalRcReadiness.evaluate(
            "1.4.0-rc1",
            evidence(knownIssues = listOf(RcKnownIssue("crash", "Launch crash on smoke device", RcIssueSeverity.BLOCKER))),
        )
        assertEquals(RcDecision.DO_NOT_SHIP, report.decision)
        assertTrue(report.issues.any { it.id == "known-issue-crash" })
    }

    @Test fun `status line summarizes decision`() {
        val report = FinalRcReadiness.evaluate("1.4.0-rc1", evidence(deviceCertificationPassed = false))
        assertEquals(RcDecision.SHIP_WITH_WARNINGS, report.decision)
        assertFalse(report.shipReady)
        assertTrue(report.statusLine().contains("1.4.0-rc1"))
        assertTrue(report.statusLine().contains("ship-with-warnings"))
    }


    @Test fun `missing cleaning qualification blocks final rc`() {
        val report = FinalRcReadiness.evaluate(
            "1.4.0-rc1",
            evidence(cleaningQualificationReport = CleaningQualificationReport.notEvaluated()),
        )
        assertEquals(RcDecision.DO_NOT_SHIP, report.decision)
        assertTrue(report.issues.any { it.id == "cleaning-intelligence" })
    }

    private fun evidence(
        testsFailed: Int = 0,
        diagnosticsWarnings: Int = 0,
        packagingReport: ReleasePackagingReport = signedPackagingReport(),
        installSmokePassed: Boolean = true,
        deviceCertificationPassed: Boolean = true,
        knownIssues: List<RcKnownIssue> = emptyList(),
        cleaningQualificationReport: CleaningQualificationReport = passedCleaningQualification(),
    ) = FinalRcEvidence(
        releaseGatePassed = true,
        testsPassed = 59,
        testsFailed = testsFailed,
        diagnosticsWarnings = diagnosticsWarnings,
        packagingReport = packagingReport,
        installSmokePassed = installSmokePassed,
        deviceCertificationPassed = deviceCertificationPassed,
        changelogReady = true,
        checklistReady = true,
        knownIssuesReviewed = true,
        cleaningQualificationReport = cleaningQualificationReport,
        knownIssues = knownIssues,
    )


    private fun passedCleaningQualification(): CleaningQualificationReport = CleaningIntelligenceQualification.evaluate(
        CleaningQualificationEvidence(
            testsPassed = 1, testsFailed = 0, lintErrors = 0, lintWarnings = 0, databaseSchemaPassed = true,
            protectedPathSuitePassed = true, fingerprintSuitePassed = true, duplicateHardLinkSuitePassed = true,
            residueOwnershipSuitePassed = true, cleanupRuleSuitePassed = true, historicalPathSuitePassed = true,
            reportRedactionSuitePassed = true, interruptedOperationSuitePassed = true, accessibilitySuitePassed = true,
            largeDatasetSuitePassed = true, surfacedPathsActionable = true, quarantineRuntimeReferences = 0,
            sampleDatasetEntries = 10_000, sampleDurationMillis = 100,
        ),
    )

    private fun signedPackagingReport() = ReleasePackaging.evaluate(
        versionName = "1.4.0-rc1",
        artifacts = listOf(
            ReleaseApkArtifact("app-debug.apk", 10, "a", ApkKind.DEBUG),
            ReleaseApkArtifact("app-release.apk", 20, "b", ApkKind.RELEASE_SIGNED),
        ),
        certificationReady = true,
    )
}
