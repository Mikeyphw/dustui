package com.mikeyphw.dustui.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveRouteContinuityInstrumentedTest {
    @Test fun semanticDetailSurvivesPhoneTabletAndRotationPolicies() {
        val original = DustRouteSelection(DustSemanticRoute.CleanCandidate, "candidate-9", 33)
        val dimensions = listOf(412f to 915f, 915f to 412f, 1280f to 800f, 800f to 1280f)
        dimensions.forEach { (width, height) ->
            val restored = DustAdaptiveLayoutPolicy.preserveSelection(original, width, height)
            assertEquals(original, restored)
            assertEquals("dustui://clean/candidate/candidate-9", DustSemanticNavigation.deepLink(restored))
        }
        assertTrue(DustAdaptiveLayoutPolicy.resolve(1280f, 800f, original).showsMasterPane)
    }

    @Test fun focusTraversalOrderIsStableAcrossWideLayouts() {
        val selection = DustRouteSelection(DustSemanticRoute.SystemDiagnostics, generation = 4)
        val medium = DustAdaptiveLayoutPolicy.resolve(700f, 900f, selection)
        val expanded = DustAdaptiveLayoutPolicy.resolve(1280f, 800f, selection)
        assertEquals(listOf("primary-navigation", "master", "detail", "context-actions"), medium.focusOrder)
        assertEquals(medium.focusOrder, expanded.focusOrder)
    }
}
