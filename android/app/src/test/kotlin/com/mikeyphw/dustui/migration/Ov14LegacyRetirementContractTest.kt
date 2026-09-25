package com.mikeyphw.dustui.migration

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Ov14LegacyRetirementContractTest {
    private fun repoRoot(): File {
        val cwd = File("").absoluteFile
        return generateSequence(cwd) { it.parentFile }
            .firstOrNull {
                File(it, ".devtool.toml").isFile &&
                    File(it, "go/internal/retirement/retirement.go").isFile &&
                    File(it, "migration/contracts/v1/ov14_legacy_retirement.md").isFile
            }
            ?: cwd
    }

    private fun repoFile(path: String): File = File(repoRoot(), path)

    @Test fun `ov14 records desired retirement target and observed blockers separately`() {
        val contract = repoFile("migration/contracts/v1/ov14_legacy_retirement.md").readText()
        assertTrue(contract.contains("cpp_scanner_runtime = none"))
        assertTrue(contract.contains("python_runtime = none"))
        assertTrue(contract.contains("room_domain_authority = none"))
        assertTrue(contract.contains("legacy_destructive_executor = none"))
        assertTrue(contract.contains("legacy_navigation = none"))
        assertTrue(contract.contains("Observed state"))
        assertTrue(contract.contains("cpp_scanner_runtime = active-production"))
        assertTrue(contract.contains("go_sqlite_persistence = missing-implementation")) // historical OV14 observed baseline
        val status = repoFile("migration/ledger/ov14_legacy_retirement_status.json").readText()
        assertTrue(status.contains("\"status\": \"reopened_blocked\""))
        assertTrue(status.contains("\"room_domain_authority\": \"none\""))
        assertTrue(status.contains("rem03_android_domain_protocol_cutover"))
    }

    @Test fun `ov14 keeps supported devtool wrapper jobs`() {
        val devtool = repoFile(".devtool.toml").readText()
        assertTrue(devtool.contains("[wrapper]"))
        assertTrue(devtool.contains("[targets.dustui_android.jobs.ov13_contract]"))
        assertTrue(devtool.contains("[targets.dustui_android.workflows]"))
        assertTrue(devtool.contains("gate_d ="))
    }
}
