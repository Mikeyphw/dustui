package com.mikeyphw.dustui.ui

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class DustRouteDomain(val label: String, val shortLabel: String, val symbol: String) {
    Overview("Overview", "Home", "◌"),
    Files("Files", "Files", "▦"),
    Clean("Clean", "Clean", "✦"),
    Apps("Apps", "Apps", "▣"),
    Activity("Activity", "Activity", "↺"),
    System("System", "System", "•••"),
}

enum class DustSemanticRoute(
    val routeKey: String,
    val domain: DustRouteDomain,
    val title: String,
    val entityKind: String? = null,
    val requiresScanData: Boolean = false,
) {
    OverviewHome("overview/home", DustRouteDomain.Overview, "Storage health"),
    OverviewComposition("overview/composition", DustRouteDomain.Overview, "Storage composition", requiresScanData = true),
    OverviewTrends("overview/trends", DustRouteDomain.Overview, "Storage trends", requiresScanData = true),
    OverviewScanHistory("overview/scan-history", DustRouteDomain.Overview, "Scan history"),

    FilesExplorer("files/explorer", DustRouteDomain.Files, "Files"),
    FilesSearch("files/search", DustRouteDomain.Files, "Whole-scan search", requiresScanData = true),
    FilesLarge("files/large", DustRouteDomain.Files, "Large files", requiresScanData = true),
    FilesTreemap("files/treemap", DustRouteDomain.Files, "Treemap", requiresScanData = true),
    FilesItem("files/item", DustRouteDomain.Files, "File details", entityKind = "path", requiresScanData = true),

    CleanRecommended("clean/recommended", DustRouteDomain.Clean, "Recommended cleanup"),
    CleanDuplicates("clean/duplicates", DustRouteDomain.Clean, "Duplicates", requiresScanData = true),
    CleanResidue("clean/residue", DustRouteDomain.Clean, "App residue", requiresScanData = true),
    CleanRules("clean/rules", DustRouteDomain.Clean, "Cleanup rules", requiresScanData = true),
    CleanCandidate("clean/candidate", DustRouteDomain.Clean, "Cleanup candidate", entityKind = "candidate", requiresScanData = true),
    CleanReview("clean/review", DustRouteDomain.Clean, "Cleanup review", entityKind = "manifest"),

    AppsList("apps/list", DustRouteDomain.Apps, "App storage", requiresScanData = true),
    AppsPackage("apps/package", DustRouteDomain.Apps, "App package", entityKind = "package", requiresScanData = true),
    AppsPaths("apps/paths", DustRouteDomain.Apps, "App paths", entityKind = "package", requiresScanData = true),

    ActivityList("activity/list", DustRouteDomain.Activity, "Activity"),
    ActivityRun("activity/run", DustRouteDomain.Activity, "Run details", entityKind = "operation", requiresScanData = false),
    ActivityEvidence("activity/evidence", DustRouteDomain.Activity, "Run evidence", entityKind = "operation", requiresScanData = false),

    SystemRuntime("system/runtime", DustRouteDomain.System, "Runtime"),
    SystemModules("system/modules", DustRouteDomain.System, "Modules"),
    SystemAutomation("system/automation", DustRouteDomain.System, "Automation"),
    SystemDiagnostics("system/diagnostics", DustRouteDomain.System, "Diagnostics"),
    SystemCertification("system/certification", DustRouteDomain.System, "Certification"),
    SystemSettings("system/settings", DustRouteDomain.System, "Settings"),
    ;

    val actionLabel: String
        get() = when (domain) {
            DustRouteDomain.Overview -> "Export"
            DustRouteDomain.Files -> "Actions"
            DustRouteDomain.Clean -> "Actions"
            DustRouteDomain.Apps -> "Apps"
            DustRouteDomain.Activity -> "Actions"
            DustRouteDomain.System -> "System"
        }

    companion object {
        val all: List<DustSemanticRoute> = entries.toList()
        val byKey: Map<String, DustSemanticRoute> = all.associateBy { it.routeKey }
    }
}

data class DustRouteSelection(
    val route: DustSemanticRoute,
    val entityId: String? = null,
    val generation: Long = 1L,
) {
    init {
        require(generation > 0) { "generation_required" }
        require(route.entityKind != null || entityId == null) { "entity_not_allowed:${route.routeKey}" }
        require(route.entityKind == null || !entityId.isNullOrBlank()) { "entity_required:${route.routeKey}" }
    }

    val routeKey: String get() = route.routeKey
    val domain: DustRouteDomain get() = route.domain

    fun withGeneration(next: Long): DustRouteSelection = copy(generation = next.coerceAtLeast(1L))
}

