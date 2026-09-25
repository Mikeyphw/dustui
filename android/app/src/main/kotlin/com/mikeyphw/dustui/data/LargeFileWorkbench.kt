package com.mikeyphw.dustui.data

import java.util.Locale
import kotlin.math.max

private const val MIB_BYTES = 1024L * 1024L
private const val GIB_BYTES = 1024L * MIB_BYTES
private const val DAY_SECONDS = 24L * 60L * 60L

enum class LargeFilePreset(val label: String) {
    LARGE("100 MiB+"),
    VERY_LARGE("500 MiB+"),
    HUGE("1 GiB+"),
    OLD("Older than 90 days"),
    OLD_ARCHIVES("Old archives"),
    LARGE_VIDEOS("Large videos"),
    INSTALLERS("APK installers"),
}

enum class LargeFileSort(val label: String) {
    LARGEST("Largest"),
    OLDEST("Oldest"),
    NEWEST("Newest"),
    PATH("Path"),
    PACKAGE("Package"),
}

enum class LargeFileGroup(val label: String) {
    NONE("No grouping"),
    CATEGORY("Category"),
    DIRECTORY("Directory"),
    PACKAGE("Package"),
}

data class LargeFileFilter(
    val query: String = "",
    val preset: LargeFilePreset = LargeFilePreset.LARGE,
    val sort: LargeFileSort = LargeFileSort.LARGEST,
    val group: LargeFileGroup = LargeFileGroup.NONE,
    val category: String? = null,
    val page: Int = 0,
    val pageSize: Int = 40,
)

data class LargeFileItem(
    val entry: ScanEntryEntity,
    val category: String,
    val extension: String,
    val packageName: String?,
    val ageDays: Long?,
    val directory: String,
)

data class LargeFileSection(
    val label: String,
    val items: List<LargeFileItem>,
    val totalBytes: Long,
)

data class LargeFilePage(
    val filter: LargeFileFilter,
    val totalMatches: Int,
    val totalBytes: Long,
    val pageIndex: Int,
    val pageCount: Int,
    val pageItems: List<LargeFileItem>,
    val sections: List<LargeFileSection>,
    val categories: List<String>,
) {
    val hasPrevious: Boolean get() = pageIndex > 0
    val hasNext: Boolean get() = pageIndex + 1 < pageCount

    companion object {
        fun empty(filter: LargeFileFilter = LargeFileFilter()): LargeFilePage = LargeFilePage(
            filter = filter,
            totalMatches = 0,
            totalBytes = 0,
            pageIndex = 0,
            pageCount = 0,
            pageItems = emptyList(),
            sections = emptyList(),
            categories = emptyList(),
        )
    }
}

object LargeFileWorkbenchEngine {
    fun fromAuthorityPage(
        page: ScanBrowsePage,
        filter: LargeFileFilter,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): LargeFilePage {
        val pageItems = page.items.map { owned ->
            val entry = owned.entry
            val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val ageDays = entry.modifiedAt.takeIf { it > 0 }
                ?.let { ((nowSeconds - it).coerceAtLeast(0L) / DAY_SECONDS) }
            LargeFileItem(
                entry = entry,
                category = owned.fileCategory,
                extension = extension,
                packageName = owned.packageName,
                ageDays = ageDays,
                directory = entry.parentPath.ifBlank { "/" },
            )
        }
        val effectiveFilter = filter.copy(page = page.pageIndex, pageSize = page.limit)
        return LargeFilePage(
            filter = effectiveFilter,
            totalMatches = page.totalCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalBytes = page.totalBytes,
            pageIndex = page.pageIndex,
            pageCount = page.pageCount,
            pageItems = pageItems,
            sections = sections(pageItems, effectiveFilter.group),
            categories = pageItems.map { it.category }.distinct().sorted(),
        )
    }

