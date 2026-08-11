package com.mikeyphw.dustui.data

import java.util.Locale
import java.util.UUID

/** Entry kinds are explicit because directory matches include descendants during deletion review. */
enum class CleanupRuleEntryType(val label: String) { ALL("Any"), FILES("Files"), DIRECTORIES("Directories") }

enum class CleanupRuleResidueConfidence(val label: String, val rank: Int) {
    ANY("Any", 0),
    UNCERTAIN("Uncertain+", 1),
    PROBABLE("Probable+", 2),
    HIGH("High only", 3),
}

data class CleanupRuleConditions(
    val includePaths: List<String> = emptyList(),
    val excludePaths: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val minimumBytes: Long = 0,
    val maximumBytes: Long? = null,
    val olderThanDays: Int? = null,
    val category: String? = null,
    val packageName: String? = null,
    val regularExpression: String? = null,
    /** Maximum path depth relative to the longest matching include root, or the scan root when no include exists. */
    val maximumDepth: Int? = null,
    val entryType: CleanupRuleEntryType = CleanupRuleEntryType.FILES,
    val duplicateOnly: Boolean = false,
    val residueConfidence: CleanupRuleResidueConfidence = CleanupRuleResidueConfidence.ANY,
)

data class CleanupRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val imported: Boolean = false,
    /** Imported rules cannot be enabled until the editor records an explicit review. */
    val reviewedAt: Long? = if (imported) null else System.currentTimeMillis(),
    val schemaVersion: Int = SCHEMA_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val conditions: CleanupRuleConditions,
) {
    val reviewed: Boolean get() = !imported || reviewedAt != null
    companion object { const val SCHEMA_VERSION = 2 }
}

data class CleanupRuleValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class CleanupRuleMatch(
    val ruleId: String,
    val ruleName: String,
    val entries: List<ScanEntryEntity>,
    val exactBytes: Long,
    val blocked: List<CleanupBlockedItem>,
) {
    val actionableEntries: List<ScanEntryEntity> = entries.filter { entry -> blocked.none { it.path == entry.path } }
    val actionableBytes: Long = actionableEntries.sumOf { it.sizeBytes }
}

data class CleanupRuleDraftPreview(val rule: CleanupRule, val match: CleanupRuleMatch)

data class CleanupRulePreview(
    val scanId: String,
    val generatedAt: Long,
    val matches: List<CleanupRuleMatch>,
    val scanGeneration: Long = 1L,
) {
    val enabledRuleCount: Int = matches.size
    val exactPaths: List<String> = CleanupRuleEngine.collapseOverlaps(matches.flatMap { it.actionableEntries }.map { it.path })
    val exactBytes: Long = matches.flatMap { it.actionableEntries }
        .distinctBy { it.path }
        .filter { it.path in exactPaths }
        .sumOf { it.sizeBytes }
}

/**
 * Safe linear-ish path-regex subset.
 *
 * Java's general-purpose backtracking engine has no timeout API. DustUI therefore rejects constructs that
 * can create unbounded or ambiguous backtracking: groups, alternation, lookarounds, backreferences, `*`, `+`,
 * and open-ended repetitions. Supported expressions may use literals, anchors, `.`, character classes, `?`,
 * and bounded `{m}` / `{m,n}` repetitions with n <= 64. Matching is also capped to normalized Android path
 * length, so a cleanup preview never feeds arbitrary text into the engine.
 */
object SafePathRegex {
    private const val MAX_EXPRESSION_LENGTH = 192
    private const val MAX_PATH_LENGTH = 4_096
    private const val MAX_REPETITION = 64

