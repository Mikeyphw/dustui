package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualAnalyticsTest {
    @Test fun `compact slices folds tail into other`() {
        val buckets = (1L..7L).map { StorageBucket("B$it", it * 10, it.toInt()) }
        val result = VisualAnalytics.compactSlices(buckets, 3)
        assertEquals(listOf("B7", "B6", "B5", "Other"), result.map { it.label })
        assertEquals(100L, result.last().bytes)
        assertEquals(setOf("B4", "B3", "B2", "B1"), result.last().sourceLabels)
    }

    @Test fun `compact slices keeps a single actionable other aggregate`() {
        val buckets = listOf(
            StorageBucket("Other", 90, 9),
            StorageBucket("Images", 80, 8),
            StorageBucket("Video", 70, 7),
            StorageBucket("Audio", 60, 6),
        )
        val result = VisualAnalytics.compactSlices(buckets, 2)
        assertEquals(listOf("Images", "Video", "Other"), result.map { it.label })
        assertEquals(setOf("Audio", "Other"), result.last().sourceLabels)
        assertEquals(150L, result.last().bytes)
    }

    @Test fun `app slices do not double count cache`() {
        val result = VisualAnalytics.appSlices(AppStorageSummary("pkg", 100, 70, 20, 10, 1, false))
        assertEquals(100L, result.sumOf { it.bytes })
    }
}
