package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.ChartSlice
import com.mikeyphw.dustui.data.ScanEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DustAdaptiveLayoutTest {
    @Test fun `AUD FIX12 phone tablet and rotation never remap semantic identity`() {
        val original = DustRouteSelection(DustSemanticRoute.ActivityRun, "operation-42", 19)
        val sizes = listOf(
            412f to 915f,
            915f to 412f,
            700f to 900f,
            1280f to 800f,
            800f to 1280f,
        )
        sizes.forEach { (width, height) ->
            val preserved = DustAdaptiveLayoutPolicy.preserveSelection(original, width, height)
            assertSame(original, preserved)
            assertEquals("activity/run", preserved.routeKey)
            assertEquals("operation-42", preserved.entityId)
            assertEquals(19L, preserved.generation)
        }
    }

    @Test fun `AUD FIX12 high density domains gain supporting and master detail panes only by size`() {
        val selection = DustRouteSelection(DustSemanticRoute.AppsPackage, "com.example.app", 3)
        val compact = DustAdaptiveLayoutPolicy.resolve(412f, 915f, selection)
        val medium = DustAdaptiveLayoutPolicy.resolve(700f, 900f, selection)
        val expanded = DustAdaptiveLayoutPolicy.resolve(1280f, 800f, selection)

        assertEquals(DustPaneMode.Single, compact.paneMode)
        assertFalse(compact.usesNavigationRail)
        assertEquals(DustPaneMode.Supporting, medium.paneMode)
        assertTrue(medium.usesNavigationRail)
        assertEquals(DustPaneMode.MasterDetail, expanded.paneMode)
        assertTrue(expanded.showsMasterPane)
        assertEquals(listOf("primary-navigation", "master", "detail", "context-actions"), expanded.focusOrder)
    }

    @Test fun `AUD FIX12 overview remains one semantic surface even on tablet`() {
        val selection = DustRouteSelection(DustSemanticRoute.OverviewHome, generation = 5)
        val expanded = DustAdaptiveLayoutPolicy.resolve(1280f, 800f, selection)
        assertEquals(DustPaneMode.Single, expanded.paneMode)
        assertTrue(expanded.usesNavigationRail)
        assertEquals(selection, DustAdaptiveLayoutPolicy.preserveSelection(selection, 1280f, 800f))
    }

    @Test fun `AUD FIX12 master selection is derived from semantic route not layout local state`() {
        val current = DustRouteSelection(DustSemanticRoute.FilesItem, "/sdcard/Download/a.zip", 8)
        val entries = listOf(
            ScanEntryEntity("scan", "/sdcard/Download/a.zip", "/sdcard/Download", "a.zip", 2048, 4096, false, 2, 0, 1),
            ScanEntryEntity("scan", "/sdcard/Download/folder", "/sdcard/Download", "folder", 8192, 8192, true, 2, 0, 2),
        )
        val items = DustAdaptiveMasterModel.fileItems(entries, current)
        val selected = items.single { it.isSelected(current) }
        assertEquals(DustSemanticRoute.FilesItem, selected.route)
        assertEquals("/sdcard/Download/a.zip", selected.entityId)
        assertEquals(current.route, selected.selection(8).route)
        assertEquals(current.entityId, selected.selection(8).entityId)
    }

    @Test fun `AUD FIX12 visual data has exact textual equivalents`() {
        val composition = DustVisualAccessibility.storageComposition(
            listOf(ChartSlice("Media", 1024, 2), ChartSlice("Apps", 3072, 3)),
        )
        assertTrue(composition.contains("Media: 1.0 KiB, 25 percent, 2 files"))
        assertTrue(composition.contains("Apps: 3.0 KiB, 75 percent, 3 files"))

        val treemap = DustVisualAccessibility.treemap(
            listOf(ScanEntryEntity("scan", "/a", "/", "a.bin", 4096, 4096, false, 1, 0, 1)),
        )
        assertEquals("a.bin: 4.0 KiB", treemap)
    }
}
