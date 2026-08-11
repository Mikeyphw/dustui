package com.mikeyphw.dustui.data

import java.util.Locale
import kotlin.math.abs

private const val LARGE_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L
private const val SUDDEN_GROWTH_THRESHOLD_BYTES = 100L * 1024L * 1024L
private val COMPLETE_GROWTH_STATUSES = setOf(ScanIntegrityStatus.COMPLETE.name, ScanIntegrityStatus.COMPLETE_WITH_WARNINGS.name)

enum class StorageGrowthKind(val label: String) { ADDED("New"), GROWN("Grew"), SHRUNK("Shrank"), REMOVED("Removed") }
enum class StorageGrowthGroup(val label: String) {
    CATEGORY("Category"),
    DIRECTORY("Directory"),
    PACKAGE("Package"),
    EXTENSION("Extension"),
    MODULE("Module"),
    MOUNT("Mount"),
}
enum class StorageGrowthScope(val label: String) { GROWTH("Growth"), ALL("All changes"), SHRINKAGE("Shrinkage") }
enum class StorageGrowthInsightKind(val label: String) {
    NEW_LARGE_FILE("New large file"),
    CROSSED_LARGE_THRESHOLD("Crossed large-file threshold"),
    FASTEST_GROWING_DIRECTORY("Fast-growing directory"),
    SUDDEN_LOG_OR_CACHE("Sudden log/cache growth"),
    MODULE_GROWTH("Root module growth"),
    MOUNT_GROWTH("Mount growth"),
}

data class StorageGrowthItem(
    val path: String,
    val name: String,
    val kind: StorageGrowthKind,
    val previousBytes: Long,
    val currentBytes: Long,
    val deltaBytes: Long,
    val previousEntry: ScanEntryEntity?,
    val currentEntry: ScanEntryEntity?,
) {
    val actionable: Boolean get() = currentEntry != null
    val groupCategory: String get() = AdvancedStorageAnalyzer.fileCategory(name)
    val groupDirectory: String get() = path.substringBeforeLast('/', "/")
    val groupPackage: String get() = SnapshotAnalyzer.packageFromPath(path) ?: "Unowned"
    val groupExtension: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf(String::isNotBlank)?.let { ".$it" } ?: "No extension"
    val groupModule: String get() = Regex("^/data/adb/modules/([^/]+)").find(path)?.groupValues?.getOrNull(1) ?: "Outside root modules"
    val groupMount: String get() = inferMount(path)
}

data class StorageGrowthTimelinePoint(
    val sessionId: String,
    val completedAt: Long,
    val totalBytes: Long,
    val deltaFromPreviousBytes: Long?,
)

data class StorageGrowthInsight(
    val id: String,
    val kind: StorageGrowthInsightKind,
    val title: String,
    val detail: String,
    val deltaBytes: Long,
    val currentPaths: List<String>,
)

data class StorageGrowthAnalysis(
    val currentSession: ScanSessionEntity,
    val previousSession: ScanSessionEntity,
    val items: List<StorageGrowthItem>,
    val timeline: List<StorageGrowthTimelinePoint> = emptyList(),
    val insights: List<StorageGrowthInsight> = emptyList(),
) {
    val totalDeltaBytes: Long = currentSession.totalBytes - previousSession.totalBytes
    val addedBytes: Long = items.filter { it.deltaBytes > 0 }.sumOf { it.deltaBytes }
    val removedBytes: Long = items.filter { it.deltaBytes < 0 }.sumOf { -it.deltaBytes }
    val changedPaths: Int = items.size
    val currentIsLatest: Boolean = timeline.maxByOrNull { it.completedAt }?.sessionId == currentSession.id
}

data class StorageGrowthFilter(
    val query: String = "",
    val scope: StorageGrowthScope = StorageGrowthScope.GROWTH,
    val group: StorageGrowthGroup = StorageGrowthGroup.CATEGORY,
    val showUnderstood: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 40,
)

