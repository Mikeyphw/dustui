package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.edit

class ResidueProtectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("app-residue-protection", Context.MODE_PRIVATE)

    fun paths(): Set<String> = preferences.getStringSet(KEY_PATHS, emptySet()).orEmpty().toSet()

    fun protect(path: String) {
        val normalized = CleanupSafetyPolicy.normalize(path) ?: return
        preferences.edit { putStringSet(KEY_PATHS, paths() + normalized) }
    }

    fun unprotect(path: String) {
        val normalized = CleanupSafetyPolicy.normalize(path) ?: return
        preferences.edit { putStringSet(KEY_PATHS, paths() - normalized) }
    }

    private companion object {
        const val KEY_PATHS = "protected-paths"
    }
}
