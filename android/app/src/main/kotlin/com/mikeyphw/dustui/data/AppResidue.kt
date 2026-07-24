package com.mikeyphw.dustui.data

import java.util.Locale

private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")
private val MANAGED_PRIVATE_PATTERN = Regex("^/data/(user|user_de)/(\\d+)/([^/]+)$")
private val LEGACY_PRIVATE_PATTERN = Regex("^/data/data/([^/]+)$")
private val MANAGED_EXTERNAL_PATTERN = Regex("^/storage/emulated/(\\d+)/Android/(data|media|obb)/([^/]+)$", RegexOption.IGNORE_CASE)
private val KNOWN_EXPORT_PATTERN = Regex("^/storage/emulated/(\\d+)/(Download|Documents|Backups?|Exports?|Logs?)/([^/]+)$", RegexOption.IGNORE_CASE)
private val SHARED_PACKAGE_PATTERN = Regex("^/storage/emulated/(\\d+)/([^/]+)$")

/** Installed package visibility across Android users, gathered by the fixed root-service package query. */
data class InstalledPackageInventory(
    val packagesByUser: Map<Int, Set<String>>,
    val complete: Boolean,
    val message: String? = null,
) {
    val userIds: Set<Int> = packagesByUser.keys
    val allPackages: Set<String> = packagesByUser.values.flatten().toSet()

    fun isInstalled(packageName: String): Boolean = packageName in allPackages

    fun relatedPackages(packageName: String): Set<String> {
        val parts = packageName.split('.')
        val family = when {
            parts.size >= 3 -> parts.dropLast(1).joinToString(".")
            else -> packageName
        }
        if (family == packageName) return emptySet()
        return allPackages.filterTo(sortedSetOf()) { candidate ->
            candidate != packageName && (candidate == family || candidate.startsWith("$family."))
        }
    }

    companion object {
        fun currentUserOnly(packages: Set<String>, message: String): InstalledPackageInventory =
            InstalledPackageInventory(mapOf(0 to packages), complete = false, message = message)
    }
}

enum class AppResidueConfidence(val label: String, val rank: Int) {
    HIGH("High confidence", 3),
    PROBABLE("Probable", 2),
    UNCERTAIN("Uncertain", 1),
}

enum class AppResidueLocationKind(val label: String) {
    PRIVATE_DATA("Private app data"),
    EXTERNAL_DATA("Android data"),
    MEDIA("Android media"),
    OBB("OBB assets"),
    DOWNLOAD_EXPORT("Download or export"),
    BACKUP("Backup"),
    LOG_EXPORT("Logs"),
    SHARED_PACKAGE_FOLDER("Shared package-named folder"),
}

data class AppResidueEvidence(
    val code: String,
    val label: String,
    val detail: String,
)

data class AppResidueLocation(
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val fileCount: Long,
    val userId: Int?,
    val kind: AppResidueLocationKind,
    val confidence: AppResidueConfidence,
    val evidence: List<AppResidueEvidence>,
    val userProtected: Boolean,
) {
    val deletionEligible: Boolean = !userProtected
}

data class AppResidueGroup(
    val packageName: String,
    val appLabel: String?,
    val confidence: AppResidueConfidence,
    val locations: List<AppResidueLocation>,
    val relatedInstalledPackages: Set<String>,
) {
    val totalBytes: Long = locations.sumOf { it.sizeBytes }
    val totalFiles: Long = locations.sumOf { it.fileCount }
    val recommendedDeletionPaths: List<String> = if (confidence == AppResidueConfidence.HIGH) {
        locations.filter { it.deletionEligible && it.confidence == AppResidueConfidence.HIGH }.map { it.path }
    } else {
        emptyList()
    }
    val protectedCount: Int = locations.count { it.userProtected }
}

