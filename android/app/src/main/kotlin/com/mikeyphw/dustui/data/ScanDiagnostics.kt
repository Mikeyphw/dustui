package com.mikeyphw.dustui.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class ScanDiagnosticSeverity { INFO, WARNING, ERROR }

data class ScanDiagnosticIssue(
    val code: String,
    val severity: ScanDiagnosticSeverity,
    val phase: String,
    val message: String,
    val path: String? = null,
    val errno: Int? = null,
    val occurredAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("code", code)
        .put("severity", severity.name)
        .put("phase", phase)
        .put("message", message)
        .put("path", path)
        .put("errno", errno)
        .put("occurredAt", occurredAt)

    companion object {
        fun fromJson(json: JSONObject) = ScanDiagnosticIssue(
            code = json.optString("code", "UNKNOWN"),
            severity = runCatching { ScanDiagnosticSeverity.valueOf(json.optString("severity")) }
                .getOrDefault(ScanDiagnosticSeverity.WARNING),
            phase = json.optString("phase", "scan"),
            message = json.optString("message", "Unknown scanner diagnostic"),
            path = json.optString("path").takeIf(String::isNotBlank),
            errno = json.optInt("errno").takeIf { json.has("errno") && !json.isNull("errno") },
            occurredAt = json.optLong("occurredAt"),
        )
    }
}

data class ScanDiagnostics(
    val scanId: String,
    val rootPath: String,
    val rootServiceOk: Boolean = false,
    val rootUid: Int = -1,
    val rootGid: Int = -1,
    val rootPid: Int = -1,
    val rootBackend: String = "Unavailable",
    val scannerVersion: String = "",
    val selinuxEnforcing: Boolean? = null,
    val selinuxContext: String? = null,
    val mountNamespace: String? = null,
    val preflightOk: Boolean = false,
    val canonicalRoot: String? = null,
    val rootOpened: Boolean = false,
    val rootHasChildren: Boolean? = null,
    val mountPoint: String? = null,
    val filesystem: String? = null,
    val mountOptions: String? = null,
    val emittedEntries: Long = 0,
    val emittedFiles: Long = 0,
    val emittedDirectories: Long = 0,
    val truncated: Boolean = false,
    val depthLimitedDirectories: Long = 0,
    val skippedFilesystemEntries: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val issues: List<ScanDiagnosticIssue> = emptyList(),
) {
    val errorCount: Int get() = issues.count { it.severity == ScanDiagnosticSeverity.ERROR }
    val warningCount: Int get() = issues.count { it.severity == ScanDiagnosticSeverity.WARNING }
    val hasFailure: Boolean get() = errorCount > 0 || !rootServiceOk || !preflightOk

    fun append(issue: ScanDiagnosticIssue): ScanDiagnostics = copy(
        issues = (issues + issue).takeLast(MAX_ISSUES),
    )

    fun toJson(): JSONObject = JSONObject()
        .put("schema", 1)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
        .put("rootServiceOk", rootServiceOk)
        .put("rootUid", rootUid)
        .put("rootGid", rootGid)
        .put("rootPid", rootPid)
        .put("rootBackend", rootBackend)
        .put("scannerVersion", scannerVersion)
        .put("selinuxEnforcing", selinuxEnforcing)
        .put("selinuxContext", selinuxContext)
        .put("mountNamespace", mountNamespace)
        .put("preflightOk", preflightOk)
        .put("canonicalRoot", canonicalRoot)
        .put("rootOpened", rootOpened)
        .put("rootHasChildren", rootHasChildren)
        .put("mountPoint", mountPoint)
        .put("filesystem", filesystem)
        .put("mountOptions", mountOptions)
        .put("emittedEntries", emittedEntries)
        .put("emittedFiles", emittedFiles)
        .put("emittedDirectories", emittedDirectories)
        .put("truncated", truncated)
        .put("depthLimitedDirectories", depthLimitedDirectories)
        .put("skippedFilesystemEntries", skippedFilesystemEntries)
        .put("startedAt", startedAt)
        .put("completedAt", completedAt)
        .put("issues", JSONArray().apply { issues.forEach { put(it.toJson()) } })

    companion object {
        private const val MAX_ISSUES = 64

        fun fromJson(json: JSONObject): ScanDiagnostics {
            val issuesJson = json.optJSONArray("issues") ?: JSONArray()
            return ScanDiagnostics(
                scanId = json.getString("scanId"),
                rootPath = json.getString("rootPath"),
                rootServiceOk = json.optBoolean("rootServiceOk"),
                rootUid = json.optInt("rootUid", -1),
                rootGid = json.optInt("rootGid", -1),
                rootPid = json.optInt("rootPid", -1),
                rootBackend = json.optString("rootBackend", "Unavailable"),
                scannerVersion = json.optString("scannerVersion"),
                selinuxEnforcing = if (json.has("selinuxEnforcing") && !json.isNull("selinuxEnforcing")) json.getBoolean("selinuxEnforcing") else null,
                selinuxContext = json.optString("selinuxContext").takeIf(String::isNotBlank),
                mountNamespace = json.optString("mountNamespace").takeIf(String::isNotBlank),
                preflightOk = json.optBoolean("preflightOk"),
                canonicalRoot = json.optString("canonicalRoot").takeIf(String::isNotBlank),
                rootOpened = json.optBoolean("rootOpened"),
                rootHasChildren = if (json.has("rootHasChildren") && !json.isNull("rootHasChildren")) json.getBoolean("rootHasChildren") else null,
                mountPoint = json.optString("mountPoint").takeIf(String::isNotBlank),
                filesystem = json.optString("filesystem").takeIf(String::isNotBlank),
                mountOptions = json.optString("mountOptions").takeIf(String::isNotBlank),
                emittedEntries = json.optLong("emittedEntries"),
                emittedFiles = json.optLong("emittedFiles"),
                emittedDirectories = json.optLong("emittedDirectories"),
                truncated = json.optBoolean("truncated"),
                depthLimitedDirectories = json.optLong("depthLimitedDirectories"),
                skippedFilesystemEntries = json.optLong("skippedFilesystemEntries"),
                startedAt = json.optLong("startedAt"),
                completedAt = json.optLong("completedAt").takeIf { json.has("completedAt") && !json.isNull("completedAt") },
                issues = buildList {
                    for (index in 0 until issuesJson.length()) add(ScanDiagnosticIssue.fromJson(issuesJson.getJSONObject(index)))
                },
            )
        }
    }
}

class ScanDiagnosticStore(context: Context) {
    private val directory = File(context.filesDir, "scan-diagnostics")

    @Synchronized
    fun save(diagnostics: ScanDiagnostics) {
        check(directory.exists() || directory.mkdirs()) { "Unable to create scan diagnostics directory" }
        val target = file(diagnostics.scanId)
        val temporary = File(directory, "${diagnostics.scanId}.json.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(diagnostics.toJson().toString(2).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(temporary.renameTo(target) || runCatching {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }.isSuccess) { "Unable to commit scan diagnostics" }
    }

    @Synchronized
    fun load(scanId: String): ScanDiagnostics? = runCatching {
        file(scanId).takeIf(File::isFile)?.readText()?.let(::JSONObject)?.let(ScanDiagnostics::fromJson)
    }.getOrNull()

    private fun file(scanId: String) = File(directory, "${scanId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
}