enum class DustRouteProblemCode {
    InvalidDeepLink,
    UnknownRoute,
    EntityRequired,
    EntityNotFound,
    EntityStale,
}

data class DustRouteProblem(
    val code: DustRouteProblemCode,
    val detail: String,
    val route: DustSemanticRoute? = null,
    val entityId: String? = null,
)

sealed interface DustRouteResolution {
    data class Resolved(val selection: DustRouteSelection) : DustRouteResolution
    data class Rejected(val problem: DustRouteProblem) : DustRouteResolution
}

object DustSemanticNavigation {
    val primaryRoutes: List<DustSemanticRoute> = listOf(
        DustSemanticRoute.OverviewHome,
        DustSemanticRoute.FilesExplorer,
        DustSemanticRoute.CleanRecommended,
        DustSemanticRoute.AppsList,
        DustSemanticRoute.ActivityList,
        DustSemanticRoute.SystemRuntime,
    )

    fun primaryFor(domain: DustRouteDomain): DustSemanticRoute = primaryRoutes.first { it.domain == domain }

    fun needsScanData(selection: DustRouteSelection): Boolean = selection.route.requiresScanData

    fun backTarget(selection: DustRouteSelection): DustRouteSelection? = when (selection.route) {
        DustSemanticRoute.OverviewHome -> null
        DustSemanticRoute.FilesExplorer,
        DustSemanticRoute.CleanRecommended,
        DustSemanticRoute.AppsList,
        DustSemanticRoute.ActivityList,
        DustSemanticRoute.SystemRuntime -> DustRouteSelection(DustSemanticRoute.OverviewHome, generation = selection.generation + 1)
        else -> DustRouteSelection(primaryFor(selection.domain), generation = selection.generation + 1)
    }

    fun deepLink(selection: DustRouteSelection): String {
        val encoded = selection.entityId?.let { URLEncoder.encode(it, StandardCharsets.UTF_8.name()).replace("+", "%20") }
        return buildString {
            append("dustui://")
            append(selection.route.routeKey)
            if (encoded != null) {
                append('/')
                append(encoded)
            }
        }
    }

    fun resolveDeepLink(raw: String?, generation: Long): DustRouteResolution {
        if (raw.isNullOrBlank()) return DustRouteResolution.Resolved(DustRouteSelection(DustSemanticRoute.OverviewHome, generation = generation))
        val uri = runCatching { URI(raw) }.getOrElse {
            return DustRouteResolution.Rejected(DustRouteProblem(DustRouteProblemCode.InvalidDeepLink, "Malformed deep link"))
        }
        if (!uri.scheme.equals("dustui", ignoreCase = true)) {
            return DustRouteResolution.Rejected(DustRouteProblem(DustRouteProblemCode.InvalidDeepLink, "Unsupported deep-link scheme: ${uri.scheme ?: "none"}"))
        }
        val segments = buildList {
            uri.host?.takeIf { it.isNotBlank() }?.let(::add)
            uri.rawPath.orEmpty().split('/').filter { it.isNotBlank() }.forEach { rawSegment ->
                add(runCatching { URLDecoder.decode(rawSegment, StandardCharsets.UTF_8.name()) }.getOrDefault(rawSegment))
            }
        }
        if (segments.isEmpty()) return DustRouteResolution.Resolved(DustRouteSelection(DustSemanticRoute.OverviewHome, generation = generation))

        val exact = DustSemanticRoute.all.firstOrNull { route ->
            val expected = route.routeKey.split('/')
            segments.size >= expected.size && segments.take(expected.size) == expected
        }
        if (exact == null) {
            return DustRouteResolution.Rejected(
                DustRouteProblem(DustRouteProblemCode.UnknownRoute, "Unknown semantic route: ${segments.joinToString("/")}"),
            )
        }
        val expectedCount = exact.routeKey.split('/').size
        val entity = segments.drop(expectedCount).joinToString("/").takeIf { it.isNotBlank() }
        if (exact.entityKind != null && entity.isNullOrBlank()) {
            return DustRouteResolution.Rejected(
                DustRouteProblem(DustRouteProblemCode.EntityRequired, "${exact.entityKind} identity is required", exact),
            )
        }
        if (exact.entityKind == null && !entity.isNullOrBlank()) {
            return DustRouteResolution.Rejected(
                DustRouteProblem(DustRouteProblemCode.InvalidDeepLink, "Unexpected entity identity for ${exact.routeKey}", exact, entity),
            )
        }
        return DustRouteResolution.Resolved(DustRouteSelection(exact, entity, generation.coerceAtLeast(1L)))
    }
}
