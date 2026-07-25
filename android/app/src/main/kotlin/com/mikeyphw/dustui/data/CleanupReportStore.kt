package com.mikeyphw.dustui.data

import android.content.Context
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A report load never silently eats corruption; diagnostics remain available to the UI. */
data class CleanupReportLoadResult(
    val reports: List<CleanupVerificationReport>,
    val diagnostics: List<String>,
)

class CleanupReportStore(private val context: Context) {
    private val directory = File(context.filesDir, "cleanup-reports-v1").apply { mkdirs() }

    fun save(report: CleanupVerificationReport) {
        val target = File(directory, safeName(report.operationId) + ".json")
        val temporary = File(directory, target.name + ".tmp")
        temporary.writeText(CleanupReportCodec.toJson(report).toString(2))
        check(temporary.renameTo(target) || runCatching { temporary.copyTo(target, overwrite = true); temporary.delete() }.isSuccess) {
            "Unable to atomically save deletion verification report"
        }
        directory.listFiles().orEmpty().filter { it.extension == "json" }
            .sortedByDescending(File::lastModified).drop(MAX_REPORTS).forEach(File::delete)
    }

    fun load(): CleanupReportLoadResult {
        val diagnostics = mutableListOf<String>()
        val reports = directory.listFiles().orEmpty()
            .filter { it.extension == "json" }
            .sortedByDescending(File::lastModified)
            .mapNotNull { file ->
                runCatching { CleanupReportCodec.fromJson(JSONObject(file.readText())) }
                    .onFailure { diagnostics += "${file.name}: ${it.message ?: it.javaClass.simpleName}" }
                    .getOrNull()
            }
        return CleanupReportLoadResult(reports, diagnostics)
    }

    fun reports(): List<CleanupVerificationReport> = load().reports
    fun diagnostics(): List<String> = load().diagnostics
    fun report(operationId: String): CleanupVerificationReport? = reports().firstOrNull { it.operationId == operationId }

    fun exportJson(report: CleanupVerificationReport): android.net.Uri = export(report, "json", CleanupReportCodec.toJson(report).toString(2))
    fun exportText(report: CleanupVerificationReport): android.net.Uri = export(report, "txt", CleanupReportCodec.toText(report))

    private fun export(report: CleanupVerificationReport, extension: String, contents: String): android.net.Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "dustui-cleanup-${safeName(report.operationId)}.$extension")
        file.writeText(contents)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun safeName(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    private companion object { const val MAX_REPORTS = 100 }
}

object CleanupReportCodec {
    fun toJson(report: CleanupVerificationReport): JSONObject = JSONObject()
        .put("schemaVersion", report.schemaVersion)
        .put("operationId", report.operationId)
        .put("scanId", report.scanId)
        .put("rootPath", report.rootPath)
        .put("startedAt", report.startedAt)
        .put("completedAt", report.completedAt)
        .put("plannedBytes", report.plannedBytes)
        .put("attemptedBytes", report.attemptedBytes)
        .put("reportedDeletedBytes", report.reportedDeletedBytes)
        .put("confirmedDeletedBytes", report.confirmedDeletedBytes)
        .put("partialBytes", report.partialBytes)
        .put("failedBytes", report.failedBytes)
        .put("verificationAttempts", report.verificationAttempts)
        .put("lastVerifiedAt", report.lastVerifiedAt)
        .apply { report.freeBytesBefore?.let { put("freeBytesBefore", it) }; report.freeBytesAfter?.let { put("freeBytesAfter", it) } }
        .put("appVersion", report.appVersion)
        .put("environment", JSONObject()
            .put("manufacturer", report.environment.manufacturer)
            .put("model", report.environment.model)
            .put("sdkInt", report.environment.sdkInt)
            .apply { report.environment.filesystem?.let { put("filesystem", it) } }
            .apply { report.environment.mountPoint?.let { put("mountPoint", it) } }
            .apply { report.environment.mountSource?.let { put("mountSource", it) } }
            .apply { report.environment.readOnly?.let { put("readOnly", it) } })
        .put("items", JSONArray(report.items.map { item ->
            JSONObject()
                .put("path", item.path)
                .put("plannedBytes", item.plannedBytes)
                .put("reportedDeletedBytes", item.reportedDeletedBytes)
                .put("status", item.status.name)
                .put("pathPresence", item.pathPresence.name)
                .put("verifiedMissing", item.verifiedMissing)
                .apply { item.message?.let { put("message", CleanupReportRedactor.redact(it)) } }
                .apply { item.fingerprint?.let { fingerprint ->
                    put("fingerprint", JSONObject()
                        .put("device", fingerprint.device)
                        .put("inode", fingerprint.inode)
                        .put("mode", fingerprint.mode)
                        .put("statSizeBytes", fingerprint.statSizeBytes)
                        .put("modifiedAt", fingerprint.modifiedAt)
                        .put("treeDigest", fingerprint.treeDigest)
                        .put("entryCount", fingerprint.entryCount)
                        .apply { fingerprint.contentSha256?.let { put("contentSha256", it) } })
                } }
        }))

