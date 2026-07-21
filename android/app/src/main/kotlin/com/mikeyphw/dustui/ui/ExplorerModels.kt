package com.mikeyphw.dustui.ui

enum class EntrySort { SIZE, NAME, MODIFIED }
enum class EntryTypeFilter { ALL, DIRECTORIES, FILES }

data class ExplorerFilter(
    val query: String = "",
    val sort: EntrySort = EntrySort.SIZE,
    val type: EntryTypeFilter = EntryTypeFilter.ALL,
    val minimumBytes: Long = 0,
    val category: String? = null,
)
