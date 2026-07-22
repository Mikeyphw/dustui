package com.mikeyphw.dustui.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

enum class ApkKind {
    DEBUG,
    RELEASE_SIGNED,
    RELEASE_UNSIGNED,
}

enum class ReleaseBlockerSeverity {
    BLOCKER,
    WARNING,
}

data class ReleaseApkArtifact(
    val path: String,
    val sizeBytes: Long,
    val sha256: String = "",
    val kind: ApkKind = ReleasePackaging.classifyApk(path),
) {
    val installable: Boolean
        get() = kind != ApkKind.RELEASE_UNSIGNED && sizeBytes > 0
}

data class ReleasePackagingIssue(
    val id: String,
    val severity: ReleaseBlockerSeverity,
    val message: String,
)

data class ReleasePackagingReport(
    val versionName: String,
    val artifacts: List<ReleaseApkArtifact>,
    val issues: List<ReleasePackagingIssue>,
) {
    val ready: Boolean
        get() = issues.none { it.severity == ReleaseBlockerSeverity.BLOCKER }

    val blockers: Int
        get() = issues.count { it.severity == ReleaseBlockerSeverity.BLOCKER }

    val warnings: Int
        get() = issues.count { it.severity == ReleaseBlockerSeverity.WARNING }

    val preferredInstallArtifact: ReleaseApkArtifact?
        get() = artifacts.firstOrNull { it.kind == ApkKind.RELEASE_SIGNED && it.installable }
            ?: artifacts.firstOrNull { it.kind == ApkKind.DEBUG && it.installable }
}

object ReleasePackaging {
    fun classifyApk(path: String): ApkKind {
        val normalized = path.replace('\\', '/').lowercase(Locale.US)
        return when {
            normalized.endsWith("app-debug.apk") || "/debug/" in normalized -> ApkKind.DEBUG
            normalized.endsWith("-unsigned.apk") -> ApkKind.RELEASE_UNSIGNED
            "/release/" in normalized && normalized.endsWith(".apk") -> ApkKind.RELEASE_SIGNED
            normalized.contains("debug") -> ApkKind.DEBUG
            normalized.contains("release") && normalized.endsWith(".apk") -> ApkKind.RELEASE_SIGNED
            else -> ApkKind.DEBUG
        }
    }

    fun rcFileName(versionName: String, artifact: ReleaseApkArtifact): String {
        val safeVersion = versionName.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val suffix = when (artifact.kind) {
            ApkKind.DEBUG -> "debug"
            ApkKind.RELEASE_SIGNED -> "release-signed"
            ApkKind.RELEASE_UNSIGNED -> "release-unsigned"
        }
        return "dustui-$safeVersion-$suffix.apk"
    }

    fun evaluate(versionName: String, artifacts: List<ReleaseApkArtifact>, certificationReady: Boolean): ReleasePackagingReport {
        val issues = mutableListOf<ReleasePackagingIssue>()
        if (artifacts.none { it.kind == ApkKind.DEBUG && it.sizeBytes > 0 }) {
            issues += ReleasePackagingIssue("debug-apk", ReleaseBlockerSeverity.BLOCKER, "Debug APK is missing or empty")
        }
        val signedRelease = artifacts.any { it.kind == ApkKind.RELEASE_SIGNED && it.sizeBytes > 0 }
        val unsignedRelease = artifacts.any { it.kind == ApkKind.RELEASE_UNSIGNED && it.sizeBytes > 0 }
        if (!signedRelease && !unsignedRelease) {
            issues += ReleasePackagingIssue("release-apk", ReleaseBlockerSeverity.BLOCKER, "Release APK is missing")
        }
        if (!signedRelease && unsignedRelease) {
            issues += ReleasePackagingIssue("signed-release", ReleaseBlockerSeverity.WARNING, "Release APK is unsigned; use it as RC evidence, not distribution")
        }
        if (!certificationReady) {
            issues += ReleasePackagingIssue("device-certification", ReleaseBlockerSeverity.WARNING, "Device certification evidence has not passed yet")
        }
        return ReleasePackagingReport(versionName, artifacts.sortedBy { it.kind.ordinal }, issues)
    }

    fun manifestJson(versionName: String, packageName: String, artifacts: List<ReleaseApkArtifact>): String {
        val json = JSONObject()
            .put("app", "DustUI")
            .put("packageName", packageName)
            .put("versionName", versionName)
            .put("artifacts", JSONArray(artifacts.map { artifact ->
                JSONObject()
                    .put("path", artifact.path)
                    .put("kind", artifact.kind.name.lowercase(Locale.US))
                    .put("sizeBytes", artifact.sizeBytes)
                    .put("sha256", artifact.sha256)
                    .put("installable", artifact.installable)
                    .put("fileName", rcFileName(versionName, artifact))
            }))
        return json.toString(2)
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
    }
}
