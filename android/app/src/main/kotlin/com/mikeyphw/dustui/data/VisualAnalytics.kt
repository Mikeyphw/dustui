package com.mikeyphw.dustui.data

data class ChartSlice(
    val label: String,
    val bytes: Long,
    val count: Int = 0,
    val sourceLabels: Set<String> = setOf(label),
)
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

        // Keep the aggregate label unique. A literal "Other" file category is folded into
        // the aggregate together with the lower-ranked categories rather than producing two
        // indistinguishable doughnut slices.
        val ranked = positive.filterNot { it.label == "Other" }
        val visible = ranked.take(limit)
        val remainder = ranked.drop(limit) + positive.filter { it.label == "Other" }
        return visible.map { ChartSlice(it.label, it.bytes, it.count) } + ChartSlice(
            label = "Other",
            bytes = remainder.sumOf { it.bytes },
            count = remainder.sumOf { it.count },
            sourceLabels = remainder.mapTo(linkedSetOf()) { it.label },
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
