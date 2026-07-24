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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class ScanRepository(
    private val context: Context,
    private val dao: ScanDao,
    private val rootClient: RootServiceClient,
) {
    private val authorization = CleanupAuthorization(context)
    private val intelligence = EntryIntelligenceResolver(context)
    private val duplicateStore = DuplicateAnalysisStore(context)
    private val residueProtectionStore = ResidueProtectionStore(context)
    private val recommendationPreferenceStore = RecommendationPreferenceStore(context)
    @Volatile private var packageInventoryCache: Pair<Long, InstalledPackageInventory>? = null
    fun observeLatestSession(): Flow<ScanSessionEntity?> = dao.observeLatestSession()
    fun observeCleanupOperations(): Flow<List<CleanupOperationEntity>> = dao.observeCleanupOperations(50)
    fun observeEntries(scanId: String): Flow<List<ScanEntryEntity>> = dao.observeEntries(scanId, 10_000)
    suspend fun rootAvailable(): Boolean = rootClient.rootAvailable()
    suspend fun entry(scanId: String, path: String): ScanEntryEntity? = dao.entryOnce(scanId, path)
    suspend fun entriesByPaths(scanId: String, paths: Collection<String>): List<ScanEntryEntity> =
        if (paths.isEmpty()) emptyList() else dao.entriesByPathsOnce(scanId, paths.distinct())


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

    suspend fun appResidueAnalysis(scanId: String): AppResidueAnalysis {
        val inventory = installedPackageInventory()
        val labels = context.packageManager.getInstalledApplications(0).associate { info ->
            info.packageName to context.packageManager.getApplicationLabel(info).toString()
        }
        return AppResidueAnalyzer.analyze(
            scanId = scanId,
            entries = dao.entriesOnce(scanId),
            inventory = inventory,
            protectedPaths = residueProtectionStore.paths(),
            appLabels = labels,
        )
    }

    fun protectResiduePath(path: String) = residueProtectionStore.protect(path)
    fun unprotectResiduePath(path: String) = residueProtectionStore.unprotect(path)

    private suspend fun installedPackageInventory(): InstalledPackageInventory {
        val now = System.currentTimeMillis()
        packageInventoryCache?.takeIf { now - it.first < PACKAGE_INVENTORY_CACHE_MS }?.let { return it.second }
        val currentPackages = context.packageManager.getInstalledPackages(0).mapTo(sortedSetOf()) { it.packageName }
        val inventory = runCatching { rootClient.installedPackages() }
            .getOrElse { error ->
                InstalledPackageInventory.currentUserOnly(
                    currentPackages,
                    error.message ?: "Unable to query package visibility across Android users",
                )
            }
            .let { rootInventory ->
                if (rootInventory.packagesByUser.isEmpty()) {
                    InstalledPackageInventory.currentUserOnly(
                        currentPackages,
                        rootInventory.message ?: "Package visibility across Android users is unavailable",
                    )
                } else {
                    rootInventory.copy(
                        packagesByUser = rootInventory.packagesByUser.toMutableMap().also { byUser ->
                            byUser[0] = byUser[0].orEmpty() + currentPackages
                        },
                    )
                }
            }
        packageInventoryCache = now to inventory
        return inventory
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

    suspend fun cleanupRecommendations(scanId: String): CleanupRecommendationAnalysis {
        val duplicateAnalysis = duplicateStore.load(scanId)
        val residueAnalysis = appResidueAnalysis(scanId)
        recommendationPreferenceStore.pruneDismissedScans(scanId)
        return CleanupRecommendationEngine.analyze(
            scanId = scanId,
            entries = dao.entriesOnce(scanId),
            duplicateAnalysis = duplicateAnalysis,
            residueAnalysis = residueAnalysis,
        )
    }

    fun cleanupRecommendationPreferences(scanId: String): CleanupRecommendationPreferences =
        recommendationPreferenceStore.snapshot(scanId)

    fun dismissCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.dismiss(scanId, recommendationId)

    fun ignoreCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.ignore(scanId, recommendationId)

    fun restoreCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.restore(scanId, recommendationId)

    suspend fun loadDuplicateAnalysis(scanId: String): DuplicateAnalysisSnapshot? = duplicateStore.load(scanId)

    suspend fun cancelDuplicateAnalysis(scanId: String): Boolean = rootClient.cancelDuplicateVerification(scanId)

    suspend fun clearDuplicateAnalysis(scanId: String) = duplicateStore.clear(scanId)

    suspend fun analyzeDuplicates(
        scanId: String,
        minSizeBytes: Long,
        reset: Boolean = false,
        onUpdate: (DuplicateAnalysisSnapshot) -> Unit = {},
    ): DuplicateAnalysisSnapshot {
        if (reset) duplicateStore.clear(scanId)
        val session = requireNotNull(dao.session(scanId)) { "Duplicate analysis scan no longer exists" }
        val existing = duplicateStore.load(scanId)?.takeIf { it.minSizeBytes == minSizeBytes }
        val buckets = dao.duplicateSizeBuckets(scanId, minSizeBytes)
        val bucketSizes = buckets.mapTo(linkedSetOf()) { it.sizeBytes }
        val now = System.currentTimeMillis()
        var snapshot = (existing ?: DuplicateAnalysisSnapshot.empty(scanId, minSizeBytes, now)).copy(
            status = "RUNNING",
            totalBuckets = buckets.size,
            completedBuckets = existing?.completedSizes?.count { it in bucketSizes } ?: 0,
            completedSizes = existing?.completedSizes?.filterTo(linkedSetOf()) { it in bucketSizes }.orEmpty(),
            groups = existing?.groups?.filter { it.sizeBytes in bucketSizes }.orEmpty(),
            currentSizeBytes = null,
            updatedAt = now,
            message = null,
        )
        duplicateStore.save(snapshot)
        onUpdate(snapshot)
        try {
            for (bucket in buckets) {
                currentCoroutineContext().ensureActive()
                if (bucket.sizeBytes in snapshot.completedSizes) continue
                snapshot = snapshot.copy(
                    status = "RUNNING",
                    currentSizeBytes = bucket.sizeBytes,
                    updatedAt = System.currentTimeMillis(),
                    message = "Verifying ${bucket.candidateCount} files sized ${formatDeletedBytes(bucket.sizeBytes)}",
                )
                duplicateStore.save(snapshot)
                onUpdate(snapshot)

                val candidates = dao.duplicateFilesBySizeOnce(scanId, bucket.sizeBytes, MAX_DUPLICATE_BUCKET_PATHS + 1)
                if (candidates.size > MAX_DUPLICATE_BUCKET_PATHS) {
                    snapshot = snapshot.copy(
                        completedBuckets = snapshot.completedBuckets + 1,
                        skippedFiles = snapshot.skippedFiles + bucket.candidateCount,
                        completedSizes = snapshot.completedSizes + bucket.sizeBytes,
                        currentSizeBytes = null,
                        updatedAt = System.currentTimeMillis(),
                        message = "Skipped an unusually large same-size bucket with ${bucket.candidateCount} files",
                    )
                } else {
                    val report = rootClient.verifyDuplicates(scanId, candidates.map { it.path }, bucket.sizeBytes, session.rootPath)
                    val mergedGroups = snapshot.groups.filterNot { it.sizeBytes == bucket.sizeBytes } + report.groups
                    snapshot = snapshot.copy(
                        completedBuckets = snapshot.completedBuckets + 1,
                        checkedFiles = snapshot.checkedFiles + report.checkedFiles,
                        skippedFiles = snapshot.skippedFiles + report.skippedFiles,
                        partialGroups = snapshot.partialGroups + report.partialGroups,
                        bytesHashed = snapshot.bytesHashed + report.bytesHashed,
                        groups = mergedGroups.sortedWith(compareByDescending<VerifiedDuplicateGroup> { it.safeReclaimableBytes }.thenByDescending { it.sizeBytes }.thenBy { it.sha256 }),
                        completedSizes = snapshot.completedSizes + bucket.sizeBytes,
                        currentSizeBytes = null,
                        updatedAt = System.currentTimeMillis(),
                        message = report.message,
                    )
                }
                duplicateStore.save(snapshot)
                onUpdate(snapshot)
            }
            snapshot = snapshot.copy(
                status = "COMPLETED",
                currentSizeBytes = null,
                updatedAt = System.currentTimeMillis(),
                message = if (snapshot.groups.isEmpty()) "No exact duplicate groups were verified" else "Verified ${snapshot.groups.size} exact duplicate group(s)",
            )
            duplicateStore.save(snapshot)
            onUpdate(snapshot)
            return snapshot
        } catch (cancelled: CancellationException) {
            snapshot = snapshot.copy(
                status = "PAUSED",
                currentSizeBytes = null,
                updatedAt = System.currentTimeMillis(),
                message = "Duplicate analysis paused. Resume continues with the next size bucket.",
            )
            duplicateStore.save(snapshot)
            onUpdate(snapshot)
            throw cancelled
        } catch (error: Throwable) {
            snapshot = snapshot.copy(
                status = "FAILED",
                currentSizeBytes = null,
                updatedAt = System.currentTimeMillis(),
                message = error.message ?: error.javaClass.simpleName,
            )
            duplicateStore.save(snapshot)
            onUpdate(snapshot)
            return snapshot
        }
    }

    suspend fun reviewCleanup(
        session: ScanSessionEntity,
        entries: List<ScanEntryEntity>,
        expectedSha256ByPath: Map<String, String> = emptyMap(),
    ): CleanupReview {
        val plan = CleanupPlan(
            scanId = session.id,
            rootPath = session.rootPath,
            items = entries.map { entry ->
                CleanupPlanItem(
                    path = entry.path,
                    sizeBytes = entry.sizeBytes,
                    isDirectory = entry.isDirectory,
                    modifiedAt = entry.modifiedAt,
                    expectedSha256 = expectedSha256ByPath[entry.path],
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

    private companion object {
        const val MAX_DUPLICATE_BUCKET_PATHS = 2_000
        const val PACKAGE_INVENTORY_CACHE_MS = 30_000L
    }
}
