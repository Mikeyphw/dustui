package com.mikeyphw.dustui.root

object NativeDustScanner {
    init {
        System.loadLibrary("dustui_scanner")
    }

    external fun scan(
        scanId: String,
        rootPath: String,
        outputFd: Int,
        maxDepth: Int,
        includeHidden: Boolean,
        apparentSize: Boolean,
        sameFilesystem: Boolean,
        mode: Int,
        minSize: Long,
        maxEntries: Long,
        excludes: Array<String>,
    ): Int

    external fun cancel(scanId: String): Boolean
    external fun version(): String
}
