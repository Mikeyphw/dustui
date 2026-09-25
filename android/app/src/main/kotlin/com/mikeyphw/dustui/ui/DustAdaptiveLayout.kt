package com.mikeyphw.dustui.ui

import com.mikeyphw.dustui.data.ActivityRun
import com.mikeyphw.dustui.data.AppStorageInspectorAnalysis
import com.mikeyphw.dustui.data.CleanupRecommendationAnalysis
import com.mikeyphw.dustui.data.ScanEntryEntity

enum class DustWindowClass(val wireName: String) {
    Compact("compact"),
    Medium("medium"),
    Expanded("expanded"),
}

enum class DustPaneMode {
    Single,
    Supporting,
    MasterDetail,
}

data class DustAdaptiveLayoutState(
    val windowClass: DustWindowClass,
    val paneMode: DustPaneMode,
    val usesNavigationRail: Boolean,
    val masterPaneWidthDp: Int,
    val focusOrder: List<String>,
) {
    val showsMasterPane: Boolean get() = paneMode != DustPaneMode.Single
}

data class DustAdaptiveMasterItem(
    val route: DustSemanticRoute,
    val entityId: String? = null,
    val title: String,
    val detail: String,
) {
    val stableKey: String = route.routeKey + "|" + entityId.orEmpty()

    fun isSelected(selection: DustRouteSelection): Boolean =
        route == selection.route && entityId == selection.entityId

    fun selection(generation: Long): DustRouteSelection =
        DustRouteSelection(route, entityId, generation.coerceAtLeast(1L))
}

object DustAdaptiveLayoutPolicy {
    private val denseDomains = setOf(
        DustRouteDomain.Files,
        DustRouteDomain.Clean,
        DustRouteDomain.Apps,
        DustRouteDomain.Activity,
        DustRouteDomain.System,
    )

    fun classify(widthDp: Float, heightDp: Float): DustWindowClass = when {
        widthDp < 600f || heightDp < 480f -> DustWindowClass.Compact
        widthDp < 840f -> DustWindowClass.Medium
        else -> DustWindowClass.Expanded
    }

    fun resolve(widthDp: Float, heightDp: Float, selection: DustRouteSelection): DustAdaptiveLayoutState {
        val windowClass = classify(widthDp, heightDp)
        val dense = selection.domain in denseDomains
        val paneMode = when {
            !dense || windowClass == DustWindowClass.Compact -> DustPaneMode.Single
            windowClass == DustWindowClass.Medium -> DustPaneMode.Supporting
            else -> DustPaneMode.MasterDetail
        }
        return DustAdaptiveLayoutState(
            windowClass = windowClass,
            paneMode = paneMode,
            usesNavigationRail = windowClass != DustWindowClass.Compact,
            masterPaneWidthDp = when (paneMode) {
                DustPaneMode.Single -> 0
                DustPaneMode.Supporting -> 224
                DustPaneMode.MasterDetail -> 288
            },
            focusOrder = if (paneMode == DustPaneMode.Single) {
                listOf("primary-navigation", "detail", "context-actions")
            } else {
                listOf("primary-navigation", "master", "detail", "context-actions")
            },
        )
    }

    /**
     * Window changes never mutate semantic route identity. Layout is derived from the route,
     * never the other way around.
     */
    fun preserveSelection(selection: DustRouteSelection, widthDp: Float, heightDp: Float): DustRouteSelection {
        resolve(widthDp, heightDp, selection)
        return selection
    }
}

object DustAdaptiveMasterModel {
    private fun routeItems(routes: List<DustSemanticRoute>, selection: DustRouteSelection): List<DustAdaptiveMasterItem> =
        routes.map { route ->
            DustAdaptiveMasterItem(
                route = route,
                title = route.title,
                detail = if (route == selection.route) "Current section" else route.routeKey,
            )
        }

    fun fileItems(entries: List<ScanEntryEntity>, selection: DustRouteSelection): List<DustAdaptiveMasterItem> = buildList {
        addAll(routeItems(listOf(
            DustSemanticRoute.FilesExplorer,
            DustSemanticRoute.FilesSearch,
            DustSemanticRoute.FilesLarge,
            DustSemanticRoute.FilesTreemap,
        ), selection))
        entries.asSequence().sortedWith(compareByDescending<ScanEntryEntity> { it.isDirectory }.thenBy { it.name.lowercase() }).take(18).forEach { entry ->
            add(DustAdaptiveMasterItem(
                route = DustSemanticRoute.FilesItem,
                entityId = entry.path,
                title = entry.name,
                detail = if (entry.isDirectory) "Folder · ${formatBytes(entry.sizeBytes)}" else formatBytes(entry.sizeBytes),
            ))
        }
    }

