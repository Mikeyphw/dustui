package com.mikeyphw.dustui.scan

data class ScanPreset(
    val id: String,
    val title: String,
    val description: String,
    val path: String,
    val maxDepth: Int,
    val includeHidden: Boolean = false,
    val minSize: Long = 0,
) {
    fun request(): ScanRequest = ScanRequest(
        presetId = id,
        rootPath = path,
        maxDepth = maxDepth,
        includeHidden = includeHidden,
        minSize = minSize,
    ).validated()

    companion object {
        val defaults = listOf(
            ScanPreset("shared", "Shared storage", "Downloads, media and user files", "/storage/emulated/0", 16),
            ScanPreset("apps", "App data", "Private data for the primary Android user", "/data/user/0", 16, true),
            ScanPreset("data", "Android data", "Broad /data scan kept on one filesystem", "/data", 16, true),
            ScanPreset("modules", "Root modules", "Magisk, KernelSU and APatch module payloads", "/data/adb/modules", 16, true),
        )
    }
}
