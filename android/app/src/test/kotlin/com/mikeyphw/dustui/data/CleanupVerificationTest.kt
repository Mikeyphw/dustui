package com.mikeyphw.dustui.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupVerificationTest {
    @Test fun `verified report keeps explicit byte accounting and environment context`() {
        val result = CleanupExecutionResult(
            ok = false, operationId = "op", status = "PARTIAL", deletedBytes = 150, failedCount = 1,
            items = listOf(
                CleanupItemResult("/storage/a", 100, 100, "DELETED"),
                CleanupItemResult("/storage/b", 100, 50, "PARTIAL", "still exists"),
            ),
        )
        val environment = CleanupEnvironmentContext("Acme", "Phone", 36, "ext4", "/storage", "/dev/block/test", false)
        val report = CleanupVerificationEngine.build(
            review(), result, 1, 2,
            mapOf("/storage/a" to observation(CleanupPathPresence.MISSING), "/storage/b" to observation(CleanupPathPresence.PRESENT)),
            1_000, 1_120, "1.0", environment,
        )
        assertEquals(200L, report.plannedBytes)
        assertEquals(200L, report.attemptedBytes)
        assertEquals(150L, report.reportedDeletedBytes)
        assertEquals(100L, report.confirmedDeletedBytes)
        assertEquals(50L, report.partialBytes)
        assertEquals(0L, report.failedBytes)
        assertEquals(120L, report.measuredFreeSpaceDelta)
        assertEquals("ext4", report.environment.filesystem)
        assertFalse(report.fullyVerified)
    }

    @Test fun `changed and failed paths retain failed byte totals`() {
        val result = CleanupExecutionResult(false, "op", "FAILED", 0, 2, listOf(
            CleanupItemResult("/storage/a", 100, 0, "CHANGED", "content changed since review"),
            CleanupItemResult("/storage/b", 100, 0, "FAILED", "permission denied"),
        ))
        val report = CleanupVerificationEngine.build(review(), result, 1, 2, emptyMap(), null, null, "1.0")
        assertEquals(CleanupVerificationStatus.CHANGED_SINCE_REVIEW, report.items[0].status)
        assertEquals(CleanupVerificationStatus.FAILED, report.items[1].status)
        assertEquals(200L, report.failedBytes)
        assertEquals(0L, report.confirmedDeletedBytes)
    }

    @Test fun `verification errors never count as confirmed deletion`() {
        val result = CleanupExecutionResult(true, "op", "COMPLETED", 200, 0, listOf(
            CleanupItemResult("/storage/a", 100, 100, "DELETED"),
            CleanupItemResult("/storage/b", 100, 100, "DELETED"),
        ))
        val report = CleanupVerificationEngine.build(
            review(), result, 1, 2,
            mapOf(
                "/storage/a" to observation(CleanupPathPresence.MISSING),
                "/storage/b" to observation(CleanupPathPresence.UNKNOWN, "root service unavailable"),
            ),
            null, null, "1.0",
        )
        assertEquals(100L, report.confirmedDeletedBytes)
        assertEquals(CleanupVerificationStatus.FAILED, report.items[1].status)
        assertFalse(report.items[1].verifiedMissing)
        assertEquals(100L, report.failedBytes)
    }

    @Test fun `retry verification promotes only root reported missing paths`() {
        val first = CleanupVerificationEngine.build(
            review(),
            CleanupExecutionResult(true, "op", "COMPLETED", 200, 0, listOf(
                CleanupItemResult("/storage/a", 100, 100, "DELETED"),
                CleanupItemResult("/storage/b", 100, 100, "DELETED"),
            )),
            1, 2,
            mapOf(
                "/storage/a" to observation(CleanupPathPresence.UNKNOWN, "inspection unavailable"),
                "/storage/b" to observation(CleanupPathPresence.PRESENT),
            ),
            null, null, "1.0",
        )
        val retried = CleanupVerificationEngine.reverify(
            first,
            mapOf(
                "/storage/a" to observation(CleanupPathPresence.MISSING),
                "/storage/b" to observation(CleanupPathPresence.PRESENT),
            ),
            verifiedAt = 5,
            freeBytesAfter = 500,
        )
        assertEquals(2, retried.verificationAttempts)
        assertEquals(5L, retried.lastVerifiedAt)
        assertEquals(CleanupVerificationStatus.VERIFIED_DELETED, retried.items[0].status)
        assertEquals(CleanupVerificationStatus.PARTIAL, retried.items[1].status)
        assertEquals(100L, retried.confirmedDeletedBytes)
    }

    @Test fun `blocked review paths remain visible and prevent full verification`() {
        val base = review()
        val withBlocked = base.copy(blocked = listOf(CleanupBlockedItem("/system", "Protected Android path")))
        val result = CleanupExecutionResult(false, "op", "FAILED", 0, 2, emptyList())
        val report = CleanupVerificationEngine.build(withBlocked, result, 1, 2, emptyMap(), null, null, "1.0")
        val blocked = report.items.single { it.path == "/system" }
        assertEquals(CleanupVerificationStatus.BLOCKED, blocked.status)
        assertTrue(blocked.message.orEmpty().contains("Protected"))
        assertFalse(report.fullyVerified)
    }

    @Test fun `targeted rescan uses smallest safe common parent`() {
        assertEquals(
            "/storage/emulated/0/Download",
            CleanupVerificationEngine.targetedRescanRoot(
                "/storage/emulated/0",
                listOf("/storage/emulated/0/Download/a.zip", "/storage/emulated/0/Download/sub/b.zip"),
            ),
        )
        assertEquals(
            "/storage/emulated/0",
            CleanupVerificationEngine.targetedRescanRoot(
                "/storage/emulated/0",
                listOf("/storage/emulated/0/Download/a.zip", "/storage/emulated/0/DCIM/b.jpg"),
            ),
        )
    }

    @Test fun `report codec round trips schema two context and counters`() {
        val report = CleanupVerificationEngine.build(
            review(),
            CleanupExecutionResult(true, "op", "COMPLETED", 200, 0, listOf(
                CleanupItemResult("/storage/a", 100, 100, "DELETED"),
                CleanupItemResult("/storage/b", 100, 100, "DELETED"),
            )),
            1, 2,
            mapOf("/storage/a" to observation(CleanupPathPresence.MISSING), "/storage/b" to observation(CleanupPathPresence.MISSING)),
            10, 210, "2.0", CleanupEnvironmentContext("Acme", "Phone", 36, "f2fs", "/storage", "/dev/test", false),
        )
        val decoded = CleanupReportCodec.fromJson(CleanupReportCodec.toJson(report))
        assertEquals(CleanupVerificationReport.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(report.confirmedDeletedBytes, decoded.confirmedDeletedBytes)
        assertEquals("f2fs", decoded.environment.filesystem)
        assertEquals(2, decoded.items.size)
        assertTrue(decoded.fullyVerified)
    }

    @Test fun `schema one report migrates with derived accounting`() {
        val legacy = JSONObject()
            .put("schemaVersion", 1)
            .put("operationId", "legacy")
            .put("scanId", "scan")
            .put("rootPath", "/storage")
            .put("startedAt", 1)
            .put("completedAt", 2)
            .put("plannedBytes", 100)
            .put("reportedDeletedBytes", 100)
            .put("confirmedDeletedBytes", 100)
            .put("appVersion", "1.0")
            .put("items", JSONArray().put(JSONObject()
                .put("path", "/storage/a")
                .put("plannedBytes", 100)
                .put("reportedDeletedBytes", 100)
                .put("status", "VERIFIED_DELETED")
                .put("verifiedMissing", true)))
        val migrated = CleanupReportCodec.fromJson(legacy)
        assertEquals(CleanupVerificationReport.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(100L, migrated.attemptedBytes)
        assertEquals(0L, migrated.failedBytes)
        assertEquals(CleanupPathPresence.MISSING, migrated.items.single().pathPresence)
    }

    @Test fun `unsupported report schema is rejected`() {
        val invalid = JSONObject().put("schemaVersion", 999)
        assertTrue(runCatching { CleanupReportCodec.fromJson(invalid) }.isFailure)
    }

    @Test fun `report redaction removes credentials but keeps status labels`() {
        val redacted = CleanupReportRedactor.redact("Authorization: Bearer secret-token cookie=session-value status=failed").orEmpty()
        assertFalse(redacted.contains("secret-token"))
        assertFalse(redacted.contains("session-value"))
        assertTrue(redacted.contains("status=failed"))
    }

    private fun observation(presence: CleanupPathPresence, message: String? = null) = CleanupPathObservation(presence, message)

    private fun review(): CleanupReview = CleanupReview(
        ok = true, operationId = "op", scanId = "scan", rootPath = "/storage", reviewedAt = 1,
        accepted = listOf(approved("/storage/a"), approved("/storage/b")), blocked = emptyList(), plannedBytes = 200,
    )

    private fun approved(path: String) = CleanupApprovedItem(path, 100, false, CleanupPathFingerprint(1, 1, 0, 100, 1, "tree", 1))
}
