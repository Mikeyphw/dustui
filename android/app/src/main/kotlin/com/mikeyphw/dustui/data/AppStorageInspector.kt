package com.mikeyphw.dustui.data

import java.util.Locale


enum class AppStorageRisk(val label: String) {
    LOW("Low risk"),
    MEDIUM("Review carefully"),
    HIGH("High risk"),
    INFORMATIONAL("Informational"),
}

enum class AppStorageBucketKind(
    val label: String,
    val risk: AppStorageRisk,
) {
    APP_CODE("APK and split code", AppStorageRisk.INFORMATIONAL),
    PRIVATE_DATA("Private app data", AppStorageRisk.HIGH),
    DATABASES("Databases", AppStorageRisk.HIGH),
    PREFERENCES("Preferences", AppStorageRisk.HIGH),
    CACHE("Cache", AppStorageRisk.MEDIUM),
    CODE_CACHE("Code cache", AppStorageRisk.MEDIUM),
    EXTERNAL_DATA("External app data", AppStorageRisk.MEDIUM),
    OBB("OBB", AppStorageRisk.MEDIUM),
    SHARED_MEDIA("Shared media", AppStorageRisk.MEDIUM),
    LOGS("Logs", AppStorageRisk.LOW),
    EXPORTS_BACKUPS("Exports and backups", AppStorageRisk.MEDIUM),
    OTHER("Other package storage", AppStorageRisk.HIGH),
}

enum class AppStorageSort(val label: String) {
    LARGEST("Largest"),
    GROWTH("Fastest growth"),
    NAME("Name"),
    CACHE("Largest cache"),
}

data class AppPackageMetadata(
    val packageName: String,
    val appLabel: String,
    val installed: Boolean,
    val systemApp: Boolean = false,
    val versionName: String? = null,
    val versionCode: Long? = null,
)

data class AppStoragePathItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val bucket: AppStorageBucketKind,
    val protected: Boolean,
    val deletionEligible: Boolean,
    val deletionReason: String,
    val expectedSha256: String? = null,
)

data class AppStorageBucket(
    val kind: AppStorageBucketKind,
    val bytes: Long,
    val fileCount: Int,
    val paths: List<AppStoragePathItem>,
) {
    val deletionPaths: List<String> = paths.filter { it.deletionEligible }.map { it.path }
    val expectedSha256ByPath: Map<String, String> = paths.mapNotNull { item ->
        item.expectedSha256?.let { item.path to it }
    }.toMap()
}

data class AppStoragePackageReport(
    val packageName: String,
    val appLabel: String,
    val installed: Boolean,
    val systemApp: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val totalBytes: Long,
    val recentGrowthBytes: Long,
    val buckets: List<AppStorageBucket>,
    val duplicatePaths: List<AppStoragePathItem>,
    val residuePaths: List<AppStoragePathItem>,
    val recommendationsIgnored: Boolean,
) {
    private val appCodePaths: List<AppStoragePathItem> = buckets.firstOrNull { it.kind == AppStorageBucketKind.APP_CODE }?.paths.orEmpty()
    val baseApkBytes: Long = appCodePaths.filter { it.name.equals("base.apk", true) }.sumOf { it.sizeBytes }
    val splitApkBytes: Long = appCodePaths.filter { it.name.endsWith(".apk", true) && !it.name.equals("base.apk", true) }.sumOf { it.sizeBytes }
    val nativeAndOtherCodeBytes: Long = (appCodePaths.sumOf { it.sizeBytes } - baseApkBytes - splitApkBytes).coerceAtLeast(0L)
    val appCodeBytes: Long = appCodePaths.sumOf { it.sizeBytes }
    val privateStateBytes: Long = buckets.filter { it.kind in setOf(AppStorageBucketKind.PRIVATE_DATA, AppStorageBucketKind.DATABASES, AppStorageBucketKind.PREFERENCES) }.sumOf { it.bytes }
    val cacheBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.CACHE }?.bytes ?: 0L
    val codeCacheBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.CODE_CACHE }?.bytes ?: 0L
    val obbBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.OBB }?.bytes ?: 0L
    val sharedMediaBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.SHARED_MEDIA }?.bytes ?: 0L
    val logBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.LOGS }?.bytes ?: 0L
    val exportBackupBytes: Long = buckets.firstOrNull { it.kind == AppStorageBucketKind.EXPORTS_BACKUPS }?.bytes ?: 0L
    val sharedBytes: Long = buckets.filter { it.kind in setOf(AppStorageBucketKind.EXTERNAL_DATA, AppStorageBucketKind.OBB, AppStorageBucketKind.SHARED_MEDIA, AppStorageBucketKind.EXPORTS_BACKUPS) }.sumOf { it.bytes }
    val duplicateBytes: Long = duplicatePaths.sumOf { it.sizeBytes }
    val residueBytes: Long = residuePaths.sumOf { it.sizeBytes }
}