data class StorageGrowthPage(
    val items: List<StorageGrowthItem>,
    val pageIndex: Int,
    val pageCount: Int,
    val totalItems: Int,
    val positiveBytes: Long,
    val groups: List<Pair<String, Long>>,
    val hiddenUnderstood: Int,
    val understoodPaths: Set<String>,
) {
    companion object { fun empty() = StorageGrowthPage(emptyList(), 0, 0, 0, 0, emptyList(), 0, emptySet()) }
}

object StorageGrowthEngine {
    fun compare(
        currentSession: ScanSessionEntity,
        currentEntries: List<ScanEntryEntity>,
        previousSession: ScanSessionEntity,
        previousEntries: List<ScanEntryEntity>,
        timelineSessions: List<ScanSessionEntity> = listOf(currentSession, previousSession),
    ): StorageGrowthAnalysis {
        if (!compatibleSnapshots(currentSession, previousSession)) {
            return StorageGrowthAnalysis(currentSession, previousSession, emptyList(), timeline(timelineSessions), emptyList())
        }
        val current = currentEntries.asSequence()
            .filterNot { it.isDirectory }
            .sortedWith(scanPathComparator())
            .toList()
        val previous = previousEntries.asSequence()
            .filterNot { it.isDirectory }
            .sortedWith(scanPathComparator())
            .toList()
        val items = mutableListOf<StorageGrowthItem>()
        var currentIndex = 0
        var previousIndex = 0
        while (currentIndex < current.size || previousIndex < previous.size) {
            val now = current.getOrNull(currentIndex)
            val before = previous.getOrNull(previousIndex)
            val comparison = when {
                now == null -> 1
                before == null -> -1
                else -> comparePaths(now.path, before.path)
            }
            val item = when {
                comparison == 0 && now != null && before != null -> {
                    currentIndex++
                    previousIndex++
                    if (now.sizeBytes == before.sizeBytes) null else {
                        val kind = if (now.sizeBytes > before.sizeBytes) StorageGrowthKind.GROWN else StorageGrowthKind.SHRUNK
                        StorageGrowthItem(now.path, now.name, kind, before.sizeBytes, now.sizeBytes, now.sizeBytes - before.sizeBytes, before, now)
                    }
                }
                comparison < 0 && now != null -> {
                    currentIndex++
                    StorageGrowthItem(now.path, now.name, StorageGrowthKind.ADDED, 0, now.sizeBytes, now.sizeBytes, null, now)
                }
                before != null -> {
                    previousIndex++
                    StorageGrowthItem(before.path, before.name, StorageGrowthKind.REMOVED, before.sizeBytes, 0, -before.sizeBytes, before, null)
                }
                else -> null
            }
            if (item != null) items += item
        }
        val orderedItems = items.sortedWith(compareByDescending<StorageGrowthItem> { abs(it.deltaBytes) }.thenBy { it.path.lowercase(Locale.ROOT) })
        val timeline = timeline(timelineSessions)
        val insights = buildInsights(orderedItems)
        return StorageGrowthAnalysis(currentSession, previousSession, orderedItems, timeline, insights)
    }


    private fun scanPathComparator(): Comparator<ScanEntryEntity> = compareBy<ScanEntryEntity> { it.path.lowercase(Locale.ROOT) }.thenBy { it.path }

    private fun comparePaths(left: String, right: String): Int {
        val folded = left.lowercase(Locale.ROOT).compareTo(right.lowercase(Locale.ROOT))
        return if (folded != 0) folded else left.compareTo(right)
    }

    private fun compatibleSnapshots(currentSession: ScanSessionEntity, previousSession: ScanSessionEntity): Boolean {
        if (currentSession.id == previousSession.id) return false
        if (currentSession.rootPath != previousSession.rootPath) return false
        if (currentSession.optionsJson != previousSession.optionsJson) return false
        if (currentSession.truncated || previousSession.truncated) return false
        return currentSession.integrityStatus in COMPLETE_GROWTH_STATUSES &&
            previousSession.integrityStatus in COMPLETE_GROWTH_STATUSES
    }

