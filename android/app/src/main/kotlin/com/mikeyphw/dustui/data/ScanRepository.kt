package com.mikeyphw.dustui.data

import android.content.Context
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.mikeyphw.dustui.scan.ScheduledScanWorker
import com.mikeyphw.dustui.BuildConfig
import com.mikeyphw.dustui.root.RootServiceClient
import com.mikeyphw.dustui.scan.ScanForegroundService
import com.mikeyphw.dustui.scan.ScanRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

class ScanRepository(
    private val context: Context,
    private val dao: ScanDao,
    private val rootClient: RootServiceClient,
    private val scanDiagnosticStore: ScanDiagnosticStore,
) {
    private val authorization = CleanupAuthorization(context)
    private val intelligence = EntryIntelligenceResolver(context)
    private val duplicateStore = DuplicateAnalysisStore(context)
    private val residueProtectionStore = ResidueProtectionStore(context)
    private val recommendationPreferenceStore = RecommendationPreferenceStore(context)
    private val cleanupRuleStore = CleanupRuleStore(context)
    private val storageGrowthPreferenceStore = StorageGrowthPreferenceStore(context)
    private val cleanupReportStore = CleanupReportStore(context)
    private val appStoragePreferenceStore = AppStoragePreferenceStore(context)
    @Volatile private var packageInventoryCache: Pair<Long, InstalledPackageInventory>? = null
    fun observeLatestSession(): Flow<ScanSessionEntity?> = dao.observeLatestSession()
    fun observeCleanupOperations(): Flow<List<CleanupOperationEntity>> = dao.observeCleanupOperations(50)
    fun observeEntries(scanId: String): Flow<List<ScanEntryEntity>> = dao.observeEntries(scanId, 10_000)
    suspend fun rootAvailable(): Boolean = runCatching {
        val info = rootClient.serviceInfo()
        info.ok && info.uid == 0
    }.getOrDefault(false)

    suspend fun rootServiceInfo(): RootServiceClient.RootServiceInfo = rootClient.serviceInfo()
    suspend fun preflightScan(request: ScanRequest): RootServiceClient.ScanRootPreflight = rootClient.preflightScan(request)
    fun scanDiagnostics(scanId: String): ScanDiagnostics? = scanDiagnosticStore.load(scanId)
    suspend fun entry(scanId: String, path: String): ScanEntryEntity? = dao.entryOnce(scanId, path)
    suspend fun entriesByPaths(scanId: String, paths: Collection<String>): List<ScanEntryEntity> =
        if (paths.isEmpty()) emptyList() else dao.entriesByPathsOnce(scanId, paths.distinct())


    suspend fun deviceCertification(): DeviceCertificationReport {
        val info = runCatching { rootClient.serviceInfo() }.getOrNull()
        val rootConnected = info?.ok == true && info.uid == 0
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val freeBytes = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
        return DeviceCertification.evaluate(
            rootConnected = rootConnected,
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

    suspend fun appStorageInspector(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
    ): AppStorageInspectorAnalysis {
        val currentEntries = dao.entriesOnce(scanId)
        val sessions = dao.completedSessions(100)
        val currentIndex = sessions.indexOfFirst { it.id == scanId }
        val previousSession = if (currentIndex >= 0) sessions.getOrNull(currentIndex + 1) else sessions.firstOrNull { it.id != scanId }
        val previousEntries = previousSession?.let { dao.entriesOnce(it.id) }.orEmpty()
        val inventory = installedPackageInventory()
        val packageMetadata = installedPackageMetadata().toMutableMap().also { metadata ->
            inventory.allPackages.forEach { packageName ->
                metadata.putIfAbsent(packageName, AppPackageMetadata(packageName, packageName, installed = true))
            }
        }
        val residue = providedResidueAnalysis ?: runCatching { appResidueAnalysis(scanId) }.getOrNull()
        return AppStorageInspectorEngine.analyze(
            scanId = scanId,
            currentEntries = currentEntries,
            previousScanId = previousSession?.id,
            previousEntries = previousEntries,
            metadata = packageMetadata,
            packageInventoryComplete = inventory.complete,
            duplicateSnapshot = duplicateStore.load(scanId),
            residueAnalysis = residue,
            protectedPaths = residueProtectionStore.paths(),
            ignoredPackages = appStoragePreferenceStore.ignoredPackages(),
        )
    }

    fun setAppStorageRecommendationsIgnored(packageName: String, ignored: Boolean) =
        appStoragePreferenceStore.setRecommendationsIgnored(packageName, ignored)

    fun protectAppStoragePath(path: String) = residueProtectionStore.protect(path)
    fun unprotectAppStoragePath(path: String) = residueProtectionStore.unprotect(path)

    fun openAppSettings(packageName: String): Result<Unit> = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    @Suppress("DEPRECATION")
    fun requestAppUninstall(packageName: String): Result<Unit> = runCatching {
        context.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$packageName".toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun installedPackageMetadata(): Map<String, AppPackageMetadata> {
        val packageManager = context.packageManager
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }
        return packages.associate { info ->
            val applicationInfo = info.applicationInfo
            val label = applicationInfo?.let(packageManager::getApplicationLabel)?.toString().orEmpty().ifBlank { info.packageName }
            info.packageName to AppPackageMetadata(
                packageName = info.packageName,
                appLabel = label,
                installed = true,
                systemApp = ((applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0,
                versionName = info.versionName,
                versionCode = packageVersionCode(info),
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    suspend fun rootSearch(scanId: String, request: RootFileSearchRequest): List<RootFileSearchResult> =
        RootFileSearchEngine.search(dao.entriesOnce(scanId), request)

    suspend fun moduleAnalysis(scanId: String): RootModuleReport {
        val session = dao.session(scanId)
        val entries = dao.entriesOnce(scanId)
        val analyzed = RootModuleAnalyzer.analyze(entries)
        val moduleRoot = "/data/adb/modules"
        val scanRoot = session?.rootPath
        val scanIncludesModules = scanRoot?.let { root ->
            val normalized = root.trimEnd('/').ifBlank { "/" }
            normalized == "/" || moduleRoot == normalized || moduleRoot.startsWith("$normalized/")
        } == true
        val preflight = runCatching {
            rootClient.preflightScan(ScanRequest(rootPath = moduleRoot, maxDepth = 1, maxEntries = 64))
        }.getOrNull()
        val storedDiagnostics = scanDiagnosticStore.load(scanId)
        val diagnostic = when {
            session == null -> ModuleAnalysisDiagnostic(
                status = ModuleDiagnosticStatus.SCAN_FAILED,
                message = "No scan session is available for module analysis.",
                currentScanRoot = null,
                modulePathAccessible = preflight?.ok,
                rootCode = preflight?.code,
            )
            !scanIncludesModules -> ModuleAnalysisDiagnostic(
                status = if (preflight?.ok == false) ModuleDiagnosticStatus.ROOT_ACCESS_FAILED else ModuleDiagnosticStatus.WRONG_SCAN_ROOT,
                message = if (preflight?.ok == false) {
                    "The current scan does not include /data/adb/modules, and the root service cannot open it: ${preflight.message}"
                } else {
                    "Module values come from the current scan. ${session.rootPath} does not include /data/adb/modules."
                },
                currentScanRoot = session.rootPath,
                scanIncludesModules = false,
                modulePathAccessible = preflight?.ok,
                rootCode = preflight?.code,
            )
            session.status == "FAILED" || storedDiagnostics?.hasFailure == true -> ModuleAnalysisDiagnostic(
                status = ModuleDiagnosticStatus.SCAN_FAILED,
                message = session.errorMessage ?: storedDiagnostics?.issues?.lastOrNull { it.severity == ScanDiagnosticSeverity.ERROR }?.message
                    ?: "The module scan failed before usable entries were indexed.",
                currentScanRoot = session.rootPath,
                scanIncludesModules = true,
                modulePathAccessible = preflight?.ok,
                rootCode = preflight?.code,
            )
            storedDiagnostics?.truncated == true -> ModuleAnalysisDiagnostic(
                status = ModuleDiagnosticStatus.INCOMPLETE_RESULTS,
                message = "The module scan reached its result limit. Module totals and conflicts may be incomplete.",
                currentScanRoot = session.rootPath,
                scanIncludesModules = true,
                modulePathAccessible = preflight?.ok,
                rootCode = "RESULT_LIMIT_REACHED",
            )
            analyzed.modules.isEmpty() -> ModuleAnalysisDiagnostic(
                status = ModuleDiagnosticStatus.NO_MODULES_FOUND,
                message = if (preflight?.ok == true) {
                    "The root service can open /data/adb/modules, but the current scan indexed no module files. Review scan warnings, depth, hidden-file, and result-limit settings."
                } else {
                    "No module files were indexed, and root access to /data/adb/modules could not be confirmed${preflight?.message?.let { ": $it" }.orEmpty()}."
                },
                currentScanRoot = session.rootPath,
                scanIncludesModules = true,
                modulePathAccessible = preflight?.ok,
                rootCode = preflight?.code,
            )
            else -> ModuleAnalysisDiagnostic(
                status = ModuleDiagnosticStatus.READY,
                message = "Indexed ${analyzed.modules.size} module(s) from ${session.rootPath}.",
                currentScanRoot = session.rootPath,
                scanIncludesModules = true,
                modulePathAccessible = preflight?.ok,
                rootCode = preflight?.code,
            )
        }
        return analyzed.copy(diagnostic = diagnostic)
    }

    suspend fun cleanupRecommendations(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
    ): CleanupRecommendationAnalysis {
        val duplicateAnalysis = duplicateStore.load(scanId)
        val residueAnalysis = providedResidueAnalysis ?: appResidueAnalysis(scanId)
        recommendationPreferenceStore.pruneDismissedScans(scanId)
        val ignoredPackages = appStoragePreferenceStore.ignoredPackages()
        val analysis = CleanupRecommendationEngine.analyze(
            scanId = scanId,
            entries = dao.entriesOnce(scanId),
            duplicateAnalysis = duplicateAnalysis,
            residueAnalysis = residueAnalysis,
        )
        if (ignoredPackages.isEmpty()) return analysis
        return analysis.copy(items = analysis.items.filterNot { recommendation ->
            val residuePackage = recommendation.id.removePrefix("residue:").takeIf { recommendation.id.startsWith("residue:") }
            val packages = recommendation.paths.mapNotNull(EntryIntelligenceResolver::packageFromPath).toSet()
            residuePackage in ignoredPackages || (packages.size == 1 && packages.single() in ignoredPackages)
        })
    }

    fun cleanupRecommendationPreferences(scanId: String): CleanupRecommendationPreferences =
        recommendationPreferenceStore.snapshot(scanId)

    fun dismissCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.dismiss(scanId, recommendationId)

    fun ignoreCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.ignore(scanId, recommendationId)

    fun restoreCleanupRecommendation(scanId: String, recommendationId: String) =
        recommendationPreferenceStore.restore(scanId, recommendationId)

    fun cleanupRules(): List<CleanupRule> = cleanupRuleStore.rules()

    fun saveCleanupRule(rule: CleanupRule) = cleanupRuleStore.save(rule)

    fun deleteCleanupRule(ruleId: String) = cleanupRuleStore.delete(ruleId)

    fun duplicateCleanupRule(ruleId: String): CleanupRule? = cleanupRuleStore.duplicate(ruleId)

    fun setCleanupRuleEnabled(ruleId: String, enabled: Boolean): Result<CleanupRule> = cleanupRuleStore.setEnabled(ruleId, enabled)

    fun importCleanupRules(raw: String): Result<List<CleanupRule>> = cleanupRuleStore.importJson(raw)

    fun exportCleanupRules() {
        val uri = cleanupRuleStore.writeExportFile(context)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Export DustUI cleanup rules").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun cleanupRulePreview(
        scanId: String,
        rules: List<CleanupRule> = cleanupRuleStore.rules(),
        includeDisabled: Boolean = false,
    ): CleanupRulePreview {
        val session = requireNotNull(dao.session(scanId)) { "Cleanup-rule scan no longer exists" }
        val entries = dao.entriesOnce(scanId)
        val duplicateSnapshot = duplicateStore.load(scanId)
        val duplicatePaths = duplicateSnapshot?.groups.orEmpty().flatMap { group ->
            val threshold = DuplicateThreshold.entries.firstOrNull { it.minBytes == duplicateSnapshot?.minSizeBytes } ?: DuplicateThreshold.ONE_MIB
            DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(threshold = threshold, keepStrategy = DuplicateKeepStrategy.NEWEST)).deletionPaths
        }.toSet()
        val residue = appResidueAnalysis(scanId)
        val residueByPath = residue.groups.flatMap { group -> group.locations.map { it.path to group.confidence } }.toMap()
        return CleanupRuleEngine.preview(scanId, session.rootPath, entries, rules, duplicatePaths, residueByPath, includeDisabled = includeDisabled)
    }

    suspend fun completedStorageGrowthSessions(): List<ScanSessionEntity> =
        dao.completedSessions(storageGrowthPreferenceStore.retentionLimit())

    suspend fun storageGrowth(currentOffset: Int = 0, previousOffset: Int = 1): StorageGrowthAnalysis? {
        val retention = storageGrowthPreferenceStore.retentionLimit()
        val sessions = dao.completedSessions(retention)
        val currentIndex = currentOffset.coerceAtLeast(0)
        val previousIndex = previousOffset.coerceAtLeast(currentIndex + 1)
        val current = sessions.getOrNull(currentIndex) ?: return null
        val previous = sessions.getOrNull(previousIndex) ?: return null
        storageGrowthPreferenceStore.prune(sessions.mapTo(linkedSetOf()) { it.id })
        return StorageGrowthEngine.compare(
            current,
            dao.entriesOnce(current.id),
            previous,
            dao.entriesOnce(previous.id),
            timelineSessions = sessions,
        )
    }

    fun storageGrowthUnderstood(): Set<String> = storageGrowthPreferenceStore.understood()
    fun markStorageGrowthUnderstood(identity: String) = storageGrowthPreferenceStore.mark(identity)
    fun restoreStorageGrowth(identity: String) = storageGrowthPreferenceStore.restore(identity)
    fun storageGrowthRetentionLimit(): Int = storageGrowthPreferenceStore.retentionLimit()

    suspend fun pruneStorageGrowthHistory(keep: Int): Int {
        val bounded = keep.coerceIn(StorageGrowthPreferenceStore.MIN_RETENTION, StorageGrowthPreferenceStore.MAX_RETENTION)
        dao.deleteEntriesBeyondCompletedRetention(bounded)
        dao.deleteSessionsBeyondCompletedRetention(bounded)
        storageGrowthPreferenceStore.setRetentionLimit(bounded)
        val remaining = dao.completedSessions(bounded)
        storageGrowthPreferenceStore.prune(remaining.mapTo(linkedSetOf()) { it.id })
        return remaining.size
    }

    fun cleanupReportLoad(): CleanupReportLoadResult = cleanupReportStore.load()
    fun cleanupReports(): List<CleanupVerificationReport> = cleanupReportStore.reports()

    fun exportCleanupReport(report: CleanupVerificationReport, asJson: Boolean) {
        val uri = if (asJson) cleanupReportStore.exportJson(report) else cleanupReportStore.exportText(report)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (asJson) "application/json" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Export DustUI deletion report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun retryCleanupVerification(operationId: String): CleanupVerificationReport {
        val original = requireNotNull(cleanupReportStore.report(operationId)) { "Deletion report was not found" }
        val observations = inspectCleanupPaths(original.items.filter { it.status != CleanupVerificationStatus.BLOCKED }.map { it.path })
        val rootInspection = runCatching { rootClient.inspectPath(original.rootPath, previewBytes = 0) }.getOrNull()
        val updated = CleanupVerificationEngine.reverify(
            original = original,
            observationsByPath = observations,
            verifiedAt = System.currentTimeMillis(),
            freeBytesAfter = availableBytes(original.rootPath),
            environment = environmentContext(rootInspection),
        )
        withContext(kotlinx.coroutines.Dispatchers.IO) { cleanupReportStore.save(updated) }
        return updated
    }

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
        val startedAt = System.currentTimeMillis()
        val freeBytesBefore = availableBytes(review.rootPath)
        authorization.authorize().getOrElse { error ->
            val blocked = CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = "BLOCKED",
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item -> CleanupItemResult(item.path, item.plannedBytes, 0, "BLOCKED", error.message) },
                message = error.message,
            )
            val saveFailure = runCatching { persistVerificationReport(review, blocked, startedAt, freeBytesBefore) }.exceptionOrNull()
            return if (saveFailure == null) blocked else blocked.copy(
                message = listOfNotNull(blocked.message, "Verification report could not be saved: ${saveFailure.message ?: saveFailure.javaClass.simpleName}").joinToString(" · "),
            )
        }
        val plannedBytes = review.accepted.sumOf { it.plannedBytes }
        dao.upsertCleanupOperation(
            CleanupOperationEntity(
                id = review.operationId, scanId = review.scanId, rootPath = review.rootPath, status = "EXECUTING",
                selectedCount = review.accepted.size, plannedBytes = plannedBytes, createdAt = startedAt,
            ),
        )
        val result = runCatching { rootClient.executeCleanup(review) }.getOrElse { error ->
            CleanupExecutionResult(
                ok = false, operationId = review.operationId, status = "FAILED", deletedBytes = 0, failedCount = review.accepted.size,
                items = review.accepted.map { item -> CleanupItemResult(item.path, item.plannedBytes, 0, "FAILED", error.message) },
                message = error.message,
            )
        }
        val summary = deletionSummary(result)
        dao.upsertCleanupOperation(
            CleanupOperationEntity(
                id = review.operationId, scanId = review.scanId, rootPath = review.rootPath, status = result.status,
                selectedCount = review.accepted.size, plannedBytes = plannedBytes, deletedBytes = result.deletedBytes,
                createdAt = startedAt, completedAt = System.currentTimeMillis(), message = summary,
            ),
        )
        val saveFailure = runCatching { persistVerificationReport(review, result, startedAt, freeBytesBefore) }.exceptionOrNull()
        val finalMessage = if (saveFailure == null) summary else "$summary · Verification report could not be saved: ${saveFailure.message ?: saveFailure.javaClass.simpleName}"
        return result.copy(message = finalMessage)
    }

    private suspend fun persistVerificationReport(
        review: CleanupReview,
        result: CleanupExecutionResult,
        startedAt: Long,
        freeBytesBefore: Long?,
    ) {
        val observationsByPath = inspectCleanupPaths(review.accepted.map { it.path })
        val rootInspection = runCatching { rootClient.inspectPath(review.rootPath, previewBytes = 0) }.getOrNull()
        val report = CleanupVerificationEngine.build(
            review = review,
            result = result,
            startedAt = startedAt,
            completedAt = System.currentTimeMillis(),
            observationsByPath = observationsByPath,
            freeBytesBefore = freeBytesBefore,
            freeBytesAfter = availableBytes(review.rootPath),
            appVersion = BuildConfig.VERSION_NAME,
            environment = environmentContext(rootInspection),
        )
        withContext(Dispatchers.IO) { cleanupReportStore.save(report) }
    }

    private suspend fun inspectCleanupPaths(paths: Collection<String>): Map<String, CleanupPathObservation> = paths.associateWith { path ->
        runCatching { rootClient.inspectPath(path, previewBytes = 0) }
            .fold(
                onSuccess = { inspection ->
                    when {
                        inspection.ok -> CleanupPathObservation(CleanupPathPresence.PRESENT)
                        inspection.message.isMissingPathMessage() -> CleanupPathObservation(CleanupPathPresence.MISSING)
                        else -> CleanupPathObservation(CleanupPathPresence.UNKNOWN, inspection.message ?: "Root service could not verify the path")
                    }
                },
                onFailure = { error -> CleanupPathObservation(CleanupPathPresence.UNKNOWN, error.message ?: error.javaClass.simpleName) },
            )
    }

    private fun environmentContext(rootInspection: RootPathInspection?): CleanupEnvironmentContext = CleanupEnvironmentContext(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        sdkInt = Build.VERSION.SDK_INT,
        filesystem = rootInspection?.filesystem,
        mountPoint = rootInspection?.mountPoint,
        mountSource = rootInspection?.mountSource,
        readOnly = rootInspection?.readOnly,
    )

    private fun availableBytes(path: String): Long? = runCatching { StatFs(path).availableBytes }.getOrNull()

    private fun String?.isMissingPathMessage(): Boolean {
        val value = this?.lowercase(Locale.ROOT) ?: return false
        return "no such file" in value || "enoent" in value || "path no longer exists" in value
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

    fun exportDebugBundle(request: DebugExportRequest): DebugExportResult {
        val result = DebugExportBundleWriter.write(context, request)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Export DustUI debug bundle").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return result
    }

    private companion object {
        const val MAX_DUPLICATE_BUCKET_PATHS = 2_000
        const val PACKAGE_INVENTORY_CACHE_MS = 30_000L
    }
}