data class AppResidueAnalysis(
    val scanId: String,
    val inventoryComplete: Boolean,
    val checkedUsers: Set<Int>,
    val groups: List<AppResidueGroup>,
    val protectedLocationCount: Int,
    val generatedAt: Long,
    val message: String? = null,
) {
    val totalBytes: Long = groups.sumOf { it.totalBytes }
    val recommendedBytes: Long = groups.sumOf { group ->
        group.locations.filter { it.path in group.recommendedDeletionPaths }.sumOf { it.sizeBytes }
    }

    companion object {
        fun empty(scanId: String = ""): AppResidueAnalysis = AppResidueAnalysis(
            scanId = scanId,
            inventoryComplete = false,
            checkedUsers = emptySet(),
            groups = emptyList(),
            protectedLocationCount = 0,
            generatedAt = 0,
        )
    }
}

enum class AppResidueSort(val label: String) {
    LARGEST("Largest"),
    CONFIDENCE("Confidence"),
    PACKAGE("Package"),
    OLDEST("Oldest"),
}

data class AppResidueFilter(
    val query: String = "",
    val confidence: AppResidueConfidence? = null,
    val sort: AppResidueSort = AppResidueSort.LARGEST,
    val page: Int = 0,
    val pageSize: Int = 12,
)

data class AppResiduePage(
    val filter: AppResidueFilter,
    val items: List<AppResidueGroup>,
    val totalGroups: Int,
    val totalBytes: Long,
    val recommendedPaths: List<String>,
    val pageIndex: Int,
    val pageCount: Int,
) {
    val hasPrevious: Boolean = pageIndex > 0
    val hasNext: Boolean = pageIndex + 1 < pageCount

    companion object {
        fun empty(): AppResiduePage = AppResiduePage(
            filter = AppResidueFilter(),
            items = emptyList(),
            totalGroups = 0,
            totalBytes = 0,
            recommendedPaths = emptyList(),
            pageIndex = 0,
            pageCount = 0,
        )
    }
}

object AppResidueAnalyzer {
    fun analyze(
        scanId: String,
        entries: List<ScanEntryEntity>,
        inventory: InstalledPackageInventory,
        protectedPaths: Set<String>,
        appLabels: Map<String, String> = emptyMap(),
        generatedAt: Long = System.currentTimeMillis(),
    ): AppResidueAnalysis {
        val candidates = entries.asSequence()
            .filter { it.isDirectory }
            .mapNotNull { entry -> candidate(entry, inventory, protectedPaths) }
            .filterNot { inventory.isInstalled(it.packageName) }
            .toList()
            .let(::dropLegacyPrivateAliases)

        val groups = candidates.groupBy { it.packageName }
            .map { (packageName, owned) ->
                val related = inventory.relatedPackages(packageName)
                val adjusted = owned.map { candidate ->
                    if (related.isEmpty() || candidate.location.confidence == AppResidueConfidence.UNCERTAIN) {
                        candidate.location
                    } else {
                        candidate.location.copy(
                            confidence = AppResidueConfidence.PROBABLE,
                            evidence = candidate.location.evidence + AppResidueEvidence(
                                code = "RELATED_PACKAGE_PRESENT",
                                label = "Related package installed",
                                detail = related.joinToString(),
                            ),
                        )
                    }
                }
                AppResidueGroup(
                    packageName = packageName,
                    appLabel = appLabels[packageName],
                    confidence = adjusted.minByOrNull { it.confidence.rank }?.confidence ?: AppResidueConfidence.UNCERTAIN,
                    locations = adjusted.sortedWith(compareByDescending<AppResidueLocation> { it.sizeBytes }.thenBy { it.path.lowercase(Locale.ROOT) }),
                    relatedInstalledPackages = related,
                )
            }
            .sortedWith(compareByDescending<AppResidueGroup> { it.confidence.rank }.thenByDescending { it.totalBytes }.thenBy { it.packageName })

        return AppResidueAnalysis(
            scanId = scanId,
            inventoryComplete = inventory.complete,
            checkedUsers = inventory.userIds,
            groups = groups,
            protectedLocationCount = groups.sumOf { it.protectedCount },
            generatedAt = generatedAt,
            message = inventory.message,
        )
    }