    fun fromJson(json: JSONObject): CleanupVerificationReport {
        val schema = json.optInt("schemaVersion", 1)
        require(schema in 1..CleanupVerificationReport.SCHEMA_VERSION) { "Unsupported cleanup-report schema $schema" }
        val array = json.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val fingerprintJson = item.optJSONObject("fingerprint")
                add(CleanupVerifiedItem(
                    path = item.getString("path"),
                    plannedBytes = item.optLong("plannedBytes"),
                    reportedDeletedBytes = item.optLong("reportedDeletedBytes"),
                    status = enumValues<CleanupVerificationStatus>().firstOrNull { it.name == item.optString("status") }
                        ?: CleanupVerificationStatus.FAILED,
                    pathPresence = enumValues<CleanupPathPresence>().firstOrNull { it.name == item.optString("pathPresence") }
                        ?: if (item.optBoolean("verifiedMissing")) CleanupPathPresence.MISSING else CleanupPathPresence.UNKNOWN,
                    message = CleanupReportRedactor.redact(item.optString("message").takeIf(String::isNotBlank)),
                    fingerprint = fingerprintJson?.let { fp ->
                        CleanupPathFingerprint(
                            fp.optLong("device"), fp.optLong("inode"), fp.optInt("mode"), fp.optLong("statSizeBytes"),
                            fp.optLong("modifiedAt"), fp.optString("treeDigest"), fp.optLong("entryCount"),
                            fp.optString("contentSha256").takeIf(String::isNotBlank),
                        )
                    },
                ))
            }
        }
        val environmentJson = json.optJSONObject("environment")
        val planned = json.optLong("plannedBytes", items.sumOf { it.plannedBytes })
        val attempted = if (json.has("attemptedBytes")) json.optLong("attemptedBytes") else items.sumOf { it.attemptedBytes }
        val partial = if (json.has("partialBytes")) json.optLong("partialBytes") else items.sumOf { it.partialBytes }
        val failed = if (json.has("failedBytes")) json.optLong("failedBytes") else items.sumOf { it.failedBytes }
        val completed = json.optLong("completedAt")
        return CleanupVerificationReport(
            schemaVersion = CleanupVerificationReport.SCHEMA_VERSION,
            operationId = json.getString("operationId"),
            scanId = json.getString("scanId"),
            rootPath = json.getString("rootPath"),
            startedAt = json.optLong("startedAt"),
            completedAt = completed,
            plannedBytes = planned,
            attemptedBytes = attempted,
            reportedDeletedBytes = json.optLong("reportedDeletedBytes"),
            confirmedDeletedBytes = json.optLong("confirmedDeletedBytes"),
            partialBytes = partial,
            failedBytes = failed,
            freeBytesBefore = json.optLongOrNull("freeBytesBefore"),
            freeBytesAfter = json.optLongOrNull("freeBytesAfter"),
            items = items,
            appVersion = json.optString("appVersion", "unknown"),
            environment = CleanupEnvironmentContext(
                manufacturer = environmentJson?.optString("manufacturer", "unknown") ?: "unknown",
                model = environmentJson?.optString("model", "unknown") ?: "unknown",
                sdkInt = environmentJson?.optInt("sdkInt") ?: 0,
                filesystem = environmentJson?.optString("filesystem")?.takeIf(String::isNotBlank),
                mountPoint = environmentJson?.optString("mountPoint")?.takeIf(String::isNotBlank),
                mountSource = environmentJson?.optString("mountSource")?.takeIf(String::isNotBlank),
                readOnly = environmentJson?.takeIf { it.has("readOnly") }?.optBoolean("readOnly"),
            ),
            verificationAttempts = json.optInt("verificationAttempts", 1).coerceAtLeast(1),
            lastVerifiedAt = json.optLong("lastVerifiedAt", completed),
        )
    }

    fun toText(report: CleanupVerificationReport): String = buildString {
        appendLine("DustUI deletion verification report")
        appendLine("Operation: ${report.operationId}")
        appendLine("Scan: ${report.scanId}")
        appendLine("Root: ${report.rootPath}")
        appendLine("Started: ${report.startedAt}")
        appendLine("Completed: ${report.completedAt}")
        appendLine("Verification attempts: ${report.verificationAttempts}")
        appendLine("Last verified: ${report.lastVerifiedAt}")
        appendLine("App version: ${report.appVersion}")
        appendLine("Device: ${report.environment.manufacturer} ${report.environment.model} · SDK ${report.environment.sdkInt}")
        appendLine("Filesystem: ${report.environment.filesystem ?: "unknown"} · mount ${report.environment.mountPoint ?: "unknown"} · source ${report.environment.mountSource ?: "unknown"}")
        appendLine("Planned bytes: ${report.plannedBytes}")
        appendLine("Attempted bytes: ${report.attemptedBytes}")
        appendLine("Reported deleted bytes: ${report.reportedDeletedBytes}")
        appendLine("Confirmed deleted bytes: ${report.confirmedDeletedBytes}")
        appendLine("Partial bytes: ${report.partialBytes}")
        appendLine("Failed bytes: ${report.failedBytes}")
        report.measuredFreeSpaceDelta?.let { appendLine("Measured free-space delta: $it") }
        appendLine()
        report.items.forEach { item ->
            appendLine("${item.status}: ${item.path}")
            appendLine("  planned=${item.plannedBytes} reported=${item.reportedDeletedBytes} presence=${item.pathPresence.name.lowercase()}")
            item.message?.let { appendLine("  ${CleanupReportRedactor.redact(it)}") }
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
}
