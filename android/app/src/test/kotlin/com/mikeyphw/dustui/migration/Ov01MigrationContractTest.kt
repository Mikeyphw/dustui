package com.mikeyphw.dustui.migration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ov01MigrationContractTest {
    private val repoRoot: File by lazy {
        generateSequence(File("").canonicalFile) { it.parentFile }
            .firstOrNull { candidate ->
                File(candidate, ".devtool.toml").isFile &&
                    File(candidate, "android/app/src/main").isDirectory &&
                    File(candidate, "migration/ledger/findings.json").isFile &&
                    File(candidate, "go/internal/readiness/readiness.go").isFile
            }
            ?: error("Unable to locate DustUI repository root from ${File("").canonicalFile}")
    }

    private fun repoFile(path: String): File = File(repoRoot, path)

    @Test fun `ledger maps every audited finding family`() {
        val ledger = repoFile("migration/ledger/findings.json").readText()
        assertTrue(ledger.contains("\"ABH\": 16"))
        assertTrue(ledger.contains("\"RC\": 14"))
        assertTrue(ledger.contains("\"N\": 8"))
        assertTrue(ledger.contains("\"CP\": 2"))
        listOf("ABH-01", "ABH-16", "RC-01", "RC-14", "N-01", "N-08", "CP-01", "CP-02").forEach { id ->
            assertTrue("missing $id", ledger.contains("\"$id\""))
        }
    }

    @Test fun `root admission is bounded while legacy root service remains reachable`() {
        val source = repoFile("android/app/src/main/kotlin/com/mikeyphw/dustui/root/DustRootService.kt").readText()
        assertFalse(source.contains("newCachedThreadPool"))
        assertTrue(source.contains("ThreadPoolExecutor"))
        assertTrue(source.contains("MAX_PRIVILEGED_QUEUE_DEPTH"))
        assertTrue(source.contains("allowCoreThreadTimeOut(true)"))
    }

    @Test fun `cleanup plan publication fails closed instead of direct overwrite fallback`() {
        val source = repoFile("android/app/src/main/kotlin/com/mikeyphw/dustui/root/RootServiceClient.kt").readText()
        assertTrue(source.contains("Reviewed cleanup plan publication failed"))
        assertFalse(source.contains("file.writeText(review.toExecutionJson()"))
    }

    @Test fun `legacy cleanup blocks degraded stale or incompatible source scans`() {
        val source = repoFile("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        assertTrue(source.contains("cleanupScanBlockReason"))
        assertTrue(source.contains("source scan is truncated"))
        assertTrue(source.contains("scan persistence is stale"))
        assertTrue(source.contains("different scan"))
    }

    @Test fun `scheduled scan launch is not represented as terminal scan success`() {
        val source = repoFile("android/app/src/main/kotlin/com/mikeyphw/dustui/scan/ScheduledScanWorker.kt").readText()
        assertTrue(source.contains("STATUS_LAUNCHED_NOT_TERMINAL"))
        assertTrue(source.contains("KEY_SCAN_STATUS"))
        assertFalse(source.contains("ScanForegroundService.start(applicationContext, request, launchedFromForegroundWorker = true)\n            Result.success()"))
    }

    @Test fun `release plan is variant correct and avoids debug tasks`() {
        val devtool = repoFile(".devtool.toml").readText()
        val releaseGate = repoFile("android/scripts/release-gate.sh").readText()
        val packageRc = repoFile("android/scripts/package-rc.sh").readText()
        listOf(devtool, releaseGate, packageRc).forEach { text ->
            assertTrue(text.contains("testReleaseUnitTest"))
            assertTrue(text.contains("lintRelease"))
            assertTrue(text.contains("assembleRelease"))
        }
        val releasePlanBlock = Regex("release_plan = \\[([^]]+)\\]").find(devtool)?.value.orEmpty()
        assertFalse(releasePlanBlock.contains("Debug"))
        assertFalse(releaseGate.contains("testDebugUnitTest"))
        assertFalse(packageRc.contains("testDebugUnitTest"))
    }

    @Test fun `go readiness authority matches migration phase`() {
        val readiness = repoFile("go/internal/readiness/readiness.go").readText()
        val devtool = repoFile(".devtool.toml").readText()
        val gateDCutover = devtool.contains("migration_phase = \"OV13\"") ||
            readiness.contains("GoCLIDefault") ||
            readiness.contains("GoCLIDefault:")

        if (gateDCutover) {
            assertTrue(
                "OV13 Gate D makes Go the product engine",
                Regex("ProductionEngine:\\s+true").containsMatchIn(readiness),
            )
            assertTrue(
                "OV13 Gate D makes Go SQLite/read authority the product model",
                Regex("SQLiteAuthority(Model)?:\\s+true").containsMatchIn(readiness) ||
                    Regex("ReadAuthority:\\s+true").containsMatchIn(readiness),
            )
            assertTrue(
                "OV13 Gate D keeps mutation authority in Go",
                Regex("MutationAuthority:\\s+true").containsMatchIn(readiness),
            )
        } else {
            assertTrue(readiness.contains("ProductionEngine:  false"))
            assertTrue(readiness.contains("SQLiteAuthority:   false"))
            assertTrue(readiness.contains("MutationAuthority: false"))
        }
    }
}
