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
import android.os.UserHandle
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap


class ScanRepository(
    private val context: Context,
    private val domainStore: GoDomainStore,
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
    private val packageEvidenceAttemptedGeneration = mutableMapOf<String, Long>()
    private val authorityStatuses = ConcurrentHashMap<String, GoDomainAuthorityStatus>()
    fun domainAuthorityStatus(scanId: String, domain: String): GoDomainAuthorityStatus? = authorityStatuses["$scanId|$domain"]
    fun observeLatestSession(): Flow<ScanSessionEntity?> = domainStore.observeLatestSession()
    fun observeCleanupOperations(): Flow<List<CleanupOperationEntity>> = domainStore.observeCleanupOperations(50)
    suspend fun browsePage(scanId: String, generation: Long, request: ScanBrowseRequest): ScanBrowsePage =
        domainStore.browsePage(scanId, generation, request)
    suspend fun rootAvailable(): Boolean = runCatching {
        val info = rootClient.serviceInfo()
        info.ok && info.uid == 0
    }.getOrDefault(false)

    suspend fun rootServiceInfo(): RootServiceClient.RootServiceInfo = rootClient.serviceInfo()
    suspend fun preflightScan(request: ScanRequest): RootServiceClient.ScanRootPreflight = rootClient.preflightScan(request)
    fun scanDiagnostics(scanId: String): ScanDiagnostics? = scanDiagnosticStore.load(scanId)
    suspend fun entry(scanId: String, path: String): ScanEntryEntity? = domainStore.entryOnce(scanId, path)
    suspend fun entriesByPaths(scanId: String, paths: Collection<String>): List<ScanEntryEntity> {
        val distinct = paths.distinct()
        if (distinct.isEmpty()) return emptyList()
        return distinct.chunked(SQLITE_SAFE_BIND_BATCH).flatMap { chunk -> domainStore.entriesByPathsOnce(scanId, chunk) }
    }


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

    private fun authorityOptions(): JSONObject {
        val labels = installedPackageMetadata().mapValues { it.value.appLabel }
        return JSONObject()
            .put("protectedPaths", JSONArray(residueProtectionStore.paths().sorted()))
            .put("ignoredPackages", JSONArray(appStoragePreferenceStore.ignoredPackages().sorted()))
            .put("appLabels", JSONObject(labels))
    }

    private suspend fun authorityData(scanId: String, domain: String, packageEvidenceRequired: Boolean): JSONObject {
        val session = requireNotNull(domainStore.session(scanId)) { "Authority scan no longer exists: $scanId" }
        if (packageEvidenceRequired && packageEvidenceAttemptedGeneration[scanId] != session.generation) {
            packageEvidenceAttemptedGeneration[scanId] = session.generation
            runCatching { refreshPackageEvidence(scanId, session.generation) }
        }
        val response = domainStore.typedAuthority(domain, scanId, session.generation, authorityOptions())
        authorityStatuses["$scanId|$domain"] = response.status()
        return response.dataObject()
    }

    fun inspect(path: String): EntryIntelligence = intelligence.resolve(path)
    suspend fun inspectRootPath(path: String): RootPathInspection = rootClient.inspectPath(path)

    suspend fun snapshotDelta(): SnapshotDelta? {
        val current = domainStore.completedSessions(1).firstOrNull() ?: return null
        return GoProductAuthorityMapper.snapshotDelta(authorityData(current.id, "cleanup-intelligence", true).getJSONObject("snapshotDelta"))
    }

    suspend fun snapshotDelta(scanId: String, currentEntries: List<ScanEntryEntity>): SnapshotDelta? =
        GoProductAuthorityMapper.snapshotDelta(authorityData(scanId, "cleanup-intelligence", true).getJSONObject("snapshotDelta"))


    suspend fun snapshotHistory(limit: Int = 12): List<SnapshotPoint> = domainStore.completedSessions(limit)
        .asReversed()
        .map { SnapshotPoint(it.completedAt ?: it.startedAt, it.totalBytes) }

    suspend fun appSummaries(scanId: String): List<AppStorageSummary> =
        GoProductAuthorityMapper.appSummaries(authorityData(scanId, "package-storage", true).getJSONArray("appSummaries"))

    suspend fun appSummaries(scanId: String, entries: List<ScanEntryEntity>): List<AppStorageSummary> = appSummaries(scanId)

    suspend fun appResidueAnalysis(scanId: String): AppResidueAnalysis =
        GoProductAuthorityMapper.residue(authorityData(scanId, "residue", true))

    suspend fun appResidueAnalysis(scanId: String, entries: List<ScanEntryEntity>): AppResidueAnalysis = appResidueAnalysis(scanId)


    fun protectResiduePath(path: String) = residueProtectionStore.protect(path)
    fun unprotectResiduePath(path: String) = residueProtectionStore.unprotect(path)

    suspend fun refreshPackageEvidence(scanId: String, generation: Long): JSONObject {
        val inventory = installedPackageInventory()
        val packageManager = context.packageManager
        val currentUser = UserHandle.myUserId()
        val currentInfos: Map<String, PackageInfo> = installedPackageInfos().associateBy { it.packageName }
        val records = JSONArray()
        val degradation = linkedSetOf<String>()
        if (!inventory.complete) degradation += (inventory.message ?: "multi_user_inventory_partial")

        inventory.packagesByUser.toSortedMap().forEach { (userId, packages) ->
            packages.toSortedSet().forEach { packageName ->
                val info = if (userId == currentUser || (userId == 0 && currentUser == 0)) currentInfos[packageName] else null
                val app = info?.applicationInfo
                val reasons = JSONArray()
                val completeness = if (app != null) {
                    reasons.put("package_byte_counts_not_available_in_platform_collector")
                    reasons.put("external_adoptable_secondary_storage_not_fully_observable")
                    "partial"
                } else {
                    reasons.put("package_visible_only_through_root_user_inventory")
                    "partial"
                }
                val record = JSONObject()
                    .put("packageName", packageName)
                    .put("userId", userId)
                    .put("installed", true)
                    .put("archived", false)
                    .put("systemApp", app?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false)
                    .put("enabledState", when (app?.enabled) { true -> "enabled"; false -> "disabled"; null -> "unknown" })
                    .put("collectionCompleteness", completeness)
                    .put("degradationReasons", reasons)
                    .put("sourceDirs", jsonArrayOfNotBlank(app?.sourceDir, app?.publicSourceDir))
                    .put("splitDirs", JSONArray(app?.splitSourceDirs?.filter(String::isNotBlank).orEmpty()))
                    .put("ceDataDirs", jsonArrayOfNotBlank(if (Build.VERSION.SDK_INT >= 24) app?.credentialProtectedDataDir else app?.dataDir))
                    .put("deDataDirs", jsonArrayOfNotBlank(if (Build.VERSION.SDK_INT >= 24) app?.deviceProtectedDataDir else null))
                    .put("externalDataDirs", JSONArray())
                    .put("adoptableDataDirs", JSONArray())
                    .put("secondaryStorageDirs", JSONArray())
                    .put("cacheDirs", JSONArray())
                    .put("nativeLibraryDirs", jsonArrayOfNotBlank(app?.nativeLibraryDir))
                    .put("codeBytes", JSONObject().put("known", false))
                    .put("dataBytes", JSONObject().put("known", false))
                    .put("cacheBytes", JSONObject().put("known", false))
                    .put("nativeBytes", JSONObject().put("known", false))
                records.put(record)
            }
        }
        if (records.length() == 0) {
            // PackageManager visibility for the app's own user is the minimum supported evidence source.
            currentInfos.keys.sorted().forEach { packageName ->
                val app = currentInfos.getValue(packageName).applicationInfo
                records.put(
                    JSONObject()
                        .put("packageName", packageName)
                        .put("userId", currentUser)
                        .put("installed", true)
                        .put("archived", false)
                        .put("systemApp", app?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false)
                        .put("enabledState", when (app?.enabled) { true -> "enabled"; false -> "disabled"; null -> "unknown" })
                        .put("collectionCompleteness", "partial")
                        .put("degradationReasons", JSONArray().put("root_multi_user_inventory_unavailable"))
                        .put("sourceDirs", jsonArrayOfNotBlank(app?.sourceDir, app?.publicSourceDir))
                        .put("splitDirs", JSONArray(app?.splitSourceDirs?.filter(String::isNotBlank).orEmpty()))
                        .put("ceDataDirs", jsonArrayOfNotBlank(if (Build.VERSION.SDK_INT >= 24) app?.credentialProtectedDataDir else app?.dataDir))
                        .put("deDataDirs", jsonArrayOfNotBlank(if (Build.VERSION.SDK_INT >= 24) app?.deviceProtectedDataDir else null))
                        .put("externalDataDirs", JSONArray())
                        .put("adoptableDataDirs", JSONArray())
                        .put("secondaryStorageDirs", JSONArray())
                        .put("cacheDirs", JSONArray())
                        .put("nativeLibraryDirs", jsonArrayOfNotBlank(app?.nativeLibraryDir))
                        .put("codeBytes", JSONObject().put("known", false))
                        .put("dataBytes", JSONObject().put("known", false))
                        .put("cacheBytes", JSONObject().put("known", false))
                        .put("nativeBytes", JSONObject().put("known", false)),
                )
            }
            degradation += "root_multi_user_inventory_unavailable"
        }
        if (records.length() == 0) {
            return JSONObject().put("completeness", "unavailable").put("degradationReasons", JSONArray().put("package_inventory_empty"))
        }
        val snapshot = JSONObject()
            .put("snapshotId", "pkg-$scanId-$generation")
            .put("generation", generation.coerceAtLeast(1L))
            .put("collector", "android-platform-adapter")
            .put("androidUserSerialScope", JSONArray(inventory.packagesByUser.keys.sorted()))
            .put("records", records)
            .put("completeness", if (degradation.isEmpty()) "partial" else "partial")
            .put("degradationReasons", JSONArray(degradation.toList()))
        return domainStore.authorityRequest(
            "domain.package.snapshot.replace",
            generation.coerceAtLeast(1L),
            JSONObject().put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA).put("scanId", scanId).put("snapshot", snapshot).put("collector", "android-platform-adapter"),
        ).getJSONObject("snapshot")
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfos(): List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getInstalledPackages(0)
    }

    private fun jsonArrayOfNotBlank(vararg values: String?): JSONArray = JSONArray(
        values.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct(),
    )

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

    suspend fun largeFilePage(scanId: String, generation: Long, filter: LargeFileFilter): LargeFilePage {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val ninetyDaysAgo = nowSeconds - 90L * 24L * 60L * 60L
        val request = ScanBrowseRequest(
            query = filter.query,
            entryType = "FILES",
            minBytes = when (filter.preset) {
                LargeFilePreset.LARGE, LargeFilePreset.OLD -> 100L * 1024L * 1024L
                LargeFilePreset.VERY_LARGE, LargeFilePreset.LARGE_VIDEOS -> 500L * 1024L * 1024L
                LargeFilePreset.HUGE -> 1024L * 1024L * 1024L
                LargeFilePreset.OLD_ARCHIVES, LargeFilePreset.INSTALLERS -> 0L
            },
            fileCategories = buildList {
                filter.category?.let(::add)
                when (filter.preset) {
                    LargeFilePreset.OLD_ARCHIVES -> add("Archives")
                    LargeFilePreset.LARGE_VIDEOS -> add("Video")
                    LargeFilePreset.INSTALLERS -> add("Android packages")
                    else -> Unit
                }
            }.distinct(),
            modifiedBefore = when (filter.preset) {
                LargeFilePreset.OLD, LargeFilePreset.OLD_ARCHIVES -> ninetyDaysAgo
                else -> null
            },
            sort = when (filter.sort) {
                LargeFileSort.LARGEST -> "SIZE"
                LargeFileSort.OLDEST -> "OLDEST"
                LargeFileSort.NEWEST -> "NEWEST"
                LargeFileSort.PATH, LargeFileSort.PACKAGE -> "PATH"
            },
            descending = filter.sort !in setOf(LargeFileSort.OLDEST, LargeFileSort.PATH, LargeFileSort.PACKAGE),
            offset = filter.page.coerceAtLeast(0) * filter.pageSize.coerceIn(10, 200),
            limit = filter.pageSize.coerceIn(10, 200),
        )
        return LargeFileWorkbenchEngine.fromAuthorityPage(domainStore.browsePage(scanId, generation, request), filter)
    }

    suspend fun advancedAnalysis(scanId: String): AdvancedStorageReport =
        GoProductAuthorityMapper.advanced(authorityData(scanId, "cleanup-intelligence", true).getJSONObject("advanced"))

    suspend fun advancedAnalysis(scanId: String, entries: List<ScanEntryEntity>): AdvancedStorageReport = advancedAnalysis(scanId)

    suspend fun packageProfiles(scanId: String): List<PackageStorageProfile> =
        GoProductAuthorityMapper.packageProfiles(authorityData(scanId, "package-storage", true).getJSONArray("packageProfiles"))

    suspend fun appStorageInspector(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
    ): AppStorageInspectorAnalysis = GoProductAuthorityMapper.appStorage(authorityData(scanId, "package-storage", true).getJSONObject("appStorage"))

    suspend fun appStorageInspector(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
        currentEntries: List<ScanEntryEntity>,
    ): AppStorageInspectorAnalysis = appStorageInspector(scanId, providedResidueAnalysis)


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

    suspend fun rootSearch(scanId: String, request: RootFileSearchRequest): RootFileSearchPage =
        domainStore.rootSearch(scanId, request)

    suspend fun moduleAnalysis(scanId: String): RootModuleReport = moduleAnalysis(scanId, emptyList())

    suspend fun moduleAnalysis(scanId: String, entries: List<ScanEntryEntity>): RootModuleReport {
        return GoProductAuthorityMapper.modules(authorityData(scanId, "modules", false))
    }

    suspend fun cleanupRecommendations(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
    ): CleanupRecommendationAnalysis = GoProductAuthorityMapper.recommendations(authorityData(scanId, "recommendations", true))

    suspend fun cleanupRecommendations(
        scanId: String,
        providedResidueAnalysis: AppResidueAnalysis? = null,
        entries: List<ScanEntryEntity>,
    ): CleanupRecommendationAnalysis = cleanupRecommendations(scanId, providedResidueAnalysis)

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
        val encodedRules = JSONArray(rules.map { rule ->
            JSONObject()
                .put("id", rule.id).put("name", rule.name).put("enabled", rule.enabled).put("imported", rule.imported)
                .put("reviewedAt", rule.reviewedAt ?: JSONObject.NULL).put("schemaVersion", rule.schemaVersion)
                .put("conditions", JSONObject()
                    .put("includePaths", JSONArray(rule.conditions.includePaths))
                    .put("excludePaths", JSONArray(rule.conditions.excludePaths))
                    .put("extensions", JSONArray(rule.conditions.extensions))
                    .put("minimumBytes", rule.conditions.minimumBytes)
                    .put("maximumBytes", rule.conditions.maximumBytes ?: JSONObject.NULL)
                    .put("olderThanDays", rule.conditions.olderThanDays ?: JSONObject.NULL)
                    .put("category", rule.conditions.category ?: JSONObject.NULL)
                    .put("packageName", rule.conditions.packageName ?: JSONObject.NULL)
                    .put("regularExpression", rule.conditions.regularExpression ?: JSONObject.NULL)
                    .put("maximumDepth", rule.conditions.maximumDepth ?: JSONObject.NULL)
                    .put("entryType", rule.conditions.entryType.name)
                    .put("duplicateOnly", rule.conditions.duplicateOnly)
                    .put("residueConfidence", rule.conditions.residueConfidence.name))
        })
        val payload = domainStore.authorityRequest(
            "domain.authority.rules.preview",
            domainStore.session(scanId)?.generation ?: 1L,
            JSONObject()
                .put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA)
                .put("domain", "rules")
                .put("scanId", scanId)
                .put("rules", encodedRules)
                .put("includeDisabled", includeDisabled),
        )
        return GoProductAuthorityMapper.rulePreview(payload.getJSONObject("data"))
    }


    suspend fun completedStorageGrowthSessions(): List<ScanSessionEntity> =
        domainStore.completedSessions(storageGrowthPreferenceStore.retentionLimit())

    suspend fun storageGrowth(currentOffset: Int = 0, previousOffset: Int = 1): StorageGrowthAnalysis? {
        val sessions = domainStore.completedSessions(storageGrowthPreferenceStore.retentionLimit())
        val current = sessions.getOrNull(currentOffset.coerceAtLeast(0)) ?: return null
        storageGrowthPreferenceStore.prune(sessions.mapTo(linkedSetOf()) { it.id })
        return GoProductAuthorityMapper.growth(authorityData(current.id, "growth", false))
    }


    fun storageGrowthUnderstood(): Set<String> = storageGrowthPreferenceStore.understood()
    fun markStorageGrowthUnderstood(identity: String) = storageGrowthPreferenceStore.mark(identity)
    fun restoreStorageGrowth(identity: String) = storageGrowthPreferenceStore.restore(identity)
    fun storageGrowthRetentionLimit(): Int = storageGrowthPreferenceStore.retentionLimit()

    suspend fun pruneStorageGrowthHistory(keep: Int): Int {
        val bounded = keep.coerceIn(StorageGrowthPreferenceStore.MIN_RETENTION, StorageGrowthPreferenceStore.MAX_RETENTION)
        domainStore.pruneCompletedScanRetention(bounded)
        storageGrowthPreferenceStore.setRetentionLimit(bounded)
        val remaining = domainStore.completedSessions(bounded)
        storageGrowthPreferenceStore.prune(remaining.mapTo(linkedSetOf()) { it.id })
        return remaining.size
    }

    suspend fun cleanupReportLoad(): CleanupReportLoadResult = runCatching {
        val payload = domainStore.authorityRequest(
            "domain.cleanup.verification.list",
            generation = 1L,
            payload = JSONObject().put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA).put("domain", "cleanup-verification").put("limit", 100),
        )
        require(payload.getString("schema") == GoDomainStore.DOMAIN_AUTHORITY_RESPONSE_SCHEMA) { "Go verification-list response schema mismatch" }
        val reports = payload.optJSONArray("records")
            ?.let { array -> (0 until array.length()).map { index -> GoProductAuthorityMapper.verificationReport(array.getJSONObject(index).getJSONObject("report")) } }
            .orEmpty()
        CleanupReportLoadResult(reports = reports, diagnostics = emptyList())
    }.getOrElse { failure ->
        CleanupReportLoadResult(
            reports = emptyList(),
            diagnostics = listOf("Go deletion-report authority unavailable: ${failure.message ?: failure.javaClass.simpleName}"),
        )
    }

    fun exportCleanupReport(report: CleanupVerificationReport, asJson: Boolean) {
        val uri = if (asJson) cleanupReportStore.exportJson(report) else cleanupReportStore.exportText(report)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = if (asJson) "application/json" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Export DustUI deletion report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun retryCleanupVerification(operationId: String): CleanupVerificationReport {
        val source = domainStore.authorityRequest(
            "domain.cleanup.verification.get",
            generation = 1L,
            payload = JSONObject().put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA).put("domain", "cleanup-verification").put("operationId", operationId),
        )
        val originalObject = source.optJSONObject("report")
        val original = requireNotNull(originalObject?.let(GoProductAuthorityMapper::verificationReport)) { "Deletion report was not found" }
        val observations = inspectCleanupPaths(original.items.filter { it.status != CleanupVerificationStatus.BLOCKED }.map { it.path })
        val rootInspection = runCatching { rootClient.inspectPath(original.rootPath, previewBytes = 0) }.getOrNull()
        val generation = domainStore.session(original.scanId)?.generation ?: 1L
        val payload = domainStore.authorityRequest(
            "domain.cleanup.verification.reverify",
            generation,
            JSONObject()
                .put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA)
                .put("domain", "cleanup-verification")
                .put("scanId", original.scanId)
                .put("original", verificationReportJson(original))
                .put("observations", verificationObservationsJson(observations))
                .put("verifiedAt", System.currentTimeMillis())
                .put("freeBytesAfter", availableBytes(original.rootPath) ?: JSONObject.NULL)
                .put("environment", verificationEnvironmentJson(environmentContext(rootInspection))),
        )
        return GoProductAuthorityMapper.verificationReport(payload.getJSONObject("report"))
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
        val session = requireNotNull(domainStore.session(scanId)) { "Duplicate analysis scan no longer exists" }
        val existing = duplicateStore.load(scanId)?.takeIf { it.minSizeBytes == minSizeBytes && it.scanGeneration == session.generation }
        val buckets = domainStore.duplicateSizeBuckets(scanId, minSizeBytes)
        val bucketSizes = buckets.mapTo(linkedSetOf()) { it.sizeBytes }
        val now = System.currentTimeMillis()
        var snapshot = (existing ?: DuplicateAnalysisSnapshot.empty(scanId, minSizeBytes, now, session.generation)).copy(
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

                val candidates = domainStore.duplicateFilesBySizeOnce(scanId, bucket.sizeBytes, MAX_DUPLICATE_BUCKET_PATHS + 1)
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
                    persistDuplicateAuthority(scanId, session, candidates, report)
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

    private suspend fun persistDuplicateAuthority(
        scanId: String,
        session: ScanSessionEntity,
        candidates: List<ScanEntryEntity>,
        report: DuplicateVerificationReport,
    ) {
        val entryByPath = candidates.associateBy { it.path }
        val sourceContextHash = "scan:$scanId:${session.generation}:${session.integrityStatus}:${session.rootPath}"
        val inputs = JSONArray()
        report.groups.forEach { group ->
            group.pathRecords.forEach { path ->
                val entry = entryByPath[path.path]
                inputs.put(
                    JSONObject()
                        .put("path", path.path)
                        .put("scanId", scanId)
                        .put("scanGeneration", session.generation)
                        .put("sourceContextHash", sourceContextHash)
                        .put("expectedIdentity", path.copyKey)
                        .put("contentHash", group.sha256)
                        .put("logicalSizeBytes", group.sizeBytes)
                        .put("allocatedSizeBytes", entry?.allocatedBytes ?: group.sizeBytes)
                        .put("protectedState", if (path.autoDeleteEligible) "unprotected" else "protected")
                        .put("retainedCopyEligible", true),
                )
            }
        }
        domainStore.authorityRequest(
            "domain.authority.duplicates.replace",
            session.generation,
            JSONObject()
                .put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA)
                .put("scanId", scanId)
                .put("generation", session.generation)
                .put("inputs", inputs),
        )
    }

    suspend fun reviewCleanup(
        session: ScanSessionEntity,
        entries: List<ScanEntryEntity>,
        expectedSha256ByPath: Map<String, String> = emptyMap(),
        retainedEvidenceByPath: Map<String, Pair<String, String>> = emptyMap(),
        packageNameByPath: Map<String, String> = emptyMap(),
    ): CleanupReview {
        cleanupScanBlockReason(session, entries)?.let { reason ->
            throw IllegalStateException(reason)
        }
        val plan = CleanupPlan(
            scanId = session.id,
            rootPath = session.rootPath,
            scanGeneration = session.generation,
            items = entries.map { entry ->
                CleanupPlanItem(
                    path = entry.path,
                    sizeBytes = entry.sizeBytes,
                    isDirectory = entry.isDirectory,
                    modifiedAt = entry.modifiedAt,
                    expectedSha256 = expectedSha256ByPath[entry.path],
                    retainedPath = retainedEvidenceByPath[entry.path]?.first,
                    retainedSha256 = retainedEvidenceByPath[entry.path]?.second,
                    packageName = packageNameByPath[entry.path],
                )
            },
        ).normalized()
        return rootClient.reviewCleanup(plan)
    }

    private fun cleanupScanBlockReason(session: ScanSessionEntity, entries: List<ScanEntryEntity>): String? {
        val completeIntegrity = session.integrityStatus == ScanIntegrityStatus.COMPLETE.name
        return when {
            session.status != "COMPLETED" -> "Cleanup requires a completed scan; current status is ${session.status}"
            session.truncated -> "Cleanup is blocked because the source scan is truncated"
            !completeIntegrity -> "Cleanup requires COMPLETE scan integrity; current integrity is ${session.integrityStatus}"
            session.observedEntryCount > 0L && session.persistedEntryCount > 0L && session.observedEntryCount != session.persistedEntryCount ->
                "Cleanup is blocked because scan persistence is stale: observed ${session.observedEntryCount}, persisted ${session.persistedEntryCount}"
            entries.isEmpty() -> "Cleanup requires at least one selected entry from the source scan"
            entries.any { it.scanId != session.id } -> "Cleanup selection contains entries from a different scan"
            else -> null
        }
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
        if (System.currentTimeMillis() - review.reviewedAt > CLEANUP_REVIEW_TTL_MS) {
            return CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = CleanupOperationStatus.BLOCKED.name,
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item -> CleanupItemResult(item.path, item.plannedBytes, 0, "BLOCKED", "Cleanup review expired; create a new plan") },
                message = "Cleanup review expired; create a new plan",
            )
        }
        val plannedBytes = review.accepted.sumOf { it.plannedBytes }
        val insertedOperation = domainStore.insertCleanupOperationIfAbsent(
            CleanupOperationEntity(
                id = review.operationId,
                scanId = review.scanId,
                rootPath = review.rootPath,
                status = CleanupOperationStatus.REVIEWED.name,
                selectedCount = review.accepted.size,
                plannedBytes = plannedBytes,
                createdAt = startedAt,
                reviewId = review.operationId,
                scanGeneration = review.scanGeneration,
                expiresAt = review.reviewedAt + CLEANUP_REVIEW_TTL_MS,
            ),
        )
        if (insertedOperation != -1L) {
            domainStore.replaceCleanupPlanItems(
                review.operationId,
                review.accepted.map { item ->
                    CleanupPlanItemEntity(
                        operationId = review.operationId,
                        path = item.path,
                        role = "DELETE",
                        itemType = if (item.isDirectory) "DIRECTORY" else "FILE",
                        plannedBytes = item.plannedBytes,
                        expectedDevice = item.fingerprint.device,
                        expectedMountId = item.fingerprint.mountId,
                        expectedInode = item.fingerprint.inode,
                        expectedMode = item.fingerprint.mode,
                        expectedStatSizeBytes = item.fingerprint.statSizeBytes,
                        expectedModifiedAt = item.fingerprint.modifiedAt,
                        expectedTreeDigest = item.fingerprint.treeDigest,
                        expectedEntryCount = item.fingerprint.entryCount,
                        expectedSha256 = item.fingerprint.contentSha256,
                        retainedPath = item.retainedPath,
                        retainedSha256 = item.retainedSha256,
                        packageName = item.packageName,
                        createdAt = startedAt,
                    )
                },
            )
        }
        val claimed = domainStore.claimCleanupOperation(review.operationId)
        if (claimed != 1) {
            return CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = CleanupOperationStatus.BLOCKED.name,
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item -> CleanupItemResult(item.path, item.plannedBytes, 0, "BLOCKED", "Cleanup operation was already claimed or is no longer reviewable") },
                message = "Cleanup operation was already claimed or is no longer reviewable",
            )
        }
        val result = runCatching { rootClient.executeCleanup(review) }.getOrElse { error ->
            CleanupExecutionResult(
                ok = false,
                operationId = review.operationId,
                status = CleanupOperationStatus.UNKNOWN_OUTCOME.name,
                deletedBytes = 0,
                failedCount = review.accepted.size,
                items = review.accepted.map { item -> CleanupItemResult(item.path, item.plannedBytes, 0, "UNKNOWN", error.message) },
                message = "Root execution result is unknown: ${error.message ?: error.javaClass.simpleName}",
            )
        }
        val summary = deletionSummary(result)
        domainStore.upsertCleanupOperation(
            CleanupOperationEntity(
                id = review.operationId,
                scanId = review.scanId,
                rootPath = review.rootPath,
                status = result.status,
                selectedCount = review.accepted.size,
                plannedBytes = plannedBytes,
                deletedBytes = result.deletedBytes,
                createdAt = startedAt,
                completedAt = System.currentTimeMillis(),
                message = summary,
                reviewId = review.operationId,
                scanGeneration = review.scanGeneration,
                expiresAt = review.reviewedAt + CLEANUP_REVIEW_TTL_MS,
                outcomeCertainty = if (result.status == CleanupOperationStatus.UNKNOWN_OUTCOME.name) CleanupOutcomeCertainty.UNKNOWN.name else CleanupOutcomeCertainty.CERTAIN.name,
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
        val payload = domainStore.authorityRequest(
            "domain.cleanup.verification.build",
            review.scanGeneration.coerceAtLeast(1L),
            JSONObject()
                .put("schema", GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA)
                .put("domain", "cleanup-verification")
                .put("scanId", review.scanId)
                .put("review", verificationReviewJson(review))
                .put("result", verificationResultJson(result))
                .put("startedAt", startedAt)
                .put("completedAt", System.currentTimeMillis())
                .put("observations", verificationObservationsJson(observationsByPath))
                .put("freeBytesBefore", freeBytesBefore ?: JSONObject.NULL)
                .put("freeBytesAfter", availableBytes(review.rootPath) ?: JSONObject.NULL)
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("environment", verificationEnvironmentJson(environmentContext(rootInspection))),
        )
        GoProductAuthorityMapper.verificationReport(payload.getJSONObject("report"))
    }

    private fun verificationReviewJson(review: CleanupReview): JSONObject = JSONObject()
        .put("operationId", review.operationId).put("scanId", review.scanId).put("rootPath", review.rootPath)
        .put("accepted", JSONArray(review.accepted.map { item ->
            JSONObject().put("path", item.path).put("plannedBytes", item.plannedBytes).put("fingerprint", verificationFingerprintJson(item.fingerprint))
        }))
        .put("blocked", JSONArray(review.blocked.map { JSONObject().put("path", it.path).put("reason", it.reason) }))

    private fun verificationResultJson(result: CleanupExecutionResult): JSONObject = JSONObject()
        .put("items", JSONArray(result.items.map { item ->
            JSONObject().put("path", item.path).put("plannedBytes", item.plannedBytes).put("deletedBytes", item.deletedBytes)
                .put("status", item.status).put("message", item.message ?: JSONObject.NULL)
        }))

    private fun verificationObservationsJson(observations: Map<String, CleanupPathObservation>): JSONObject = JSONObject().also { out ->
        observations.forEach { (path, observation) ->
            out.put(path, JSONObject().put("presence", observation.presence.name).put("message", observation.message ?: JSONObject.NULL))
        }
    }

    private fun verificationEnvironmentJson(environment: CleanupEnvironmentContext): JSONObject = JSONObject()
        .put("manufacturer", environment.manufacturer).put("model", environment.model).put("sdkInt", environment.sdkInt)
        .put("filesystem", environment.filesystem ?: JSONObject.NULL).put("mountPoint", environment.mountPoint ?: JSONObject.NULL)
        .put("mountSource", environment.mountSource ?: JSONObject.NULL).put("readOnly", environment.readOnly ?: JSONObject.NULL)

    private fun verificationFingerprintJson(fingerprint: CleanupPathFingerprint): JSONObject = JSONObject()
        .put("device", fingerprint.device).put("mountId", fingerprint.mountId ?: JSONObject.NULL).put("inode", fingerprint.inode)
        .put("mode", fingerprint.mode).put("statSizeBytes", fingerprint.statSizeBytes).put("modifiedAt", fingerprint.modifiedAt)
        .put("treeDigest", fingerprint.treeDigest).put("entryCount", fingerprint.entryCount).put("contentSha256", fingerprint.contentSha256 ?: JSONObject.NULL)

    private fun verificationReportJson(report: CleanupVerificationReport): JSONObject = JSONObject()
        .put("schemaVersion", report.schemaVersion).put("operationId", report.operationId).put("scanId", report.scanId).put("rootPath", report.rootPath)
        .put("startedAt", report.startedAt).put("completedAt", report.completedAt).put("plannedBytes", report.plannedBytes)
        .put("attemptedBytes", report.attemptedBytes).put("reportedDeletedBytes", report.reportedDeletedBytes).put("confirmedDeletedBytes", report.confirmedDeletedBytes)
        .put("partialBytes", report.partialBytes).put("failedBytes", report.failedBytes).put("freeBytesBefore", report.freeBytesBefore ?: JSONObject.NULL)
        .put("freeBytesAfter", report.freeBytesAfter ?: JSONObject.NULL).put("appVersion", report.appVersion)
        .put("environment", verificationEnvironmentJson(report.environment)).put("verificationAttempts", report.verificationAttempts).put("lastVerifiedAt", report.lastVerifiedAt)
        .put("items", JSONArray(report.items.map { item ->
            JSONObject().put("path", item.path).put("plannedBytes", item.plannedBytes).put("reportedDeletedBytes", item.reportedDeletedBytes)
                .put("status", item.status.name).put("pathPresence", item.pathPresence.name).put("message", item.message ?: JSONObject.NULL)
                .put("fingerprint", item.fingerprint?.let(::verificationFingerprintJson) ?: JSONObject.NULL)
        }))

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
        val operations = domainStore.executingCleanupOperations()
        operations.forEach { domainStore.upsertCleanupOperation(OperationRecovery.recover(it, now)) }
        val scans = domainStore.interruptActiveScanSessions(now)
        return operations.size + scans
    }

    fun schedule(request: ScanRequest, hours: Long) = ScheduledScanWorker.set(context, request, hours)
    fun cancelSchedule() = ScheduledScanWorker.cancel(context)

    fun startScan(request: ScanRequest) = ScanForegroundService.start(context, request)
    fun cancelScan(scanId: String) = ScanForegroundService.cancel(context, scanId)

    suspend fun exportCsv(session: ScanSessionEntity) {
        val uri = ExportWriter.writeCsvPaged(context, session) { offset, limit ->
            domainStore.browsePage(
                session.id,
                session.generation,
                ScanBrowseRequest(offset = offset, limit = limit, sort = "PATH", descending = false),
            )
        }
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
        const val SQLITE_SAFE_BIND_BATCH = 500
        const val CLEANUP_REVIEW_TTL_MS = 15 * 60 * 1000L
    }
}
