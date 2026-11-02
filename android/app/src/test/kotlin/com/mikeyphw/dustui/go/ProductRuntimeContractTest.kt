package com.mikeyphw.dustui.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRuntimeContractTest {
    @Test
    fun runtimeContractDescribesCapabilitiesNotHistoricalAcceptance() {
        assertEquals("dustui.product.v2", ProductRuntimeContract.Protocol)
        assertEquals("mandatory", ProductRuntimeContract.AndroidBuildability)
        assertEquals("go-sqlite-runtime", ProductRuntimeContract.TargetGoRuntimeAuthority)
        assertEquals("dustui.android.runtime.session.v1", ProductRuntimeContract.RuntimeProtocol)
        assertEquals("dustui.sqlite.schema.v1", ProductRuntimeContract.SQLiteSchemaProtocol)
        assertTrue(ProductRuntimeContract.RequiredActionGateFields.containsAll(setOf("allowed", "reasonCode", "message", "remediationActions")))
    }

    @Test
    fun phoneAndTabletLayoutsKeepSameRouteSemantics() {
        val fixtures = ProductRouteFixtures.phoneAndTabletFixtures()
        assertEquals(ProductRuntimeContract.Routes.size * 2, fixtures.size)
        ProductRuntimeContract.Routes.forEach { route ->
            val routeFixtures = fixtures.filter { it.route == route }
            assertEquals(setOf("phone", "tablet"), routeFixtures.map { it.layoutClass }.toSet())
            routeFixtures.forEach { fixture ->
                assertEquals(route, fixture.semanticIdentity)
                assertEquals(ProductRuntimeContract.TargetGoRuntimeAuthority, fixture.targetSourceOfTruth)
            }
        }
    }
}