    fun timeline(sessions: List<ScanSessionEntity>): List<StorageGrowthTimelinePoint> {
        val ordered = sessions.filter { it.status == "COMPLETED" }.sortedBy { it.completedAt ?: it.startedAt }
        return ordered.mapIndexed { index, session ->
            val previous = ordered.getOrNull(index - 1)
            StorageGrowthTimelinePoint(
                sessionId = session.id,
                completedAt = session.completedAt ?: session.startedAt,
                totalBytes = session.totalBytes,
                deltaFromPreviousBytes = previous?.let { session.totalBytes - it.totalBytes },
            )
        }
    }

    fun evaluate(analysis: StorageGrowthAnalysis?, filter: StorageGrowthFilter, understood: Set<String>): StorageGrowthPage {
        if (analysis == null) return StorageGrowthPage.empty()
        val all = analysis.items.asSequence()
            .filter { filter.query.isBlank() || it.path.contains(filter.query, ignoreCase = true) }
            .filter {
                when (filter.scope) {
                    StorageGrowthScope.GROWTH -> it.deltaBytes > 0
                    StorageGrowthScope.SHRINKAGE -> it.deltaBytes < 0
                    StorageGrowthScope.ALL -> true
                }
            }
            .toList()
        val hidden = all.count { identity(it, analysis) in understood }
        val visible = if (filter.showUnderstood) all else all.filterNot { identity(it, analysis) in understood }
        val pageCount = if (visible.isEmpty()) 0 else (visible.size + filter.pageSize - 1) / filter.pageSize
        val page = filter.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageItems = visible.drop(page * filter.pageSize).take(filter.pageSize)
        val groups = visible.groupBy { groupLabel(it, filter.group) }
            .mapValues { (_, values) -> values.sumOf { it.deltaBytes } }
            .entries.sortedByDescending { abs(it.value) }
            .take(12).map { it.key to it.value }
        val understoodPaths = all.filter { identity(it, analysis) in understood }.mapTo(linkedSetOf()) { it.path }
        return StorageGrowthPage(pageItems, page, pageCount, visible.size, visible.filter { it.deltaBytes > 0 }.sumOf { it.deltaBytes }, groups, hidden, understoodPaths)
    }

    fun identity(item: StorageGrowthItem, analysis: StorageGrowthAnalysis): String =
        "${analysis.currentSession.id}|${analysis.previousSession.id}|${item.path}|${item.deltaBytes}"

