package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapLayoutTest {
    @Test fun `layout fills bounds without losing items`() {
        val result = TreemapLayout.layout(listOf(TreemapInput("a", 60), TreemapInput("b", 30), TreemapInput("c", 10)), 100f, 80f)
        assertEquals(3, result.size)
        assertTrue(result.all { it.left >= 0 && it.top >= 0 && it.right <= 100 && it.bottom <= 80 })
        val area = result.sumOf { (it.width * it.height).toDouble() }
        assertEquals(8000.0, area, 0.1)
    }

    @Test fun `non positive entries are omitted`() {
        val result = TreemapLayout.layout(listOf(TreemapInput("zero", 0), TreemapInput("real", 2)), 10f, 10f)
        assertEquals(listOf("real"), result.map { it.id })
    }
}
