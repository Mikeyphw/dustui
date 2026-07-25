package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningIntelligenceQualificationTest {
    @Test fun `complete evidence passes final cleaning gate`() {
        val report = CleaningIntelligenceQualification.evaluate(greenEvidence())
        assertTrue(report.ready)
        assertEquals(0, report.blockers)
        assertTrue(report.summary().contains("passed"))
    }

    @Test fun `any safety regression blocks release`() {
        val report = CleaningIntelligenceQualification.evaluate(greenEvidence().copy(protectedPathSuitePassed = false))
        assertFalse(report.ready)
        assertTrue(report.checks.any { it.id == "protected-paths" && !it.passed })
    }

    @Test fun `warnings are forbidden by qualification gate`() {
        val report = CleaningIntelligenceQualification.evaluate(greenEvidence().copy(lintWarnings = 1))
        assertFalse(report.ready)
        assertTrue(report.checks.any { it.id == "lint" && !it.passed })
    }

    @Test fun `large dataset gate requires scale and bounded duration`() {
        assertFalse(CleaningIntelligenceQualification.evaluate(greenEvidence().copy(sampleDatasetEntries = 9_999)).ready)
        assertFalse(CleaningIntelligenceQualification.evaluate(greenEvidence().copy(sampleDurationMillis = 15_001)).ready)
    }

    @Test fun `quarantine runtime reference blocks final gate`() {
        val report = CleaningIntelligenceQualification.evaluate(greenEvidence().copy(quarantineRuntimeReferences = 1))
        assertFalse(report.ready)
        assertTrue(report.checks.any { it.id == "no-quarantine-runtime" && !it.passed })
    }

    private fun greenEvidence() = CleaningQualificationEvidence(
        testsPassed = 120, testsFailed = 0, lintErrors = 0, lintWarnings = 0,
        databaseSchemaPassed = true, protectedPathSuitePassed = true, fingerprintSuitePassed = true,
        duplicateHardLinkSuitePassed = true, residueOwnershipSuitePassed = true, cleanupRuleSuitePassed = true,
        historicalPathSuitePassed = true, reportRedactionSuitePassed = true, interruptedOperationSuitePassed = true,
        accessibilitySuitePassed = true, largeDatasetSuitePassed = true, surfacedPathsActionable = true,
        quarantineRuntimeReferences = 0, sampleDatasetEntries = 25_000, sampleDurationMillis = 750,
    )
}
