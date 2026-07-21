package com.mikeyphw.dustui.data

import java.util.Locale

data class RootModuleReport(
    val modules: List<RootModuleProfile>,
    val overlayConflicts: List<ModuleOverlayConflict>,
) {
    val totalBytes: Long = modules.sumOf { it.totalBytes }
    val disabledCount: Int = modules.count { it.disabled }
    val webUiCount: Int = modules.count { it.hasWebUi }
    val conflictCount: Int = overlayConflicts.size
}

data class RootModuleProfile(
    val moduleId: String,
    val totalBytes: Long,
    val entryCount: Int,
    val hasModuleProp: Boolean,
    val hasServiceScript: Boolean,
    val hasPostFsDataScript: Boolean,
    val hasWebUi: Boolean,
    val disabled: Boolean,
    val updatePending: Boolean,
    val overlayTargets: List<String>,
    val largestPaths: List<PackagePathSummary>,
)

data class ModuleOverlayConflict(
    val targetPath: String,
    val moduleIds: List<String>,
)

object RootModuleAnalyzer {
    private const val MODULE_PREFIX = "/data/adb/modules/"

    fun analyze(entries: List<ScanEntryEntity>): RootModuleReport {
        val grouped = entries.asSequence()
            .mapNotNull { entry -> moduleId(entry.path)?.let { it to entry } }
            .groupBy({ it.first }, { it.second })
        val profiles = grouped.map { (moduleId, owned) -> profile(moduleId, owned) }
            .sortedByDescending { it.totalBytes }
        return RootModuleReport(profiles, conflicts(profiles))
    }

    fun moduleId(path: String): String? {
        if (!path.startsWith(MODULE_PREFIX)) return null
        return path.removePrefix(MODULE_PREFIX).substringBefore('/').takeIf { it.isNotBlank() }
    }

    fun overlayTarget(path: String): String? {
        val moduleId = moduleId(path) ?: return null
        val relative = path.removePrefix(MODULE_PREFIX).removePrefix(moduleId).trimStart('/')
        if (relative.isBlank()) return null
        val first = relative.substringBefore('/')
        return when (first) {
            "system", "system_ext", "vendor", "product", "odm" -> "/$relative"
            else -> null
        }
    }

    private fun profile(moduleId: String, entries: List<ScanEntryEntity>): RootModuleProfile {
        val paths = entries.map { it.path }.toSet()
        val overlayTargets = entries.mapNotNull { overlayTarget(it.path) }.distinct().sorted()
        return RootModuleProfile(
            moduleId = moduleId,
            totalBytes = entries.sumOf { it.sizeBytes },
            entryCount = entries.size,
            hasModuleProp = paths.any { it.endsWith("/module.prop") },
            hasServiceScript = paths.any { it.endsWith("/service.sh") },
            hasPostFsDataScript = paths.any { it.endsWith("/post-fs-data.sh") },
            hasWebUi = paths.any { it.contains("/webroot/") || it.contains("/webui/") || it.endsWith("/webroot") },
            disabled = paths.any { it.endsWith("/disable") },
            updatePending = paths.any { it.endsWith("/update") || it.endsWith("/remove") },
            overlayTargets = overlayTargets.take(20),
            largestPaths = entries.sortedByDescending { it.sizeBytes }.take(6).map { PackagePathSummary(it.path, it.sizeBytes, moduleEntryKind(it.path)) },
        )
    }

    private fun conflicts(profiles: List<RootModuleProfile>): List<ModuleOverlayConflict> = profiles
        .flatMap { profile -> profile.overlayTargets.map { it.lowercase(Locale.ROOT) to profile.moduleId } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.distinct().size > 1 }
        .map { (target, modules) -> ModuleOverlayConflict(target, modules.distinct().sorted()) }
        .sortedBy { it.targetPath }

    private fun moduleEntryKind(path: String): String = when {
        path.endsWith("/module.prop") -> "Metadata"
        path.endsWith("/service.sh") || path.endsWith("/post-fs-data.sh") -> "Boot script"
        "/webroot/" in path || "/webui/" in path -> "WebUI"
        overlayTarget(path) != null -> "System overlay"
        else -> "Module file"
    }
}
