package com.mikeyphw.dustui.go

/**
 * Supported Android-to-Go product interface. These values describe the
 * source contract, not the state of a specific APK, signing identity or device.
 * Host verification is performed in GitHub Actions and device acceptance.
 */
object ProductRuntimeContract {
    const val Protocol: String = "dustui.product.v2"
    const val AndroidBuildability: String = "mandatory"
    const val TargetGoRuntimeAuthority: String = "go-sqlite-runtime"
    const val RuntimeProtocol: String = "dustui.android.runtime.session.v1"
    const val SQLiteSchemaProtocol: String = "dustui.sqlite.schema.v1"
    val Routes: List<String> = listOf("overview", "files", "clean", "apps", "activity", "system")
    val RequiredStates: Set<String> = setOf("loading", "empty", "blocked", "error", "content")
    val RequiredActionGateFields: Set<String> = setOf("allowed", "reasonCode", "message", "remediationActions")
}

data class ProductRouteContract(
    val route: String,
    val layoutClass: String,
    val semanticIdentity: String = route,
    val state: String,
    val actionGateReasonCode: String,
    val targetSourceOfTruth: String = ProductRuntimeContract.TargetGoRuntimeAuthority,
) {
    init {
        require(route in ProductRuntimeContract.Routes) { "unknown route: $route" }
        require(state in ProductRuntimeContract.RequiredStates) { "unknown state: $state" }
        require(actionGateReasonCode.isNotBlank()) { "action gate reason code is required" }
        require(semanticIdentity == route) { "layout must not change semantic route identity" }
    }
}

object ProductRouteFixtures {
    fun phoneAndTabletFixtures(): List<ProductRouteContract> = ProductRuntimeContract.Routes.flatMap { route ->
        listOf(
            ProductRouteContract(route = route, layoutClass = "phone", state = "content", actionGateReasonCode = "allowed"),
            ProductRouteContract(route = route, layoutClass = "tablet", state = "content", actionGateReasonCode = "allowed"),
        )
    }
}
