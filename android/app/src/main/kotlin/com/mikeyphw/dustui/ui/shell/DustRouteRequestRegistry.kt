package com.mikeyphw.dustui.ui.shell

import com.mikeyphw.dustui.ui.DustRouteSelection

/**
 * One request authority replaces the scattered per-feature AtomicLong guards.
 * The latest request is tracked by semantic purpose, but acceptance also requires
 * the active semantic route identity and optional query/draft fingerprint.
 */
class DustRouteRequestRegistry(initial: DustRouteSelection) {
    @Volatile private var activeSelection: DustRouteSelection = initial
    private val latestByPurpose = linkedMapOf<String, DustRouteBinding>()

    @Synchronized
    fun activate(selection: DustRouteSelection) {
        activeSelection = selection
        latestByPurpose.entries.removeIf { (_, binding) ->
            binding.routeKey != selection.route.routeKey ||
                binding.routeGeneration != selection.generation ||
                binding.entityKind != selection.route.entityKind ||
                binding.entityId != selection.entityId
        }
    }

    @Synchronized
    fun issue(purpose: String, fingerprint: String? = null): DustRouteBinding {
        require(purpose.isNotBlank()) { "request_purpose_required" }
        val binding = DustRouteBinding.create(activeSelection, fingerprint)
        latestByPurpose[purpose] = binding
        return binding
    }

    @Synchronized
    fun accepts(purpose: String, binding: DustRouteBinding): Boolean {
        val latest = latestByPurpose[purpose] ?: return false
        val current = activeSelection
        return latest.accepts(binding) &&
            binding.routeKey == current.route.routeKey &&
            binding.routeGeneration == current.generation &&
            binding.entityKind == current.route.entityKind &&
            binding.entityId == current.entityId
    }

    @Synchronized
    fun invalidate(purpose: String) {
        latestByPurpose.remove(purpose)
    }

    @Synchronized
    fun currentSelection(): DustRouteSelection = activeSelection
}
