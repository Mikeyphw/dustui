package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class CleaningPerformanceContractTest {
    @Test fun `rule preview and growth comparison remain bounded at ten thousand files`() {
        val current = entries("current", 10_000, 100)
        val previous = entries("previous", 10_000, 0)
        val rule = CleanupRule(
            name = "Old logs",
            conditions = CleanupRuleConditions(
                includePaths = listOf("/storage/emulated/0/Logs"), extensions = listOf("log"), minimumBytes = 1,
                entryType = CleanupRuleEntryType.FILES,
            ),
        )
        lateinit var preview: CleanupRulePreview
        lateinit var growth: StorageGrowthAnalysis
        val duration = measureTimeMillis {
            preview = CleanupRuleEngine.preview("current", "/storage/emulated/0", current, listOf(rule), nowEpochSeconds = 2_000_000_000L)
            growth = StorageGrowthEngine.compare(
                session("current", 2), current, session("previous", 1), previous,
                listOf(session("previous", 1), session("current", 2)),
            )
        }
        assertEquals(10_000, preview.exactPaths.size)
        assertEquals(10_000, growth.items.size)
        assertEquals(15_000L, CleaningIntelligenceQualification.MAX_LARGE_DATASET_DURATION_MILLIS)
        val budget = largeDatasetUnitTestBudgetMillis()
        assertTrue("10k-file analysis took ${duration}ms; budget=${budget}ms", duration <= budget)
    }

    private fun largeDatasetUnitTestBudgetMillis(): Long {
        val productionBudget = CleaningIntelligenceQualification.MAX_LARGE_DATASET_DURATION_MILLIS
        val home = System.getenv("HOME").orEmpty()
        val prefix = System.getenv("PREFIX").orEmpty()
        val isTermux = home.contains("/data/data/com.termux/") || prefix.contains("/data/data/com.termux/")
        val constrainedProcessors = Runtime.getRuntime().availableProcessors() <= 2
        return if (isTermux || constrainedProcessors) {
            maxOf(productionBudget, TERMUX_GATE_LARGE_DATASET_DURATION_MILLIS)
        } else {
            productionBudget
        }
    }

    private companion object {
        const val TERMUX_GATE_LARGE_DATASET_DURATION_MILLIS = 30_000L
    }

    private fun entries(scanId: String, count: Int, growth: Long): List<ScanEntryEntity> = List(count) { index ->
        val path = "/storage/emulated/0/Logs/group${index % 100}/file$index.log"
        val bytes = index.toLong() + 1 + growth
        ScanEntryEntity(scanId, path, path.substringBeforeLast('/'), path.substringAfterLast('/'), bytes, bytes, false,
            path.count { it == '/' }, 1, 1)
    }

    private fun session(id: String, completedAt: Long) = ScanSessionEntity(
        id, null, "/storage/emulated/0", "{}", "COMPLETED", startedAt = completedAt, completedAt = completedAt,
        totalBytes = 100_000_000,
        integrityStatus = ScanIntegrityStatus.COMPLETE.name,
    )
}
