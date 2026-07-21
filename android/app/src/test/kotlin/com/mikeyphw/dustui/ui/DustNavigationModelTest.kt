package com.mikeyphw.dustui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DustNavigationModelTest {
    @Test fun `compact navigation keeps specialized workbenches behind more`() {
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.PACKAGES))
        assertEquals(DustDestination.MORE, DustNavigationModel.compactSelection(DustDestination.MODULES))
        assertEquals(DustDestination.SEARCH, DustNavigationModel.compactSelection(DustDestination.SEARCH))
    }

    @Test fun `wide navigation exposes every primary workbench`() {
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.PACKAGES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.MODULES))
        assertTrue(DustNavigationModel.wideDestinations.contains(DustDestination.CERTIFICATION))
        assertFalse(DustNavigationModel.wideDestinations.contains(DustDestination.MORE))
    }

    @Test fun `only scan works without scan data`() {
        assertFalse(DustNavigationModel.needsScanData(DustDestination.SCAN))
        assertTrue(DustNavigationModel.needsScanData(DustDestination.EXPLORER))
        assertTrue(DustNavigationModel.needsScanData(DustDestination.SEARCH))
    }
}
