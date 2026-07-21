package com.mikeyphw.dustui.data

import org.json.JSONObject

data class RootPathInspection(
    val ok: Boolean,
    val path: String,
    val type: String = "unknown",
    val sizeBytes: Long = 0,
    val inode: Long = 0,
    val uid: Int = -1,
    val gid: Int = -1,
    val mode: Int = 0,
    val permissions: String = "---------",
    val selinuxContext: String? = null,
    val symlinkTarget: String? = null,
    val mountPoint: String? = null,
    val filesystem: String? = null,
    val mountSource: String? = null,
    val bindMount: Boolean = false,
    val overlayMount: Boolean = false,
    val readOnly: Boolean = false,
    val preview: String? = null,
    val previewTruncated: Boolean = false,
    val message: String? = null,
) {
    companion object {
        fun fromJson(raw: String): RootPathInspection {
            val json = JSONObject(raw)
            return RootPathInspection(
                ok = json.optBoolean("ok"),
                path = json.optString("path"),
                type = json.optString("type", "unknown"),
                sizeBytes = json.optLong("sizeBytes"),
                inode = json.optLong("inode"),
                uid = json.optInt("uid", -1),
                gid = json.optInt("gid", -1),
                mode = json.optInt("mode"),
                permissions = json.optString("permissions", "---------"),
                selinuxContext = json.optString("selinuxContext").takeIf(String::isNotBlank),
                symlinkTarget = json.optString("symlinkTarget").takeIf(String::isNotBlank),
                mountPoint = json.optString("mountPoint").takeIf(String::isNotBlank),
                filesystem = json.optString("filesystem").takeIf(String::isNotBlank),
                mountSource = json.optString("mountSource").takeIf(String::isNotBlank),
                bindMount = json.optBoolean("bindMount"),
                overlayMount = json.optBoolean("overlayMount"),
                readOnly = json.optBoolean("readOnly"),
                preview = json.optString("preview").takeIf(String::isNotBlank),
                previewTruncated = json.optBoolean("previewTruncated"),
                message = json.optString("message").takeIf(String::isNotBlank),
            )
        }
    }
}

data class TreemapInput(val id: String, val size: Long)
data class TreemapRect(val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object TreemapLayout {
    /** Deterministic slice-and-dice layout. Alternating axes keeps deep zoom readable on phones. */
    fun layout(items: List<TreemapInput>, width: Float, height: Float): List<TreemapRect> {
        if (width <= 0f || height <= 0f) return emptyList()
        val positive = items.filter { it.size > 0 }.sortedByDescending { it.size }
        if (positive.isEmpty()) return emptyList()
        return split(positive, 0f, 0f, width, height, width >= height)
    }

    private fun split(items: List<TreemapInput>, left: Float, top: Float, right: Float, bottom: Float, vertical: Boolean): List<TreemapRect> {
        if (items.size == 1) return listOf(TreemapRect(items[0].id, left, top, right, bottom))
        val total = items.sumOf { it.size }.toDouble()
        var running = 0L
        var index = 0
        val half = total / 2.0
        while (index < items.lastIndex && running + items[index].size <= half) {
            running += items[index].size
            index++
        }
        if (index == 0) { running = items[0].size; index = 1 }
        val first = items.take(index)
        val second = items.drop(index)
        val ratio = (first.sumOf { it.size }.toDouble() / total).toFloat().coerceIn(0.05f, 0.95f)
        return if (vertical) {
            val cut = left + (right - left) * ratio
            split(first, left, top, cut, bottom, false) + split(second, cut, top, right, bottom, false)
        } else {
            val cut = top + (bottom - top) * ratio
            split(first, left, top, right, cut, true) + split(second, left, cut, right, bottom, true)
        }
    }
}
