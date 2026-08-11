package com.mikeyphw.dustui.data

import java.util.Locale
import kotlin.math.min

private const val REC_MIB = 1024L * 1024L
private const val REC_GIB = 1024L * REC_MIB
private const val REC_DAY_SECONDS = 24L * 60L * 60L

/** Explainable recommendation classes. None of them bypasses permanent-deletion review. */
enum class CleanupRecommendationKind(val label: String) {
    EXACT_DUPLICATES("Exact duplicates"),
    APP_RESIDUE("App residue"),
    LARGE_OLD_FILE("Large old files"),
    STALE_INSTALLER("Stale installers"),
    OLD_ARCHIVE("Old archives"),
    OVERSIZED_LOG("Oversized logs"),
}

enum class CleanupRecommendationConfidence(val label: String, val rank: Int) {
    VERIFIED("Verified", 4),
    HIGH("High", 3),
    MEDIUM("Medium", 2),
    LOW("Low", 1),
}

enum class CleanupRecommendationRisk(val label: String, val rank: Int) {
    LOW("Low risk", 1),
    MEDIUM("Review carefully", 2),
    HIGH("High risk", 3),
}

enum class CleanupRecommendationDisposition(val label: String) {
    ACTIVE("Active"),
    DISMISSED("Dismissed until next scan"),
    IGNORED("Ignored permanently"),
}

enum class CleanupRecommendationSort(val label: String) {
    RANKED("Ranked"),
    LARGEST("Largest"),
    CONFIDENCE("Confidence"),
    LOWEST_RISK("Lowest risk"),
}

data class CleanupRecommendationEvidence(
    val code: String,
    val label: String,
    val detail: String,
)

data class CleanupRecommendation(
    val id: String,
    val kind: CleanupRecommendationKind,
    val title: String,
    val summary: String,
    val paths: List<String>,
    val reclaimableBytes: Long,
    val confidence: CleanupRecommendationConfidence,
    val risk: CleanupRecommendationRisk,
    val score: Int,
    val ageDays: Long?,
    val evidence: List<CleanupRecommendationEvidence>,
    val expectedSha256ByPath: Map<String, String> = emptyMap(),
) {
    val batchEligible: Boolean =
        confidence.rank >= CleanupRecommendationConfidence.HIGH.rank && risk != CleanupRecommendationRisk.HIGH && paths.isNotEmpty()
}

data class CleanupRecommendationAnalysis(
    val scanId: String,
    val items: List<CleanupRecommendation>,
    val generatedAt: Long,
    val scanGeneration: Long = 1L,
) {
    val totalBytes: Long = items.sumOf { it.reclaimableBytes }
    val highConfidenceBytes: Long = items.filter { it.batchEligible }.sumOf { it.reclaimableBytes }

    companion object {
        fun empty(scanId: String = "", scanGeneration: Long = 1L): CleanupRecommendationAnalysis = CleanupRecommendationAnalysis(scanId, emptyList(), 0, scanGeneration)
    }
}

data class CleanupRecommendationFilter(
    val query: String = "",
    val kind: CleanupRecommendationKind? = null,
    val sort: CleanupRecommendationSort = CleanupRecommendationSort.RANKED,
    val showHidden: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 10,
)

data class CleanupRecommendationPage(
    val filter: CleanupRecommendationFilter,
    val items: List<CleanupRecommendation>,
    val dispositions: Map<String, CleanupRecommendationDisposition>,
    val totalMatches: Int,
    val activeCount: Int,
    val hiddenCount: Int,
    val totalBytes: Long,
    val batchPaths: List<String>,
    val batchHashes: Map<String, String>,
    val pageIndex: Int,
    val pageCount: Int,
) {
    val hasPrevious: Boolean = pageIndex > 0
    val hasNext: Boolean = pageIndex + 1 < pageCount

    companion object {
        fun empty(): CleanupRecommendationPage = CleanupRecommendationPage(
            filter = CleanupRecommendationFilter(),
            items = emptyList(),
            dispositions = emptyMap(),
            totalMatches = 0,
            activeCount = 0,
            hiddenCount = 0,
            totalBytes = 0,
            batchPaths = emptyList(),
            batchHashes = emptyMap(),
            pageIndex = 0,
            pageCount = 0,
        )
    }
}

