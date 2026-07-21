package com.mikeyphw.dustui.data

import android.content.Context
import android.content.Intent
import com.mikeyphw.dustui.scan.ScheduledScanWorker
import com.mikeyphw.dustui.root.RootServiceClient
import com.mikeyphw.dustui.scan.ScanForegroundService
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.flow.Flow

class ScanRepository(
    private val context: Context,
    private val dao: ScanDao,
    private val rootClient: RootServiceClient,
) {
    private val intelligence = EntryIntelligenceResolver(context)
    fun observeLatestSession(): Flow<ScanSessionEntity?> = dao.observeLatestSession()
    fun observeCleanupOperations(): Flow<List<CleanupOperationEntity>> = dao.observeCleanupOperations(50)
    fun observeEntries(scanId: String): Flow<List<ScanEntryEntity>> = dao.observeEntries(scanId, 10_000)
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()
    fun inspect(path: String): EntryIntelligence = intelligence.resolve(path)

    suspend fun snapshotDelta(): SnapshotDelta? {
        val sessions = dao.completedSessions(2)
        val current = sessions.firstOrNull() ?: return null
        val previous = sessions.getOrNull(1)
        return SnapshotAnalyzer.diff(dao.entriesOnce(current.id), previous?.let { dao.entriesOnce(it.id) }.orEmpty(), current.id, previous?.id)
    }

    suspend fun appSummaries(scanId: String): List<AppStorageSummary> {
        val installed = context.packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }
        return SnapshotAnalyzer.appSummaries(dao.entriesOnce(scanId), installed)
    }

    suspend fun advancedAnalysis(scanId: String): AdvancedStorageReport {
        val installed = context.packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }
        return AdvancedStorageAnalyzer.analyze(dao.entriesOnce(scanId), installed, readMountTable())
    }


    suspend fun reviewCleanup(session: ScanSessionEntity, entries: List<ScanEntryEntity>): CleanupReview {
        val plan = CleanupPlan(scanId = session.id, rootPath = session.rootPath, items = entries.map { CleanupPlanItem(it.path, it.sizeBytes) }).normalized()
        return rootClient.reviewCleanup(plan)
    }

    suspend fun executeCleanup(session: ScanSessionEntity, entries: List<ScanEntryEntity>): CleanupExecutionResult {
        val plan = CleanupPlan(scanId = session.id, rootPath = session.rootPath, items = entries.map { CleanupPlanItem(it.path, it.sizeBytes) }).normalized()
        val createdAt = System.currentTimeMillis()
        val plannedBytes = plan.items.sumOf { it.sizeBytes }
        dao.upsertCleanupOperation(CleanupOperationEntity(plan.id, session.id, session.rootPath, "EXECUTING", plan.items.size, plannedBytes, createdAt = createdAt))
        val result = runCatching { rootClient.executeCleanup(plan) }.getOrElse { CleanupExecutionResult(false, plan.id, "FAILED", 0, emptyList(), it.message) }
        if (result.entries.isNotEmpty()) dao.upsertQuarantineEntries(result.entries)
        dao.upsertCleanupOperation(CleanupOperationEntity(plan.id, session.id, session.rootPath, result.status, plan.items.size, plannedBytes, result.reclaimedBytes, createdAt = createdAt, completedAt = System.currentTimeMillis(), message = result.message))
        return result
    }

    suspend fun restoreCleanup(operationId: String): CleanupExecutionResult {
        val operation = dao.cleanupOperation(operationId) ?: return CleanupExecutionResult(false, operationId, "RESTORE_FAILED", 0, emptyList(), "Unknown cleanup operation")
        val result = runCatching { rootClient.restoreCleanup(operationId, operation.rootPath) }.getOrElse { CleanupExecutionResult(false, operationId, "RESTORE_FAILED", 0, emptyList(), it.message) }
        dao.upsertCleanupOperation(operation.copy(status = result.status, completedAt = System.currentTimeMillis(), message = result.message))
        return result
    }

    fun schedule(request: ScanRequest, hours: Long) = ScheduledScanWorker.set(context, request, hours)
    fun cancelSchedule() = ScheduledScanWorker.cancel(context)

    fun startScan(request: ScanRequest) = ScanForegroundService.start(context, request)
    fun cancelScan(scanId: String) = ScanForegroundService.cancel(context, scanId)

    fun exportCsv(session: ScanSessionEntity, entries: List<ScanEntryEntity>) {
        val uri = ExportWriter.writeCsv(context, session, entries)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Export DustUI scan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
