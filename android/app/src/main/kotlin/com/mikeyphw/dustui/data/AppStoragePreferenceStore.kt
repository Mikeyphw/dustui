package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.edit

class AppStoragePreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences("app-storage-inspector", Context.MODE_PRIVATE)

    fun ignoredPackages(): Set<String> = preferences.getStringSet(KEY_IGNORED_PACKAGES, emptySet()).orEmpty().toSet()

    fun setRecommendationsIgnored(packageName: String, ignored: Boolean) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return
        preferences.edit {
            val updated = ignoredPackages().toMutableSet().also { packages ->
                if (ignored) packages += normalized else packages -= normalized
            }
            putStringSet(KEY_IGNORED_PACKAGES, updated)
        }
    }

    private companion object {
        const val KEY_IGNORED_PACKAGES = "ignored-packages"
    }
}
