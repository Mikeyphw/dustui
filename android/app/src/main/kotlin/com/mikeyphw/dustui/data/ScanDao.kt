package com.mikeyphw.dustui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DuplicateSizeBucket(val sizeBytes: Long, val candidateCount: Int)

@Dao
interface ScanDao {
    @Upsert
    suspend fun upsertSession(session: ScanSessionEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<ScanEntryEntity>)

    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 1")
    fun observeLatestSession(): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_sessions WHERE id = :scanId LIMIT 1")
    suspend fun session(scanId: String): ScanSessionEntity?

    @Query("""
        UPDATE scan_sessions
        SET status = 'INTERRUPTED', completedAt = :completedAt, integrityStatus = 'INTERRUPTED', errorMessage = 'Scan was interrupted before reaching a terminal event'
        WHERE status IN ('STARTING', 'RUNNING', 'CANCELLING')
    """)
    suspend fun interruptActiveScanSessions(completedAt: Long): Int

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId ORDER BY sizeBytes DESC, path COLLATE NOCASE LIMIT :limit")
    fun observeEntries(scanId: String, limit: Int): Flow<List<ScanEntryEntity>>

    @Query("SELECT * FROM scan_sessions WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    suspend fun completedSessions(limit: Int): List<ScanSessionEntity>

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId ORDER BY sizeBytes DESC")
    suspend fun entriesOnce(scanId: String): List<ScanEntryEntity>

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId AND path = :path LIMIT 1")
    suspend fun entryOnce(scanId: String, path: String): ScanEntryEntity?

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId AND path IN (:paths)")
    suspend fun entriesByPathsOnce(scanId: String, paths: List<String>): List<ScanEntryEntity>

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId AND isDirectory = 0 AND sizeBytes >= :minBytes ORDER BY sizeBytes DESC, path COLLATE NOCASE")
    suspend fun filesAtLeastOnce(scanId: String, minBytes: Long): List<ScanEntryEntity>

    @Query("""
        SELECT sizeBytes, COUNT(*) AS candidateCount
        FROM scan_entries
        WHERE scanId = :scanId AND isDirectory = 0 AND sizeBytes >= :minBytes
        GROUP BY sizeBytes
        HAVING COUNT(*) > 1
        ORDER BY (sizeBytes * (COUNT(*) - 1)) DESC, sizeBytes DESC
    """)
    suspend fun duplicateSizeBuckets(scanId: String, minBytes: Long): List<DuplicateSizeBucket>

    @Query("""
        SELECT * FROM scan_entries
        WHERE scanId = :scanId AND isDirectory = 0 AND sizeBytes = :sizeBytes
        ORDER BY path COLLATE NOCASE
        LIMIT :limit
    """)
    suspend fun duplicateFilesBySizeOnce(scanId: String, sizeBytes: Long, limit: Int): List<ScanEntryEntity>

    @Query("""
        SELECT * FROM scan_entries
        WHERE scanId = :scanId AND isDirectory = 0
          AND (LOWER(name) LIKE '%.apk' OR LOWER(name) LIKE '%.apex')
        ORDER BY sizeBytes DESC, path COLLATE NOCASE
    """)
    suspend fun installerFilesOnce(scanId: String): List<ScanEntryEntity>

    @Query("""
        SELECT * FROM scan_entries
        WHERE scanId = :scanId AND isDirectory = 0
          AND (
            LOWER(name) LIKE '%.zip' OR LOWER(name) LIKE '%.7z' OR LOWER(name) LIKE '%.rar' OR
            LOWER(name) LIKE '%.tar' OR LOWER(name) LIKE '%.gz' OR LOWER(name) LIKE '%.zst'
          )
        ORDER BY sizeBytes DESC, path COLLATE NOCASE
    """)
    suspend fun archiveFilesOnce(scanId: String): List<ScanEntryEntity>

    @Query("DELETE FROM scan_entries WHERE scanId = :scanId")
    suspend fun deleteEntries(scanId: String)

    @Query("""
        DELETE FROM scan_entries
        WHERE scanId IN (
            SELECT id FROM scan_sessions
            WHERE status = 'COMPLETED'
            ORDER BY completedAt DESC
            LIMIT -1 OFFSET :keep
        )
    """)
    suspend fun deleteEntriesBeyondCompletedRetention(keep: Int)

    @Query("""
        DELETE FROM scan_sessions
        WHERE id IN (
            SELECT id FROM scan_sessions
            WHERE status = 'COMPLETED'
            ORDER BY completedAt DESC
            LIMIT -1 OFFSET :keep
        )
    """)
    suspend fun deleteSessionsBeyondCompletedRetention(keep: Int)

    @Transaction
    suspend fun pruneCompletedScanRetention(keep: Int) {
        deleteEntriesBeyondCompletedRetention(keep)
        deleteSessionsBeyondCompletedRetention(keep)
    }

    @Upsert
    suspend fun upsertCleanupOperation(operation: CleanupOperationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCleanupOperationIfAbsent(operation: CleanupOperationEntity): Long

    @Query("SELECT * FROM cleanup_operations ORDER BY createdAt DESC LIMIT :limit")
    fun observeCleanupOperations(limit: Int): Flow<List<CleanupOperationEntity>>

    @Query("SELECT * FROM cleanup_operations WHERE id = :operationId LIMIT 1")
    suspend fun cleanupOperation(operationId: String): CleanupOperationEntity?


    @Query("UPDATE cleanup_operations SET status = 'EXECUTING', outcomeCertainty = 'CERTAIN' WHERE id = :operationId AND status = 'REVIEWED'")
    suspend fun claimCleanupOperation(operationId: String): Int

    @Query("DELETE FROM cleanup_plan_items WHERE operationId = :operationId")
    suspend fun deleteCleanupPlanItems(operationId: String)

    @Upsert
    suspend fun upsertCleanupPlanItems(items: List<CleanupPlanItemEntity>)

    @Transaction
    suspend fun replaceCleanupPlanItems(operationId: String, items: List<CleanupPlanItemEntity>) {
        deleteCleanupPlanItems(operationId)
        if (items.isNotEmpty()) upsertCleanupPlanItems(items)
    }

    @Query("SELECT * FROM cleanup_operations WHERE status = 'EXECUTING'")
    suspend fun executingCleanupOperations(): List<CleanupOperationEntity>
}
