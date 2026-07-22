package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePackagingTest {
    @Test fun `classifies debug signed release and unsigned release apks`() {
        assertEquals(ApkKind.DEBUG, ReleasePackaging.classifyApk("app/build/outputs/apk/debug/app-debug.apk"))
        assertEquals(ApkKind.RELEASE_SIGNED, ReleasePackaging.classifyApk("app/build/outputs/apk/release/app-release.apk"))
        assertEquals(ApkKind.RELEASE_UNSIGNED, ReleasePackaging.classifyApk("app/build/outputs/apk/release/app-release-unsigned.apk"))
    }

    @Test fun `unsigned release is warning but still produces rc evidence`() {
        val report = ReleasePackaging.evaluate(
            versionName = "1.3.0-rc1",
            artifacts = listOf(
                ReleaseApkArtifact("app-debug.apk", 10, "a", ApkKind.DEBUG),
                ReleaseApkArtifact("app-release-unsigned.apk", 20, "b", ApkKind.RELEASE_UNSIGNED),
            ),
            certificationReady = true,
        )
        assertTrue(report.ready)
        assertEquals(0, report.blockers)
        assertEquals(1, report.warnings)
        assertNotNull(report.preferredInstallArtifact)
    }

    @Test fun `missing debug artifact blocks release packaging`() {
        val report = ReleasePackaging.evaluate("1.3.0-rc1", listOf(ReleaseApkArtifact("app-release.apk", 20, "b", ApkKind.RELEASE_SIGNED)), true)
        assertFalse(report.ready)
        assertEquals("debug-apk", report.issues.single().id)
    }

    @Test fun `manifest includes stable artifact names and hashes`() {
        val manifest = ReleasePackaging.manifestJson(
            versionName = "1.3.0-rc1",
            packageName = "com.mikeyphw.dustui",
            artifacts = listOf(ReleaseApkArtifact("app/build/outputs/apk/debug/app-debug.apk", 10, "abc", ApkKind.DEBUG)),
        )
        assertTrue(manifest.contains("dustui-1.3.0-rc1-debug.apk"))
        assertTrue(manifest.contains("abc"))
    }

    @Test fun `sha256 helper is deterministic`() {
        assertEquals(ReleasePackaging.sha256("dust".toByteArray()), ReleasePackaging.sha256("dust".toByteArray()))
    }
}
