package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageInspectorTest {
    private fun entry(path: String, size: Long, directory: Boolean = false, modifiedAt: Long = 0L) = ScanEntryEntity(
        scanId = "scan",
        path = path,
        parentPath = path.substringBeforeLast('/', "/"),
        name = path.substringAfterLast('/'),
        sizeBytes = size,
        allocatedBytes = size,
        isDirectory = directory,
        depth = path.count { it == '/' },
        modifiedAt = modifiedAt,
        fileCount = if (directory) 1 else 0,
    )

    @Test
    fun `active internal state and cache are blocked while shared media remains reviewable`() {
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(
                entry("/data/user/0/com.example/databases/main.db", 10),
                entry("/data/user/0/com.example/cache/thumb.bin", 20),
                entry("/storage/emulated/0/Android/media/com.example/movie.mp4", 30),
            ),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = emptySet(),
        )
        val report = analysis.packages.single()
        assertFalse(report.buckets.first { it.kind == AppStorageBucketKind.DATABASES }.paths.single().deletionEligible)
        assertFalse(report.buckets.first { it.kind == AppStorageBucketKind.CACHE }.paths.single().deletionEligible)
        assertTrue(report.buckets.first { it.kind == AppStorageBucketKind.SHARED_MEDIA }.paths.single().deletionEligible)
    }

    @Test
    fun `totals use files only and recent growth compares the previous scan`() {
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(
                entry("/data/user/0/com.example", 999, directory = true),
                entry("/data/user/0/com.example/files/a.bin", 100),
            ),
            previousScanId = "previous",
            previousEntries = listOf(entry("/data/user/0/com.example/files/a.bin", 40)),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = emptySet(),
        )
        assertEquals(100L, analysis.packages.single().totalBytes)
        assertEquals(60L, analysis.packages.single().recentGrowthBytes)
    }

    @Test
    fun `protected shared paths are not deletion eligible`() {
        val path = "/storage/emulated/0/Android/media/com.example/movie.mp4"
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(entry(path, 30)),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = setOf("/storage/emulated/0/Android/media/com.example"),
            ignoredPackages = emptySet(),
        )
        val item = analysis.packages.single().buckets.single().paths.single()
        assertTrue(item.protected)
        assertFalse(item.deletionEligible)
    }

    @Test
    fun `filter supports installed packages and cache sorting`() {
        val analysis = AppStorageInspectorAnalysis(
            scanId = "scan",
            previousScanId = null,
            packages = listOf(
                report("one", installed = true, cache = 10, total = 100),
                report("two", installed = false, cache = 50, total = 70),
            ),
            generatedAt = 0,
        )
        val installed = AppStorageInspectorEngine.evaluate(analysis, AppStorageInspectorFilter(installedOnly = true))
        assertEquals(listOf("one"), installed.items.map { it.packageName })
        val cache = AppStorageInspectorEngine.evaluate(analysis, AppStorageInspectorFilter(sort = AppStorageSort.CACHE))
        assertEquals("two", cache.items.first().packageName)
    }

    @Test
    fun `ignored recommendation state is visible in package report`() {
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(entry("/storage/emulated/0/Android/media/com.example/file.bin", 30)),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = setOf("com.example"),
        )
        assertTrue(analysis.packages.single().recommendationsIgnored)
    }

    @Test
    fun `incomplete multi user visibility blocks unknown package deletion`() {
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(entry("/storage/emulated/10/Android/media/com.unknown/file.bin", 50)),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = emptyMap(),
            packageInventoryComplete = false,
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = emptySet(),
        )
        val item = analysis.packages.single().buckets.single().paths.single()
        assertFalse(item.deletionEligible)
        assertTrue(item.deletionReason.contains("visibility across Android users is incomplete"))
    }

    @Test
    fun `base split and native code stay attributed to the installed package`() {
        val root = "/data/app/~~hash/com.example-random"
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(
                entry("$root/base.apk", 100),
                entry("$root/split_config.arm64_v8a.apk", 30),
                entry("$root/lib/arm64/libexample.so", 20),
            ),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = emptySet(),
        )
        val report = analysis.packages.single()
        assertEquals("com.example", report.packageName)
        assertEquals(100L, report.baseApkBytes)
        assertEquals(30L, report.splitApkBytes)
        assertEquals(20L, report.nativeAndOtherCodeBytes)
        assertFalse(report.buckets.single().paths.any { it.deletionEligible })
    }

    @Test
    fun `package-named backup folders are attributed and classified`() {
        val analysis = AppStorageInspectorEngine.analyze(
            scanId = "scan",
            currentEntries = listOf(entry("/storage/emulated/0/Backups/com.example/archive.zip", 55)),
            previousScanId = null,
            previousEntries = emptyList(),
            metadata = mapOf("com.example" to AppPackageMetadata("com.example", "Example", installed = true)),
            duplicateSnapshot = null,
            residueAnalysis = null,
            protectedPaths = emptySet(),
            ignoredPackages = emptySet(),
        )
        val report = analysis.packages.single()
        assertEquals(AppStorageBucketKind.EXPORTS_BACKUPS, report.buckets.single().kind)
        assertTrue(report.buckets.single().paths.single().deletionEligible)
    }

    private fun report(packageName: String, installed: Boolean, cache: Long, total: Long): AppStoragePackageReport {
        val cacheBucket = AppStorageBucket(AppStorageBucketKind.CACHE, cache, 1, emptyList())
        val other = AppStorageBucket(AppStorageBucketKind.OTHER, total - cache, 1, emptyList())
        return AppStoragePackageReport(packageName, packageName, installed, false, null, null, total, 0, listOf(cacheBucket, other), emptyList(), emptyList(), false)
    }
}
