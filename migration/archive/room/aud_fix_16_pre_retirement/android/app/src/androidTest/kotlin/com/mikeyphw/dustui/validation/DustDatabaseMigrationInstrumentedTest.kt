package com.mikeyphw.dustui.validation

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikeyphw.dustui.AppContainer
import com.mikeyphw.dustui.data.DustDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room migration validation for the remediation schema.
 *
 * This deliberately uses MigrationTestHelper and exported schemas instead of the
 * source-string checks that allowed the v1->v2 index drift to escape earlier.
 */
@RunWith(AndroidJUnit4::class)
class DustDatabaseMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DustDatabase::class.java,
    )

    @Test
    fun migratesV1ToV3AndDropsOrphanEntries() {
        val db = helper.createDatabase(DB_NAME, 1)
        createVersion1Schema(db)
        db.execSQL(
            "INSERT INTO scan_sessions (id, presetId, rootPath, optionsJson, status, backend, startedAt, completedAt, scannedItems, scannedBytes, totalBytes, warningCount, currentPath, errorMessage) " +
                "VALUES ('kept', NULL, '/data/local/tmp/dustui', '{}', 'COMPLETED', 'native', 1, 2, 1, 10, 10, 0, '', NULL)",
        )
        db.execSQL(
            "INSERT INTO scan_entries (scanId, path, parentPath, name, sizeBytes, allocatedBytes, isDirectory, depth, modifiedAt, fileCount) " +
                "VALUES ('kept', '/data/local/tmp/dustui/a.bin', '/data/local/tmp/dustui', 'a.bin', 10, 10, 0, 1, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO scan_entries (scanId, path, parentPath, name, sizeBytes, allocatedBytes, isDirectory, depth, modifiedAt, fileCount) " +
                "VALUES ('orphan', '/data/local/tmp/dustui/orphan.bin', '/data/local/tmp/dustui', 'orphan.bin', 20, 20, 0, 1, 1, 1)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            3,
            true,
            AppContainer.MIGRATION_1_2,
            AppContainer.MIGRATION_2_3,
        )
        assertEquals(1L, migrated.longQuery("SELECT COUNT(*) FROM scan_entries"))
        assertEquals(1L, migrated.longQuery("SELECT COUNT(*) FROM scan_entries WHERE scanId = 'kept'"))
        assertEquals(0L, migrated.longQuery("SELECT COUNT(*) FROM scan_entries WHERE scanId = 'orphan'"))
        assertEquals("COMPLETE_WITH_WARNINGS", migrated.stringQuery("SELECT integrityStatus FROM scan_sessions WHERE id = 'kept'"))
        assertEquals(1L, migrated.longQuery("SELECT COUNT(*) FROM pragma_index_list('cleanup_operations') WHERE name = 'index_cleanup_operations_scanId'"))
        migrated.close()
    }

    @Test
    fun migratesV2ToV3AndMarksAbandonedRunningScansInterrupted() {
        val db = helper.createDatabase(DB_NAME, 2)
        createVersion2Schema(db)
        db.execSQL(
            "INSERT INTO scan_sessions (id, presetId, rootPath, optionsJson, status, backend, startedAt, completedAt, scannedItems, scannedBytes, totalBytes, warningCount, currentPath, errorMessage) " +
                "VALUES ('running', NULL, '/data/local/tmp/dustui', '{}', 'RUNNING', 'native', 1, NULL, 1, 10, 10, 0, '', NULL)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            3,
            true,
            AppContainer.MIGRATION_2_3,
        )
        assertEquals("INTERRUPTED", migrated.stringQuery("SELECT integrityStatus FROM scan_sessions WHERE id = 'running'"))
        assertNotNull(migrated.stringQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='cleanup_plan_items'"))
        migrated.close()
    }

    @Test
    fun freshV3EnforcesScanEntryForeignKeyCascade() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DustDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            "INSERT INTO scan_sessions (id, presetId, rootPath, optionsJson, status, backend, startedAt, completedAt, scannedItems, scannedBytes, totalBytes, warningCount, currentPath, errorMessage, generation, observedEntryCount, persistedEntryCount, malformedEventCount, truncated, integrityStatus) " +
                "VALUES ('scan', NULL, '/data/local/tmp/dustui', '{}', 'COMPLETED', 'native', 1, 2, 1, 10, 10, 0, '', NULL, 1, 1, 1, 0, 0, 'COMPLETE')",
        )
        sql.execSQL(
            "INSERT INTO scan_entries (scanId, path, parentPath, name, sizeBytes, allocatedBytes, isDirectory, depth, modifiedAt, fileCount) " +
                "VALUES ('scan', '/data/local/tmp/dustui/a.bin', '/data/local/tmp/dustui', 'a.bin', 10, 10, 0, 1, 1, 1)",
        )
        assertEquals(1L, sql.longQuery("SELECT COUNT(*) FROM scan_entries"))
        sql.execSQL("DELETE FROM scan_sessions WHERE id = 'scan'")
        assertEquals(0L, sql.longQuery("SELECT COUNT(*) FROM scan_entries"))
        db.close()
    }

    private fun createVersion1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_sessions (id TEXT NOT NULL, presetId TEXT, rootPath TEXT NOT NULL, optionsJson TEXT NOT NULL, status TEXT NOT NULL, backend TEXT NOT NULL DEFAULT '', startedAt INTEGER NOT NULL, completedAt INTEGER, scannedItems INTEGER NOT NULL DEFAULT 0, scannedBytes INTEGER NOT NULL DEFAULT 0, totalBytes INTEGER NOT NULL DEFAULT 0, warningCount INTEGER NOT NULL DEFAULT 0, currentPath TEXT NOT NULL DEFAULT '', errorMessage TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS scan_entries (scanId TEXT NOT NULL, path TEXT NOT NULL, parentPath TEXT NOT NULL, name TEXT NOT NULL, sizeBytes INTEGER NOT NULL, allocatedBytes INTEGER NOT NULL, isDirectory INTEGER NOT NULL, depth INTEGER NOT NULL, modifiedAt INTEGER NOT NULL, fileCount INTEGER NOT NULL, PRIMARY KEY(scanId, path))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_entries_scanId ON scan_entries(scanId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_entries_scanId_sizeBytes ON scan_entries(scanId, sizeBytes)")
    }

    private fun createVersion2Schema(db: SupportSQLiteDatabase) {
        createVersion1Schema(db)
        db.execSQL("CREATE TABLE IF NOT EXISTS cleanup_operations (id TEXT NOT NULL, scanId TEXT NOT NULL, rootPath TEXT NOT NULL, status TEXT NOT NULL, selectedCount INTEGER NOT NULL, plannedBytes INTEGER NOT NULL, reclaimedBytes INTEGER NOT NULL, createdAt INTEGER NOT NULL, completedAt INTEGER, message TEXT, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_cleanup_operations_scanId ON cleanup_operations(scanId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS quarantine_entries (id TEXT NOT NULL, operationId TEXT NOT NULL, originalPath TEXT NOT NULL, quarantinePath TEXT NOT NULL, sizeBytes INTEGER NOT NULL, restoredAt INTEGER, PRIMARY KEY(id))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quarantine_entries_operationId ON quarantine_entries(operationId)")
    }

    private fun SupportSQLiteDatabase.longQuery(sql: String): Long = query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    private fun SupportSQLiteDatabase.stringQuery(sql: String): String = query(sql).use { cursor ->
        cursor.moveToFirst()
        cursor.getString(0)
    }

    private companion object {
        const val DB_NAME = "dustui-migration-validation"
    }
}
