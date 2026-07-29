package com.mikeyphw.dustui.scan

import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Paths
import java.util.UUID

enum class EntryMode(val nativeValue: Int) { ALL(0), DIRECTORIES(1), FILES(2) }
enum class ScanStatus { STARTING, RUNNING, COMPLETED, CANCELLED, FAILED }

data class ScanRequest(
    val scanId: String = UUID.randomUUID().toString(),
    val presetId: String? = null,
    val rootPath: String,
    val maxDepth: Int = 16,
    val maxEntries: Long = 20_000,
    val mode: EntryMode = EntryMode.ALL,
    val minSize: Long = 0,
    val includeHidden: Boolean = false,
    val apparentSize: Boolean = false,
    val sameFilesystem: Boolean = true,
    val excludes: List<String> = DEFAULT_EXCLUDES,
) {
    fun validated(): ScanRequest {
        require(SCAN_ID.matches(scanId)) { "Invalid scan identifier" }
        require(rootPath.indexOf('\u0000') < 0) { "Path contains a NUL character" }
        val normalized = Paths.get(rootPath).normalize().toString()
        require(normalized.startsWith('/')) { "Scan root must be absolute" }
        require(PROTECTED_PSEUDO_FS.none { normalized == it || normalized.startsWith("$it/") }) {
            "Pseudo filesystems cannot be scanned"
        }
        require(maxDepth in -1..64) { "Depth must be -1 or between 0 and 64" }
        require(maxEntries in 1..1_000_000) { "Result limit must be between 1 and 1,000,000" }
        require(minSize >= 0) { "Minimum size cannot be negative" }
        require(excludes.size <= 128 && excludes.all { it.length <= 256 && '\u0000' !in it }) {
            "Invalid exclusion list"
        }
        return copy(rootPath = normalized, excludes = excludes.distinct())
    }

    fun toJson(): String = JSONObject()
        .put("scanId", scanId)
        .put("presetId", presetId)
        .put("rootPath", rootPath)
        .put("maxDepth", maxDepth)
        .put("maxEntries", maxEntries)
        .put("mode", mode.name)
        .put("minSize", minSize)
        .put("includeHidden", includeHidden)
        .put("apparentSize", apparentSize)
        .put("sameFilesystem", sameFilesystem)
        .put("excludes", JSONArray(excludes))
        .toString()

    companion object {
        private val SCAN_ID = Regex("[A-Za-z0-9._-]{1,96}")
        private val PROTECTED_PSEUDO_FS = listOf("/proc", "/sys", "/dev")
        val DEFAULT_EXCLUDES = listOf("lost+found", ".thumbnails")

        fun fromJson(raw: String): ScanRequest {
            val json = JSONObject(raw)
            val excludes = json.optJSONArray("excludes")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) add(array.getString(index))
                }
            } ?: DEFAULT_EXCLUDES
            return ScanRequest(
                scanId = json.getString("scanId"),
                presetId = json.optString("presetId").takeIf { it.isNotBlank() && it != "null" },
                rootPath = json.getString("rootPath"),
                maxDepth = json.optInt("maxDepth", 16),
                maxEntries = json.optLong("maxEntries", 20_000),
                mode = runCatching { EntryMode.valueOf(json.optString("mode", EntryMode.ALL.name)) }
                    .getOrDefault(EntryMode.ALL),
                minSize = json.optLong("minSize", 0),
                includeHidden = json.optBoolean("includeHidden", false),
                apparentSize = json.optBoolean("apparentSize", false),
                sameFilesystem = json.optBoolean("sameFilesystem", true),
                excludes = excludes,
            ).validated()
        }
    }
}
