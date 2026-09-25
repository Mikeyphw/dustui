package com.mikeyphw.dustui.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mikeyphw.dustui.root.RootServiceClient
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
    /** Packaging-only adapter. Selection/redaction/sealing are Go-owned. */
    fun writeSealed(context: Context, request: DebugExportRequest, sealedBundle: JSONObject): DebugExportResult {
        require(!sealedBundle.optBoolean("includesFileContent", true)) { "Go support authority refused metadata-only invariant" }
        require(!sealedBundle.optBoolean("automaticUpload", true)) { "Go support authority refused no-auto-upload invariant" }
        val manifest = sealedBundle.optString("manifest")
        val operationId = sealedBundle.optString("operationId")
        val reproducibleKey = sealedBundle.optString("reproducibleExportKey")
        require(manifest.isNotBlank() && operationId.isNotBlank() && reproducibleKey.isNotBlank()) { "Go support bundle is not sealed/durable" }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "dustui-support-${request.createdAtMillis}-${request.mode.name.lowercase(Locale.ROOT)}.zip"
        val file = File(dir, fileName)
        val envelope = JSONObject()
            .put("schema", 2)
            .put("authority", "go-domain-support-bundle")
            .put("operationId", operationId)
            .put("destination", request.destination)
            .put("layout", request.layout)
            .put("createdAtMillis", request.createdAtMillis)
            .put("sealedManifest", manifest)
            .put("reproducibleExportKey", reproducibleKey)
            .put("noFileContents", true)
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("go-support-bundle.json")); zip.write(sealedBundle.toString(2).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("android-export-envelope.json")); zip.write(envelope.toString(2).toByteArray()); zip.closeEntry()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return DebugExportResult(
            mode = request.mode,
            uri = uri,
            fileName = fileName,
            createdAtMillis = request.createdAtMillis,
            entryCount = 0,
            eventCount = sealedBundle.optInt("diagnosticEventCount", 0),
            probeCount = 0,
            pathPolicy = "go_authority_redacted_metadata_only",
        )
    }
}
