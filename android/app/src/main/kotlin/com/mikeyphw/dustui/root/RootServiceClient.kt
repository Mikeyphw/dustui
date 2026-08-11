package com.mikeyphw.dustui.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.mikeyphw.dustui.scan.ScanRequest
import com.mikeyphw.dustui.data.CleanupApprovedItem
import com.mikeyphw.dustui.data.CleanupBlockedItem
import com.mikeyphw.dustui.data.CleanupExecutionResult
import com.mikeyphw.dustui.data.CleanupItemResult
import com.mikeyphw.dustui.data.CleanupPlan
import com.mikeyphw.dustui.data.CleanupPathFingerprint
import com.mikeyphw.dustui.data.CleanupReview
import com.mikeyphw.dustui.data.DuplicateSkippedPath
import com.mikeyphw.dustui.data.DuplicateVerificationReport
import com.mikeyphw.dustui.data.DuplicateVerifiedPath
import com.mikeyphw.dustui.data.InstalledPackageInventory
import com.mikeyphw.dustui.data.VerifiedDuplicateGroup
import com.mikeyphw.dustui.data.RootPathInspection
import org.json.JSONArray
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RootServiceClient(private val context: Context) {
    private val bindMutex = Mutex()
    @Volatile private var remote: IDustRootService? = null
    @Volatile private var activeConnection: ServiceConnection? = null
    private val activeDuplicateOperations = ConcurrentHashMap<String, String>()

    suspend fun rootAvailable(): Boolean = withTimeoutOrNull(ROOT_TIMEOUT_MS) {
        runCatching { serviceInfo().let { it.ok && it.uid == 0 } }.getOrDefault(false)
    } ?: false

    suspend fun serviceInfo(): RootServiceInfo = cleanupCall { it.serviceInfo() }.let { raw ->
        val json = JSONObject(raw)
        RootServiceInfo(
            ok = json.optBoolean("ok"),
            uid = json.optInt("uid", -1),
            gid = json.optInt("gid", -1),
            pid = json.optInt("pid", -1),
            scanner = json.optString("scanner"),
            backend = json.optString("backend", "Unknown"),
            selinuxEnforcing = if (json.has("selinuxEnforcing") && !json.isNull("selinuxEnforcing")) json.getBoolean("selinuxEnforcing") else null,
            selinuxContext = json.optString("selinuxContext").takeIf(String::isNotBlank),
            mountNamespace = json.optString("mountNamespace").takeIf(String::isNotBlank),
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    suspend fun preflightScan(request: ScanRequest): ScanRootPreflight = cleanupCall { it.preflightScan(request.toJson()) }.let { raw ->
        val json = JSONObject(raw)
        ScanRootPreflight(
            ok = json.optBoolean("ok"),
            code = json.optString("code", if (json.optBoolean("ok")) "READY" else "PREFLIGHT_FAILED"),
            requestedPath = json.optString("requestedPath", request.rootPath),
            canonicalPath = json.optString("canonicalPath").takeIf(String::isNotBlank),
            exists = json.optBoolean("exists"),
            directory = json.optBoolean("directory"),
            symbolicLink = json.optBoolean("symbolicLink"),
            opened = json.optBoolean("opened"),
            hasChildren = if (json.has("hasChildren") && !json.isNull("hasChildren")) json.getBoolean("hasChildren") else null,
            errno = json.optInt("errno").takeIf { json.has("errno") && !json.isNull("errno") },
            message = json.optString("message", "Scan-root preflight failed"),
            mountPoint = json.optString("mountPoint").takeIf(String::isNotBlank),
            filesystem = json.optString("filesystem").takeIf(String::isNotBlank),
            mountOptions = json.optString("mountOptions").takeIf(String::isNotBlank),
        )
    }

    suspend fun startScan(request: ScanRequest, output: ParcelFileDescriptor): RootScanState =
        call(START_TIMEOUT_MS) { it.startScan(request.toJson(), output) }

    suspend fun scanState(scanId: String): RootScanState = call { it.scanState(scanId) }
    suspend fun cancelScan(scanId: String): RootScanState = call { it.cancelScan(scanId) }

    suspend fun verifyDuplicates(scanId: String, paths: List<String>, sizeBytes: Long, scanRoot: String): DuplicateVerificationReport {
        val operationId = UUID.randomUUID().toString()
        activeDuplicateOperations[scanId] = operationId
        val raw = try {
            duplicateExecutionCall(operationId, DUPLICATE_TIMEOUT_MS) { service ->
                service.verifyDuplicates(
                    JSONObject()
                        .put("operationId", operationId)
                        .put("scanId", scanId)
                        .put("sizeBytes", sizeBytes)
                        .put("partialBytes", 64 * 1024)
                        .put("scanRoot", scanRoot)
                        .put("paths", JSONArray(paths))
                        .toString(),
                )
            }
        } finally {
            activeDuplicateOperations.remove(scanId, operationId)
        }
        val json = JSONObject(raw)
        if (json.optBoolean("cancelled")) throw kotlinx.coroutines.CancellationException("Duplicate verification cancelled")
        check(json.optBoolean("ok")) { json.optString("message", "Duplicate verification failed") }
        val groupsJson = json.optJSONArray("groups") ?: JSONArray()
        val skippedJson = json.optJSONArray("skipped") ?: JSONArray()
        return DuplicateVerificationReport(
            groups = buildList {
                for (index in 0 until groupsJson.length()) {
                    val group = groupsJson.getJSONObject(index)
                    val pathsJson = group.optJSONArray("paths") ?: JSONArray()
                    add(VerifiedDuplicateGroup(
                        sha256 = group.getString("sha256"),
                        partialHash = group.optString("partialHash"),
                        sizeBytes = group.optLong("sizeBytes"),
                        pathRecords = buildList {
                            for (pathIndex in 0 until pathsJson.length()) {
                                val item = pathsJson.getJSONObject(pathIndex)
                                add(DuplicateVerifiedPath(
                                    path = item.getString("path"),
                                    deviceId = item.optLong("deviceId"),
                                    inode = item.optLong("inode"),
                                    linkCount = item.optLong("linkCount", 1L),
                                    modifiedAt = item.optLong("modifiedAt"),
                                    copyKey = item.getString("copyKey"),
                                    hardLinked = item.optBoolean("hardLinked"),
                                    autoDeleteEligible = item.optBoolean("autoDeleteEligible"),
                                    deletionBlockReason = item.optString("deletionBlockReason").takeIf(String::isNotBlank),
                                ))
                            }
                        },
                        physicalCopyCount = group.optInt("physicalCopyCount"),
                        hardLinkedCopyCount = group.optInt("hardLinkedCopyCount"),
                        reclaimableBytes = group.optLong("reclaimableBytes"),
                        safeReclaimableBytes = group.optLong("safeReclaimableBytes"),
                    ))
                }
            },
            checkedFiles = json.optInt("checkedFiles"),
            skipped = buildList {
                for (index in 0 until skippedJson.length()) {
                    val item = skippedJson.getJSONObject(index)
                    add(DuplicateSkippedPath(item.optString("path"), item.optString("reason", "Skipped")))
                }
            },
            partialGroups = json.optInt("partialGroups"),
            bytesHashed = json.optLong("bytesHashed"),
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }


    suspend fun cancelDuplicateVerification(scanId: String): Boolean = withContext(Dispatchers.IO) {
        val operationId = activeDuplicateOperations[scanId] ?: scanId
        runCatching { JSONObject(service().cancelDuplicateVerification(operationId)).optBoolean("cancelled") }.getOrDefault(false)
    }

    suspend fun installedPackages(): InstalledPackageInventory = cleanupCall { it.installedPackages() }.let { raw ->
        val json = JSONObject(raw)
        val users = json.optJSONArray("users") ?: JSONArray()
        val packagesByUser = buildMap {
            for (index in 0 until users.length()) {
                val user = users.getJSONObject(index)
                val packages = user.optJSONArray("packages") ?: JSONArray()
                put(
                    user.getInt("id"),
                    buildSet { for (packageIndex in 0 until packages.length()) add(packages.getString(packageIndex)) },
                )
            }
        }
        InstalledPackageInventory(
            packagesByUser = packagesByUser,
            complete = json.optBoolean("complete") && packagesByUser.isNotEmpty(),
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    suspend fun inspectPath(path: String, previewBytes: Int = 16 * 1024): RootPathInspection = cleanupCall { it.inspectPath(path, previewBytes) }.let(RootPathInspection::fromJson)

    suspend fun reviewCleanup(plan: CleanupPlan): CleanupReview {
        val normalized = plan.normalized()
        return cleanupCall { it.reviewCleanup(normalized.toJson()) }
            .let { raw -> parseReview(raw, normalized) }
    }

    suspend fun executeCleanup(review: CleanupReview): CleanupExecutionResult {
        val request = writeCleanupExecutionPlan(review)
        return cleanupExecutionCall(review.operationId, 120_000L) { it.executeCleanup(request) }.let(::parseExecution)
    }

    private fun writeCleanupExecutionPlan(review: CleanupReview): String {
        val directory = File(context.cacheDir, CLEANUP_PLAN_DIRECTORY).apply { mkdirs() }
        pruneOldCleanupPlanFiles(directory)
        val file = File(directory, "${safeOperationFileName(review.operationId)}.json")
        val temporary = File(directory, "${file.name}.tmp")
        temporary.writeText(review.toExecutionJson(), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.writeText(review.toExecutionJson(), Charsets.UTF_8)
            temporary.delete()
        }
        return JSONObject()
            .put("operationId", review.operationId)
            .toString()
    }

    private fun safeOperationFileName(operationId: String): String = operationId.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("").take(120).ifBlank { "operation" }

    private fun pruneOldCleanupPlanFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - CLEANUP_PLAN_RETENTION_MS
        directory.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
    }


    private suspend fun cleanupExecutionCall(
        operationId: String,
        timeoutMs: Long,
        block: (IDustRootService) -> String,
    ): String = try {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { block(service()) }
        }
    } catch (error: TimeoutCancellationException) {
        runCatching { service().cancelCleanup(operationId) }
        dropConnection()
        throw error
    }

    private suspend fun duplicateExecutionCall(
        operationId: String,
        timeoutMs: Long,
        block: (IDustRootService) -> String,
    ): String = try {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { block(service()) }
        }
    } catch (error: TimeoutCancellationException) {
        runCatching { service().cancelDuplicateVerification(operationId) }
        dropConnection()
        throw error
    }

    private suspend fun cleanupCall(timeoutMs: Long = CALL_TIMEOUT_MS, block: (IDustRootService) -> String): String = try {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) { block(service()) }
        }
    } catch (error: TimeoutCancellationException) {
        dropConnection()
        throw error
    }

    private fun parseReview(raw: String, plan: CleanupPlan): CleanupReview {
        val json = JSONObject(raw)
        val accepted = json.optJSONArray("accepted")
        val blocked = json.optJSONArray("blocked")
        return CleanupReview(
            ok = json.optBoolean("ok"),
            operationId = json.optString("operationId").ifBlank { plan.id },
            scanId = plan.scanId,
            rootPath = plan.rootPath,
            scanGeneration = plan.scanGeneration,
            reviewedAt = System.currentTimeMillis(),
            accepted = buildList {
                if (accepted != null) for (index in 0 until accepted.length()) {
                    val item = accepted.getJSONObject(index)
                    val fingerprint = item.getJSONObject("fingerprint")
                    add(
                        CleanupApprovedItem(
                            path = item.getString("path"),
                            plannedBytes = item.optLong("plannedBytes"),
                            isDirectory = item.optBoolean("directory"),
                            fingerprint = CleanupPathFingerprint(
                                device = fingerprint.optLong("device"),
                                mountId = fingerprint.optString("mountId").takeIf(String::isNotBlank),
                                inode = fingerprint.optLong("inode"),
                                mode = fingerprint.optInt("mode"),
                                statSizeBytes = fingerprint.optLong("statSizeBytes"),
                                modifiedAt = fingerprint.optLong("modifiedAt"),
                                treeDigest = fingerprint.getString("treeDigest"),
                                entryCount = fingerprint.optLong("entryCount"),
                                contentSha256 = fingerprint.optString("contentSha256").takeIf(String::isNotBlank),
                            ),
                            retainedPath = item.optString("retainedPath").takeIf(String::isNotBlank),
                            retainedSha256 = item.optString("retainedSha256").takeIf(String::isNotBlank),
                            packageName = item.optString("packageName").takeIf(String::isNotBlank),
                        ),
                    )
                }
            },
            blocked = buildList {
                if (blocked != null) for (index in 0 until blocked.length()) {
                    blocked.getJSONObject(index).let { add(CleanupBlockedItem(it.getString("path"), it.getString("reason"))) }
                }
            },
            plannedBytes = json.optLong("plannedBytes"),
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    private fun parseExecution(raw: String): CleanupExecutionResult {
        val json = JSONObject(raw)
        val items = json.optJSONArray("items")
        return CleanupExecutionResult(
            ok = json.optBoolean("ok"),
            operationId = json.optString("operationId"),
            status = json.optString("status"),
            deletedBytes = json.optLong("deletedBytes"),
            failedCount = json.optInt("failedCount"),
            items = buildList {
                if (items != null) for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(
                        CleanupItemResult(
                            path = item.getString("path"),
                            plannedBytes = item.optLong("plannedBytes"),
                            deletedBytes = item.optLong("deletedBytes"),
                            status = item.optString("status"),
                            message = item.optString("message").takeIf(String::isNotBlank),
                        ),
                    )
                }
            },
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    private suspend fun call(
        timeoutMs: Long = CALL_TIMEOUT_MS,
        block: (IDustRootService) -> String,
    ): RootScanState = try {
        withTimeout(timeoutMs) {
            withContext(Dispatchers.IO) {
                val service = service()
                runCatching { RootScanState.fromJson(block(service)) }.getOrElse { error ->
                    remote = null
                    throw error
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        dropConnection()
        RootScanState(false, message = "Root service call timed out")
    }

    private suspend fun service(): IDustRootService = remote ?: bindMutex.withLock {
        remote ?: bindService().also { remote = it }
    }

    private fun dropConnection() {
        val connection = activeConnection
        activeConnection = null
        remote = null
        if (connection != null) runCatching { RootService.unbind(connection) }
    }

    private suspend fun bindService(): IDustRootService = withTimeout(BIND_TIMEOUT_MS) {
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val intent = Intent(context, DustRootService::class.java)
                lateinit var connection: ServiceConnection
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        val service = IDustRootService.Stub.asInterface(binder)
                        remote = service
                        activeConnection = this
                        if (continuation.isActive) continuation.resume(service)
                    }
                    override fun onServiceDisconnected(name: ComponentName) {
                        if (activeConnection === this) activeConnection = null
                        remote = null
                    }
                    override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
                    override fun onNullBinding(name: ComponentName) {
                        onServiceDisconnected(name)
                        if (continuation.isActive) continuation.resumeWithException(
                            IllegalStateException("Root service returned no binder"),
                        )
                    }
                }
                continuation.invokeOnCancellation { runCatching { RootService.unbind(connection) } }
                runCatching { RootService.bind(intent, connection) }
                    .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
            }
        }
    }

    data class RootServiceInfo(
        val ok: Boolean,
        val uid: Int,
        val gid: Int,
        val pid: Int,
        val scanner: String,
        val backend: String,
        val selinuxEnforcing: Boolean?,
        val selinuxContext: String?,
        val mountNamespace: String?,
        val message: String?,
    )

    data class ScanRootPreflight(
        val ok: Boolean,
        val code: String,
        val requestedPath: String,
        val canonicalPath: String?,
        val exists: Boolean,
        val directory: Boolean,
        val symbolicLink: Boolean,
        val opened: Boolean,
        val hasChildren: Boolean?,
        val errno: Int?,
        val message: String,
        val mountPoint: String?,
        val filesystem: String?,
        val mountOptions: String?,
    )

    data class RootScanState(
        val ok: Boolean,
        val scanId: String = "",
        val status: String = "",
        val exitCode: Int? = null,
        val error: String? = null,
        val message: String? = null,
        val code: String? = null,
        val path: String? = null,
        val errno: Int? = null,
    ) {
        val terminal: Boolean get() = status in setOf("completed", "cancelled", "failed")

        companion object {
            fun fromJson(raw: String): RootScanState {
                val json = JSONObject(raw)
                return RootScanState(
                    ok = json.optBoolean("ok", false),
                    scanId = json.optString("scanId"),
                    status = json.optString("status"),
                    exitCode = if (json.has("exitCode") && !json.isNull("exitCode")) json.getInt("exitCode") else null,
                    error = json.optString("error").takeIf(String::isNotBlank),
                    message = json.optString("message").takeIf(String::isNotBlank),
                    code = json.optString("code").takeIf(String::isNotBlank),
                    path = json.optString("path").ifBlank { json.optString("requestedPath") }.takeIf(String::isNotBlank),
                    errno = json.optInt("errno").takeIf { json.has("errno") && !json.isNull("errno") },
                )
            }
        }
    }

    private companion object {
        const val ROOT_TIMEOUT_MS = 8_000L
        const val BIND_TIMEOUT_MS = 20_000L
        const val START_TIMEOUT_MS = 30_000L
        const val DUPLICATE_TIMEOUT_MS = 10 * 60 * 1000L
        const val CALL_TIMEOUT_MS = 10_000L
        const val CLEANUP_PLAN_DIRECTORY = "dustui_cleanup_plans"
        const val CLEANUP_PLAN_RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
