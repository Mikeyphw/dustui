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
import java.util.Locale

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

    suspend fun largeFileEntries(scanId: String, preset: LargeFilePreset): List<ScanEntryEntity> = when (preset) {
        LargeFilePreset.LARGE -> dao.filesAtLeastOnce(scanId, 100L * 1024L * 1024L)
        LargeFilePreset.VERY_LARGE -> dao.filesAtLeastOnce(scanId, 500L * 1024L * 1024L)
        LargeFilePreset.HUGE -> dao.filesAtLeastOnce(scanId, 1024L * 1024L * 1024L)
        LargeFilePreset.OLD -> dao.filesAtLeastOnce(scanId, 100L * 1024L * 1024L)
        LargeFilePreset.OLD_ARCHIVES -> dao.archiveFilesOnce(scanId)
        LargeFilePreset.LARGE_VIDEOS -> dao.filesAtLeastOnce(scanId, 500L * 1024L * 1024L)
        LargeFilePreset.INSTALLERS -> dao.installerFilesOnce(scanId)
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
        val plan = CleanupPlan(
            scanId = session.id,
            rootPath = session.rootPath,
            items = entries.map { entry ->
                CleanupPlanItem(
                    path = entry.path,
                    sizeBytes = entry.sizeBytes,
                    isDirectory = entry.isDirectory,
                    modifiedAt = entry.modifiedAt,
                )
            },
        ).normalized()
        return rootClient.reviewCleanup(plan)
    }

    suspend fun executeCleanup(review: CleanupReview): CleanupExecutionResult {
        authorization.authorize().getOrElse { error ->
            return CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = "BLOCKED",
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item ->
                    CleanupItemResult(item.path, item.plannedBytes, 0, "BLOCKED", error.message)
                },
                message = error.message,
            )
        }
        val createdAt = System.currentTimeMillis()
        val plannedBytes = review.accepted.sumOf { it.plannedBytes }
        dao.upsertCleanupOperation(
            CleanupOperationEntity(
                id = review.operationId,
                scanId = review.scanId,
                rootPath = review.rootPath,
                status = "EXECUTING",
                selectedCount = review.accepted.size,
                plannedBytes = plannedBytes,
                createdAt = createdAt,
            ),
        )
        val result = runCatching { rootClient.executeCleanup(review) }.getOrElse { error ->
            CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = "FAILED",
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item ->
                    CleanupItemResult(item.path, item.plannedBytes, 0, "FAILED", error.message)
                },
                message = error.message,
            )
        }
        val summary = deletionSummary(result)
        dao.upsertCleanupOperation(
            CleanupOperationEntity(
                id = review.operationId,
                scanId = review.scanId,
                rootPath = review.rootPath,
                status = result.status,
                selectedCount = review.accepted.size,
                plannedBytes = plannedBytes,
                deletedBytes = result.deletedBytes,
                createdAt = createdAt,
                completedAt = System.currentTimeMillis(),
                message = summary,
            ),
        )
        return result.copy(message = summary)
    }

    private fun deletionSummary(result: CleanupExecutionResult): String {
        val failures = result.items.filter { it.status != "DELETED" }
        if (failures.isEmpty()) return "Deleted ${result.items.size} item(s) permanently"
        val details = failures.take(3).joinToString("; ") { item ->
            "${item.path}: ${item.message ?: item.status.lowercase()}"
        }
        val remainder = if (failures.size > 3) "; and ${failures.size - 3} more" else ""
        return "Deleted ${formatDeletedBytes(result.deletedBytes)}; ${failures.size} item(s) incomplete: $details$remainder"
    }

    private fun formatDeletedBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
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
