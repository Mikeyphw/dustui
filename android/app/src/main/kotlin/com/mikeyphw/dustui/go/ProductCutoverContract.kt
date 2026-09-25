package com.mikeyphw.dustui.go

/**
 * Gate-D target contract.
 *
 * This object intentionally does not claim that Gate D is qualified or that
 * legacy authorities are already absent. Runtime qualification truth lives in
 * migration/qualification/v1/current_state.json and is produced only from the
 * executed-evidence registry.
 */
object ProductCutoverContract {
    const val Phase: String = "OV13"
    const val Gate: String = "Gate D"
    const val AndroidBuildability: String = "mandatory"
    const val TargetGoRuntimeAuthority: String = "go-sqlite-runtime"
    const val QualificationStateSource: String = "migration/qualification/v1/current_state.json"
    const val QualificationEvidenceSource: String = "migration/qualification/v1/gate_d_evidence.json"
    const val TargetLegacyPythonRetired: Boolean = true
    const val TargetLegacyKotlinDomainRetired: Boolean = true
    val Routes: List<String> = listOf("overview", "files", "clean", "apps", "activity", "system")
    val RequiredStates: Set<String> = setOf("loading", "empty", "blocked", "error", "content")
    val RequiredActionGates: Set<String> = setOf("allowed", "blocked", "needs_fresh_scan", "needs_capability")
}

data class ProductRouteContract(
    val route: String,
    val layoutClass: String,
    val semanticIdentity: String = route,
    val state: String,
    val actionGate: String,
    val targetSourceOfTruth: String = ProductCutoverContract.TargetGoRuntimeAuthority,
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
