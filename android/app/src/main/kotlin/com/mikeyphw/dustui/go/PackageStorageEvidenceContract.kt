package com.mikeyphw.dustui.go

/**
 * OV09 Android-side platform API collector contract.
 *
 * This file is intentionally a thin data contract. Android APIs remain the only
 * source for package/user inventory, source/split dirs, CE/DE and external path
 * visibility, but Go normalizes the evidence, owns the snapshot hash, and decides
 * package-sensitive residue/cleanup eligibility. Regex-only package inference is
 * not authoritative.
 */
data class PackageStorageEvidenceSnapshot(
    val snapshotId: String,
    val generation: Long,
    val collector: String = "android-platform-api",
    val records: List<PackageStorageEvidenceRecord>,
    val collectionCompleteness: EvidenceCompleteness,
    val degradationReasons: List<String> = emptyList(),
)

data class PackageStorageEvidenceRecord(
    val packageName: String,
    val userId: Int,
    val installed: Boolean,
    val archived: Boolean,
    val systemApp: Boolean,
    val enabledState: EnabledState,
    val sourceDirs: List<String> = emptyList(),
    val splitDirs: List<String> = emptyList(),
    val ceDataDirs: List<String> = emptyList(),
    val deDataDirs: List<String> = emptyList(),
    val externalDataDirs: List<String> = emptyList(),
    val adoptableDataDirs: List<String> = emptyList(),
    val secondaryStorageDirs: List<String> = emptyList(),
    val cacheDirs: List<String> = emptyList(),
    val nativeLibraryDirs: List<String> = emptyList(),
    val codeBytes: EvidenceBytes = EvidenceBytes.Unknown,
    val dataBytes: EvidenceBytes = EvidenceBytes.Unknown,
    val cacheBytes: EvidenceBytes = EvidenceBytes.Unknown,
    val nativeBytes: EvidenceBytes = EvidenceBytes.Unknown,
    val collectionCompleteness: EvidenceCompleteness,
    val degradationReasons: List<String> = emptyList(),
)

sealed interface EvidenceBytes {
    data class Known(val bytes: Long) : EvidenceBytes
    data object Unknown : EvidenceBytes
}

enum class EnabledState { Enabled, Disabled, Unknown }

enum class EvidenceCompleteness { Supported, Partial, Unavailable, Blocked }
