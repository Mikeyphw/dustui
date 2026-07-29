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
    fun request(depthProfile: ScanDepthProfile = ScanDepthProfile.default): ScanRequest = ScanRequest(
        presetId = id,
        rootPath = path,
        maxDepth = depthProfile.maxDepth,
        maxEntries = depthProfile.maxEntries,
        includeHidden = includeHidden,
        minSize = minSize,
    ).validated()

    companion object {
        val defaults = listOf(
            ScanPreset("shared", "Shared storage", "Downloads, media and user files", "/storage/emulated/0", ScanDepthPolicy.DEFAULT_MAX_DEPTH),
            ScanPreset("apps", "App data", "Private data for the primary Android user", "/data/user/0", ScanDepthPolicy.DEFAULT_MAX_DEPTH, true),
            ScanPreset("data", "Android data", "Broad /data scan kept on one filesystem", "/data", ScanDepthPolicy.DEFAULT_MAX_DEPTH, true),
            ScanPreset("modules", "Root modules", "Magisk, KernelSU and APatch module payloads", "/data/adb/modules", ScanDepthPolicy.DEFAULT_MAX_DEPTH, true),
        )
    }
}
