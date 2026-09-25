package com.mikeyphw.dustui.data

/** Bounded Go/SQLite persisted-scan page. */
data class ScanBrowseRequest(
    val parentPath: String? = null,
    val query: String = "",
    val entryType: String = "ANY",
    val minBytes: Long = 0L,
    val maxBytes: Long? = null,
    val category: String? = null,
    val fileCategories: List<String> = emptyList(),
    val packageName: String? = null,
    val extensions: List<String> = emptyList(),
    val modifiedBefore: Long? = null,
    val sort: String = "SIZE",
    val descending: Boolean = true,
    val offset: Int = 0,
    val limit: Int = 100,
)

data class ScanBrowseItem(
    val entry: ScanEntryEntity,
    val category: String,
    val fileCategory: String,
    val packageName: String?,
)

data class ScanBrowsePage(
    val scanId: String,
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val totalCount: Long,
    val totalBytes: Long,
    val hasMore: Boolean,
    val items: List<ScanBrowseItem>,
) {
    val entries: List<ScanEntryEntity> get() = items.map(ScanBrowseItem::entry)
    val pageIndex: Int get() = if (limit <= 0) 0 else offset / limit
    val pageCount: Int get() = if (totalCount <= 0 || limit <= 0) 0 else ((totalCount + limit - 1) / limit).toInt()
    val hasPrevious: Boolean get() = offset > 0

    companion object {
        fun empty(scanId: String = "", generation: Long = 0L, limit: Int = 100) = ScanBrowsePage(
            scanId = scanId,
            generation = generation,
            offset = 0,
            limit = limit,
            totalCount = 0,
            totalBytes = 0,
            hasMore = false,
            items = emptyList(),
        )
    }
}