    fun buildInsights(items: List<StorageGrowthItem>): List<StorageGrowthInsight> {
        val insights = mutableListOf<StorageGrowthInsight>()
        items.filter { it.kind == StorageGrowthKind.ADDED && it.currentBytes >= LARGE_FILE_THRESHOLD_BYTES }
            .take(12).forEach { item ->
                insights += StorageGrowthInsight(
                    id = "new-large:${item.path}",
                    kind = StorageGrowthInsightKind.NEW_LARGE_FILE,
                    title = item.name,
                    detail = "New file added at ${formatGrowthBytes(item.currentBytes)}",
                    deltaBytes = item.deltaBytes,
                    currentPaths = listOf(item.path),
                )
            }
        items.filter { it.previousBytes < LARGE_FILE_THRESHOLD_BYTES && it.currentBytes >= LARGE_FILE_THRESHOLD_BYTES && it.kind != StorageGrowthKind.ADDED }
            .take(12).forEach { item ->
                insights += StorageGrowthInsight(
                    id = "crossed:${item.path}",
                    kind = StorageGrowthInsightKind.CROSSED_LARGE_THRESHOLD,
                    title = item.name,
                    detail = "Crossed 100 MiB: ${formatGrowthBytes(item.previousBytes)} to ${formatGrowthBytes(item.currentBytes)}",
                    deltaBytes = item.deltaBytes,
                    currentPaths = listOf(item.path),
                )
            }
        items.filter { it.deltaBytes > 0 }.groupBy(StorageGrowthItem::groupDirectory)
            .mapValues { (_, values) -> values.sumOf { it.deltaBytes } }
            .entries.sortedByDescending { it.value }.take(6).forEach { (directory, delta) ->
                val paths = items.filter { it.deltaBytes > 0 && it.groupDirectory == directory && it.currentEntry != null }.map { it.path }
                insights += StorageGrowthInsight(
                    id = "directory:$directory",
                    kind = StorageGrowthInsightKind.FASTEST_GROWING_DIRECTORY,
                    title = directory.substringAfterLast('/').ifBlank { directory },
                    detail = "$directory grew by ${formatGrowthBytes(delta)}",
                    deltaBytes = delta,
                    currentPaths = paths,
                )
            }
        items.filter { item ->
            item.deltaBytes >= SUDDEN_GROWTH_THRESHOLD_BYTES &&
                (item.groupCategory.contains("Log", ignoreCase = true) || "/cache/" in item.path.lowercase(Locale.ROOT)) &&
                (item.previousBytes == 0L || item.currentBytes >= item.previousBytes * 2)
        }.take(12).forEach { item ->
            insights += StorageGrowthInsight(
                id = "sudden:${item.path}",
                kind = StorageGrowthInsightKind.SUDDEN_LOG_OR_CACHE,
                title = item.name,
                detail = "Sudden ${item.groupCategory.lowercase(Locale.ROOT)} growth of ${formatGrowthBytes(item.deltaBytes)}",
                deltaBytes = item.deltaBytes,
                currentPaths = listOfNotNull(item.currentEntry?.path),
            )
        }
        addAggregateInsights(items, StorageGrowthInsightKind.MODULE_GROWTH, StorageGrowthItem::groupModule, "module", insights) { it != "Outside root modules" }
        addAggregateInsights(items, StorageGrowthInsightKind.MOUNT_GROWTH, StorageGrowthItem::groupMount, "mount", insights) { true }
        return insights.distinctBy { it.id }.sortedByDescending { it.deltaBytes }
    }

    private fun addAggregateInsights(
        items: List<StorageGrowthItem>,
        kind: StorageGrowthInsightKind,
        key: (StorageGrowthItem) -> String,
        noun: String,
        destination: MutableList<StorageGrowthInsight>,
        include: (String) -> Boolean,
    ) {
        items.filter { it.deltaBytes > 0 }.groupBy(key).forEach { (label, values) ->
            val delta = values.sumOf { it.deltaBytes }
            if (include(label) && delta >= LARGE_FILE_THRESHOLD_BYTES) {
                destination += StorageGrowthInsight(
                    id = "$noun:$label",
                    kind = kind,
                    title = label,
                    detail = "$noun growth of ${formatGrowthBytes(delta)}",
                    deltaBytes = delta,
                    currentPaths = values.mapNotNull { it.currentEntry?.path },
                )
            }
        }
    }

    private fun groupLabel(item: StorageGrowthItem, group: StorageGrowthGroup): String = when (group) {
        StorageGrowthGroup.CATEGORY -> item.groupCategory
        StorageGrowthGroup.DIRECTORY -> item.groupDirectory
        StorageGrowthGroup.PACKAGE -> item.groupPackage
        StorageGrowthGroup.EXTENSION -> item.groupExtension
        StorageGrowthGroup.MODULE -> item.groupModule
        StorageGrowthGroup.MOUNT -> item.groupMount
    }
}

private fun inferMount(path: String): String = when {
    path.startsWith("/storage/emulated/") -> path.split('/').take(4).joinToString("/").ifBlank { "/storage/emulated" }
    path.startsWith("/data/adb/modules/") -> "/data/adb/modules"
    path.startsWith("/data/") || path == "/data" -> "/data"
    path.startsWith("/system/") || path == "/system" -> "/system"
    path.startsWith("/vendor/") || path == "/vendor" -> "/vendor"
    path.startsWith("/product/") || path == "/product" -> "/product"
    path.startsWith("/odm/") || path == "/odm" -> "/odm"
    else -> path.substringBefore('/', missingDelimiterValue = "/").ifBlank { "/" }
}

private fun formatGrowthBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GiB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
    else -> "$bytes B"
}
