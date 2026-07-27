package com.mikeyphw.dustui.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DatabaseSchemaContractTest {
    private val androidRoot = locateAndroidRoot()

    @Test fun `current room schema is exported and matches database version`() {
        val databaseSource = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/DustDatabase.kt").readText()
        val version = Regex("version\\s*=\\s*(\\d+)").find(databaseSource)?.groupValues?.get(1)?.toInt()
        assertEquals(2, version)
        val schemaFile = File(androidRoot, "app/schemas/com.mikeyphw.dustui.data.DustDatabase/$version.json")
        assertTrue(schemaFile.isFile)
        val schema = JSONObject(schemaFile.readText())
        assertEquals(version, schema.getJSONObject("database").getInt("version"))
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        val tables = buildSet { for (index in 0 until entities.length()) add(entities.getJSONObject(index).getString("tableName")) }
        assertTrue(tables.containsAll(setOf("scan_sessions", "scan_entries", "cleanup_operations", "quarantine_entries")))
    }

    @Test fun `version one migration is explicit and never destructive`() {
        val container = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/AppContainer.kt").readText()
        assertTrue(container.contains("addMigrations(MIGRATION_1_2)"))
        assertTrue(container.contains("Migration(1, 2)"))
        assertTrue(container.contains("CREATE TABLE IF NOT EXISTS `cleanup_operations`"))
        assertTrue(container.contains("CREATE TABLE IF NOT EXISTS `quarantine_entries`"))
        assertFalse(container.contains("fallbackToDestructiveMigration"))
        assertFalse(container.contains("DROP TABLE"))
    }

    @Test fun `legacy quarantine table is compatibility only`() {
        val cleanupModels = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/CleanupModels.kt").readText()
        val repository = File(androidRoot, "app/src/main/kotlin/com/mikeyphw/dustui/data/ScanRepository.kt").readText()
        assertTrue(cleanupModels.contains("Retained only so existing version-2 databases remain readable"))
        assertFalse(repository.contains("insertQuarantine"))
        assertFalse(repository.contains("fun restoreCleanup("))
    }

    private fun locateAndroidRoot(): File {
        val cwd = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(cwd, cwd.parentFile, cwd.parentFile?.parentFile).filterNotNull()
            .first { File(it, "app/schemas/com.mikeyphw.dustui.data.DustDatabase/2.json").isFile }
    }
}
