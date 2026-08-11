package com.mikeyphw.dustui.data

import java.io.File
import java.util.Locale

private const val MIB = 1024L * 1024L
private const val DAY_MS = 24L * 60L * 60L * 1000L

data class DuplicateCandidateGroup(
    val sizeBytes: Long,
    val paths: List<String>,
    val estimatedSavingsBytes: Long,
)

data class LargeOldFile(
    val path: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val ageDays: Long,
)

data class ModuleFootprint(
    val moduleId: String,
    val totalBytes: Long,
    val entryCount: Int,
)

data class StorageBucket(val label: String, val bytes: Long, val count: Int)

data class MountTopology(
    val source: String,
    val target: String,
    val filesystem: String,
    val bind: Boolean,
    val overlay: Boolean,
    val readOnly: Boolean,
)

data class AdvancedStorageReport(
    val duplicateCandidates: List<DuplicateCandidateGroup>,
    val largeOldFiles: List<LargeOldFile>,
    val moduleFootprints: List<ModuleFootprint>,
    val typeBuckets: List<StorageBucket>,
    val ageBuckets: List<StorageBucket>,
    val mountTopology: List<MountTopology>,
    val orphanedPackages: List<AppStorageSummary>,
)

object AdvancedStorageAnalyzer {
    fun analyze(
        entries: List<ScanEntryEntity>,
        installedPackages: Set<String>,
        mountText: String,
        now: Long = System.currentTimeMillis(),
    ): AdvancedStorageReport {
        val files = entries.filterNot { it.isDirectory }
        val apps = SnapshotAnalyzer.appSummaries(entries, installedPackages)
        return AdvancedStorageReport(
            duplicateCandidates = duplicateCandidates(files),
            largeOldFiles = largeOldFiles(files, now),
            moduleFootprints = moduleFootprints(entries),
            typeBuckets = typeBuckets(files),
            ageBuckets = ageBuckets(files, now),
            mountTopology = parseMounts(mountText),
            orphanedPackages = apps.filter { it.orphaned },
        )
    }

    fun duplicateCandidates(files: List<ScanEntryEntity>): List<DuplicateCandidateGroup> = files.asSequence()
        .filter { it.sizeBytes >= MIB }
        .groupBy { it.sizeBytes to extensionOf(it.name) }
        .values
        .filter { it.size > 1 }
        .map { group ->
            val ordered = group.sortedBy { it.path }
            DuplicateCandidateGroup(ordered.first().sizeBytes, ordered.map { it.path }, ordered.first().sizeBytes * (ordered.size - 1L))
        }
        .sortedByDescending { it.estimatedSavingsBytes }
        .take(50)

    fun largeOldFiles(files: List<ScanEntryEntity>, now: Long): List<LargeOldFile> = files.asSequence()
        .map { it to modifiedAtMillis(it.modifiedAt) }
        .filter { (entry, modifiedAt) -> entry.sizeBytes >= 100L * MIB && modifiedAt > 0 && now - modifiedAt >= 180L * DAY_MS }
        .map { (entry, modifiedAt) -> LargeOldFile(entry.path, entry.sizeBytes, modifiedAt, ((now - modifiedAt) / DAY_MS).coerceAtLeast(0)) }
        .sortedByDescending { it.sizeBytes }
        .take(100)
        .toList()

    fun moduleFootprints(entries: List<ScanEntryEntity>): List<ModuleFootprint> = entries.asSequence()
        .mapNotNull { entry ->
            val marker = "/data/adb/modules/"
            val index = entry.path.indexOf(marker)
            if (index < 0) null else entry.path.substring(index + marker.length).substringBefore('/').takeIf { it.isNotBlank() }?.let { it to entry }
        }
        .groupBy({ it.first }, { it.second })
        .map { (module, owned) -> ModuleFootprint(module, owned.sumOf { it.sizeBytes }, owned.size) }
        .sortedByDescending { it.totalBytes }

    fun typeBuckets(files: List<ScanEntryEntity>): List<StorageBucket> = files.groupBy { fileCategory(it.name) }
        .map { (label, owned) -> StorageBucket(label, owned.sumOf { it.sizeBytes }, owned.size) }
        .sortedByDescending { it.bytes }

    fun ageBuckets(files: List<ScanEntryEntity>, now: Long): List<StorageBucket> {
        fun bucket(modifiedAt: Long): String = when {
            modifiedAt <= 0 -> "Unknown age"
            now - modifiedAt < 7L * DAY_MS -> "Last 7 days"
            now - modifiedAt < 30L * DAY_MS -> "8–30 days"
            now - modifiedAt < 180L * DAY_MS -> "31–180 days"
            now - modifiedAt < 365L * DAY_MS -> "181–365 days"
            else -> "Older than 1 year"
        }
        return files.groupBy { bucket(modifiedAtMillis(it.modifiedAt)) }
            .map { (label, owned) -> StorageBucket(label, owned.sumOf { it.sizeBytes }, owned.size) }
            .sortedByDescending { it.bytes }
    }

    private fun modifiedAtMillis(value: Long): Long = when {
        value <= 0L -> value
        value < 10_000_000_000L -> value * 1000L
        else -> value
    }

    fun parseMounts(text: String): List<MountTopology> = text.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 4) return@mapNotNull null
        val options = parts[3].split(',').toSet()
        MountTopology(parts[0], parts[1], parts[2], "bind" in options || "rbind" in options, parts[2] == "overlay", "ro" in options)
    }.filter { it.bind || it.overlay }.distinctBy { it.target }.sortedBy { it.target }.toList()

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun fileCategory(name: String): String = when (extensionOf(name)) {
        "apk", "apex" -> "Android packages"
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> "Images"
        "mp4", "mkv", "webm", "avi", "mov" -> "Video"
        "mp3", "m4a", "ogg", "flac", "wav" -> "Audio"
        "zip", "7z", "rar", "tar", "gz", "zst" -> "Archives"
        "db", "sqlite", "sqlite3" -> "Databases"
        "log", "txt" -> "Logs and text"
        "dex", "odex", "vdex", "so" -> "Runtime and native code"
        else -> "Other"
    }
}

internal fun readMountTable(): String = runCatching { File("/proc/mounts").readText() }.getOrDefault("")
