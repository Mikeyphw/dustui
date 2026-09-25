package com.mikeyphw.dustui.go

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCutoverContractTest {
    @Test
    fun gateDContractPointsToCanonicalQualificationTruth() {
        assertEquals("OV13", ProductCutoverContract.Phase)
        assertEquals("Gate D", ProductCutoverContract.Gate)
        assertEquals("mandatory", ProductCutoverContract.AndroidBuildability)
        assertEquals("migration/qualification/v1/current_state.json", ProductCutoverContract.QualificationStateSource)
        assertEquals("migration/qualification/v1/gate_d_evidence.json", ProductCutoverContract.QualificationEvidenceSource)
        assertTrue(ProductCutoverContract.TargetLegacyPythonRetired)
        assertTrue(ProductCutoverContract.TargetLegacyKotlinDomainRetired)
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
                assertEquals(ProductCutoverContract.TargetGoRuntimeAuthority, fixture.targetSourceOfTruth)
            }
        }
    }
}
