package com.mikeyphw.dustui.go

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainAuthorityProductionReachabilityContractTest {
    private fun androidRoot(): File = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main/kotlin").isDirectory }

    @Test fun migratedProductTruthHasNoProductionKotlinAnalyzerCaller() {
        val root = androidRoot()
        val production = File(root, "app/src/main/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        listOf(
            "AppStorageInspectorEngine.analyze(",
            "CleanupRecommendationEngine.analyze(",
            "StorageGrowthEngine.compare(",
            "CleanupRuleEngine.preview(",
            "RootModuleAnalyzer.analyze(",
            "CleanupVerificationEngine.build(",
            "CleanupVerificationEngine.reverify(",
        ).forEach { forbidden -> assertFalse("production caller remains: $forbidden", production.contains(forbidden)) }
        assertFalse(production.contains("domain.product.insights"))
        assertFalse(production.contains("domain.product.rules.preview"))
    }

    @Test fun verificationReportsReconnectToGoAuthorityNotFileCache() {
        val root = androidRoot()
        val repository = File(root, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        assertTrue(repository.contains("domain.cleanup.verification.list"))
        assertTrue(repository.contains("domain.cleanup.verification.get"))
        listOf("cleanupReportStore.load(", "cleanupReportStore.reports(", "cleanupReportStore.report(", "cleanupReportStore.save(")
            .forEach { forbidden -> assertFalse("local report cache remains authoritative: $forbidden", repository.contains(forbidden)) }
    }
}
