package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupRulesTest {
    @Test fun `rules filter exact current scan candidates and collapse descendants`() {
        val parent = directory("/storage/emulated/0/Download/archive")
        val child = file("/storage/emulated/0/Download/archive/old.zip", 700L * MIB, ageDays = 180)
        val outside = file("/storage/emulated/0/DCIM/photo.jpg", 900L * MIB, ageDays = 200)
        val rule = CleanupRule(
            name = "Old downloads",
            conditions = CleanupRuleConditions(
                includePaths = listOf("/storage/emulated/0/Download"),
                extensions = listOf("zip"), minimumBytes = 500L * MIB, olderThanDays = 90,
                entryType = CleanupRuleEntryType.ALL,
            ),
        )
        val preview = CleanupRuleEngine.preview("scan", "/storage/emulated/0", listOf(parent, child, outside), listOf(rule), nowEpochSeconds = NOW)
        assertEquals(listOf(child.path), preview.exactPaths)
        assertEquals(child.sizeBytes, preview.exactBytes)
    }

    @Test fun `maximum depth is relative to longest include root`() {
        val direct = file("/storage/emulated/0/Download/a.log", MIB, 2)
        val nested = file("/storage/emulated/0/Download/sub/b.log", MIB, 2)
        val conditions = CleanupRuleConditions(includePaths = listOf("/storage/emulated/0/Download"), maximumDepth = 1)
        assertTrue(CleanupRuleEngine.matches(direct, "/storage/emulated/0", conditions, emptySet(), emptyMap(), NOW))
        assertFalse(CleanupRuleEngine.matches(nested, "/storage/emulated/0", conditions, emptySet(), emptyMap(), NOW))
        assertEquals(2, CleanupRuleEngine.relativeDepth(nested.path, "/storage/emulated/0", listOf("/storage/emulated/0/Download")))
    }

    @Test fun `protected and scan root paths never become actionable`() {
        val root = directory("/storage/emulated/0")
        val rule = CleanupRule(name = "Everything", conditions = CleanupRuleConditions(entryType = CleanupRuleEntryType.ALL))
        val preview = CleanupRuleEngine.preview("scan", root.path, listOf(root), listOf(rule), nowEpochSeconds = NOW)
        assertTrue(preview.matches.single().actionableEntries.isEmpty())
        assertEquals("The scan root cannot be deleted", preview.matches.single().blocked.single().reason)
    }

    @Test fun `duplicate residue package and category conditions require exact evidence`() {
        val duplicate = file("/storage/emulated/0/Download/a.bin", MIB, ageDays = 1)
        val residue = directory("/storage/emulated/0/Android/data/com.example.gone")
        val packageFile = file("/storage/emulated/0/Android/media/com.example.app/video.mp4", 20 * MIB, 3)
        val rules = listOf(
            CleanupRule(name = "Dupes", conditions = CleanupRuleConditions(duplicateOnly = true)),
            CleanupRule(name = "Residue", conditions = CleanupRuleConditions(entryType = CleanupRuleEntryType.ALL, residueConfidence = CleanupRuleResidueConfidence.HIGH)),
            CleanupRule(name = "Package video", conditions = CleanupRuleConditions(packageName = "com.example.app", category = "Video")),
        )
        val preview = CleanupRuleEngine.preview(
            "scan", "/storage/emulated/0", listOf(duplicate, residue, packageFile), rules,
            duplicatePaths = setOf(duplicate.path), residueConfidenceByPath = mapOf(residue.path to AppResidueConfidence.HIGH), nowEpochSeconds = NOW,
        )
        assertEquals(listOf(duplicate.path), preview.matches.first { it.ruleName == "Dupes" }.entries.map { it.path })
        assertEquals(listOf(residue.path), preview.matches.first { it.ruleName == "Residue" }.entries.map { it.path })
        assertEquals(listOf(packageFile.path), preview.matches.first { it.ruleName == "Package video" }.entries.map { it.path })
    }

    @Test fun `imported rules require explicit review before enabling`() {
        val imported = CleanupRule(name = "Imported", imported = true, reviewedAt = null, enabled = true, conditions = CleanupRuleConditions())
        val validation = CleanupRuleEngine.validate(imported)
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("reviewed") })
        assertTrue(CleanupRuleEngine.validate(imported.copy(enabled = false)).valid)
        assertTrue(CleanupRuleEngine.validate(imported.copy(reviewedAt = NOW * 1000, enabled = true)).valid)
    }

    @Test fun `safe path expression rejects backtracking constructs and accepts bounded patterns`() {
        listOf("(.*)+", ".*.*", "a+", "(a|aa)", "(a)", "a{1,}", "\\1").forEach { expression ->
            assertFalse("Expected rejection: $expression", CleanupRuleEngine.validate(CleanupRule(name = "Bad", conditions = CleanupRuleConditions(regularExpression = expression))).valid)
        }
        val safe = "^/storage/[a-z]{1,8}/file[0-9]{1,3}\\.log$"
        assertNull(SafePathRegex.validate(safe))
        assertTrue(SafePathRegex.containsMatch(requireNotNull(SafePathRegex.compile(safe)), "/storage/cache/file12.log"))
    }

    @Test fun `rule codec round trips reviewed state and schema`() {
        val original = CleanupRule(
            name = "Reviewed import", imported = true, reviewedAt = 1234, enabled = false,
            conditions = CleanupRuleConditions(includePaths = listOf("/storage/emulated/0/Download"), maximumDepth = 2, regularExpression = "^/storage/[a-z]{1,8}$"),
        )
        val decoded = CleanupRuleCodec.decode(CleanupRuleCodec.encode(listOf(original)).toString()).single()
        assertEquals(CleanupRule.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.reviewedAt, decoded.reviewedAt)
        assertEquals(original.conditions, decoded.conditions)
    }

    private fun file(path: String, bytes: Long, ageDays: Int) = ScanEntryEntity(
        scanId = "scan", path = path, parentPath = path.substringBeforeLast('/'), name = path.substringAfterLast('/'),
        sizeBytes = bytes, allocatedBytes = bytes, isDirectory = false, depth = path.count { it == '/' },
        modifiedAt = NOW - ageDays * 86_400L, fileCount = 1,
    )

    private fun directory(path: String) = ScanEntryEntity(
        scanId = "scan", path = path, parentPath = path.substringBeforeLast('/').ifBlank { "/" }, name = path.substringAfterLast('/'),
        sizeBytes = 0, allocatedBytes = 0, isDirectory = true, depth = path.count { it == '/' }, modifiedAt = NOW - 200L * 86_400L, fileCount = 1,
    )

    private companion object { const val MIB = 1024L * 1024L; const val NOW = 2_000_000_000L }
}