    fun validate(expression: String): String? {
        if (expression.length > MAX_EXPRESSION_LENGTH) return "Regular expression is longer than $MAX_EXPRESSION_LENGTH characters"
        var inClass = false
        var escaped = false
        var index = 0
        while (index < expression.length) {
            val char = expression[index]
            if (escaped) {
                if (char.isDigit()) return "Backreferences are not supported in safe path expressions"
                escaped = false
                index++
                continue
            }
            if (char == '\\') {
                escaped = true
                index++
                continue
            }
            if (char == '[') {
                if (inClass) return "Nested character classes are not supported"
                inClass = true
                index++
                continue
            }
            if (char == ']') {
                if (!inClass) return "Unmatched character-class terminator"
                inClass = false
                index++
                continue
            }
            if (!inClass) {
                when (char) {
                    '(', ')', '|' -> return "Groups and alternation are not supported in safe path expressions"
                    '*', '+' -> return "Unbounded repetition is not supported; use a bounded {m,n} repetition"
                    '{' -> {
                        val end = expression.indexOf('}', startIndex = index + 1)
                        if (end < 0) return "Unclosed bounded repetition"
                        val body = expression.substring(index + 1, end)
                        val parts = body.split(',')
                        if (parts.size !in 1..2 || parts.any { it.isBlank() || it.toIntOrNull() == null }) {
                            return "Repetitions must use {m} or {m,n}"
                        }
                        val minimum = parts[0].toInt()
                        val maximum = parts.last().toInt()
                        if (minimum < 0 || maximum < minimum || maximum > MAX_REPETITION) {
                            return "Repetition bounds must satisfy 0 <= m <= n <= $MAX_REPETITION"
                        }
                        index = end + 1
                        continue
                    }
                    '}' -> return "Unmatched bounded-repetition terminator"
                }
            }
            index++
        }
        if (escaped) return "Expression ends with an incomplete escape"
        if (inClass) return "Unclosed character class"
        return runCatching { Regex(expression) }.exceptionOrNull()?.let { "Regular expression is invalid: ${it.message}" }
    }

    fun compile(expression: String?): Regex? {
        val value = expression?.takeIf(String::isNotBlank) ?: return null
        return if (validate(value) == null) Regex(value) else null
    }

    fun containsMatch(regex: Regex, path: String): Boolean = regex.containsMatchIn(path.take(MAX_PATH_LENGTH))
}

