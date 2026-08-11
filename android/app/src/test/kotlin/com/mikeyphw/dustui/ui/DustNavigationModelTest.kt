package com.mikeyphw.dustui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DustNavigationModelTest {
    @Test fun `compact navigation exposes portrait task zones`() {
        assertEquals(
            listOf(
                DustDestination.INSIGHTS,
                DustDestination.EXPLORER,
                DustDestination.RECOMMENDATIONS,
                DustDestination.MORE,
            ),
            DustNavigationModel.compactDestinations,
        )
        assertFalse(DustNavigationModel.compactDestinations.contains(DustDestination.SCAN))
        assertFalse(DustNavigationModel.compactDestinations.contains(DustDestination.SEARCH))
        assertEquals("Home", DustDestination.INSIGHTS.shortLabel)
        assertEquals("Files", DustDestination.EXPLORER.shortLabel)
        assertEquals("Clean", DustDestination.RECOMMENDATIONS.shortLabel)
        assertEquals("Tools", DustDestination.MORE.shortLabel)
    }

    @Test fun `compact selection maps hidden routes to their task zones`() {
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.compactSelection(DustDestination.SCAN))
        assertEquals(DustDestination.EXPLORER, DustNavigationModel.compactSelection(DustDestination.SEARCH))
        assertEquals(DustDestination.RECOMMENDATIONS, DustNavigationModel.compactSelection(DustDestination.RECOMMENDATIONS))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.RULES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.GROWTH))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.LARGE_FILES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.DUPLICATES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.APP_RESIDUE))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.PACKAGES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.MODULES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.CERTIFICATION))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.CLEANUP))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.DEBUG))
    }

    @Test fun `compact home stays home while scan opens from top sheet`() {
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.compactNavigationTarget(DustDestination.INSIGHTS, hasScanData = false))
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.compactNavigationTarget(DustDestination.INSIGHTS, hasScanData = true))
        assertEquals(DustDestination.EXPLORER, DustNavigationModel.compactNavigationTarget(DustDestination.EXPLORER, hasScanData = false))
        assertEquals(DustDestination.RECOMMENDATIONS, DustNavigationModel.compactNavigationTarget(DustDestination.RECOMMENDATIONS, hasScanData = true))
    }

    @Test fun `tools contains advanced workbenches but clean is a primary tab`() {
        assertFalse(DustNavigationModel.moreDestinations.contains(DustDestination.RECOMMENDATIONS))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.RULES))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.GROWTH))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.LARGE_FILES))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.DUPLICATES))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.APP_RESIDUE))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.PACKAGES))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.MODULES))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.CERTIFICATION))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.CLEANUP))
        assertTrue(DustNavigationModel.moreDestinations.contains(DustDestination.DEBUG))
    }

    @Test fun `wide navigation exposes every primary workbench`() {
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.SCAN))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.RECOMMENDATIONS))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.RULES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.GROWTH))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.LARGE_FILES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.DUPLICATES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.APP_RESIDUE))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.PACKAGES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.MODULES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.CERTIFICATION))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.DEBUG))
        assertFalse(DustNavigationModel.wideDestinations.contains(DustDestination.MORE))
    }

    @Test fun `back returns compact routes to task zones`() {
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.backTarget(DustDestination.RECOMMENDATIONS, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.RULES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.GROWTH, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.LARGE_FILES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.DUPLICATES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.APP_RESIDUE, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.PACKAGES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.DEBUG, wide = false))
        assertEquals(DustDestination.EXPLORER, DustNavigationModel.backTarget(DustDestination.SEARCH, wide = false))
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.backTarget(DustDestination.EXPLORER, wide = false))
        assertEquals(DustDestination.SCAN, DustNavigationModel.backTarget(DustDestination.PACKAGES, wide = true))
        assertNull(DustNavigationModel.backTarget(DustDestination.SCAN, wide = false))
        assertNull(DustNavigationModel.backTarget(DustDestination.INSIGHTS, wide = false))
    }

    @Test fun `home files clean tools scan debug and reports work without scan data`() {
        assertFalse(DustNavigationModel.needsScanData(DustDestination.SCAN))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.INSIGHTS))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.EXPLORER))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.RECOMMENDATIONS))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.CLEANUP))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.DEBUG))
        assertFalse(DustNavigationModel.needsScanData(DustDestination.MORE))
        assertTrue(DustNavigationModel.needsScanData(DustDestination.SEARCH))
    }
    @Test fun `layout normalization prevents invalid adaptive destinations`() {
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.layoutDestination(DustDestination.SCAN, wide = false))
        assertEquals(DustDestination.INSIGHTS, DustNavigationModel.layoutDestination(DustDestination.MORE, wide = true))
        assertEquals(DustDestination.SCAN, DustNavigationModel.layoutDestination(DustDestination.SCAN, wide = true))
        assertEquals(DustDestination.GROWTH, DustNavigationModel.layoutDestination(DustDestination.GROWTH, wide = false))
    }

}
