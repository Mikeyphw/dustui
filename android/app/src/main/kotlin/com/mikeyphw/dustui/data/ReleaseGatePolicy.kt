package com.mikeyphw.dustui.data

import java.io.File

object ReleaseGatePolicy {
    private val taskerActions = setOf("scan", "cancel")

    fun isAllowedTaskerAction(action: String): Boolean = action.lowercase() in taskerActions

    fun validateRestoreEntry(originalPath: String, quarantinePath: String): String? {
        val original = normalize(originalPath) ?: return "Invalid original path"
        val quarantined = normalize(quarantinePath) ?: return "Invalid quarantine path"
        if (!quarantined.contains("/.dustui-quarantine/")) return "Path is not inside a DustUI quarantine vault"
        if (original.contains("/.dustui-quarantine/")) return "Original path cannot be inside quarantine"
        if (CleanupSafetyPolicy.blockReason(original, "/") != null && original in setOf("/", "/data", "/system", "/vendor")) {
            return "Protected destination"
        }
        return null
    }

    fun normalize(path: String): String? {
        if (!path.startsWith('/')) return null
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun fingerprint(file: File): PathFingerprint = PathFingerprint(
        canonicalPath = file.canonicalPath,
        length = file.length(),
        modifiedAt = file.lastModified(),
        directory = file.isDirectory,
    )
}

data class PathFingerprint(
    val canonicalPath: String,
    val length: Long,
    val modifiedAt: Long,
    val directory: Boolean,
) {
    fun stillMatches(file: File): Boolean = this == ReleaseGatePolicy.fingerprint(file)
}
