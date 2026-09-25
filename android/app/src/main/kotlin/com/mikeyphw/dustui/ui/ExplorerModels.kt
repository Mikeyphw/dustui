package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.AdvancedStorageAnalyzer
import com.mikeyphw.dustui.data.ScanEntryEntity

enum class EntrySort { SIZE, NAME, MODIFIED }
enum class EntryTypeFilter { ALL, DIRECTORIES, FILES }

data class ExplorerFilter(
    val query: String = "",
    val sort: EntrySort = EntrySort.SIZE,
    val type: EntryTypeFilter = EntryTypeFilter.ALL,
    val minimumBytes: Long = 0,
    val category: String? = null,
)

data class CategoryFileSelection(
    val title: String,
    val categories: Set<String>,
    val entries: List<ScanEntryEntity>,
    val totalCount: Long = entries.size.toLong(),
    val truncated: Boolean = false,
)

object CategoryPathSelector {
    fun matchingEntries(entries: List<ScanEntryEntity>, categories: Set<String>): List<ScanEntryEntity> {
        if (categories.isEmpty()) return emptyList()
        return entries.asSequence()
            .filterNot { it.isDirectory }
            .filter { AdvancedStorageAnalyzer.fileCategory(it.name) in categories }
            .sortedWith(compareByDescending<ScanEntryEntity> { it.sizeBytes }.thenBy { it.path })
            .toList()
    }
}
