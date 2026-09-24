package com.mikeyphw.dustui.ui.shell

/** OV05 route contract. The six routes are provisional grouping, not final UX count. */
enum class DustRouteId {
    Overview,
    Files,
    Clean,
    Apps,
    Activity,
    System,
}

enum class DustContentState {
    Loading,
    Empty,
    Blocked,
    Error,
    Content,
}

enum class DustActionGate {
    Allowed,
    Blocked,
    NeedsFreshScan,
    NeedsCapability,
}

data class BoundDustRoute(
    val id: DustRouteId,
    val entityId: String? = null,
    val generation: Long,
)

fun BoundDustRoute.acceptsResponse(route: BoundDustRoute): Boolean =
    id == route.id && entityId == route.entityId && generation == route.generation
