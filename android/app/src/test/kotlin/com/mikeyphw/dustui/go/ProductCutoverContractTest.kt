package com.mikeyphw.dustui.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCutoverContractTest {
    @Test
    fun gateDMakesAndroidBuildabilityMandatoryAgain() {
        assertEquals("OV13", ProductCutoverContract.Phase)
        assertEquals("Gate D", ProductCutoverContract.Gate)
        assertEquals("mandatory", ProductCutoverContract.AndroidBuildability)
        assertFalse(ProductCutoverContract.LegacyPythonTextualProduction)
        assertFalse(ProductCutoverContract.LegacyKotlinDomainTruth)
        assertTrue(ProductCutoverContract.MandatoryGateEvidence.contains("compile"))
        assertTrue(ProductCutoverContract.MandatoryGateEvidence.contains("runtime-apk-binding"))
    }

    @Test
    fun phoneAndTabletLayoutsKeepSameRouteSemantics() {
        val fixtures = ProductRouteFixtures.phoneAndTabletFixtures()
        assertEquals(ProductCutoverContract.Routes.size * 2, fixtures.size)
        ProductCutoverContract.Routes.forEach { route ->
            val routeFixtures = fixtures.filter { it.route == route }
            assertEquals(setOf("phone", "tablet"), routeFixtures.map { it.layoutClass }.toSet())
            routeFixtures.forEach { fixture ->
                assertEquals(route, fixture.semanticIdentity)
                assertEquals(ProductCutoverContract.GoRuntimeAuthority, fixture.sourceOfTruth)
            }
        }
    }
}