    fun evaluate(
        entries: List<ScanEntryEntity>,
        filter: LargeFileFilter,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
    ): LargeFilePage {
        val allFiles = entries.asSequence()
            .filterNot { it.isDirectory }
            .map { entry -> toItem(entry, nowSeconds) }
            .toList()
        val presetMatches = allFiles.filter { matchesPreset(it, filter.preset) }
        val categories = presetMatches.asSequence()
            .map { it.category }
            .distinct()
            .sorted()
            .toList()
        val normalizedQuery = filter.query.trim()
        val matches = presetMatches.asSequence()
            .filter { filter.category == null || it.category == filter.category }
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.entry.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.entry.path.contains(normalizedQuery, ignoreCase = true) ||
                    item.packageName?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .sortedWith(comparator(filter.sort))
            .toList()
        val pageSize = filter.pageSize.coerceIn(10, 200)
        val pageCount = if (matches.isEmpty()) 0 else (matches.size + pageSize - 1) / pageSize
        val pageIndex = filter.page.coerceIn(0, max(pageCount - 1, 0))
        val pageItems = matches.drop(pageIndex * pageSize).take(pageSize)
        val effectiveFilter = filter.copy(page = pageIndex, pageSize = pageSize)
        return LargeFilePage(
            filter = effectiveFilter,
            totalMatches = matches.size,
            totalBytes = matches.sumOf { it.entry.sizeBytes },
            pageIndex = pageIndex,
            pageCount = pageCount,
            pageItems = pageItems,
            sections = sections(pageItems, effectiveFilter.group),
            categories = categories,
        )
    }

    private fun toItem(entry: ScanEntryEntity, nowSeconds: Long): LargeFileItem {
        val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val ageDays = entry.modifiedAt.takeIf { it > 0 }
            ?.let { ((nowSeconds - it).coerceAtLeast(0L) / DAY_SECONDS) }
        return LargeFileItem(
            entry = entry,
            category = AdvancedStorageAnalyzer.fileCategory(entry.name),
            extension = extension,
            packageName = EntryIntelligenceResolver.packageFromPath(entry.path),
            ageDays = ageDays,
            directory = entry.parentPath.ifBlank { "/" },
        )
    }

    private fun matchesPreset(item: LargeFileItem, preset: LargeFilePreset): Boolean = when (preset) {
        LargeFilePreset.LARGE -> item.entry.sizeBytes >= 100L * MIB_BYTES
        LargeFilePreset.VERY_LARGE -> item.entry.sizeBytes >= 500L * MIB_BYTES
        LargeFilePreset.HUGE -> item.entry.sizeBytes >= GIB_BYTES
        LargeFilePreset.OLD -> item.entry.sizeBytes >= 100L * MIB_BYTES && (item.ageDays ?: -1) >= 90
        LargeFilePreset.OLD_ARCHIVES -> item.category == "Archives" && (item.ageDays ?: -1) >= 90
        LargeFilePreset.LARGE_VIDEOS -> item.category == "Video" && item.entry.sizeBytes >= 500L * MIB_BYTES
        LargeFilePreset.INSTALLERS -> item.category == "Android packages"
    }

    private fun comparator(sort: LargeFileSort): Comparator<LargeFileItem> = when (sort) {
        LargeFileSort.LARGEST -> compareByDescending<LargeFileItem> { it.entry.sizeBytes }.thenBy { it.entry.path }
        LargeFileSort.OLDEST -> compareByDescending<LargeFileItem> { it.ageDays ?: -1L }.thenByDescending { it.entry.sizeBytes }
        LargeFileSort.NEWEST -> compareByDescending<LargeFileItem> { it.entry.modifiedAt }.thenByDescending { it.entry.sizeBytes }
        LargeFileSort.PATH -> compareBy<LargeFileItem> { it.entry.path.lowercase(Locale.ROOT) }
        LargeFileSort.PACKAGE -> compareBy<LargeFileItem> { it.packageName ?: "~" }.thenByDescending { it.entry.sizeBytes }
    }

    private fun sections(items: List<LargeFileItem>, group: LargeFileGroup): List<LargeFileSection> {
        if (items.isEmpty()) return emptyList()
        if (group == LargeFileGroup.NONE) {
            return listOf(LargeFileSection("Files", items, items.sumOf { it.entry.sizeBytes }))
        }
        val grouped = items.groupBy { item ->
            when (group) {
                LargeFileGroup.NONE -> "Files"
                LargeFileGroup.CATEGORY -> item.category
                LargeFileGroup.DIRECTORY -> item.directory
                LargeFileGroup.PACKAGE -> item.packageName ?: "No package owner"
            }
        }
        return grouped.entries
            .map { (label, owned) -> LargeFileSection(label, owned, owned.sumOf { it.entry.sizeBytes }) }
            .sortedWith(compareByDescending<LargeFileSection> { it.totalBytes }.thenBy { it.label })
    }
}
