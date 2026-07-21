package com.mikeyphw.dustui.data

private const val MIB_BYTES = 1024L * 1024L

data class PackagePathSummary(
    val path: String,
    val sizeBytes: Long,
    val category: String,
)

data class PackageStorageProfile(
    val packageName: String,
    val appLabel: String?,
    val installed: Boolean,
    val totalBytes: Long,
    val privateBytes: Long,
    val cacheBytes: Long,
    val codeBytes: Long,
    val databaseBytes: Long,
    val preferencesBytes: Long,
    val externalBytes: Long,
    val obbBytes: Long,
    val mediaBytes: Long,
    val logBytes: Long,
    val duplicateCandidateBytes: Long,
    val largestPaths: List<PackagePathSummary>,
) {
    val otherBytes: Long = (totalBytes - privateBytes - cacheBytes - codeBytes - databaseBytes - preferencesBytes - externalBytes - obbBytes - mediaBytes - logBytes).coerceAtLeast(0)
    val cacheShare: Float = if (totalBytes <= 0L) 0f else cacheBytes.toFloat() / totalBytes.toFloat()
    val hasLargeCache: Boolean = cacheBytes >= 512L * MIB_BYTES || cacheShare >= 0.35f
    val hasObb: Boolean = obbBytes > 0L
    val hasDuplicateCandidates: Boolean = duplicateCandidateBytes > 0L
}

object PackageIntelligenceAnalyzer {
    fun profiles(
        entries: List<ScanEntryEntity>,
        installedPackages: Set<String>,
        appLabels: Map<String, String> = emptyMap(),
        duplicateCandidates: List<DuplicateCandidateGroup> = emptyList(),
    ): List<PackageStorageProfile> {
        val duplicateBytesByPackage = duplicateCandidates
            .flatMap { group -> group.paths.mapNotNull { path -> EntryIntelligenceResolver.packageFromPath(path)?.let { it to group.sizeBytes } } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sizes) -> sizes.sum() }

        return entries.asSequence()
            .mapNotNull { entry -> EntryIntelligenceResolver.packageFromPath(entry.path)?.let { it to entry } }
            .groupBy({ it.first }, { it.second })
            .map { (packageName, owned) -> profile(packageName, owned, packageName in installedPackages, appLabels[packageName], duplicateBytesByPackage[packageName] ?: 0L) }
            .sortedWith(compareByDescending<PackageStorageProfile> { it.totalBytes }.thenBy { it.packageName })
    }

    private fun profile(
        packageName: String,
        entries: List<ScanEntryEntity>,
        installed: Boolean,
        appLabel: String?,
        duplicateBytes: Long,
    ): PackageStorageProfile {
        fun bytesFor(role: PackagePathRole): Long = entries.filter { roleOf(it.path) == role }.sumOf { it.sizeBytes }
        return PackageStorageProfile(
            packageName = packageName,
            appLabel = appLabel,
            installed = installed,
            totalBytes = entries.sumOf { it.sizeBytes },
            privateBytes = bytesFor(PackagePathRole.PRIVATE_DATA),
            cacheBytes = bytesFor(PackagePathRole.CACHE) + bytesFor(PackagePathRole.CODE_CACHE),
            codeBytes = bytesFor(PackagePathRole.CODE),
            databaseBytes = bytesFor(PackagePathRole.DATABASE),
            preferencesBytes = bytesFor(PackagePathRole.PREFERENCES),
            externalBytes = bytesFor(PackagePathRole.EXTERNAL_DATA),
            obbBytes = bytesFor(PackagePathRole.OBB),
            mediaBytes = bytesFor(PackagePathRole.MEDIA),
            logBytes = bytesFor(PackagePathRole.LOG),
            duplicateCandidateBytes = duplicateBytes,
            largestPaths = entries.sortedByDescending { it.sizeBytes }.take(8).map { PackagePathSummary(it.path, it.sizeBytes, roleOf(it.path).label) },
        )
    }

    fun roleOf(path: String): PackagePathRole {
        val lower = path.lowercase()
        return when {
            lower.startsWith("/data/app/") -> PackagePathRole.CODE
            "/code_cache" in lower -> PackagePathRole.CODE_CACHE
            "/cache" in lower -> PackagePathRole.CACHE
            "/databases" in lower || lower.endsWith(".db") || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3") -> PackagePathRole.DATABASE
            "/shared_prefs" in lower || lower.endsWith(".xml") && lower.startsWith("/data/") -> PackagePathRole.PREFERENCES
            "/android/obb/" in lower -> PackagePathRole.OBB
            "/android/media/" in lower -> PackagePathRole.MEDIA
            "/android/data/" in lower -> PackagePathRole.EXTERNAL_DATA
            lower.endsWith(".log") || "/logs" in lower -> PackagePathRole.LOG
            lower.startsWith("/data/user/") || lower.startsWith("/data/user_de/") || lower.startsWith("/data/data/") -> PackagePathRole.PRIVATE_DATA
            else -> PackagePathRole.OTHER
        }
    }
}

enum class PackagePathRole(val label: String) {
    PRIVATE_DATA("Private data"),
    CACHE("Cache"),
    CODE_CACHE("Code cache"),
    CODE("APK and native code"),
    DATABASE("Databases"),
    PREFERENCES("Preferences"),
    EXTERNAL_DATA("External data"),
    OBB("OBB"),
    MEDIA("Media"),
    LOG("Logs"),
    OTHER("Other"),
}