    private fun candidate(
        entry: ScanEntryEntity,
        inventory: InstalledPackageInventory,
        protectedPaths: Set<String>,
    ): Candidate? {
        managedPrivate(entry.path)?.let { parsed ->
            return buildCandidate(entry, parsed.packageName, parsed.userId, AppResidueLocationKind.PRIVATE_DATA, managed = true, inventory, protectedPaths)
        }
        managedExternal(entry.path)?.let { parsed ->
            return buildCandidate(entry, parsed.packageName, parsed.userId, parsed.kind, managed = true, inventory, protectedPaths)
        }
        knownExport(entry.path)?.let { parsed ->
            return buildCandidate(entry, parsed.packageName, parsed.userId, parsed.kind, managed = false, inventory, protectedPaths, knownExport = true)
        }
        sharedPackageFolder(entry.path)?.let { parsed ->
            return buildCandidate(entry, parsed.packageName, parsed.userId, AppResidueLocationKind.SHARED_PACKAGE_FOLDER, managed = false, inventory, protectedPaths)
        }
        return null
    }

    private fun buildCandidate(
        entry: ScanEntryEntity,
        packageName: String,
        userId: Int?,
        kind: AppResidueLocationKind,
        managed: Boolean,
        inventory: InstalledPackageInventory,
        protectedPaths: Set<String>,
        knownExport: Boolean = false,
    ): Candidate? {
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) return null
        if (inventory.isInstalled(packageName)) return null
        val userCovered = userId == null || userId in inventory.userIds
        val confidence = when {
            !inventory.complete || !userCovered -> AppResidueConfidence.UNCERTAIN
            managed -> AppResidueConfidence.HIGH
            knownExport -> AppResidueConfidence.PROBABLE
            else -> AppResidueConfidence.UNCERTAIN
        }
        val protected = protectedPaths.any { root -> entry.path == root || entry.path.startsWith(root.trimEnd('/') + "/") }
        val evidence = buildList {
            add(
                AppResidueEvidence(
                    code = if (inventory.complete && userCovered) "PACKAGE_ABSENT_ALL_USERS" else "PACKAGE_INVENTORY_INCOMPLETE",
                    label = if (inventory.complete && userCovered) "Package absent across checked users" else "Package visibility incomplete",
                    detail = if (inventory.complete && userCovered) {
                        "Not installed for users ${inventory.userIds.sorted().joinToString()}"
                    } else {
                        inventory.message ?: "DustUI could not prove package absence for every Android user"
                    },
                ),
            )
            add(
                AppResidueEvidence(
                    code = when {
                        managed -> "MANAGED_PACKAGE_DIRECTORY"
                        knownExport -> "KNOWN_EXPORT_LOCATION"
                        else -> "PACKAGE_NAME_ONLY"
                    },
                    label = when {
                        managed -> "Android package directory"
                        knownExport -> "Known export or backup location"
                        else -> "Package-shaped folder name"
                    },
                    detail = kind.label,
                ),
            )
            if (protected) {
                add(AppResidueEvidence("USER_PROTECTED", "Protected by you", "This path is excluded from residue selection"))
            }
        }
        return Candidate(
            packageName = packageName,
            location = AppResidueLocation(
                path = entry.path,
                sizeBytes = entry.sizeBytes,
                modifiedAt = entry.modifiedAt,
                fileCount = entry.fileCount,
                userId = userId,
                kind = kind,
                confidence = confidence,
                evidence = evidence,
                userProtected = protected,
            ),
        )
    }

    private fun managedPrivate(path: String): ParsedLocation? {
        MANAGED_PRIVATE_PATTERN.matchEntire(path)?.let { match ->
            return ParsedLocation(match.groupValues[3], match.groupValues[2].toIntOrNull(), AppResidueLocationKind.PRIVATE_DATA)
        }
        LEGACY_PRIVATE_PATTERN.matchEntire(path)?.let { match ->
            return ParsedLocation(match.groupValues[1], 0, AppResidueLocationKind.PRIVATE_DATA)
        }
        return null
    }

    private fun managedExternal(path: String): ParsedLocation? = MANAGED_EXTERNAL_PATTERN.matchEntire(path)?.let { match ->
        val kind = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "media" -> AppResidueLocationKind.MEDIA
            "obb" -> AppResidueLocationKind.OBB
            else -> AppResidueLocationKind.EXTERNAL_DATA
        }
        ParsedLocation(match.groupValues[3], match.groupValues[1].toIntOrNull(), kind)
    }

    private fun knownExport(path: String): ParsedLocation? = KNOWN_EXPORT_PATTERN.matchEntire(path)?.let { match ->
        val segment = match.groupValues[2].lowercase(Locale.ROOT)
        val kind = when {
            segment.startsWith("backup") -> AppResidueLocationKind.BACKUP
            segment.startsWith("log") -> AppResidueLocationKind.LOG_EXPORT
            else -> AppResidueLocationKind.DOWNLOAD_EXPORT
        }
        ParsedLocation(match.groupValues[3], match.groupValues[1].toIntOrNull(), kind)
    }

    private fun sharedPackageFolder(path: String): ParsedLocation? = SHARED_PACKAGE_PATTERN.matchEntire(path)?.let { match ->
        ParsedLocation(match.groupValues[2], match.groupValues[1].toIntOrNull(), AppResidueLocationKind.SHARED_PACKAGE_FOLDER)
    }

    private fun dropLegacyPrivateAliases(candidates: List<Candidate>): List<Candidate> {
        val userZeroPackages = candidates.asSequence()
            .filter { it.location.path.startsWith("/data/user/0/") }
            .mapTo(hashSetOf()) { it.packageName }
        return candidates.filterNot { it.location.path.startsWith("/data/data/") && it.packageName in userZeroPackages }
    }

    private data class ParsedLocation(val packageName: String, val userId: Int?, val kind: AppResidueLocationKind)
    private data class Candidate(val packageName: String, val location: AppResidueLocation)
}

