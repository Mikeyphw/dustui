package com.mikeyphw.dustui.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * One-shot, read-only importer for the pre-Go Room v3 database.
 *
 * The legacy database is never opened through Room and never receives writes;
 * it is an import source only.  Once import succeeds a marker prevents it from
 * competing with the Go SQLite authority on future starts.
 */
class LegacyRoomV3Importer(
    context: Context,
    private val domainStore: GoDomainStore,
) {
    private val appContext = context.applicationContext
    private val marker = File(appContext.noBackupFilesDir, "go-state/room-v3-imported.marker")

    suspend fun importOnce(): Boolean {
        if (marker.isFile) return false
        val legacy = appContext.getDatabasePath("dustui.db")
        if (!legacy.isFile) {
            markImported("absent")
            return false
        }

        val db = SQLiteDatabase.openDatabase(legacy.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            if (db.version !in 1..3) return false
            db.query("scan_sessions", null, null, null, null, null, "startedAt ASC").use { sessions ->
                while (sessions.moveToNext()) {
                    val session = sessions.scanSession()
                    domainStore.upsertSession(session)
                    domainStore.deleteEntries(session.id)
                    db.query("scan_entries", null, "scanId = ?", arrayOf(session.id), null, null, "sizeBytes DESC, path COLLATE NOCASE").use { entries ->
                        val batch = ArrayList<ScanEntryEntity>(IMPORT_BATCH)
                        while (entries.moveToNext()) {
                            batch += entries.scanEntry()
                            if (batch.size == IMPORT_BATCH) {
                                domainStore.upsertEntries(batch.toList())
                                batch.clear()
                            }
                        }
                        if (batch.isNotEmpty()) domainStore.upsertEntries(batch)
                    }
                }
            }
            if (db.hasTable("cleanup_operations")) {
                db.query("cleanup_operations", null, null, null, null, null, "createdAt ASC").use { operations ->
                    while (operations.moveToNext()) {
                        val operation = operations.cleanupOperation()
                        domainStore.upsertCleanupOperation(operation)
                        if (db.hasTable("cleanup_plan_items")) {
                            db.query("cleanup_plan_items", null, "operationId = ?", arrayOf(operation.id), null, null, "path ASC").use { items ->
                                val plan = buildList { while (items.moveToNext()) add(items.cleanupPlanItem()) }
                                domainStore.replaceCleanupPlanItems(operation.id, plan)
                            }
                        }
                    }
                }
            }
            domainStore.verifySchema()
            markImported("room-v${db.version}")
            true
        } finally {
            db.close()
        }
    }

    private fun markImported(source: String) {
        marker.parentFile?.mkdirs()
        marker.writeText("$source\n")
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun Cursor.scanSession() = ScanSessionEntity(
        id = string("id"),
        presetId = nullableString("presetId"),
        rootPath = string("rootPath"),
        optionsJson = stringOr("optionsJson", "{}"),
        status = string("status"),
        backend = stringOr("backend", ""),
        startedAt = long("startedAt"),
        completedAt = nullableLong("completedAt"),
        scannedItems = longOr("scannedItems", 0),
        scannedBytes = longOr("scannedBytes", 0),
        totalBytes = longOr("totalBytes", 0),
        warningCount = longOr("warningCount", 0),
        currentPath = stringOr("currentPath", ""),
        errorMessage = nullableString("errorMessage"),
        generation = longOr("generation", 1),
        observedEntryCount = longOr("observedEntryCount", 0),
        persistedEntryCount = longOr("persistedEntryCount", 0),
        malformedEventCount = longOr("malformedEventCount", 0),
        truncated = longOr("truncated", 0) != 0L,
        integrityStatus = stringOr("integrityStatus", ScanIntegrityStatus.UNKNOWN.name),
    )

    private fun Cursor.scanEntry() = ScanEntryEntity(
        scanId = string("scanId"), path = string("path"), parentPath = string("parentPath"), name = string("name"),
        sizeBytes = long("sizeBytes"), allocatedBytes = long("allocatedBytes"), isDirectory = long("isDirectory") != 0L,
        depth = long("depth").toInt(), modifiedAt = long("modifiedAt"), fileCount = longOr("fileCount", 0),
    )

    private fun Cursor.cleanupOperation() = CleanupOperationEntity(
        id = string("id"), scanId = string("scanId"), rootPath = string("rootPath"), status = string("status"),
        selectedCount = long("selectedCount").toInt(), plannedBytes = long("plannedBytes"), deletedBytes = longOr("reclaimedBytes", 0),
        createdAt = long("createdAt"), completedAt = nullableLong("completedAt"), message = nullableString("message"),
        reviewId = stringOr("reviewId", ""), scanGeneration = longOr("scanGeneration", 1), planHash = stringOr("planHash", ""),
        expiresAt = longOr("expiresAt", 0), outcomeCertainty = stringOr("outcomeCertainty", CleanupOutcomeCertainty.CERTAIN.name),
    )

    private fun Cursor.cleanupPlanItem() = CleanupPlanItemEntity(
        operationId = string("operationId"), path = string("path"), role = string("role"), itemType = string("itemType"), plannedBytes = long("plannedBytes"),
        expectedDevice = nullableLong("expectedDevice"), expectedMountId = nullableString("expectedMountId"), expectedInode = nullableLong("expectedInode"),
        expectedMode = nullableLong("expectedMode")?.toInt(), expectedStatSizeBytes = nullableLong("expectedStatSizeBytes"), expectedModifiedAt = nullableLong("expectedModifiedAt"),
        expectedTreeDigest = nullableString("expectedTreeDigest"), expectedEntryCount = nullableLong("expectedEntryCount"), expectedSha256 = nullableString("expectedSha256"),
        retainedPath = nullableString("retainedPath"), retainedSha256 = nullableString("retainedSha256"), packageName = nullableString("packageName"),
        createdAt = longOr("createdAt", System.currentTimeMillis()),
    )

    private fun Cursor.index(name: String): Int = getColumnIndex(name)
    private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
    private fun Cursor.stringOr(name: String, default: String): String = index(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) } ?: default
    private fun Cursor.nullableString(name: String): String? = index(name).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.longOr(name: String, default: Long): Long = index(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: default
    private fun Cursor.nullableLong(name: String): Long? = index(name).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    companion object { private const val IMPORT_BATCH = 128 }
}
