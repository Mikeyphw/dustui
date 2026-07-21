package com.mikeyphw.dustui.data

data class AppStorageSummary(
    val packageName: String,
    val totalBytes: Long,
    val privateBytes: Long,
    val cacheBytes: Long,
    val externalBytes: Long,
    val entryCount: Int,
    val orphaned: Boolean,
)

data class SnapshotDelta(
    val currentScanId: String,
    val previousScanId: String?,
    val totalDeltaBytes: Long,
    val addedBytes: Long,
    val removedBytes: Long,
    val changedEntries: Int,
)

object SnapshotAnalyzer {
    fun appSummaries(entries: List<ScanEntryEntity>, installedPackages: Set<String>): List<AppStorageSummary> {
        return entries.asSequence()
            .mapNotNull { entry -> packageFromPath(entry.path)?.let { it to entry } }
            .groupBy({ it.first }, { it.second })
            .map { (pkg, owned) ->
                AppStorageSummary(
                    packageName = pkg,
                    totalBytes = owned.sumOf { it.sizeBytes },
                    privateBytes = owned.filter { it.path.startsWith("/data/user/") || it.path.startsWith("/data/data/") }.sumOf { it.sizeBytes },
                    cacheBytes = owned.filter { "/cache" in it.path || "/code_cache" in it.path }.sumOf { it.sizeBytes },
                    externalBytes = owned.filter { it.path.startsWith("/storage/") }.sumOf { it.sizeBytes },
                    entryCount = owned.size,
                    orphaned = pkg !in installedPackages,
                )
            }.sortedByDescending { it.totalBytes }
    }

    fun diff(current: List<ScanEntryEntity>, previous: List<ScanEntryEntity>, currentId: String, previousId: String?): SnapshotDelta {
        val old = previous.associateBy { it.path }
        val now = current.associateBy { it.path }
        var added = 0L; var removed = 0L; var changed = 0
        for ((path, entry) in now) {
            val before = old[path]
            if (before == null) added += entry.sizeBytes
            else if (before.sizeBytes != entry.sizeBytes) changed++
        }
        for ((path, entry) in old) if (path !in now) removed += entry.sizeBytes
        return SnapshotDelta(currentId, previousId, current.sumOf { it.sizeBytes } - previous.sumOf { it.sizeBytes }, added, removed, changed)
    }

    fun packageFromPath(path: String): String? {
        val patterns = listOf(
            Regex("^/data/(?:user/\\d+|data)/([^/]+)"),
            Regex("^/storage/(?:emulated/\\d+|[^/]+)/Android/(?:data|media|obb)/([^/]+)"),
            Regex("^/mnt/(?:user|runtime/(?:default|read|write))/\\d+/emulated/\\d+/Android/(?:data|media|obb)/([^/]+)"),
        )
        return patterns.firstNotNullOfOrNull { it.find(path)?.groupValues?.getOrNull(1) }
            ?.takeIf { it.contains('.') && it.length <= 255 }
    }
}
