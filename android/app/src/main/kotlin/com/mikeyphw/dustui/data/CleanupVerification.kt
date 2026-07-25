package com.mikeyphw.dustui.data

enum class CleanupVerificationStatus { VERIFIED_DELETED, PARTIAL, FAILED, BLOCKED, CHANGED_SINCE_REVIEW }
enum class CleanupPathPresence { MISSING, PRESENT, UNKNOWN }

data class CleanupPathObservation(
    val presence: CleanupPathPresence,
    val message: String? = null,
)

data class CleanupEnvironmentContext(
    val manufacturer: String = "unknown",
    val model: String = "unknown",
    val sdkInt: Int = 0,
    val filesystem: String? = null,
    val mountPoint: String? = null,
    val mountSource: String? = null,
    val readOnly: Boolean? = null,
)

data class CleanupVerifiedItem(
    val path: String,
    val plannedBytes: Long,
    val reportedDeletedBytes: Long,
    val status: CleanupVerificationStatus,
    val pathPresence: CleanupPathPresence,
    val message: String? = null,
    val fingerprint: CleanupPathFingerprint? = null,
) {
    val verifiedMissing: Boolean get() = pathPresence == CleanupPathPresence.MISSING
    val attemptedBytes: Long get() = if (status == CleanupVerificationStatus.BLOCKED) 0 else plannedBytes
    val partialBytes: Long get() = if (status == CleanupVerificationStatus.PARTIAL) reportedDeletedBytes.coerceAtMost(plannedBytes) else 0
    val failedBytes: Long get() = if (status in setOf(CleanupVerificationStatus.FAILED, CleanupVerificationStatus.CHANGED_SINCE_REVIEW)) plannedBytes else 0
}

data class CleanupVerificationReport(
    val schemaVersion: Int = SCHEMA_VERSION,
    val operationId: String,
    val scanId: String,
    val rootPath: String,
    val startedAt: Long,
    val completedAt: Long,
    val plannedBytes: Long,
    val attemptedBytes: Long,
    val reportedDeletedBytes: Long,
    val confirmedDeletedBytes: Long,
    val partialBytes: Long,
    val failedBytes: Long,
    val freeBytesBefore: Long? = null,
    val freeBytesAfter: Long? = null,
    val items: List<CleanupVerifiedItem>,
    val appVersion: String,
    val environment: CleanupEnvironmentContext = CleanupEnvironmentContext(),
    val verificationAttempts: Int = 1,
    val lastVerifiedAt: Long = completedAt,
) {
    val fullyVerified: Boolean get() = items.isNotEmpty() && items.all { it.status == CleanupVerificationStatus.VERIFIED_DELETED }
    val failedCount: Int get() = items.count { it.status != CleanupVerificationStatus.VERIFIED_DELETED && it.status != CleanupVerificationStatus.BLOCKED }
    val unknownVerificationCount: Int get() = items.count { it.pathPresence == CleanupPathPresence.UNKNOWN && it.status != CleanupVerificationStatus.BLOCKED }
    val measuredFreeSpaceDelta: Long? get() = freeBytesBefore?.let { before -> freeBytesAfter?.minus(before) }

    companion object { const val SCHEMA_VERSION = 2 }
}

object CleanupVerificationEngine {
    fun build(
        review: CleanupReview,
        result: CleanupExecutionResult,
        startedAt: Long,
        completedAt: Long,
        observationsByPath: Map<String, CleanupPathObservation>,
        freeBytesBefore: Long?,
        freeBytesAfter: Long?,
        appVersion: String,
        environment: CleanupEnvironmentContext = CleanupEnvironmentContext(),
    ): CleanupVerificationReport {
        val resultByPath = result.items.associateBy { it.path }
        val acceptedItems = review.accepted.map { approved ->
            val actual = resultByPath[approved.path]
            val observation = observationsByPath[approved.path]
                ?: CleanupPathObservation(CleanupPathPresence.UNKNOWN, "No post-deletion path observation was recorded")
            buildVerifiedItem(approved, actual, observation)
        }
        val blockedItems = review.blocked.map { blocked ->
            CleanupVerifiedItem(
                path = blocked.path,
                plannedBytes = 0,
                reportedDeletedBytes = 0,
                status = CleanupVerificationStatus.BLOCKED,
                pathPresence = CleanupPathPresence.UNKNOWN,
                message = CleanupReportRedactor.redact(blocked.reason),
            )
        }
        return report(
            operationId = review.operationId,
            scanId = review.scanId,
            rootPath = review.rootPath,
            startedAt = startedAt,
            completedAt = completedAt,
            freeBytesBefore = freeBytesBefore,
            freeBytesAfter = freeBytesAfter,
            items = acceptedItems + blockedItems,
            appVersion = appVersion,
            environment = environment,
            verificationAttempts = 1,
        )
    }

