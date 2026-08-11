package com.mikeyphw.dustui.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mikeyphw.dustui.root.RootServiceClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class DebugExportMode(val title: String, val detail: String) {
    Safe(
        title = "Safe",
        detail = "Counts, states, warnings, and redacted roots. No raw file paths.",
    ),
    PathAware(
        title = "Path-aware",
        detail = "Adds diagnostic paths for debugging. No file contents are exported.",
    ),
    FullDiagnostic(
        title = "Full diagnostic",
        detail = "Adds bounded path samples and detailed evidence. Still exports metadata only, never file contents.",
    ),
}

data class DebugExportRequest(
    val mode: DebugExportMode,
    val destination: String,
    val destinationLabel: String,
    val layout: String,
    val session: ScanSessionEntity?,
    val entries: List<ScanEntryEntity>,
    val events: List<DebugEvent>,
    val probeResults: List<DebugProbeResult>,
    val rootServiceInfo: RootServiceClient.RootServiceInfo?,
    val rootFailureMessage: String?,
    val pathPreflight: RootServiceClient.ScanRootPreflight?,
    val diagnostics: ScanDiagnostics?,
    val moduleReport: RootModuleReport?,
    val recommendationAnalysis: CleanupRecommendationAnalysis?,
    val selectedPaths: Set<String>,
    val cleanupReview: CleanupReview?,
    val cleanupReports: List<CleanupVerificationReport>,
    val scanDataStale: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

data class DebugExportResult(
    val mode: DebugExportMode,
    val uri: Uri,
    val fileName: String,
    val createdAtMillis: Long,
    val entryCount: Int,
    val eventCount: Int,
    val probeCount: Int,
    val pathPolicy: String,
)

object DebugExportBundleWriter {
    private const val DEBUG_EXPORT_SCHEMA = 1
    private const val FULL_SAMPLE_LIMIT = 32
    private const val PATH_AWARE_SAMPLE_LIMIT = 16
    private val ABSOLUTE_PATH_PATTERN = Regex("""(?<![A-Za-z0-9_.-])/(?:[^\s,;:"']+)""")

    fun write(context: Context, request: DebugExportRequest): DebugExportResult {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = request.createdAtMillis.toString()
        val fileName = "dustui-debug-${stamp}-${request.mode.name.lowercase(Locale.ROOT)}.zip"
        val file = File(dir, fileName)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.jsonEntry("dustui-debug-report.json", reportJson(context, request))
            zip.jsonLinesEntry("trace-events.jsonl", request.events.map { eventJson(it, request.mode) })
            zip.jsonLinesEntry("probe-results.jsonl", request.probeResults.map { probeJson(it, request.mode) })
            zip.jsonEntry("scan-summary.json", scanJson(request))
            zip.jsonEntry("root-diagnostics.json", rootJson(request))
            zip.jsonEntry("navigation-state.json", navigationJson(request))
            zip.jsonEntry("cleanup-state.json", cleanupJson(request))
            zip.jsonEntry("recommendation-summary.json", recommendationJson(request))
            zip.jsonEntry("module-diagnostics.json", moduleJson(request))
            zip.jsonEntry("app-info.json", appInfoJson(context, request))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return DebugExportResult(
            mode = request.mode,
            uri = uri,
            fileName = fileName,
            createdAtMillis = request.createdAtMillis,
            entryCount = request.entries.size,
            eventCount = request.events.size,
            probeCount = request.probeResults.size,
            pathPolicy = request.mode.pathPolicy,
        )
    }

    fun valueForMode(value: String?, mode: DebugExportMode): String = when {
        value == null -> "null"
        mode == DebugExportMode.Safe -> safeValue(value)
        mode == DebugExportMode.PathAware -> pathAwareValue(value)
        else -> pathAwareValue(value)
    }

    private fun reportJson(context: Context, request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("mode", request.mode.name)
        .put("modeTitle", request.mode.title)
        .put("pathPolicy", request.mode.pathPolicy)
        .put("createdAtMillis", request.createdAtMillis)
        .put("packageName", context.packageName)
        .put("files", JSONArray(listOf(
            "dustui-debug-report.json",
            "trace-events.jsonl",
            "probe-results.jsonl",
            "scan-summary.json",
            "root-diagnostics.json",
            "navigation-state.json",
            "cleanup-state.json",
            "recommendation-summary.json",
            "module-diagnostics.json",
            "app-info.json",
        )))
        .put("privacy", request.mode.detail)
        .put("noFileContents", true)

    private fun appInfoJson(context: Context, request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("packageName", context.packageName)
        .put("createdAtMillis", request.createdAtMillis)
        .put("exportMode", request.mode.name)

    private fun navigationJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("destination", request.destination)
        .put("destinationLabel", request.destinationLabel)
        .put("layout", request.layout)
        .put("scanDataStale", request.scanDataStale)

    private fun scanJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("hasSession", request.session != null)
        .put("session", request.session?.let { session ->
            JSONObject()
                .put("id", session.id)
                .put("presetId", session.presetId ?: JSONObject.NULL)
                .put("rootPath", valueForMode(session.rootPath, request.mode))
                .put("status", session.status)
                .put("backend", session.backend)
                .put("startedAt", session.startedAt)
                .put("completedAt", session.completedAt ?: JSONObject.NULL)
                .put("scannedItems", session.scannedItems)
                .put("scannedBytes", session.scannedBytes)
                .put("totalBytes", session.totalBytes)
                .put("warningCount", session.warningCount)
                .put("currentPath", valueForMode(session.currentPath, request.mode))
                .put("errorMessage", session.errorMessage ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        .put("entryCount", request.entries.size)
        .put("entrySamples", entrySamples(request))
        .put("diagnostics", request.diagnostics?.let { diagnosticsJson(it, request.mode) } ?: JSONObject.NULL)

    private fun diagnosticsJson(diagnostics: ScanDiagnostics, mode: DebugExportMode): JSONObject = JSONObject()
        .put("scanId", diagnostics.scanId)
        .put("rootPath", valueForMode(diagnostics.rootPath, mode))
        .put("rootServiceOk", diagnostics.rootServiceOk)
        .put("rootUid", diagnostics.rootUid)
        .put("rootBackend", diagnostics.rootBackend)
        .put("scannerVersion", diagnostics.scannerVersion)
        .put("selinuxEnforcing", diagnostics.selinuxEnforcing ?: JSONObject.NULL)
        .put("preflightOk", diagnostics.preflightOk)
        .put("canonicalRoot", valueForMode(diagnostics.canonicalRoot, mode))
        .put("rootOpened", diagnostics.rootOpened)
        .put("rootHasChildren", diagnostics.rootHasChildren ?: JSONObject.NULL)
        .put("mountPoint", valueForMode(diagnostics.mountPoint, mode))
        .put("filesystem", diagnostics.filesystem ?: JSONObject.NULL)
        .put("emittedEntries", diagnostics.emittedEntries)
        .put("emittedFiles", diagnostics.emittedFiles)
        .put("emittedDirectories", diagnostics.emittedDirectories)
        .put("truncated", diagnostics.truncated)
        .put("depthLimitedDirectories", diagnostics.depthLimitedDirectories)
        .put("skippedFilesystemEntries", diagnostics.skippedFilesystemEntries)
        .put("warningCount", diagnostics.warningCount)
        .put("errorCount", diagnostics.errorCount)
        .put("issues", JSONArray(diagnostics.issues.take(16).map { issue ->
            JSONObject()
                .put("severity", issue.severity.name)
                .put("code", issue.code)
                .put("message", issue.message)
                .put("path", valueForMode(issue.path, mode))
        }))

    private fun rootJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("rootService", request.rootServiceInfo?.let { info ->
            JSONObject()
                .put("ok", info.ok)
                .put("uid", info.uid)
                .put("gid", info.gid)
                .put("pid", info.pid)
                .put("scanner", info.scanner)
                .put("backend", info.backend)
                .put("selinuxEnforcing", info.selinuxEnforcing ?: JSONObject.NULL)
                .put("selinuxContext", valueForMode(info.selinuxContext, request.mode))
                .put("mountNamespace", info.mountNamespace ?: JSONObject.NULL)
                .put("message", info.message ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        .put("rootFailureMessage", request.rootFailureMessage ?: JSONObject.NULL)
        .put("pathPreflight", request.pathPreflight?.let { preflight ->
            JSONObject()
                .put("ok", preflight.ok)
                .put("code", preflight.code)
                .put("requestedPath", valueForMode(preflight.requestedPath, request.mode))
                .put("canonicalPath", valueForMode(preflight.canonicalPath, request.mode))
                .put("exists", preflight.exists)
                .put("directory", preflight.directory)
                .put("opened", preflight.opened)
                .put("hasChildren", preflight.hasChildren ?: JSONObject.NULL)
                .put("errno", preflight.errno ?: JSONObject.NULL)
                .put("message", preflight.message)
                .put("mountPoint", valueForMode(preflight.mountPoint, request.mode))
                .put("filesystem", preflight.filesystem ?: JSONObject.NULL)
        } ?: JSONObject.NULL)

    private fun cleanupJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("scanDataStale", request.scanDataStale)
        .put("selectedPathCount", request.selectedPaths.size)
        .put("selectedPathSamples", pathSamples(request.selectedPaths.toList(), request.mode))
        .put("cleanupReportCount", request.cleanupReports.size)
        .put("cleanupReports", JSONArray(request.cleanupReports.take(12).map { report ->
            JSONObject()
                .put("operationId", report.operationId)
                .put("scanId", report.scanId)
                .put("rootPath", valueForMode(report.rootPath, request.mode))
                .put("startedAt", report.startedAt)
                .put("completedAt", report.completedAt)
                .put("plannedBytes", report.plannedBytes)
                .put("attemptedBytes", report.attemptedBytes)
                .put("confirmedDeletedBytes", report.confirmedDeletedBytes)
                .put("partialBytes", report.partialBytes)
                .put("failedBytes", report.failedBytes)
                .put("fullyVerified", report.fullyVerified)
                .put("failedCount", report.failedCount)
                .put("unknownVerificationCount", report.unknownVerificationCount)
        }))
        .put("review", request.cleanupReview?.let { review ->
            JSONObject()
                .put("ok", review.ok)
                .put("operationId", review.operationId)
                .put("scanId", review.scanId)
                .put("rootPath", valueForMode(review.rootPath, request.mode))
                .put("acceptedCount", review.accepted.size)
                .put("blockedCount", review.blocked.size)
                .put("plannedBytes", review.plannedBytes)
                .put("message", review.message ?: JSONObject.NULL)
                .put("acceptedPathSamples", pathSamples(review.accepted.map { it.path }, request.mode))
                .put("blockedPathSamples", JSONArray(review.blocked.take(sampleLimit(request.mode)).map { blocked ->
                    JSONObject()
                        .put("path", valueForMode(blocked.path, request.mode))
                        .put("reason", blocked.reason)
                }))
        } ?: JSONObject.NULL)

    private fun recommendationJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("analysis", request.recommendationAnalysis?.let { analysis ->
            JSONObject()
                .put("scanId", analysis.scanId)
                .put("generatedAt", analysis.generatedAt)
                .put("itemCount", analysis.items.size)
                .put("totalBytes", analysis.totalBytes)
                .put("highConfidenceBytes", analysis.highConfidenceBytes)
                .put("items", JSONArray(analysis.items.take(12).map { item ->
                    JSONObject()
                        .put("id", item.id)
                        .put("kind", item.kind.name)
                        .put("title", item.title)
                        .put("reclaimableBytes", item.reclaimableBytes)
                        .put("confidence", item.confidence.name)
                        .put("risk", item.risk.name)
                        .put("batchEligible", item.batchEligible)
                        .put("pathCount", item.paths.size)
                        .put("pathSamples", pathSamples(item.paths, request.mode))
                }))
        } ?: JSONObject.NULL)

    private fun moduleJson(request: DebugExportRequest): JSONObject = JSONObject()
        .put("schema", DEBUG_EXPORT_SCHEMA)
        .put("report", request.moduleReport?.let { report ->
            JSONObject()
                .put("moduleCount", report.modules.size)
                .put("totalBytes", report.totalBytes)
                .put("disabledCount", report.disabledCount)
                .put("webUiCount", report.webUiCount)
                .put("conflictCount", report.conflictCount)
                .put("diagnostic", JSONObject()
                    .put("status", report.diagnostic.status.name)
                    .put("message", report.diagnostic.message)
                    .put("currentScanRoot", valueForMode(report.diagnostic.currentScanRoot, request.mode))
                    .put("expectedRoot", valueForMode(report.diagnostic.expectedRoot, request.mode))
                    .put("scanIncludesModules", report.diagnostic.scanIncludesModules)
                    .put("modulePathAccessible", report.diagnostic.modulePathAccessible ?: JSONObject.NULL)
                    .put("rootCode", report.diagnostic.rootCode ?: JSONObject.NULL))
                .put("modules", JSONArray(report.modules.take(12).map { module ->
                    JSONObject()
                        .put("moduleId", if (request.mode == DebugExportMode.Safe) "<module-id-redacted>" else module.moduleId)
                        .put("totalBytes", module.totalBytes)
                        .put("entryCount", module.entryCount)
                        .put("hasModuleProp", module.hasModuleProp)
                        .put("hasServiceScript", module.hasServiceScript)
                        .put("hasPostFsDataScript", module.hasPostFsDataScript)
                        .put("hasWebUi", module.hasWebUi)
                        .put("disabled", module.disabled)
                        .put("updatePending", module.updatePending)
                        .put("largestPathSamples", pathSamples(module.largestPaths.map { it.path }, request.mode))
                }))
        } ?: JSONObject.NULL)

    private fun eventJson(event: DebugEvent, mode: DebugExportMode): JSONObject = JSONObject()
        .put("id", event.id)
        .put("timestampMillis", event.timestampMillis)
        .put("category", event.category.name)
        .put("severity", event.severity.name)
        .put("name", event.name)
        .put("message", event.message)
        .put("evidence", JSONObject(event.evidence.mapValues { (_, value) -> valueForMode(value, mode) }))

    private fun probeJson(result: DebugProbeResult, mode: DebugExportMode): JSONObject = JSONObject()
        .put("target", result.target.name)
        .put("title", result.target.title)
        .put("group", result.target.group.name)
        .put("status", result.status.name)
        .put("message", result.message)
        .put("updatedAtMillis", result.updatedAtMillis)
        .put("evidence", JSONObject(result.evidence.mapValues { (_, value) -> valueForMode(value, mode) }))

    private fun entrySamples(request: DebugExportRequest): JSONArray {
        if (request.mode == DebugExportMode.Safe) return JSONArray()
        val limit = sampleLimit(request.mode)
        return JSONArray(request.entries.take(limit).map { entry ->
            JSONObject()
                .put("path", valueForMode(entry.path, request.mode))
                .put("parentPath", valueForMode(entry.parentPath, request.mode))
                .put("name", if (request.mode == DebugExportMode.FullDiagnostic) entry.name else "<name-redacted>")
                .put("sizeBytes", entry.sizeBytes)
                .put("allocatedBytes", entry.allocatedBytes)
                .put("directory", entry.isDirectory)
                .put("depth", entry.depth)
                .put("modifiedAt", entry.modifiedAt)
                .put("fileCount", entry.fileCount)
        })
    }

    private fun pathSamples(paths: List<String>, mode: DebugExportMode): JSONArray {
        if (mode == DebugExportMode.Safe) return JSONArray()
        return JSONArray(paths.take(sampleLimit(mode)).map { valueForMode(it, mode) })
    }

    private fun sampleLimit(mode: DebugExportMode): Int = when (mode) {
        DebugExportMode.Safe -> 0
        DebugExportMode.PathAware -> PATH_AWARE_SAMPLE_LIMIT
        DebugExportMode.FullDiagnostic -> FULL_SAMPLE_LIMIT
    }

    private val DebugExportMode.pathPolicy: String get() = when (this) {
        DebugExportMode.Safe -> "redacted counts-only; raw paths omitted"
        DebugExportMode.PathAware -> "raw diagnostic paths allowed; file contents omitted"
        DebugExportMode.FullDiagnostic -> "bounded path samples and detailed metadata; file contents omitted"
    }

    private fun safeValue(value: String): String {
        val sensitive = when {
            value.contains("token", ignoreCase = true) -> "<redacted>"
            value.contains("secret", ignoreCase = true) -> "<redacted>"
            value.contains("authorization", ignoreCase = true) -> "<redacted>"
            else -> value
        }
        return ABSOLUTE_PATH_PATTERN.replace(sensitive) { match ->
            val raw = match.value
            when {
                raw.startsWith("/data/adb/modules") -> "/data/adb/modules/…"
                raw.startsWith("/data") -> "/data/…"
                else -> "<path-redacted>"
            }
        }
    }

    private fun pathAwareValue(value: String): String = when {
        value.contains("token", ignoreCase = true) -> "<redacted>"
        value.contains("secret", ignoreCase = true) -> "<redacted>"
        value.contains("authorization", ignoreCase = true) -> "<redacted>"
        else -> value
    }

    private fun ZipOutputStream.jsonEntry(name: String, json: JSONObject) {
        add(name, json.toString(2))
    }

    private fun ZipOutputStream.jsonLinesEntry(name: String, rows: List<JSONObject>) {
        add(name, rows.joinToString(separator = "\n", postfix = "\n") { it.toString() })
    }

    private fun ZipOutputStream.add(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