object AppResidueWorkbenchEngine {
    fun evaluate(analysis: AppResidueAnalysis?, filter: AppResidueFilter): AppResiduePage {
        val source = analysis?.groups.orEmpty()
        val query = filter.query.trim()
        val filtered = source.asSequence()
            .filter { filter.confidence == null || it.confidence == filter.confidence }
            .filter { group ->
                query.isBlank() || group.packageName.contains(query, ignoreCase = true) ||
                    group.appLabel?.contains(query, ignoreCase = true) == true ||
                    group.locations.any { it.path.contains(query, ignoreCase = true) }
            }
            .sortedWith(
                when (filter.sort) {
                    AppResidueSort.LARGEST -> compareByDescending<AppResidueGroup> { it.totalBytes }.thenBy { it.packageName }
                    AppResidueSort.CONFIDENCE -> compareByDescending<AppResidueGroup> { it.confidence.rank }.thenByDescending { it.totalBytes }.thenBy { it.packageName }
                    AppResidueSort.PACKAGE -> compareBy<AppResidueGroup> { it.packageName.lowercase(Locale.ROOT) }
                    AppResidueSort.OLDEST -> compareBy<AppResidueGroup> { group -> group.locations.minOfOrNull { it.modifiedAt } ?: Long.MAX_VALUE }.thenBy { it.packageName }
                },
            )
            .toList()
        val pageCount = if (filtered.isEmpty()) 0 else (filtered.size + filter.pageSize - 1) / filter.pageSize
        val pageIndex = filter.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val items = filtered.drop(pageIndex * filter.pageSize).take(filter.pageSize)
        return AppResiduePage(
            filter = filter.copy(page = pageIndex),
            items = items,
            totalGroups = filtered.size,
            totalBytes = filtered.sumOf { it.totalBytes },
            recommendedPaths = items.flatMap { it.recommendedDeletionPaths },
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
    }
}