    fun cleanItems(analysis: CleanupRecommendationAnalysis?, selection: DustRouteSelection): List<DustAdaptiveMasterItem> = buildList {
        addAll(routeItems(listOf(
            DustSemanticRoute.CleanRecommended,
            DustSemanticRoute.CleanDuplicates,
            DustSemanticRoute.CleanResidue,
            DustSemanticRoute.CleanRules,
        ), selection))
        analysis?.items.orEmpty().take(16).forEach { item ->
            add(DustAdaptiveMasterItem(
                route = DustSemanticRoute.CleanCandidate,
                entityId = item.id,
                title = item.title,
                detail = "${formatBytes(item.reclaimableBytes)} · ${item.confidence.name.lowercase()} confidence",
            ))
        }
    }

    fun appItems(analysis: AppStorageInspectorAnalysis?): List<DustAdaptiveMasterItem> = buildList {
        add(DustAdaptiveMasterItem(DustSemanticRoute.AppsList, title = "All apps", detail = "Measured app storage"))
        analysis?.packages.orEmpty().sortedByDescending { it.totalBytes }.take(24).forEach { report ->
            add(DustAdaptiveMasterItem(
                route = DustSemanticRoute.AppsPackage,
                entityId = report.packageName,
                title = report.appLabel.ifBlank { report.packageName },
                detail = "${formatBytes(report.totalBytes)} · ${report.evidenceState.name.lowercase()}",
            ))
        }
    }

    fun activityItems(runs: List<ActivityRun>): List<DustAdaptiveMasterItem> = buildList {
        add(DustAdaptiveMasterItem(DustSemanticRoute.ActivityList, title = "All activity", detail = "Durable operation history"))
        runs.sortedWith(compareByDescending<ActivityRun> { it.active }.thenByDescending { it.updatedAt }).take(24).forEach { run ->
            add(DustAdaptiveMasterItem(
                route = DustSemanticRoute.ActivityRun,
                entityId = run.operationId,
                title = run.kind.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() },
                detail = "${run.statusLabel} · ${run.operationId}",
            ))
        }
    }

    fun systemItems(selection: DustRouteSelection): List<DustAdaptiveMasterItem> = routeItems(
        listOf(
            DustSemanticRoute.SystemRuntime,
            DustSemanticRoute.SystemModules,
            DustSemanticRoute.SystemAutomation,
            DustSemanticRoute.SystemDiagnostics,
            DustSemanticRoute.SystemCertification,
            DustSemanticRoute.SystemSettings,
        ),
        selection,
    )

    fun forSelection(
        selection: DustRouteSelection,
        files: List<ScanEntryEntity>,
        cleanup: CleanupRecommendationAnalysis?,
        apps: AppStorageInspectorAnalysis?,
        activity: List<ActivityRun>,
    ): List<DustAdaptiveMasterItem> = when (selection.domain) {
        DustRouteDomain.Files -> fileItems(files, selection)
        DustRouteDomain.Clean -> cleanItems(cleanup, selection)
        DustRouteDomain.Apps -> appItems(apps)
        DustRouteDomain.Activity -> activityItems(activity)
        DustRouteDomain.System -> systemItems(selection)
        DustRouteDomain.Overview -> emptyList()
    }
}

object DustVisualAccessibility {
    private fun percentage(bytes: Long, total: Long): Int =
        if (total <= 0L) 0 else ((bytes * 100.0) / total).toInt().coerceIn(0, 100)

    fun storageComposition(slices: List<com.mikeyphw.dustui.data.ChartSlice>): String {
        val total = slices.sumOf { it.bytes }
        if (total <= 0L) return "No measured storage data"
        return slices.joinToString(separator = "; ") { slice ->
            "${slice.label}: ${formatBytes(slice.bytes)}, ${percentage(slice.bytes, total)} percent, ${slice.count} files"
        }
    }

    fun treemap(entries: List<ScanEntryEntity>): String {
        val shown = entries.filter { it.sizeBytes > 0 }.sortedByDescending { it.sizeBytes }.take(16)
        if (shown.isEmpty()) return "No measured files"
        return shown.joinToString(separator = "; ") { entry -> "${entry.name}: ${formatBytes(entry.sizeBytes)}" }
    }
}
