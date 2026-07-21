package com.mikeyphw.dustui.data

enum class CertificationStatus { PASS, WARNING, FAIL }

data class CertificationCheck(
    val id: String,
    val label: String,
    val status: CertificationStatus,
    val detail: String,
)

data class DeviceCertificationReport(
    val generatedAt: Long,
    val checks: List<CertificationCheck>,
) {
    val releaseReady: Boolean get() = checks.none { it.status == CertificationStatus.FAIL }
    val failures: Int get() = checks.count { it.status == CertificationStatus.FAIL }
    val warnings: Int get() = checks.count { it.status == CertificationStatus.WARNING }
}

object DeviceCertification {
    private const val MIN_FREE_BYTES = 256L * 1024L * 1024L

    fun evaluate(
        rootConnected: Boolean,
        rootUid: Int,
        backend: String,
        scannerVersion: String,
        selinuxEnforcing: Boolean?,
        deviceUnlocked: Boolean,
        notificationsEnabled: Boolean,
        appStorageWritable: Boolean,
        freeBytes: Long,
        batteryOptimizationIgnored: Boolean,
        generatedAt: Long = System.currentTimeMillis(),
    ): DeviceCertificationReport = DeviceCertificationReport(
        generatedAt = generatedAt,
        checks = listOf(
            check("root", "Root service", rootConnected && rootUid == 0, "UID $rootUid via $backend"),
            check("scanner", "Native scanner", scannerVersion.isNotBlank(), scannerVersion.ifBlank { "Version unavailable" }),
            CertificationCheck("selinux", "SELinux", if (selinuxEnforcing == false) CertificationStatus.WARNING else CertificationStatus.PASS, when (selinuxEnforcing) { true -> "Enforcing"; false -> "Permissive"; null -> "State unavailable" }),
            check("unlock", "Device unlocked", deviceUnlocked, if (deviceUnlocked) "User storage is available" else "Unlock before destructive operations"),
            CertificationCheck("notifications", "Notifications", if (notificationsEnabled) CertificationStatus.PASS else CertificationStatus.WARNING, if (notificationsEnabled) "Foreground progress can be shown" else "Scanning works, but progress alerts may be hidden"),
            check("storage", "App storage", appStorageWritable, if (appStorageWritable) "Writable" else "Not writable"),
            check("free-space", "Free working space", freeBytes >= MIN_FREE_BYTES, "$freeBytes bytes available"),
            CertificationCheck("battery", "Battery restrictions", if (batteryOptimizationIgnored) CertificationStatus.PASS else CertificationStatus.WARNING, if (batteryOptimizationIgnored) "Unrestricted" else "Scheduled or long scans may be delayed"),
        ),
    )

    private fun check(id: String, label: String, passed: Boolean, detail: String) = CertificationCheck(
        id = id,
        label = label,
        status = if (passed) CertificationStatus.PASS else CertificationStatus.FAIL,
        detail = detail,
    )
}
