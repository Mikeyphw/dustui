package com.mikeyphw.dustui.go

/**
 * OV13 Gate D: Android reconnect is build-critical again.  This contract keeps
 * Android as a thin semantic shell over the Go engine/runtime instead of
 * reintroducing Kotlin-local truth.
 */
object ProductCutoverContract {
    const val Phase: String = "OV13"
    const val Gate: String = "Gate D"
    const val AndroidBuildability: String = "mandatory"
    const val GoRuntimeAuthority: String = "go-sqlite-runtime"
    const val LegacyPythonTextualProduction: Boolean = false
    const val LegacyKotlinDomainTruth: Boolean = false
    val Routes: List<String> = listOf("overview", "files", "clean", "apps", "activity", "system")
    val RequiredStates: Set<String> = setOf("loading", "empty", "blocked", "error", "content")
    val RequiredActionGates: Set<String> = setOf("allowed", "blocked", "needs_fresh_scan", "needs_capability")
    val MandatoryGateEvidence: Set<String> = setOf(
        "compile",
        "unit-test",
        "lint",
        "package",
        "install-smoke",
        "route-instrumentation",
        "accessibility",
        "runtime-apk-binding",
    )
}

data class ProductRouteContract(
    val route: String,
    val layoutClass: String,
    val semanticIdentity: String = route,
    val state: String,
    val actionGate: String,
    val sourceOfTruth: String = ProductCutoverContract.GoRuntimeAuthority,
) {
    init {
        require(route in ProductCutoverContract.Routes) { "unknown route: $route" }
        require(state in ProductCutoverContract.RequiredStates) { "unknown state: $state" }
        require(actionGate in ProductCutoverContract.RequiredActionGates) { "unknown action gate: $actionGate" }
        require(semanticIdentity == route) { "layout must not change semantic route identity" }
    }
}

object ProductRouteFixtures {
    fun phoneAndTabletFixtures(): List<ProductRouteContract> = ProductCutoverContract.Routes.flatMap { route ->
        listOf(
            ProductRouteContract(route = route, layoutClass = "phone", state = "content", actionGate = "allowed"),
            ProductRouteContract(route = route, layoutClass = "tablet", state = "content", actionGate = "allowed"),
        )
    }
}
