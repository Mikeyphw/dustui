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

    /** Requests cancellation for an in-flight native reviewed deletion keyed by operationId. */
    external fun cancelDeletion(operationId: String): Boolean

    /**
     * Delete a reviewed target using root-fd-relative native traversal.
     *
     * The root service still performs plan-wide and final JSON/fingerprint
     * validation before this call. This native boundary prevents the last
     * mutation step from walking absolute pathnames again: it opens the
     * reviewed root, resolves the target relative to that root with openat /
     * fstatat, refuses symlinks and filesystem-boundary crossings, verifies
     * the reviewed top-level identity, then unlinks via unlinkat.
     */
    external fun deleteReviewedPath(
        operationId: String,
        rootPath: String,
        relativePath: String,
        expectedDevice: Long,
        expectedInode: Long,
        expectedMode: Int,
        expectedSize: Long,
        expectedModifiedAt: Long,
    ): String

    external fun version(): String
}
