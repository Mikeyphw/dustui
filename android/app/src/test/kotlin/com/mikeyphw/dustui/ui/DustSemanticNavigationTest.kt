package com.mikeyphw.dustui.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DustSemanticNavigationTest {
    @Test fun `AUD FIX08 semantic route inventory is stable and complete`() {
        assertEquals(27, DustSemanticRoute.all.size)
        assertEquals(27, DustSemanticRoute.all.map { it.routeKey }.toSet().size)
        assertEquals(
            listOf("overview/home", "files/explorer", "clean/recommended", "apps/list", "activity/list", "system/runtime"),
            DustSemanticNavigation.primaryRoutes.map { it.routeKey },
        )
        assertEquals(DustRouteDomain.entries.toSet(), DustSemanticNavigation.primaryRoutes.map { it.domain }.toSet())
    }

    @Test fun `AUD FIX08 deep links round trip nested and entity routes`() {
        val cases = listOf(
            DustRouteSelection(DustSemanticRoute.OverviewTrends, generation = 7),
            DustRouteSelection(DustSemanticRoute.FilesItem, "/sdcard/Download/movie one.mkv", 8),
            DustRouteSelection(DustSemanticRoute.AppsPackage, "com.example.demo", 9),
            DustRouteSelection(DustSemanticRoute.ActivityEvidence, "op/a:b", 10),
            DustRouteSelection(DustSemanticRoute.CleanReview, "manifest-42", 11),
        )
        cases.forEachIndexed { index, selection ->
            val raw = DustSemanticNavigation.deepLink(selection)
            val resolved = DustSemanticNavigation.resolveDeepLink(raw, 100L + index)
            assertTrue("expected resolved deep link: $raw", resolved is DustRouteResolution.Resolved)
            val actual = (resolved as DustRouteResolution.Resolved).selection
            assertEquals(selection.route, actual.route)
            assertEquals(selection.entityId, actual.entityId)
            assertEquals(100L + index, actual.generation)
        }
    }

    @Test fun `AUD FIX08 entity identity failures are typed`() {
        val missing = DustSemanticNavigation.resolveDeepLink("dustui://files/item", 2)
        assertTrue(missing is DustRouteResolution.Rejected)
        assertEquals(DustRouteProblemCode.EntityRequired, (missing as DustRouteResolution.Rejected).problem.code)

        val unknown = DustSemanticNavigation.resolveDeepLink("dustui://legacy/debug", 3)
        assertTrue(unknown is DustRouteResolution.Rejected)
        assertEquals(DustRouteProblemCode.UnknownRoute, (unknown as DustRouteResolution.Rejected).problem.code)
    }

    @Test fun `AUD FIX08 saved semantic identity restores independently of layout`() {
        val savedName = DustSemanticRoute.ActivityRun.name
        val savedEntity = "operation-123"
        val savedGeneration = 17L
        val restored = DustRouteSelection(DustSemanticRoute.valueOf(savedName), savedEntity, savedGeneration)
        assertEquals("activity/run", restored.routeKey)
        assertEquals(savedEntity, restored.entityId)
        assertEquals(savedGeneration, restored.generation)
        assertEquals(DustSemanticRoute.ActivityList, DustSemanticNavigation.backTarget(restored)?.route)
    }

    @Test fun `AUD FIX08 compact and wide chrome cannot remap semantic route`() {
        DustSemanticRoute.all.forEach { route ->
            val entity = route.entityKind?.let { "identity" }
            val selection = DustRouteSelection(route, entity, 1)
            assertEquals(route, selection.route)
            assertEquals(route.domain, selection.domain)
        }
    }
}