object CleanupRuleEngine {
    fun validate(rule: CleanupRule): CleanupRuleValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val conditions = rule.conditions
        if (rule.name.isBlank()) errors += "Rule name is required"
        if (rule.schemaVersion != CleanupRule.SCHEMA_VERSION) errors += "Unsupported rule schema ${rule.schemaVersion}"
        if (conditions.minimumBytes < 0) errors += "Minimum size cannot be negative"
        if (conditions.maximumBytes != null && conditions.maximumBytes < conditions.minimumBytes) errors += "Maximum size must be at least the minimum"
        if (conditions.olderThanDays != null && conditions.olderThanDays < 0) errors += "Age cannot be negative"
        if (conditions.maximumDepth != null && conditions.maximumDepth < 0) errors += "Maximum depth cannot be negative"
        val invalidPaths = (conditions.includePaths + conditions.excludePaths).filter { CleanupSafetyPolicy.normalize(it) == null }
        if (invalidPaths.isNotEmpty()) errors += "Include and exclude paths must be absolute and normalized"
        conditions.regularExpression?.takeIf(String::isNotBlank)?.let { expression ->
            SafePathRegex.validate(expression)?.let(errors::add)
        }
        if (rule.imported && !rule.reviewed && rule.enabled) errors += "Imported rules must be opened and reviewed before enabling"
        val broad = conditions.includePaths.isEmpty() && conditions.extensions.isEmpty() && conditions.minimumBytes == 0L &&
            conditions.olderThanDays == null && conditions.category == null && conditions.packageName == null &&
            conditions.regularExpression.isNullOrBlank() && !conditions.duplicateOnly &&
            conditions.residueConfidence == CleanupRuleResidueConfidence.ANY
        if (broad) warnings += "This rule matches a very broad scope. Review every path carefully."
        if (conditions.entryType == CleanupRuleEntryType.DIRECTORIES) warnings += "Directory matches include descendants in deletion review."
        if (rule.imported && !rule.reviewed) warnings += "Open and save this imported rule before it can be enabled."
        return CleanupRuleValidation(errors.isEmpty(), errors, warnings)
    }

    fun preview(
        scanId: String,
        scanRoot: String,
        entries: List<ScanEntryEntity>,
        rules: List<CleanupRule>,
        duplicatePaths: Set<String> = emptySet(),
        residueConfidenceByPath: Map<String, AppResidueConfidence> = emptyMap(),
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        includeDisabled: Boolean = false,
        scanGeneration: Long = 1L,
    ): CleanupRulePreview {
        val eligible = rules.filter { (includeDisabled || it.enabled) && validate(it).valid }
        val matches = eligible.map { rule ->
            val compiled = compile(rule.conditions)
            val matched = entries.asSequence()
                .filter { matches(it, scanRoot, compiled, duplicatePaths, residueConfidenceByPath, nowEpochSeconds) }
                .distinctBy { it.path }
                .sortedWith(compareByDescending<ScanEntryEntity> { it.sizeBytes }.thenBy { it.path.lowercase(Locale.ROOT) })
                .toList()
            val collapsed = collapseEntryOverlaps(matched)
            val blocked = collapsed.mapNotNull { entry ->
                CleanupSafetyPolicy.blockReason(entry.path, scanRoot)?.let { CleanupBlockedItem(entry.path, it) }
            }
            CleanupRuleMatch(rule.id, rule.name, collapsed, collapsed.sumOf { it.sizeBytes }, blocked)
        }
        return CleanupRulePreview(scanId, System.currentTimeMillis(), matches, scanGeneration)
    }

    fun matches(
        entry: ScanEntryEntity,
        scanRoot: String,
        conditions: CleanupRuleConditions,
        duplicatePaths: Set<String>,
        residueConfidenceByPath: Map<String, AppResidueConfidence>,
        nowEpochSeconds: Long,
    ): Boolean = matches(entry, scanRoot, compile(conditions), duplicatePaths, residueConfidenceByPath, nowEpochSeconds)

    /** Compatibility overload for existing callers whose entries already belong to one scan root. */
    fun matches(
        entry: ScanEntryEntity,
        conditions: CleanupRuleConditions,
        duplicatePaths: Set<String>,
        residueConfidenceByPath: Map<String, AppResidueConfidence>,
        nowEpochSeconds: Long,
    ): Boolean = matches(entry, "/", conditions, duplicatePaths, residueConfidenceByPath, nowEpochSeconds)

    private data class CompiledConditions(
        val source: CleanupRuleConditions,
        val includes: List<String>,
        val excludes: List<String>,
        val extensions: Set<String>,
        val regularExpression: Regex?,
    )

    private fun compile(conditions: CleanupRuleConditions): CompiledConditions = CompiledConditions(
        source = conditions,
        includes = conditions.includePaths.mapNotNull(CleanupSafetyPolicy::normalize),
        excludes = conditions.excludePaths.mapNotNull(CleanupSafetyPolicy::normalize),
        extensions = conditions.extensions.map { it.trim().trimStart('.').lowercase(Locale.ROOT) }.filter(String::isNotBlank).toSet(),
        regularExpression = SafePathRegex.compile(conditions.regularExpression),
    )

    private fun matches(
        entry: ScanEntryEntity,
        scanRoot: String,
        compiled: CompiledConditions,
        duplicatePaths: Set<String>,
        residueConfidenceByPath: Map<String, AppResidueConfidence>,
        nowEpochSeconds: Long,
    ): Boolean {
        val conditions = compiled.source
        val path = CleanupSafetyPolicy.normalize(entry.path) ?: return false
        if (conditions.entryType == CleanupRuleEntryType.FILES && entry.isDirectory) return false
        if (conditions.entryType == CleanupRuleEntryType.DIRECTORIES && !entry.isDirectory) return false
        if (compiled.includes.isNotEmpty() && compiled.includes.none { PathSafety.isSameOrDescendant(path, it) }) return false
        if (compiled.excludes.any { PathSafety.intersects(path, it) }) return false
        if (compiled.extensions.isNotEmpty() && entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT) !in compiled.extensions) return false
        if (entry.sizeBytes < conditions.minimumBytes) return false
        if (conditions.maximumBytes != null && entry.sizeBytes > conditions.maximumBytes) return false
        if (conditions.olderThanDays != null) {
            val cutoff = nowEpochSeconds - conditions.olderThanDays * 86_400L
            if (entry.modifiedAt <= 0L || entry.modifiedAt > cutoff) return false
        }
        if (conditions.category != null && AdvancedStorageAnalyzer.fileCategory(entry.name) != conditions.category) return false
        if (conditions.packageName != null && SnapshotAnalyzer.packageFromPath(path) != conditions.packageName) return false
        if (conditions.maximumDepth != null && relativeDepth(path, scanRoot, compiled.includes) > conditions.maximumDepth) return false
        if (compiled.regularExpression != null && !SafePathRegex.containsMatch(compiled.regularExpression, path)) return false
        if (conditions.duplicateOnly && path !in duplicatePaths) return false
        if (conditions.residueConfidence != CleanupRuleResidueConfidence.ANY) {
            val actual = residueConfidenceByPath.entries
                .filter { PathSafety.isSameOrDescendant(path, it.key) }
                .maxOfOrNull { it.value.rank } ?: 0
            if (actual < conditions.residueConfidence.rank) return false
        }
        return true
    }

    fun relativeDepth(path: String, scanRoot: String, includeRoots: List<String>): Int {
        val normalizedPath = CleanupSafetyPolicy.normalize(path) ?: return Int.MAX_VALUE
        val normalizedScanRoot = CleanupSafetyPolicy.normalize(scanRoot) ?: "/"
        val base = includeRoots.filter { PathSafety.isSameOrDescendant(normalizedPath, it) }.maxByOrNull(String::length) ?: normalizedScanRoot
        val relative = normalizedPath.removePrefix(base.trimEnd('/')).trim('/')
        return if (relative.isBlank()) 0 else relative.split('/').size
    }

    fun collapseOverlaps(paths: Collection<String>): List<String> = PathSafety.collapseRoots(paths)

    private fun collapseEntryOverlaps(entries: List<ScanEntryEntity>): List<ScanEntryEntity> {
        val byPath = entries.associateBy { it.path }
        return collapseOverlaps(byPath.keys).mapNotNull(byPath::get)
    }

    private fun isWithin(path: String, root: String): Boolean = PathSafety.isSameOrDescendant(path, root)
}

object CleanupRulePresets {
    fun defaults(now: Long = System.currentTimeMillis()): List<CleanupRule> = listOf(
        CleanupRule(name = "Old APK installers", createdAt = now, updatedAt = now, conditions = CleanupRuleConditions(extensions = listOf("apk", "apex"), olderThanDays = 30)),
        CleanupRule(name = "Old archives over 500 MiB", createdAt = now, updatedAt = now, conditions = CleanupRuleConditions(extensions = listOf("zip", "7z", "rar", "tar", "gz", "zst"), minimumBytes = 500L * 1024L * 1024L, olderThanDays = 90)),
        CleanupRule(name = "Large logs", createdAt = now, updatedAt = now, conditions = CleanupRuleConditions(extensions = listOf("log", "txt"), minimumBytes = 100L * 1024L * 1024L)),
        CleanupRule(name = "Verified duplicates", createdAt = now, updatedAt = now, conditions = CleanupRuleConditions(duplicateOnly = true)),
        CleanupRule(name = "High-confidence app residue", createdAt = now, updatedAt = now, conditions = CleanupRuleConditions(entryType = CleanupRuleEntryType.ALL, residueConfidence = CleanupRuleResidueConfidence.HIGH)),
    )
}
