package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootModuleAnalyzerTest {
    @Test fun `detects module footprint and flags`() {
        val report = RootModuleAnalyzer.analyze(listOf(
            entry("/data/adb/modules/mod_a/module.prop", 100),
            entry("/data/adb/modules/mod_a/service.sh", 200),
            entry("/data/adb/modules/mod_a/webroot/index.html", 300),
            entry("/data/adb/modules/mod_a/system/bin/tool", 400),
        ))
        val module = report.modules.single()
        assertEquals("mod_a", module.moduleId)
        assertEquals(1_000L, module.totalBytes)
        assertTrue(module.hasModuleProp)
        assertTrue(module.hasServiceScript)
        assertTrue(module.hasWebUi)
        assertEquals("/system/bin/tool", module.overlayTargets.single())
    }

    @Test fun `detects overlay conflicts`() {
        val report = RootModuleAnalyzer.analyze(listOf(
            entry("/data/adb/modules/a/system/bin/tool", 100),
            entry("/data/adb/modules/b/system/bin/tool", 200),
        ))
        assertEquals(1, report.overlayConflicts.size)
        val conflict = report.overlayConflicts.single()
        assertEquals(listOf("a", "b"), conflict.moduleIds)
        assertEquals(
            listOf("/data/adb/modules/b/system/bin/tool", "/data/adb/modules/a/system/bin/tool"),
            conflict.sourcePaths.map { it.path },
        )
    }

    private fun entry(path: String, size: Long) = ScanEntryEntity("scan", path, path.substringBeforeLast('/'), path.substringAfterLast('/'), size, size, false, 1, 0L, 1)
}
