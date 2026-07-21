package com.mikeyphw.dustui.data

data class ChartSlice(val label: String, val bytes: Long, val count: Int = 0)
data class SnapshotPoint(val timestamp: Long, val totalBytes: Long)

data class VisualAnalyticsReport(
    val storageComposition: List<ChartSlice>,
    val ageDistribution: List<ChartSlice>,
    val snapshotHistory: List<SnapshotPoint>,
)

object VisualAnalytics {
    fun compactSlices(buckets: List<StorageBucket>, limit: Int = 5): List<ChartSlice> {
        val positive = buckets.filter { it.bytes > 0 }.sortedByDescending { it.bytes }
        if (positive.size <= limit) return positive.map { ChartSlice(it.label, it.bytes, it.count) }
        val visible = positive.take(limit)
        val other = positive.drop(limit)
        return visible.map { ChartSlice(it.label, it.bytes, it.count) } + ChartSlice(
            label = "Other",
            bytes = other.sumOf { it.bytes },
            count = other.sumOf { it.count },
        )
    }

    fun appSlices(app: AppStorageSummary): List<ChartSlice> {
        val known = listOf(
            ChartSlice("Private", (app.privateBytes - app.cacheBytes).coerceAtLeast(0)),
            ChartSlice("Cache", app.cacheBytes),
            ChartSlice("External", app.externalBytes),
        ).filter { it.bytes > 0 }
        val knownBytes = known.sumOf { it.bytes }
        val other = (app.totalBytes - knownBytes).coerceAtLeast(0)
        return if (other > 0) known + ChartSlice("Other", other) else known
    }
}
