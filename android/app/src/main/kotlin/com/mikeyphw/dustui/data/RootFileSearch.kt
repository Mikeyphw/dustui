package com.mikeyphw.dustui.data

import java.util.Locale

data class RootFileSearchRequest(
    val query: String = "",
    val type: SearchEntryType = SearchEntryType.ANY,
    val minBytes: Long = 0L,
    val maxBytes: Long? = null,
    val category: StorageCategory? = null,
    val packageName: String? = null,
    val extension: String? = null,
    val offset: Int = 0,
    val limit: Int = 100,
)


data class RootFileSearchPage(
    val scanId: String,
    val generation: Long,
    val offset: Int,
    val limit: Int,
    val totalCount: Long,
    val totalBytes: Long,
    val hasMore: Boolean,
    val results: List<RootFileSearchResult>,
) {
    val pageIndex: Int get() = if (limit <= 0) 0 else offset / limit
    val pageCount: Int get() = if (totalCount <= 0 || limit <= 0) 0 else ((totalCount + limit - 1) / limit).toInt()
    val hasPrevious: Boolean get() = offset > 0
    companion object { fun empty() = RootFileSearchPage("", 0, 0, 100, 0, 0, false, emptyList()) }
}

data class RootFileSearchResult(
    val path: String,
    val parentPath: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val packageName: String?,
)

enum class SearchEntryType { ANY, FILES, DIRECTORIES }

object RootFileSearchEngine {
    fun search(entries: List<ScanEntryEntity>, request: RootFileSearchRequest): List<RootFileSearchResult> {
        val parsed = SearchQueryParser.parse(request.query)
        val effectiveExtension = request.extension ?: parsed.extension
        val effectiveMinBytes = maxOf(request.minBytes, parsed.minBytes ?: 0L)
        val effectivePackage = request.packageName ?: parsed.packageName
        val term = parsed.term
        return entries.asSequence()
            .filter { entry -> when (request.type) { SearchEntryType.ANY -> true; SearchEntryType.FILES -> !entry.isDirectory; SearchEntryType.DIRECTORIES -> entry.isDirectory } }
            .filter { it.sizeBytes >= effectiveMinBytes }
            .filter { request.maxBytes == null || it.sizeBytes <= request.maxBytes }
            .filter { term.isBlank() || it.name.contains(term, ignoreCase = true) || it.path.contains(term, ignoreCase = true) }
            .filter { effectiveExtension == null || !it.isDirectory && it.name.substringAfterLast('.', "").equals(effectiveExtension, ignoreCase = true) }
            .map { entry -> entry to EntryIntelligenceResolver.classify(entry.path) }
            .filter { (_, category) -> request.category == null || category == request.category }
            .map { (entry, category) -> entry to category to EntryIntelligenceResolver.packageFromPath(entry.path) }
            .filter { (_, packageName) -> effectivePackage == null || packageName == effectivePackage }
            .map { (entryAndCategory, packageName) ->
                val (entry, category) = entryAndCategory
                RootFileSearchResult(entry.path, entry.parentPath, entry.name, entry.sizeBytes, entry.modifiedAt, entry.isDirectory, category, packageName)
            }
            .sortedWith(compareByDescending<RootFileSearchResult> { it.sizeBytes }.thenBy { it.path.lowercase(Locale.ROOT) })
            .take(request.limit.coerceIn(1, 1_000))
            .toList()
    }
}

data class ParsedSearchQuery(
    val term: String,
    val extension: String?,
    val minBytes: Long?,
    val packageName: String?,
)

object SearchQueryParser {
    fun parse(raw: String): ParsedSearchQuery {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        var extension: String? = null
        var minBytes: Long? = null
        var packageName: String? = null
        val terms = mutableListOf<String>()
        tokens.forEach { token ->
            when {
                token.startsWith("*.") && token.length > 2 -> extension = token.substring(2).trimStart('.').lowercase(Locale.ROOT)
                token.startsWith("ext:", ignoreCase = true) -> extension = token.substringAfter(':').trimStart('.').lowercase(Locale.ROOT)
                token.startsWith("package:", ignoreCase = true) -> packageName = token.substringAfter(':').takeIf { it.isNotBlank() }
                token.startsWith(">") -> minBytes = parseSize(token.drop(1)) ?: minBytes
                else -> terms.add(token)
            }
        }
        return ParsedSearchQuery(terms.joinToString(" "), extension, minBytes, packageName)
    }

    private fun parseSize(raw: String): Long? {
        val match = Regex("(?i)^([0-9]+)([kmgt]?b?)?$").matchEntire(raw.trim()) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        val suffix = match.groupValues.getOrNull(2)?.lowercase(Locale.ROOT).orEmpty()
        val multiplier = when (suffix.trimEnd('b')) {
            "k" -> 1024L
            "m" -> 1024L * 1024L
            "g" -> 1024L * 1024L * 1024L
            "t" -> 1024L * 1024L * 1024L * 1024L
            else -> 1L
        }
        return value * multiplier
    }
}