data class CleanupRecommendationPreferences(
    val dismissedIds: Set<String> = emptySet(),
    val ignoredIds: Set<String> = emptySet(),
) {
    fun disposition(id: String): CleanupRecommendationDisposition = when (id) {
        in ignoredIds -> CleanupRecommendationDisposition.IGNORED
        in dismissedIds -> CleanupRecommendationDisposition.DISMISSED
        else -> CleanupRecommendationDisposition.ACTIVE
    }
}

object CleanupRecommendationEngine {
    fun analyze(
        scanId: String,
        entries: List<ScanEntryEntity>,
        duplicateAnalysis: DuplicateAnalysisSnapshot?,
        residueAnalysis: AppResidueAnalysis?,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
        scanGeneration: Long = 1L,
    ): CleanupRecommendationAnalysis {
        val recommendations = mutableListOf<CleanupRecommendation>()
        val claimed = PathSafety.MutableOverlapIndex()

        residueRecommendations(residueAnalysis).forEach { candidate ->
            val accepted = candidate.withUnclaimedPaths(claimed, entries)
            if (accepted != null) {
                recommendations += accepted
                claimed.addAll(accepted.paths)
            }
        }
        duplicateRecommendations(duplicateAnalysis).forEach { candidate ->
            val accepted = candidate.withUnclaimedPaths(claimed, entries)
            if (accepted != null) {
                recommendations += accepted
                claimed.addAll(accepted.paths)
            }
        }

        val files = entries.asSequence().filterNot { it.isDirectory }.toList()
        val generic = buildList {
            files.forEach { entry ->
                val ageDays = entry.ageDays(nowSeconds) ?: return@forEach
                val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                when {
                    extension in setOf("apk", "apex") && entry.sizeBytes >= 10L * REC_MIB && ageDays >= 30 -> add(
                        fileRecommendation(
                            entry,
                            CleanupRecommendationKind.STALE_INSTALLER,
                            "Old Android installer",
                            "An installer has remained in storage for at least 30 days.",
                            CleanupRecommendationConfidence.MEDIUM,
                            CleanupRecommendationRisk.MEDIUM,
                            ageDays,
                            "INSTALLER_AGE" to "Installer age",
                        ),
                    )
                    extension in setOf("zip", "7z", "rar", "tar", "gz", "zst") && entry.sizeBytes >= 50L * REC_MIB && ageDays >= 180 -> add(
                        fileRecommendation(
                            entry,
                            CleanupRecommendationKind.OLD_ARCHIVE,
                            "Old archive",
                            "A large archive has not changed for at least 180 days.",
                            CleanupRecommendationConfidence.MEDIUM,
                            CleanupRecommendationRisk.MEDIUM,
                            ageDays,
                            "ARCHIVE_AGE" to "Archive age",
                        ),
                    )
                    extension in setOf("log", "txt", "trace", "trc") && entry.sizeBytes >= 50L * REC_MIB && ageDays >= 30 -> add(
                        fileRecommendation(
                            entry,
                            CleanupRecommendationKind.OVERSIZED_LOG,
                            "Oversized log or text file",
                            "A log-like file is unusually large and has not changed recently.",
                            CleanupRecommendationConfidence.MEDIUM,
                            CleanupRecommendationRisk.MEDIUM,
                            ageDays,
                            "LOG_SIZE" to "Log size",
                        ),
                    )
                    entry.sizeBytes >= 250L * REC_MIB && ageDays >= 180 -> add(
                        fileRecommendation(
                            entry,
                            CleanupRecommendationKind.LARGE_OLD_FILE,
                            "Large old file",
                            "A large file has not changed for at least 180 days.",
                            CleanupRecommendationConfidence.MEDIUM,
                            CleanupRecommendationRisk.MEDIUM,
                            ageDays,
                            "FILE_AGE" to "File age",
                        ),
                    )
                }
            }
        }
        generic.sortedWith(compareByDescending<CleanupRecommendation> { it.score }.thenByDescending { it.reclaimableBytes }.thenBy { it.id })
            .forEach { candidate ->
                val accepted = candidate.withUnclaimedPaths(claimed, entries)
                if (accepted != null) {
                    recommendations += accepted
                    claimed.addAll(accepted.paths)
                }
            }

        return CleanupRecommendationAnalysis(
            scanId = scanId,
            items = recommendations.sortedWith(
                compareByDescending<CleanupRecommendation> { it.score }
                    .thenByDescending { it.reclaimableBytes }
                    .thenBy { it.id },
            ),
            generatedAt = System.currentTimeMillis(),
            scanGeneration = scanGeneration,
        )
    }

