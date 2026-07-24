package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.edit

class RecommendationPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("cleanup-recommendations", Context.MODE_PRIVATE)

    fun snapshot(scanId: String): CleanupRecommendationPreferences = CleanupRecommendationPreferences(
        dismissedIds = preferences.getStringSet(dismissedKey(scanId), emptySet()).orEmpty().toSet(),
        ignoredIds = preferences.getStringSet(KEY_IGNORED, emptySet()).orEmpty().toSet(),
    )

    fun dismiss(scanId: String, id: String) {
        preferences.edit {
            putStringSet(dismissedKey(scanId), snapshot(scanId).dismissedIds + id)
        }
    }

    fun ignore(scanId: String, id: String) {
        val current = snapshot(scanId)
        preferences.edit {
            putStringSet(KEY_IGNORED, current.ignoredIds + id)
            putStringSet(dismissedKey(scanId), current.dismissedIds - id)
        }
    }

    fun restore(scanId: String, id: String) {
        val current = snapshot(scanId)
        preferences.edit {
            putStringSet(KEY_IGNORED, current.ignoredIds - id)
            putStringSet(dismissedKey(scanId), current.dismissedIds - id)
        }
    }

    fun pruneDismissedScans(activeScanId: String) {
        val activeKey = dismissedKey(activeScanId)
        val obsolete = preferences.all.keys.filter { it.startsWith(DISMISSED_PREFIX) && it != activeKey }
        if (obsolete.isEmpty()) return
        preferences.edit { obsolete.forEach { remove(it) } }
    }

    private fun dismissedKey(scanId: String): String = "$DISMISSED_PREFIX$scanId"

    private companion object {
        const val KEY_IGNORED = "ignored"
        const val DISMISSED_PREFIX = "dismissed:"
    }
}
