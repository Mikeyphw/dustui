package com.mikeyphw.dustui.data

import java.util.Locale

private const val MIB = 1024L * 1024L

enum class DuplicateThreshold(val label: String, val minBytes: Long) {
    ONE_MIB("1 MiB+", MIB),
    TEN_MIB("10 MiB+", 10L * MIB),
    HUNDRED_MIB("100 MiB+", 100L * MIB),
}

enum class DuplicateKeepStrategy(val label: String) {
    NEWEST("Keep newest"),
    OLDEST("Keep oldest"),
    SHORTEST_PATH("Keep shortest path"),
    PREFERRED_DIRECTORY("Prefer folder"),
    MANUAL("Choose manually"),
}

data class DuplicateAnalysisSnapshot(
    val scanId: String,
    val minSizeBytes: Long,
    val status: String,
    val totalBuckets: Int,
    val completedBuckets: Int,
    val checkedFiles: Int,
    val skippedFiles: Int,
    val partialGroups: Int,
    val bytesHashed: Long,
    val groups: List<VerifiedDuplicateGroup>,
    val completedSizes: Set<Long>,
    val startedAt: Long,
    val updatedAt: Long,
    val currentSizeBytes: Long? = null,
    val message: String? = null,
    val scanGeneration: Long = 1L,
) {
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }
    val safeReclaimableBytes: Long get() = groups.sumOf { it.safeReclaimableBytes }
    val running: Boolean get() = status == "RUNNING"
    val resumable: Boolean get() = status == "PAUSED" || status == "FAILED"

    companion object {
        fun empty(scanId: String, minSizeBytes: Long, now: Long = System.currentTimeMillis(), scanGeneration: Long = 1L) = DuplicateAnalysisSnapshot(
            scanId = scanId,
            minSizeBytes = minSizeBytes,
            status = "IDLE",
            totalBuckets = 0,
            completedBuckets = 0,
            checkedFiles = 0,
            skippedFiles = 0,
            partialGroups = 0,
            bytesHashed = 0,
            groups = emptyList(),
            completedSizes = emptySet(),
            startedAt = now,
            updatedAt = now,
            scanGeneration = scanGeneration,
        )
    }
}

data class DuplicateFilter(
    val threshold: DuplicateThreshold = DuplicateThreshold.ONE_MIB,
    val query: String = "",
    val keepStrategy: DuplicateKeepStrategy = DuplicateKeepStrategy.NEWEST,
    val preferredDirectory: String = "",
    val page: Int = 0,
    val pageSize: Int = 12,
)

data class DuplicatePhysicalCopy(
    val key: String,
    val paths: List<DuplicateVerifiedPath>,
) {
    val primaryPath: String get() = paths.minByOrNull { it.path.length }?.path.orEmpty()
    val modifiedAt: Long get() = paths.maxOfOrNull { it.modifiedAt } ?: 0L
    val hardLinked: Boolean get() = paths.any { it.hardLinked || it.linkCount > 1L }
    val deletionBlockReason: String? get() = paths.firstNotNullOfOrNull { it.deletionBlockReason }
    val autoDeleteEligible: Boolean get() = paths.size == 1 && paths.single().autoDeleteEligible && !hardLinked && deletionBlockReason == null
}

data class DuplicateGroupItem(
    val group: VerifiedDuplicateGroup,
    val copies: List<DuplicatePhysicalCopy>,
    val keepCopyKey: String?,
    val deletionPaths: List<String>,
    val plannedBytes: Long,
    val hardLinkedCopies: Int,
)

data class DuplicatePage(
    val filter: DuplicateFilter,
    val items: List<DuplicateGroupItem>,
    val totalGroups: Int,
    val totalReclaimableBytes: Long,
    val totalSafeReclaimableBytes: Long,
    val pageIndex: Int,
    val pageCount: Int,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex + 1 < pageCount
    val deletionPaths: List<String> get() = items.flatMap { it.deletionPaths }.distinct()

    companion object {
        fun empty(filter: DuplicateFilter = DuplicateFilter()) = DuplicatePage(filter, emptyList(), 0, 0, 0, 0, 0)
    }
}

