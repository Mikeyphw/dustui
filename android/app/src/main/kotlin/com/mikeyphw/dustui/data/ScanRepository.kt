package com.mikeyphw.dustui.data

import android.content.Context
import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
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
    private val authorization = CleanupAuthorization(context)
    private val intelligence = EntryIntelligenceResolver(context)
    fun observeLatestSession(): Flow<ScanSessionEntity?> = dao.observeLatestSession()
    fun observeCleanupOperations(): Flow<List<CleanupOperationEntity>> = dao.observeCleanupOperations(50)
    fun observeEntries(scanId: String): Flow<List<ScanEntryEntity>> = dao.observeEntries(scanId, 10_000)
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()

    suspend fun deviceCertification(): DeviceCertificationReport {
        val rootConnected = rootClient.rootAvailable()
        val info = if (rootConnected) runCatching { rootClient.serviceInfo() }.getOrNull() else null
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
        return DeviceCertification.evaluate(
            rootConnected = rootConnected && info?.ok == true,
            rootUid = info?.uid ?: -1,
            backend = info?.backend ?: "Unavailable",
            scannerVersion = info?.scanner.orEmpty(),
            selinuxEnforcing = info?.selinuxEnforcing,
            deviceUnlocked = keyguard?.isDeviceLocked != true,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            appStorageWritable = context.filesDir.canWrite(),
            freeBytes = freeBytes,
            batteryOptimizationIgnored = power?.isIgnoringBatteryOptimizations(context.packageName) == true,
        )
    }

    fun inspect(path: String): EntryIntelligence = intelligence.resolve(path)
    suspend fun inspectRootPath(path: String): RootPathInspection = rootClient.inspectPath(path)

    suspend fun snapshotDelta(): SnapshotDelta? {
        val sessions = dao.completedSessions(2)
        val current = sessions.firstOrNull() ?: return null
        val previous = sessions.getOrNull(1)
        return SnapshotAnalyzer.diff(dao.entriesOnce(current.id), previous?.let { dao.entriesOnce(it.id) }.orEmpty(), current.id, previous?.id)
    }


    suspend fun snapshotHistory(limit: Int = 12): List<SnapshotPoint> = dao.completedSessions(limit)
        .asReversed()
        .map { SnapshotPoint(it.completedAt ?: it.startedAt, it.totalBytes) }

    suspend fun appSummaries(scanId: String): List<AppStorageSummary> {
        val installed = context.packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }
        return SnapshotAnalyzer.appSummaries(dao.entriesOnce(scanId), installed)
    }

    suspend fun advancedAnalysis(scanId: String): AdvancedStorageReport {
        val installed = context.packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }
        return AdvancedStorageAnalyzer.analyze(dao.entriesOnce(scanId), installed, readMountTable())
    }

    suspend fun packageProfiles(scanId: String): List<PackageStorageProfile> {
        val entries = dao.entriesOnce(scanId)
        val installed = context.packageManager.getInstalledPackages(0).mapTo(mutableSetOf()) { it.packageName }
        val labels = entries.asSequence()
            .mapNotNull { EntryIntelligenceResolver.packageFromPath(it.path) }
            .distinct()
            .associateWith { packageName -> intelligence.resolve("/data/data/$packageName").appLabel }
            .filterValues { it != null }
            .mapValues { it.value.orEmpty() }
        val duplicates = AdvancedStorageAnalyzer.duplicateCandidates(entries.filterNot { it.isDirectory })
        return PackageIntelligenceAnalyzer.profiles(entries, installed, labels, duplicates)
    }

    suspend fun rootSearch(scanId: String, request: RootFileSearchRequest): List<RootFileSearchResult> =
        RootFileSearchEngine.search(dao.entriesOnce(scanId), request)

    suspend fun moduleAnalysis(scanId: String): RootModuleReport = RootModuleAnalyzer.analyze(dao.entriesOnce(scanId))


    suspend fun verifyDuplicates(group: DuplicateCandidateGroup): DuplicateVerificationReport = rootClient.verifyDuplicates(group.paths, group.sizeBytes)

    suspend fun reviewCleanup(session: ScanSessionEntity, entries: List<ScanEntryEntity>): CleanupReview {
        val plan = CleanupPlan(scanId = session.id, rootPath = session.rootPath, items = entries.map { CleanupPlanItem(it.path, it.sizeBytes) }).normalized()
        return rootClient.reviewCleanup(plan)
    }

    suspend fun executeCleanup(review: CleanupReview): CleanupExecutionResult {
        authorization.authorize().getOrElse { return CleanupExecutionResult(false, review.operationId, "BLOCKED", 0, emptyList(), it.message) }
        val plan = CleanupPlan(
            id = review.operationId,
            scanId = review.scanId,
            rootPath = review.rootPath,
            items = review.accepted,
        ).normalized()
        val createdAt = System.currentTimeMillis()
        val plannedBytes = plan.items.sumOf { it.sizeBytes }
        dao.upsertCleanupOperation(CleanupOperationEntity(plan.id, plan.scanId, plan.rootPath, "EXECUTING", plan.items.size, plannedBytes, createdAt = createdAt))
        val result = runCatching { rootClient.executeCleanup(plan) }.getOrElse { CleanupExecutionResult(false, plan.id, "FAILED", 0, emptyList(), it.message) }
        if (result.entries.isNotEmpty()) dao.upsertQuarantineEntries(result.entries)
        dao.upsertCleanupOperation(CleanupOperationEntity(plan.id, plan.scanId, plan.rootPath, result.status, plan.items.size, plannedBytes, result.reclaimedBytes, createdAt = createdAt, completedAt = System.currentTimeMillis(), message = result.message))
        return result
    }

    suspend fun restoreCleanup(operationId: String): CleanupExecutionResult {
        authorization.authorize().getOrElse { return CleanupExecutionResult(false, operationId, "RESTORE_BLOCKED", 0, emptyList(), it.message) }
        val operation = dao.cleanupOperation(operationId) ?: return CleanupExecutionResult(false, operationId, "RESTORE_FAILED", 0, emptyList(), "Unknown cleanup operation")
        val result = runCatching { rootClient.restoreCleanup(operationId, operation.rootPath) }.getOrElse { CleanupExecutionResult(false, operationId, "RESTORE_FAILED", 0, emptyList(), it.message) }
        dao.upsertCleanupOperation(operation.copy(status = result.status, completedAt = System.currentTimeMillis(), message = result.message))
        return result
    }

    suspend fun recoverInterruptedOperations(): Int {
        val now = System.currentTimeMillis()
        val operations = dao.executingCleanupOperations()
        operations.forEach { dao.upsertCleanupOperation(OperationRecovery.recover(it, now)) }
        return operations.size
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