    /** Re-inspects the same operation without attributing externally removed paths to DustUI. */
    fun reverify(
        original: CleanupVerificationReport,
        observationsByPath: Map<String, CleanupPathObservation>,
        verifiedAt: Long,
        freeBytesAfter: Long?,
        environment: CleanupEnvironmentContext = original.environment,
    ): CleanupVerificationReport {
        val items = original.items.map { item ->
            if (item.status == CleanupVerificationStatus.BLOCKED || item.status == CleanupVerificationStatus.CHANGED_SINCE_REVIEW) return@map item
            val observation = observationsByPath[item.path]
                ?: CleanupPathObservation(CleanupPathPresence.UNKNOWN, "No retry observation was recorded")
            val status = when {
                observation.presence == CleanupPathPresence.UNKNOWN -> CleanupVerificationStatus.FAILED
                observation.presence == CleanupPathPresence.MISSING && item.reportedDeletedBytes >= item.plannedBytes && item.plannedBytes > 0 -> CleanupVerificationStatus.VERIFIED_DELETED
                observation.presence == CleanupPathPresence.MISSING && item.reportedDeletedBytes > 0 -> CleanupVerificationStatus.PARTIAL
                observation.presence == CleanupPathPresence.PRESENT && item.reportedDeletedBytes > 0 -> CleanupVerificationStatus.PARTIAL
                else -> CleanupVerificationStatus.FAILED
            }
            item.copy(
                status = status,
                pathPresence = observation.presence,
                message = listOfNotNull(item.message, observation.message)
                    .mapNotNull(CleanupReportRedactor::redact)
                    .distinct().joinToString(" · ").takeIf(String::isNotBlank),
            )
        }
        return report(
            operationId = original.operationId,
            scanId = original.scanId,
            rootPath = original.rootPath,
            startedAt = original.startedAt,
            completedAt = original.completedAt,
            freeBytesBefore = original.freeBytesBefore,
            freeBytesAfter = freeBytesAfter,
            items = items,
            appVersion = original.appVersion,
            environment = environment,
            verificationAttempts = original.verificationAttempts + 1,
            lastVerifiedAt = verifiedAt,
        )
    }

    private fun buildVerifiedItem(
        approved: CleanupApprovedItem,
        actual: CleanupItemResult?,
        observation: CleanupPathObservation,
    ): CleanupVerifiedItem {
        val status = when {
            actual == null -> CleanupVerificationStatus.FAILED
            actual.status == "CHANGED" || actual.message?.contains("changed", ignoreCase = true) == true -> CleanupVerificationStatus.CHANGED_SINCE_REVIEW
            actual.status == "BLOCKED" -> CleanupVerificationStatus.BLOCKED
            observation.presence == CleanupPathPresence.UNKNOWN -> CleanupVerificationStatus.FAILED
            observation.presence == CleanupPathPresence.MISSING && actual.status == "DELETED" && actual.deletedBytes >= approved.plannedBytes -> CleanupVerificationStatus.VERIFIED_DELETED
            observation.presence == CleanupPathPresence.MISSING && actual.deletedBytes > 0 -> CleanupVerificationStatus.PARTIAL
            observation.presence == CleanupPathPresence.PRESENT && actual.deletedBytes > 0 -> CleanupVerificationStatus.PARTIAL
            else -> CleanupVerificationStatus.FAILED
        }
        val messages = listOfNotNull(actual?.message, observation.message)
            .mapNotNull(CleanupReportRedactor::redact)
            .distinct()
        return CleanupVerifiedItem(
            path = approved.path,
            plannedBytes = approved.plannedBytes,
            reportedDeletedBytes = actual?.deletedBytes ?: 0,
            status = status,
            pathPresence = observation.presence,
            message = messages.joinToString(" · ").takeIf(String::isNotBlank),
            fingerprint = approved.fingerprint,
        )
    }

    private fun report(
        operationId: String,
        scanId: String,
        rootPath: String,
        startedAt: Long,
        completedAt: Long,
        freeBytesBefore: Long?,
        freeBytesAfter: Long?,
        items: List<CleanupVerifiedItem>,
        appVersion: String,
        environment: CleanupEnvironmentContext,
        verificationAttempts: Int,
        lastVerifiedAt: Long = completedAt,
    ): CleanupVerificationReport = CleanupVerificationReport(
        operationId = operationId,
        scanId = scanId,
        rootPath = rootPath,
        startedAt = startedAt,
        completedAt = completedAt,
        plannedBytes = items.sumOf { it.plannedBytes },
        attemptedBytes = items.sumOf { it.attemptedBytes },
        reportedDeletedBytes = items.sumOf { it.reportedDeletedBytes },
        confirmedDeletedBytes = items.filter { it.status == CleanupVerificationStatus.VERIFIED_DELETED }.sumOf { it.plannedBytes },
        partialBytes = items.sumOf { it.partialBytes },
        failedBytes = items.sumOf { it.failedBytes },
        freeBytesBefore = freeBytesBefore,
        freeBytesAfter = freeBytesAfter,
        items = items,
        appVersion = appVersion,
        environment = environment,
        verificationAttempts = verificationAttempts,
        lastVerifiedAt = lastVerifiedAt,
    )

    fun targetedRescanRoot(reportRoot: String, paths: Collection<String>): String {
        val normalizedRoot = CleanupSafetyPolicy.normalize(reportRoot) ?: return reportRoot
        val parents = paths.mapNotNull(CleanupSafetyPolicy::normalize)
            .map { path -> path.substringBeforeLast('/', normalizedRoot).ifBlank { normalizedRoot } }
            .filter { it == normalizedRoot || it.startsWith(normalizedRoot.trimEnd('/') + "/") }
        if (parents.isEmpty()) return normalizedRoot
        val commonSegments = parents.map { it.trim('/').split('/').filter(String::isNotBlank) }
            .reduce { common, next -> common.zip(next).takeWhile { (left, right) -> left == right }.map { it.first } }
        val common = "/" + commonSegments.joinToString("/")
        return common.takeIf { it == normalizedRoot || it.startsWith(normalizedRoot.trimEnd('/') + "/") } ?: normalizedRoot
    }
}

object CleanupReportRedactor {
    private val patterns = listOf(
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+"),
        Regex("(?i)((?:cookie|token|session|password|secret)\\s*[:=]\\s*)[^\\s,;]+"),
    )
    fun redact(value: String?): String? = value?.let { raw -> patterns.fold(raw) { text, regex -> regex.replace(text, "$1[redacted]") } }
}
