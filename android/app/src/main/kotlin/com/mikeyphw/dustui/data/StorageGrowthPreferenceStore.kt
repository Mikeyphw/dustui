package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.edit

class StorageGrowthPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun understood(): Set<String> = preferences.getStringSet(KEY_UNDERSTOOD, emptySet()).orEmpty().toSet()
    fun mark(identity: String) = preferences.edit { putStringSet(KEY_UNDERSTOOD, understood() + identity) }
    fun restore(identity: String) = preferences.edit { putStringSet(KEY_UNDERSTOOD, understood() - identity) }
    fun retentionLimit(): Int = preferences.getInt(KEY_RETENTION, DEFAULT_RETENTION).coerceIn(MIN_RETENTION, MAX_RETENTION)
    fun setRetentionLimit(limit: Int) = preferences.edit { putInt(KEY_RETENTION, limit.coerceIn(MIN_RETENTION, MAX_RETENTION)) }
    fun prune(activeScanIds: Set<String>) {
        preferences.edit {
            putStringSet(KEY_UNDERSTOOD, understood().filterTo(linkedSetOf()) { token -> activeScanIds.any { token.startsWith("$it|") } })
        }
    }
    companion object {
        const val MIN_RETENTION = 3
        const val MAX_RETENTION = 50
        const val DEFAULT_RETENTION = 12
        private const val PREFS = "storage-growth-v1"
        private const val KEY_UNDERSTOOD = "understood"
        private const val KEY_RETENTION = "retention"
    }
}
