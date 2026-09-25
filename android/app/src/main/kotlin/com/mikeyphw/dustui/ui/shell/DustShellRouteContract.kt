package com.mikeyphw.dustui.ui.shell

import com.mikeyphw.dustui.ui.DustRouteSelection
import org.json.JSONObject
import java.util.UUID

enum class DustContentState {
    Loading,
    Empty,
    Blocked,
    Error,
    Content,
    ;

    companion object {
        fun fromWire(value: String): DustContentState = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: error("unknown_screen_state:$value")
    }
}

data class DustRemediationAction(
    val code: String,
    val label: String,
    val routeKey: String? = null,
)

data class DustActionGate(
    val allowed: Boolean,
    val reasonCode: String,
    val message: String,
    val remediationActions: List<DustRemediationAction>,
) {
    init {
        require(reasonCode.isNotBlank()) { "action_gate_reason_required" }
        require(message.isNotBlank()) { "action_gate_message_required" }
    }

    companion object {
        fun fromWire(json: JSONObject): DustActionGate = DustActionGate(
            allowed = json.getBoolean("allowed"),
            reasonCode = json.getString("reasonCode"),
            message = json.getString("message"),
            remediationActions = json.optJSONArray("remediationActions")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            DustRemediationAction(
                                code = item.getString("code"),
                                label = item.getString("label"),
                                routeKey = item.optString("routeKey").takeIf(String::isNotBlank),
                            ),
                        )
                    }
                }
            }.orEmpty(),
        )
    }
}

/**
 * Canonical async identity for every content-route request.
 *
 * No response is allowed to update UI state unless all populated identity fields
 * still match. The optional fingerprint binds searches/drafts to their semantic
 * input, not merely to whichever request finished last.
 */
data class DustRouteBinding(
    val routeKey: String,
    val routeGeneration: Long,
    val entityKind: String?,
    val entityId: String?,
    val requestId: String,
    val fingerprint: String? = null,
) {
    init {
        require(routeKey.isNotBlank()) { "route_key_required" }
        require(routeGeneration > 0) { "route_generation_required" }
        require(requestId.isNotBlank()) { "request_id_required" }
        require((entityKind == null) == (entityId == null)) { "entity_binding_incomplete" }
    }

    fun accepts(response: DustRouteBinding): Boolean = this == response

    fun toWireJson(): JSONObject = JSONObject()
        .put("routeKey", routeKey)
        .put("routeGeneration", routeGeneration)
        .put("requestId", requestId)
        .apply {
            entityKind?.let { put("entityKind", it) }
            entityId?.let { put("entityId", it) }
            fingerprint?.let { put("fingerprint", it) }
        }

    companion object {
        fun create(selection: DustRouteSelection, fingerprint: String? = null): DustRouteBinding = DustRouteBinding(
            routeKey = selection.route.routeKey,
            routeGeneration = selection.generation,
            entityKind = selection.route.entityKind,
            entityId = selection.entityId,
            requestId = UUID.randomUUID().toString(),
            fingerprint = fingerprint?.takeIf(String::isNotBlank),
        )

        fun fromWire(json: JSONObject): DustRouteBinding = DustRouteBinding(
            routeKey = json.getString("routeKey"),
            routeGeneration = json.getLong("routeGeneration"),
            entityKind = json.optString("entityKind").takeIf(String::isNotBlank),
            entityId = json.optString("entityId").takeIf(String::isNotBlank),
            requestId = json.getString("requestId"),
            fingerprint = json.optString("fingerprint").takeIf(String::isNotBlank),
        )
    }
}

sealed interface DustScreenState<out T> {
    val binding: DustRouteBinding

    data class Loading(override val binding: DustRouteBinding) : DustScreenState<Nothing>

    data class Empty(
        override val binding: DustRouteBinding,
        val reasonCode: String,
        val message: String,
        val primaryRecovery: DustRemediationAction,
    ) : DustScreenState<Nothing>

    data class Blocked(
        override val binding: DustRouteBinding,
        val reasonCode: String,
        val message: String,
        val actionGates: List<DustActionGate>,
    ) : DustScreenState<Nothing>

    data class Error(
        override val binding: DustRouteBinding,
        val problemRef: String,
        val message: String,
        val actionGates: List<DustActionGate>,
    ) : DustScreenState<Nothing>

    data class Content<T>(
        override val binding: DustRouteBinding,
        val data: T,
        val freshness: String,
        val actionGates: List<DustActionGate>,
    ) : DustScreenState<T>
}

data class DustWireScreenState(
    val kind: DustContentState,
    val binding: DustRouteBinding,
    val reasonCode: String?,
    val message: String?,
    val problemRef: String?,
    val freshness: String?,
    val primaryRecovery: DustRemediationAction?,
    val actionGates: List<DustActionGate>,
) {
    companion object {
        fun fromWire(json: JSONObject): DustWireScreenState {
            val recovery = json.optJSONObject("primaryRecovery")?.let { item ->
                DustRemediationAction(
                    code = item.getString("code"),
                    label = item.getString("label"),
                    routeKey = item.optString("routeKey").takeIf(String::isNotBlank),
                )
            }
            val gates = json.optJSONArray("actionGates")?.let { array ->
                buildList { for (index in 0 until array.length()) add(DustActionGate.fromWire(array.getJSONObject(index))) }
            }.orEmpty()
            return DustWireScreenState(
                kind = DustContentState.fromWire(json.getString("kind")),
                binding = DustRouteBinding.fromWire(json.getJSONObject("binding")),
                reasonCode = json.optString("reasonCode").takeIf(String::isNotBlank),
                message = json.optString("message").takeIf(String::isNotBlank),
                problemRef = json.optString("problemRef").takeIf(String::isNotBlank),
                freshness = json.optString("freshness").takeIf(String::isNotBlank),
                primaryRecovery = recovery,
                actionGates = gates,
            )
        }
    }
}
