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

class DebugEventStore(private val limit: Int = DUST_DEBUG_EVENT_LIMIT) {
    private var nextId = 1L
    private val _events = MutableStateFlow<List<DebugEvent>>(emptyList())
    val events: StateFlow<List<DebugEvent>> = _events.asStateFlow()

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
