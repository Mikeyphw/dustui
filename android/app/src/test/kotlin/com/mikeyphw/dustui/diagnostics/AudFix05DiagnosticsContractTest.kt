package com.mikeyphw.dustui.diagnostics

import java.io.File
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudFix05DiagnosticsContractTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }.first { File(it, ".devtool.toml").isFile }
    private fun read(path: String) = File(root, path).readText()

    @Test fun `support export is Go sealed and Android is packaging only`() {
        val repo = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt")
        val writer = read("android/app/src/main/kotlin/com/mikeyphw/dustui/data/DebugExportBundle.kt")
        assertTrue(repo.contains("domain.support.bundle"))
        assertTrue(repo.contains("diagnosticsSnapshot"))
        assertTrue(repo.contains("recordDiagnosticEvent"))
        assertTrue(writer.contains("writeSealed"))
        assertTrue(writer.contains("go-domain-support-bundle"))
        assertFalse(writer.contains("fun write(context"))
        assertFalse(writer.contains("trace-events.jsonl"))
    }

    @Test fun `diagnostics truth comes from durable Go authority`() {
        val runtime = read("go/cmd/dustui-go/runtime_domain.go")
        val store = read("go/internal/state/diagnostics_authority.go")
        val vm = read("android/app/src/main/kotlin/com/mikeyphw/dustui/ui/MainViewModel.kt")
        listOf("domain.diagnostics.record", "domain.diagnostics.list", "domain.diagnostics.snapshot", "domain.diagnostics.compact", "domain.support.bundle").forEach { assertTrue(runtime.contains(it)) }
        assertTrue(store.contains("PruneDiagnostics"))
        assertTrue(store.contains("problem_graph_nodes"))
        assertTrue(store.contains("privacy_retention_rules"))
        assertTrue(vm.contains("repository.recordDiagnosticEvent"))
        assertTrue(vm.contains("diagnosticsAuthority"))
        assertTrue(vm.contains("entries = emptyList()"))
        assertTrue(vm.contains("events = emptyList()"))
    }
}
