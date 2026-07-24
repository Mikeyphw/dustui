package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.ScanEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryPathSelectorTest {
    @Test fun `category selection returns files from every requested source label`() {
        val entries = listOf(
            entry("/scan/photo.jpg", 10),
            entry("/scan/movie.mp4", 30),
            entry("/scan/readme.txt", 20),
            entry("/scan/folder", 100, directory = true),
        )

        val result = CategoryPathSelector.matchingEntries(entries, setOf("Images", "Video"))

        assertEquals(listOf("/scan/movie.mp4", "/scan/photo.jpg"), result.map { it.path })
    }

    @Test fun `empty category selection produces no accidental targets`() {
        assertEquals(emptyList<ScanEntryEntity>(), CategoryPathSelector.matchingEntries(listOf(entry("/scan/file.apk", 10)), emptySet()))
    }

    private fun entry(path: String, bytes: Long, directory: Boolean = false) = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/'),
        name = path.substringAfterLast('/'),
        sizeBytes = bytes,
        allocatedBytes = bytes,
        isDirectory = directory,
        depth = 1,
        modifiedAt = 1,
        fileCount = if (directory) 1 else 0,
    )
}