object DuplicateWorkbenchEngine {
    fun evaluate(
        snapshot: DuplicateAnalysisSnapshot?,
        filter: DuplicateFilter,
        manualKeep: Map<String, String> = emptyMap(),
    ): DuplicatePage {
        if (snapshot == null || snapshot.scanId.isBlank() || snapshot.minSizeBytes != filter.threshold.minBytes) {
            return DuplicatePage.empty(filter)
        }
        val query = filter.query.trim()
        val filtered = snapshot.groups.asSequence()
            .filter { group ->
                query.isBlank() || group.sha256.contains(query, ignoreCase = true) ||
                    group.pathRecords.any { record ->
                        record.path.contains(query, ignoreCase = true) ||
                            record.path.substringAfterLast('/').contains(query, ignoreCase = true)
                    }
            }
            .sortedWith(compareByDescending<VerifiedDuplicateGroup> { it.safeReclaimableBytes }
                .thenByDescending { it.sizeBytes }
                .thenBy { it.sha256 })
            .toList()
        val pageCount = if (filtered.isEmpty()) 0 else (filtered.size + filter.pageSize - 1) / filter.pageSize
        val pageIndex = filter.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val items = filtered.drop(pageIndex * filter.pageSize).take(filter.pageSize).map { group ->
            groupItem(group, filter, manualKeep[group.sha256])
        }
        return DuplicatePage(
            filter = filter.copy(page = pageIndex),
            items = items,
            totalGroups = filtered.size,
            totalReclaimableBytes = filtered.sumOf { it.reclaimableBytes },
            totalSafeReclaimableBytes = filtered.sumOf { it.safeReclaimableBytes },
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
    }

    fun groupItem(
        group: VerifiedDuplicateGroup,
        filter: DuplicateFilter,
        manualKeepKey: String? = null,
    ): DuplicateGroupItem {
        val copies = group.pathRecords.groupBy { it.copyKey }
            .map { (key, paths) -> DuplicatePhysicalCopy(key, paths.sortedBy { it.path }) }
            .sortedBy { it.primaryPath.lowercase(Locale.ROOT) }
        val protectedCopies = copies.filterNot { it.autoDeleteEligible }
        val eligibleCopies = copies.filter { it.autoDeleteEligible }
        val keepKey = when {
            protectedCopies.isNotEmpty() -> protectedCopies.first().key
            filter.keepStrategy == DuplicateKeepStrategy.MANUAL -> manualKeepKey?.takeIf { key -> copies.any { it.key == key } }
            filter.keepStrategy == DuplicateKeepStrategy.NEWEST -> copies.maxByOrNull { it.modifiedAt }?.key
            filter.keepStrategy == DuplicateKeepStrategy.OLDEST -> copies.minByOrNull { it.modifiedAt }?.key
            filter.keepStrategy == DuplicateKeepStrategy.SHORTEST_PATH -> copies.minByOrNull { it.primaryPath.length }?.key
            filter.keepStrategy == DuplicateKeepStrategy.PREFERRED_DIRECTORY -> preferredCopy(copies, filter.preferredDirectory)?.key
            else -> null
        }
        val deletionCopies = when {
            filter.keepStrategy == DuplicateKeepStrategy.MANUAL && keepKey == null -> emptyList()
            else -> eligibleCopies.filterNot { it.key == keepKey }
        }
        return DuplicateGroupItem(
            group = group,
            copies = copies,
            keepCopyKey = keepKey,
            deletionPaths = deletionCopies.map { it.primaryPath },
            plannedBytes = group.sizeBytes * deletionCopies.size,
            hardLinkedCopies = protectedCopies.count { it.hardLinked },
        )
    }

    private fun preferredCopy(copies: List<DuplicatePhysicalCopy>, preferredDirectory: String): DuplicatePhysicalCopy? {
        val preferred = preferredDirectory.trim().trimEnd('/')
        return if (preferred.isBlank()) {
            copies.minByOrNull { it.primaryPath.length }
        } else {
            copies.filter { it.primaryPath == preferred || it.primaryPath.startsWith("$preferred/") }
                .minByOrNull { it.primaryPath.length }
                ?: copies.minByOrNull { it.primaryPath.length }
        }
    }
}
