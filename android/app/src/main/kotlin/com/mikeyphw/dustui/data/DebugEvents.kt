package com.mikeyphw.dustui.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DUST_DEBUG_EVENT_LIMIT = 500

enum class DebugCategory {
    App,
    Navigation,
    Scan,
    Root,
    Module,
    Search,
    Recommendation,
    Deletion,
    Persistence,
    Performance,
}

enum class DebugSeverity {
    Trace,
    Info,
    Warning,
    Error,
}

data class DebugEvent(
    val id: Long,
    val timestampMillis: Long,
    val category: DebugCategory,
    val severity: DebugSeverity,
    val name: String,
    val message: String,
    val evidence: Map<String, String> = emptyMap(),
)


enum class DebugProbeGroup {
    Root,
    Scan,
    Module,
    Cleanup,
    Search,
}

enum class DebugProbeStatus {
    Pass,
    Warning,
    Failed,
}

enum class DebugProbeTarget(val title: String, val group: DebugProbeGroup, val detail: String) {
    RootService(
        title = "Root service",
        group = DebugProbeGroup.Root,
        detail = "Checks root binder identity, UID, backend, scanner, and SELinux evidence.",
    ),
    DataRootAccess(
        title = "/data access",
        group = DebugProbeGroup.Root,
        detail = "Runs a dry preflight against /data without starting a scan.",
    ),
    ModuleRootAccess(
        title = "/data/adb/modules access",
        group = DebugProbeGroup.Module,
        detail = "Runs a dry preflight against the module source root.",
    ),
    ActiveScan(
        title = "Active scan state",
        group = DebugProbeGroup.Scan,
        detail = "Explains whether the latest scan session can feed Home, Files, Clean, and Tools.",
    ),
    ZeroFileScan(
        title = "Zero-file scan reason",
        group = DebugProbeGroup.Scan,
        detail = "Flags completed scans that indexed zero entries so they never look successful silently.",
    ),
    ModuleScope(
        title = "Module scope",
        group = DebugProbeGroup.Module,
        detail = "Checks whether module values came from /data/adb/modules instead of the wrong scan root.",
    ),
    DeletionReadiness(
        title = "Deletion readiness",
        group = DebugProbeGroup.Cleanup,
        detail = "Explains why review-first permanent deletion is available or blocked.",
    ),
    SearchReadiness(
        title = "Search readiness",
        group = DebugProbeGroup.Search,
        detail = "Checks whether whole-scan search has a scan and indexed entries to search.",
    ),
}

data class DebugProbeResult(
    val target: DebugProbeTarget,
    val status: DebugProbeStatus,
    val message: String,
    val evidence: Map<String, String> = emptyMap(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

class DebugEventStore(private val limit: Int = DUST_DEBUG_EVENT_LIMIT) {
    private var nextId = 1L
    private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
    val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

    @Synchronized
    fun record(
        category: DebugCategory,
        severity: DebugSeverity,
        name: String,
        message: String,
        evidence: Map<String, String> = emptyMap(),
    ) {
        val event = DebugEvent(
            id = nextId++,
            timestampMillis = System.currentTimeMillis(),
            category = category,
            severity = severity,
            name = name,
            message = message,
            evidence = evidence.mapValues { (_, value) -> value.redactDebugValue() },
        )
        _events.value = (_events.value + event).takeLast(limit.coerceAtLeast(1))
    }

    @Synchronized
    fun clear() {
        _events.value = emptyList()
    }
}

fun String.redactDebugValue(): String = when {
    startsWith("/data/adb/modules") -> this
    startsWith("/data") -> this.replaceAfter("/data", "/…")
    contains("token", ignoreCase = true) -> "<redacted>"
    contains("secret", ignoreCase = true) -> "<redacted>"
    contains("authorization", ignoreCase = true) -> "<redacted>"
    else -> this
}
