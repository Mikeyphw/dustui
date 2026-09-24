package com.mikeyphw.dustui.go

/**
 * OV05 reconnect scaffold only. Android buildability is intentionally suspended
 * until OV13; this contract documents the fixed app-owned Go runtime boundary.
 */
data class GoRuntimeSpec(
    val productionAbi: String = "arm64-v8a",
    val testOnlyAbi: String = "x86_64",
    val runtimeLibraryName: String = "libdustui_go.so",
    val maxFrameBytes: Int = 1 shl 20,
    val protocol: String = "dustui.android.runtime.session.v1",
)

enum class GoRuntimeSessionState {
    Stopped,
    Starting,
    Ready,
    Dead,
    ReconcileRequired,
}

data class GoRuntimeRequest(
    val requestId: String,
    val sequence: Long,
    val generation: Long,
    val type: String,
    val payloadJson: String,
)

data class GoRuntimeResponse(
    val requestId: String,
    val sequence: Long,
    val generation: Long,
    val type: String,
    val payloadJson: String,
)

object GoRuntimePathContract {
    fun executablePath(nativeLibraryDir: String): String {
        require(nativeLibraryDir.isNotBlank()) { "nativeLibraryDir required" }
        return nativeLibraryDir.trimEnd('/') + "/" + GoRuntimeSpec().runtimeLibraryName
    }
}

fun GoRuntimeResponse.matches(request: GoRuntimeRequest): Boolean =
    requestId == request.requestId && sequence == request.sequence && generation == request.generation
