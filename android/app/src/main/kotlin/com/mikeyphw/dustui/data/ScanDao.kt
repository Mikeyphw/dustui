package com.mikeyphw.dustui.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId ORDER BY sizeBytes DESC, path COLLATE NOCASE LIMIT :limit")
    fun observeEntries(scanId: String, limit: Int): Flow<List<ScanEntryEntity>>

    @Query("SELECT * FROM scan_sessions WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    suspend fun completedSessions(limit: Int): List<ScanSessionEntity>

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId ORDER BY sizeBytes DESC")
    suspend fun entriesOnce(scanId: String): List<ScanEntryEntity>

    @Query("SELECT * FROM scan_entries WHERE scanId = :scanId AND isDirectory = 0 AND sizeBytes >= :minBytes ORDER BY sizeBytes DESC, path COLLATE NOCASE")
    suspend fun filesAtLeastOnce(scanId: String, minBytes: Long): List<ScanEntryEntity>

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

    @Upsert
    suspend fun upsertCleanupOperation(operation: CleanupOperationEntity)

    @Query("SELECT * FROM cleanup_operations ORDER BY createdAt DESC LIMIT :limit")
    fun observeCleanupOperations(limit: Int): Flow<List<CleanupOperationEntity>>

    @Query("SELECT * FROM cleanup_operations WHERE id = :operationId LIMIT 1")
    suspend fun cleanupOperation(operationId: String): CleanupOperationEntity?

    @Query("SELECT * FROM cleanup_operations WHERE status = 'EXECUTING'")
    suspend fun executingCleanupOperations(): List<CleanupOperationEntity>
}