data class AppStorageInspectorAnalysis(
    val scanId: String,
    val previousScanId: String?,
    val packages: List<AppStoragePackageReport>,
    val generatedAt: Long,
) {
    companion object {
        fun empty(): AppStorageInspectorAnalysis = AppStorageInspectorAnalysis("", null, emptyList(), 0L)
    }
}

data class AppStorageInspectorFilter(
    val query: String = "",
    val installedOnly: Boolean = false,
    val sort: AppStorageSort = AppStorageSort.LARGEST,
    val page: Int = 0,
    val pageSize: Int = 20,
)

data class AppStorageInspectorPage(
    val items: List<AppStoragePackageReport>,
    val totalMatches: Int,
    val pageIndex: Int,
    val pageCount: Int,
) {
    companion object {
        fun empty() = AppStorageInspectorPage(emptyList(), 0, 0, 0)
    }
}

object AppStorageInspectorEngine {
    fun analyze(
        scanId: String,
        currentEntries: List<ScanEntryEntity>,
        previousScanId: String?,
        previousEntries: List<ScanEntryEntity>,
        metadata: Map<String, AppPackageMetadata>,
        packageInventoryComplete: Boolean = true,
        duplicateSnapshot: DuplicateAnalysisSnapshot?,
        residueAnalysis: AppResidueAnalysis?,
        protectedPaths: Set<String>,
        ignoredPackages: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): AppStorageInspectorAnalysis {
        val currentFiles = currentEntries.filterNot { it.isDirectory }
        val residueByPackage = residueAnalysis?.groups.orEmpty().associateBy { it.packageName }
        val initiallyKnownPackages = buildSet {
            addAll(metadata.keys)
            addAll(residueByPackage.keys)
            currentFiles.mapNotNullTo(this) { entry ->
                EntryIntelligenceResolver.packageFromPath(entry.path)
                    ?.takeUnless { entry.path.startsWith("/data/app/") }
            }
        }
        val ownedByPackage = currentFiles.mapNotNull { entry ->
            resolvePackage(entry.path, initiallyKnownPackages)?.let { it to entry }
        }.groupBy({ it.first }, { it.second })
        val previousTotals = previousEntries.asSequence()
            .filterNot { it.isDirectory }
            .mapNotNull { entry -> resolvePackage(entry.path, initiallyKnownPackages)?.let { it to entry.sizeBytes } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sizes) -> sizes.sum() }
        val duplicateHashes = duplicateDeletionHashes(duplicateSnapshot)
        val packageNames = initiallyKnownPackages + ownedByPackage.keys
        val reports = packageNames.mapNotNull { packageName ->
            val owned = ownedByPackage[packageName].orEmpty()
            val packageMetadata = metadata[packageName] ?: AppPackageMetadata(packageName, packageName, installed = false)
            val bucketItems = owned.map { entry ->
                val bucket = bucketFor(entry.path)
                val protected = protectedPaths.any { root -> entry.path == root || entry.path.startsWith(root.trimEnd('/') + "/") }
                val eligible = deletionEligibility(packageMetadata.installed, packageInventoryComplete, bucket, protected)
                AppStoragePathItem(
                    path = entry.path,
                    name = entry.name,
                    sizeBytes = entry.sizeBytes,
                    modifiedAt = entry.modifiedAt,
                    bucket = bucket,
                    protected = protected,
                    deletionEligible = eligible.first,
                    deletionReason = eligible.second,
                    expectedSha256 = duplicateHashes[entry.path],
                )
            }
            val buckets = bucketItems.groupBy { it.bucket }.map { (kind, items) ->
                AppStorageBucket(kind, items.sumOf { it.sizeBytes }, items.size, items.sortedByDescending { it.sizeBytes })
            }.sortedByDescending { it.bytes }
            val duplicatePaths = bucketItems.filter { it.expectedSha256 != null && it.deletionEligible }
            val residueLocations = residueByPackage[packageName]?.locations.orEmpty()
                .filter { location -> location.confidence == AppResidueConfidence.HIGH }
                .map { location ->
                    val protected = location.userProtected
                    AppStoragePathItem(
                        path = location.path,
                        name = location.path.substringAfterLast('/'),
                        sizeBytes = location.sizeBytes,
                        modifiedAt = location.modifiedAt,
                        bucket = AppStorageBucketKind.OTHER,
                        protected = protected,
                        deletionEligible = !protected && !packageMetadata.installed,
                        deletionReason = if (protected) "Protected by the user" else if (packageMetadata.installed) "Package is currently installed" else "High-confidence package residue",
                    )
                }
            val total = bucketItems.sumOf { it.sizeBytes }
            if (total <= 0L && residueLocations.isEmpty()) return@mapNotNull null
            AppStoragePackageReport(
                packageName = packageName,
                appLabel = packageMetadata.appLabel,
                installed = packageMetadata.installed,
                systemApp = packageMetadata.systemApp,
                versionName = packageMetadata.versionName,
                versionCode = packageMetadata.versionCode,
                totalBytes = total,
                recentGrowthBytes = total - (previousTotals[packageName] ?: 0L),
                buckets = buckets,
                duplicatePaths = duplicatePaths,
                residuePaths = residueLocations,
                recommendationsIgnored = packageName in ignoredPackages,
            )
        }.sortedWith(compareByDescending<AppStoragePackageReport> { it.totalBytes }.thenBy { it.appLabel.lowercase(Locale.ROOT) })
        return AppStorageInspectorAnalysis(scanId, previousScanId, reports, now)
    }

    fun evaluate(analysis: AppStorageInspectorAnalysis?, filter: AppStorageInspectorFilter): AppStorageInspectorPage {
        val query = filter.query.trim()
        val filtered = analysis?.packages.orEmpty().asSequence()
            .filter { !filter.installedOnly || it.installed }
            .filter { query.isBlank() || it.packageName.contains(query, true) || it.appLabel.contains(query, true) }
            .sortedWith(when (filter.sort) {
                AppStorageSort.LARGEST -> compareByDescending<AppStoragePackageReport> { it.totalBytes }.thenBy { it.appLabel }
                AppStorageSort.GROWTH -> compareByDescending<AppStoragePackageReport> { it.recentGrowthBytes }.thenByDescending { it.totalBytes }
                AppStorageSort.NAME -> compareBy<AppStoragePackageReport> { it.appLabel.lowercase(Locale.ROOT) }.thenBy { it.packageName }
                AppStorageSort.CACHE -> compareByDescending<AppStoragePackageReport> { it.cacheBytes }.thenByDescending { it.totalBytes }
            }).toList()
        val pageSize = filter.pageSize.coerceIn(10, 50)
        val pageCount = if (filtered.isEmpty()) 0 else (filtered.size + pageSize - 1) / pageSize
        val pageIndex = filter.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        return AppStorageInspectorPage(filtered.drop(pageIndex * pageSize).take(pageSize), filtered.size, pageIndex, pageCount)
    }

    private fun resolvePackage(path: String, knownPackages: Set<String>): String? {
        val inferred = EntryIntelligenceResolver.packageFromPath(path)
        if (inferred != null && inferred in knownPackages) return inferred
        val segments = path.split('/')
        val matchedKnown = knownPackages.asSequence()
            .sortedByDescending { it.length }
            .firstOrNull { packageName ->
                segments.any { segment ->
                    segment == packageName || segment.startsWith("$packageName-") || segment.startsWith("$packageName=")
                }
            }
        return matchedKnown ?: inferred
    }

    private fun duplicateDeletionHashes(snapshot: DuplicateAnalysisSnapshot?): Map<String, String> = snapshot?.groups.orEmpty()
        .flatMap { group ->
            val item = DuplicateWorkbenchEngine.groupItem(group, DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST))
            item.deletionPaths.map { it to group.sha256 }
        }.toMap()

    fun bucketFor(path: String): AppStorageBucketKind {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("/data/app/") || lower.endsWith(".apk") -> AppStorageBucketKind.APP_CODE
            "/code_cache/" in lower || lower.endsWith("/code_cache") -> AppStorageBucketKind.CODE_CACHE
            "/cache/" in lower || lower.endsWith("/cache") -> AppStorageBucketKind.CACHE
            "/databases/" in lower || lower.endsWith(".db") || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3") -> AppStorageBucketKind.DATABASES
            "/shared_prefs/" in lower -> AppStorageBucketKind.PREFERENCES
            "/android/obb/" in lower -> AppStorageBucketKind.OBB
            "/android/media/" in lower -> AppStorageBucketKind.SHARED_MEDIA
            "/backup/" in lower || "/backups/" in lower || "/export/" in lower || "/exports/" in lower || lower.endsWith(".bak") -> AppStorageBucketKind.EXPORTS_BACKUPS
            lower.endsWith(".log") || "/logs/" in lower -> AppStorageBucketKind.LOGS
            "/android/data/" in lower -> AppStorageBucketKind.EXTERNAL_DATA
            lower.startsWith("/data/user/") || lower.startsWith("/data/user_de/") || lower.startsWith("/data/data/") -> AppStorageBucketKind.PRIVATE_DATA
            else -> AppStorageBucketKind.OTHER
        }
    }

    private fun deletionEligibility(
        installed: Boolean,
        packageInventoryComplete: Boolean,
        bucket: AppStorageBucketKind,
        protected: Boolean,
    ): Pair<Boolean, String> = when {
        protected -> false to "Protected by the user"
        bucket == AppStorageBucketKind.APP_CODE -> false to "Application code must be managed by Android package manager"
        !installed && !packageInventoryComplete -> false to "Package visibility across Android users is incomplete; ownership cannot be proven inactive"
        installed && bucket in setOf(AppStorageBucketKind.PRIVATE_DATA, AppStorageBucketKind.DATABASES, AppStorageBucketKind.PREFERENCES) -> false to "Active internal app state is blocked from direct deletion"
        installed && bucket in setOf(AppStorageBucketKind.CACHE, AppStorageBucketKind.CODE_CACHE) -> false to "Clear active app cache through Android app settings"
        else -> true to "Eligible for explicit review and fingerprinted deletion planning"
    }
}
