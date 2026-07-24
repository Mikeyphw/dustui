package com.mikeyphw.dustui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DustNavigationModelTest {
    @Test fun `compact navigation keeps specialized workbenches behind more`() {
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.LARGE_FILES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.DUPLICATES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.APP_RESIDUE))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.PACKAGES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.MODULES))
        assertEquals(DustDestination.SEARCH, DustNavigationModel.compactSelection(DustDestination.SEARCH))
    }

    @Test fun `wide navigation exposes every primary workbench`() {
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.LARGE_FILES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.DUPLICATES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.APP_RESIDUE))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.PACKAGES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.MODULES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.CERTIFICATION))
        assertFalse(DustNavigationModel.wideDestinations.contains(DustDestination.MORE))
    }

    @Test fun `back returns compact specialized workbenches to more`() {
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.LARGE_FILES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.DUPLICATES, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.APP_RESIDUE, wide = false))
        assertEquals(DustDestination.MORE, DustNavigationModel.backTarget(DustDestination.PACKAGES, wide = false))
        assertEquals(DustDestination.SCAN, DustNavigationModel.backTarget(DustDestination.PACKAGES, wide = true))
        assertEquals(DustDestination.SCAN, DustNavigationModel.backTarget(DustDestination.EXPLORER, wide = false))
        assertNull(DustNavigationModel.backTarget(DustDestination.SCAN, wide = false))
    }

    @Test fun `only scan works without scan data`() {
        assertFalse(DustNavigationModel.needsScanData(DustDestination.SCAN))
        assertTrue(DustNavigationModel.needsScanData(DustDestination.EXPLORER))
        assertTrue(DustNavigationModel.needsScanData(DustDestination.SEARCH))
    }
}