    fun evaluate(
        analysis: CleanupRecommendationAnalysis?,
        filter: CleanupRecommendationFilter,
        preferences: CleanupRecommendationPreferences,
    ): CleanupRecommendationPage {
        val source = analysis?.items.orEmpty()
        val dispositions = source.associate { it.id to preferences.disposition(it.id) }
        val query = filter.query.trim()
        val activeCount = dispositions.count { it.value == CleanupRecommendationDisposition.ACTIVE }
        val hiddenCount = dispositions.size - activeCount
        val filtered = source.asSequence()
            .filter { filter.showHidden || dispositions[it.id] == CleanupRecommendationDisposition.ACTIVE }
            .filter { filter.kind == null || it.kind == filter.kind }
            .filter { item ->
                query.isBlank() || item.title.contains(query, ignoreCase = true) ||
                    item.summary.contains(query, ignoreCase = true) ||
                    item.paths.any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(comparator(filter.sort))
            .toList()
        val pageSize = filter.pageSize.coerceIn(5, 50)
        val pageCount = if (filtered.isEmpty()) 0 else (filtered.size + pageSize - 1) / pageSize
        val pageIndex = filter.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val items = filtered.drop(pageIndex * pageSize).take(pageSize)
        val batchItems = items.filter { it.batchEligible && dispositions[it.id] == CleanupRecommendationDisposition.ACTIVE }
        val batchPaths = batchItems.flatMap { it.paths }.distinct()
        val batchHashes = batchItems.flatMap { item -> item.expectedSha256ByPath.entries }.associate { it.toPair() }
        return CleanupRecommendationPage(
            filter = filter.copy(page = pageIndex, pageSize = pageSize),
            items = items,
            dispositions = dispositions,
            totalMatches = filtered.size,
            activeCount = activeCount,
            hiddenCount = hiddenCount,
            totalBytes = filtered.sumOf { it.reclaimableBytes },
            batchPaths = batchPaths,
            batchHashes = batchHashes,
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
    }

    private fun residueRecommendations(analysis: AppResidueAnalysis?): List<CleanupRecommendation> = analysis?.groups.orEmpty().mapNotNull { group ->
        val paths = normalizePaths(group.recommendedDeletionPaths)
        if (group.confidence != AppResidueConfidence.HIGH || paths.isEmpty()) return@mapNotNull null
        val bytesByPath = group.locations.associate { it.path to it.sizeBytes }
        val bytes = paths.sumOf { bytesByPath[it] ?: 0L }
        val oldestSeconds = group.locations.filter { it.path in paths }.map { it.modifiedAt }.filter { it > 0 }.minOrNull()
        val ageDays = oldestSeconds?.let { ((System.currentTimeMillis() / 1000L - it).coerceAtLeast(0L) / REC_DAY_SECONDS) }
        recommendation(
            id = "residue:${group.packageName}",
            kind = CleanupRecommendationKind.APP_RESIDUE,
            title = group.appLabel ?: group.packageName,
            summary = "High-confidence folders remain for a package absent across checked Android users.",
            paths = paths,
            bytes = bytes,
            confidence = CleanupRecommendationConfidence.HIGH,
            risk = CleanupRecommendationRisk.MEDIUM,
            ageDays = ageDays,
            evidence = listOf(
                CleanupRecommendationEvidence("PACKAGE_ABSENT", "Package absence", "${group.packageName} is absent across the completed package inventory"),
                CleanupRecommendationEvidence("RESIDUE_LOCATIONS", "Managed locations", "${paths.size} unprotected high-confidence location(s)"),
            ),
        )
    }

    private fun duplicateRecommendations(snapshot: DuplicateAnalysisSnapshot?): List<CleanupRecommendation> = snapshot?.groups.orEmpty().mapNotNull { group ->
        val item = DuplicateWorkbenchEngine.groupItem(
            group,
            DuplicateFilter(keepStrategy = DuplicateKeepStrategy.NEWEST),
        )
        if (item.deletionPaths.isEmpty()) return@mapNotNull null
        val keptPath = item.copies.firstOrNull { it.key == item.keepCopyKey }?.primaryPath ?: "Protected copy"
        recommendation(
            id = "duplicate:${group.sha256}",
            kind = CleanupRecommendationKind.EXACT_DUPLICATES,
            title = "${item.deletionPaths.size} removable duplicate copy or copies",
            summary = "Full SHA-256 verification proved these physical copies contain identical content.",
            paths = item.deletionPaths,
            bytes = item.plannedBytes,
            confidence = CleanupRecommendationConfidence.VERIFIED,
            risk = CleanupRecommendationRisk.LOW,
            ageDays = null,
            evidence = listOf(
                CleanupRecommendationEvidence("SHA256_MATCH", "Verified content", group.sha256),
                CleanupRecommendationEvidence("RETAINED_COPY", "Retained copy", keptPath),
                CleanupRecommendationEvidence("PHYSICAL_COPIES", "Physical copies", "${group.physicalCopyCount}; hard links are not counted as reclaimable copies"),
            ),
            expectedSha256ByPath = item.deletionPaths.associateWith { group.sha256 },
        )
    }

    private fun fileRecommendation(
        entry: ScanEntryEntity,
        kind: CleanupRecommendationKind,
        title: String,
        summary: String,
        confidence: CleanupRecommendationConfidence,
        risk: CleanupRecommendationRisk,
        ageDays: Long,
        evidenceCode: Pair<String, String>,
    ): CleanupRecommendation = recommendation(
        id = "${kind.name.lowercase(Locale.ROOT)}:${entry.path}",
        kind = kind,
        title = title,
        summary = summary,
        paths = listOf(entry.path),
        bytes = entry.sizeBytes,
        confidence = confidence,
        risk = risk,
        ageDays = ageDays,
        evidence = listOf(
            CleanupRecommendationEvidence(evidenceCode.first, evidenceCode.second, "$ageDays days since modification"),
            CleanupRecommendationEvidence("CURRENT_SCAN_PATH", "Current scan", entry.path),
        ),
    )

    private fun recommendation(
        id: String,
        kind: CleanupRecommendationKind,
        title: String,
        summary: String,
        paths: List<String>,
        bytes: Long,
        confidence: CleanupRecommendationConfidence,
        risk: CleanupRecommendationRisk,
        ageDays: Long?,
        evidence: List<CleanupRecommendationEvidence>,
        expectedSha256ByPath: Map<String, String> = emptyMap(),
    ): CleanupRecommendation {
        val score = score(bytes, confidence, risk, ageDays)
        val rankEvidence = CleanupRecommendationEvidence(
            code = "RANK_BREAKDOWN",
            label = "Rank score $score",
            detail = scoreBreakdown(bytes, confidence, risk, ageDays),
        )
        return CleanupRecommendation(
            id = id,
            kind = kind,
            title = title,
            summary = summary,
            paths = normalizePaths(paths),
            reclaimableBytes = bytes.coerceAtLeast(0L),
            confidence = confidence,
            risk = risk,
            score = score,
            ageDays = ageDays,
            evidence = evidence + rankEvidence,
            expectedSha256ByPath = expectedSha256ByPath,
        )
    }

    private fun score(
        bytes: Long,
        confidence: CleanupRecommendationConfidence,
        risk: CleanupRecommendationRisk,
        ageDays: Long?,
    ): Int {
        val sizePoints = sizePoints(bytes)
        val confidencePoints = when (confidence) {
            CleanupRecommendationConfidence.VERIFIED -> 30
            CleanupRecommendationConfidence.HIGH -> 26
            CleanupRecommendationConfidence.MEDIUM -> 16
            CleanupRecommendationConfidence.LOW -> 8
        }
        val riskPoints = when (risk) {
            CleanupRecommendationRisk.LOW -> 20
            CleanupRecommendationRisk.MEDIUM -> 10
            CleanupRecommendationRisk.HIGH -> 0
        }
        val agePoints = ageDays?.let { min(10, (it / 30L).toInt()) } ?: 0
        return (sizePoints + confidencePoints + riskPoints + agePoints).coerceIn(0, 100)
    }

    private fun scoreBreakdown(
        bytes: Long,
        confidence: CleanupRecommendationConfidence,
        risk: CleanupRecommendationRisk,
        ageDays: Long?,
    ): String {
        val confidencePoints = when (confidence) {
            CleanupRecommendationConfidence.VERIFIED -> 30
            CleanupRecommendationConfidence.HIGH -> 26
            CleanupRecommendationConfidence.MEDIUM -> 16
            CleanupRecommendationConfidence.LOW -> 8
        }
        val riskPoints = when (risk) {
            CleanupRecommendationRisk.LOW -> 20
            CleanupRecommendationRisk.MEDIUM -> 10
            CleanupRecommendationRisk.HIGH -> 0
        }
        val agePoints = ageDays?.let { min(10, (it / 30L).toInt()) } ?: 0
        return "${sizePoints(bytes)} size + $confidencePoints confidence + $riskPoints safety + $agePoints age"
    }

    private fun sizePoints(bytes: Long): Int = when {
        bytes >= 10L * REC_GIB -> 40
        bytes >= REC_GIB -> 34
        bytes >= 500L * REC_MIB -> 28
        bytes >= 100L * REC_MIB -> 22
        bytes >= 50L * REC_MIB -> 16
        else -> 8
    }

    private fun CleanupRecommendation.withUnclaimedPaths(
        claimed: PathSafety.MutableOverlapIndex,
        entries: List<ScanEntryEntity>,
    ): CleanupRecommendation? {
        val remaining = paths.filterNot { path -> claimed.intersects(path) }
        if (remaining.isEmpty()) return null
        if (remaining.size == paths.size) return this
        val sizes = entries.associate { it.path to it.sizeBytes }
        val bytes = if (kind == CleanupRecommendationKind.EXACT_DUPLICATES && paths.isNotEmpty()) {
            (reclaimableBytes / paths.size) * remaining.size
        } else {
            remaining.sumOf { sizes[it] ?: 0L }
        }
        val updatedScore = score(bytes, confidence, risk, ageDays)
        val updatedRank = CleanupRecommendationEvidence(
            code = "RANK_BREAKDOWN",
            label = "Rank score $updatedScore",
            detail = scoreBreakdown(bytes, confidence, risk, ageDays),
        )
        return copy(
            paths = remaining,
            reclaimableBytes = bytes,
            score = updatedScore,
            evidence = evidence.filterNot { it.code == "RANK_BREAKDOWN" } + updatedRank,
            expectedSha256ByPath = expectedSha256ByPath.filterKeys { it in remaining },
        )
    }

    private fun comparator(sort: CleanupRecommendationSort): Comparator<CleanupRecommendation> = when (sort) {
        CleanupRecommendationSort.RANKED -> compareByDescending<CleanupRecommendation> { it.score }
            .thenByDescending { it.reclaimableBytes }
            .thenBy { it.id }
        CleanupRecommendationSort.LARGEST -> compareByDescending<CleanupRecommendation> { it.reclaimableBytes }
            .thenByDescending { it.score }
            .thenBy { it.id }
        CleanupRecommendationSort.CONFIDENCE -> compareByDescending<CleanupRecommendation> { it.confidence.rank }
            .thenByDescending { it.score }
            .thenBy { it.id }
        CleanupRecommendationSort.LOWEST_RISK -> compareBy<CleanupRecommendation> { it.risk.rank }
            .thenByDescending { it.score }
            .thenBy { it.id }
    }

    private fun ScanEntryEntity.ageDays(nowSeconds: Long): Long? = modifiedAt.takeIf { it > 0 }
        ?.let { ((nowSeconds - it).coerceAtLeast(0L) / REC_DAY_SECONDS) }

    private fun normalizePaths(paths: Collection<String>): List<String> = PathSafety.collapseRoots(paths)
}
