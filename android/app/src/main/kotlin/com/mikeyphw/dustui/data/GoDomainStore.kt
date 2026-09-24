package com.mikeyphw.dustui.data

import android.content.Context
import com.mikeyphw.dustui.go.GoRuntimeProcess
import com.mikeyphw.dustui.go.GoRuntimeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Android's production domain-state adapter after REM-03.
 *
 * This is intentionally API-shaped like the former ScanDao at the shell boundary,
 * but every authoritative read/write is transported to the app-owned Go process
 * and persisted in Go-owned SQLite. Room is no longer a live domain authority.
 */
class GoDomainStore(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicLong(0)
    private val processLock = Any()
    @Volatile private var process: GoRuntimeProcess? = null

    private val latest = MutableStateFlow<ScanSessionEntity?>(null)
    private val entryFlows = ConcurrentHashMap<String, MutableStateFlow<List<ScanEntryEntity>>>()
    private val cleanup = MutableStateFlow<List<CleanupOperationEntity>>(emptyList())

    fun observeLatestSession(): Flow<ScanSessionEntity?> {
        scope.launch { runCatching { refreshLatest() } }
        return latest
    }

    fun observeEntries(scanId: String, limit: Int): Flow<List<ScanEntryEntity>> {
        val flow = entryFlows.computeIfAbsent(scanId) { MutableStateFlow(emptyList()) }
        scope.launch { runCatching { flow.value = entriesOnce(scanId).take(limit) } }
        return flow
    }

    fun observeCleanupOperations(limit: Int): Flow<List<CleanupOperationEntity>> {
        scope.launch { runCatching { cleanup.value = cleanupOperations(limit) } }
        return cleanup
    }

    suspend fun upsertSession(session: ScanSessionEntity) = io {
        call("domain.scan.session.upsert", session.generation, JSONObject().put("session", session.toWireJson()))
        val current = latest.value
        if (current == null || session.startedAt >= current.startedAt || current.id == session.id) latest.value = session
    }

    suspend fun deleteEntries(scanId: String) = io {
        call("domain.scan.entries.delete", 1, JSONObject().put("scanId", scanId))
        entryFlows[scanId]?.value = emptyList()
    }

    suspend fun upsertEntries(entries: List<ScanEntryEntity>) = io {
        if (entries.isEmpty()) return@io
        entries.chunked(ENTRY_WRITE_BATCH).forEach { chunk ->
            val generation = session(chunk.first().scanId)?.generation ?: 1L
            call(
                "domain.scan.entries.upsert",
                generation,
                JSONObject().put("entries", JSONArray(chunk.map(ScanEntryEntity::toWireJson))),
            )
        }
        entries.groupBy { it.scanId }.forEach { (scanId, newEntries) ->
            entryFlows[scanId]?.let { flow ->
                val merged = (flow.value + newEntries).associateBy { it.path }.values
                    .sortedWith(compareByDescending<ScanEntryEntity> { it.sizeBytes }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.path })
                flow.value = merged.take(10_000)
            }
        }
    }

    suspend fun session(scanId: String): ScanSessionEntity? = io {
        val payload = call("domain.scan.session.get", 1, JSONObject().put("scanId", scanId))
        payload.optJSONObjectOrNull("session")?.toScanSession()
    }

    suspend fun completedSessions(limit: Int): List<ScanSessionEntity> = io {
        val payload = call("domain.scan.sessions.completed", 1, JSONObject().put("limit", limit))
        payload.getJSONArray("sessions").objects().map(JSONObject::toScanSession)
    }

    suspend fun entriesOnce(scanId: String): List<ScanEntryEntity> = io {
        val all = ArrayList<ScanEntryEntity>()
        val generation = session(scanId)?.generation ?: 1L
        var offset = 0
        while (true) {
            val payload = call(
                "domain.scan.entries.page",
                generation,
                JSONObject().put("scanId", scanId).put("offset", offset).put("limit", ENTRY_READ_PAGE),
            )
            val page = payload.getJSONArray("entries").objects().map(JSONObject::toScanEntry)
            all += page
            if (page.size < ENTRY_READ_PAGE) break
            offset += page.size
        }
        all
    }

    suspend fun entryOnce(scanId: String, path: String): ScanEntryEntity? = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call("domain.scan.entry.get", generation, JSONObject().put("scanId", scanId).put("path", path))
        payload.optJSONObjectOrNull("entry")?.toScanEntry()
    }

    suspend fun entriesByPathsOnce(scanId: String, paths: List<String>): List<ScanEntryEntity> = io {
        if (paths.isEmpty()) return@io emptyList()
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.entries.by-paths",
            generation,
            JSONObject().put("scanId", scanId).put("paths", JSONArray(paths)),
        )
        payload.getJSONArray("entries").objects().map(JSONObject::toScanEntry)
    }

    suspend fun filesAtLeastOnce(scanId: String, minBytes: Long): List<ScanEntryEntity> = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.files.at-least",
            generation,
            JSONObject().put("scanId", scanId).put("minBytes", minBytes),
        )
        payload.getJSONArray("entries").objects().map(JSONObject::toScanEntry)
    }

    suspend fun archiveFilesOnce(scanId: String): List<ScanEntryEntity> = filesByExtensions(scanId, ARCHIVE_EXTENSIONS)

    suspend fun installerFilesOnce(scanId: String): List<ScanEntryEntity> = filesByExtensions(scanId, INSTALLER_EXTENSIONS)

    private suspend fun filesByExtensions(scanId: String, extensions: Set<String>): List<ScanEntryEntity> = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.files.extensions",
            generation,
            JSONObject().put("scanId", scanId).put("extensions", JSONArray(extensions.toList())),
        )
        payload.getJSONArray("entries").objects().map(JSONObject::toScanEntry)
    }

    suspend fun duplicateSizeBuckets(scanId: String, minBytes: Long): List<DuplicateSizeBucket> = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.duplicate.buckets",
            generation,
            JSONObject().put("scanId", scanId).put("minBytes", minBytes),
        )
        payload.getJSONArray("buckets").objects().map { DuplicateSizeBucket(it.getLong("sizeBytes"), it.getInt("candidateCount")) }
    }

    suspend fun duplicateFilesBySizeOnce(scanId: String, sizeBytes: Long, limit: Int): List<ScanEntryEntity> = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.duplicate.files",
            generation,
            JSONObject().put("scanId", scanId).put("sizeBytes", sizeBytes).put("limit", limit),
        )
        payload.getJSONArray("entries").objects().map(JSONObject::toScanEntry)
    }

    suspend fun rootSearch(scanId: String, request: RootFileSearchRequest): List<RootFileSearchResult> = io {
        val generation = session(scanId)?.generation ?: 1L
        val payload = call(
            "domain.scan.search",
            generation,
            JSONObject().put("scanId", scanId).put("request", request.toWireJson()),
        )
        payload.getJSONArray("results").objects().map(JSONObject::toRootSearchResult)
    }

    suspend fun pruneCompletedScanRetention(keep: Int) = io {
        call("domain.scan.prune", 1, JSONObject().put("keep", keep))
        refreshLatest()
    }

    suspend fun interruptActiveScanSessions(completedAt: Long): Int = io {
        val payload = call("domain.scan.interrupt-active", 1, JSONObject().put("completedAt", completedAt))
        refreshLatest()
        payload.optInt("count", 0)
    }

    suspend fun upsertCleanupOperation(operation: CleanupOperationEntity) = io {
        call("domain.cleanup.operation.upsert", max(1L, operation.scanGeneration), JSONObject().put("operation", operation.toWireJson()))
        refreshCleanup()
    }

    suspend fun insertCleanupOperationIfAbsent(operation: CleanupOperationEntity): Long = io {
        val payload = call("domain.cleanup.operation.insert-if-absent", max(1L, operation.scanGeneration), JSONObject().put("operation", operation.toWireJson()))
        refreshCleanup()
        if (payload.optBoolean("inserted")) 1L else -1L
    }

    suspend fun replaceCleanupPlanItems(operationId: String, items: List<CleanupPlanItemEntity>) = io {
        call(
            "domain.cleanup.plan.replace",
            1,
            JSONObject().put("operationId", operationId).put("items", JSONArray(items.map(CleanupPlanItemEntity::toWireJson))),
        )
    }

    suspend fun claimCleanupOperation(operationId: String): Int = io {
        val payload = call("domain.cleanup.operation.claim", 1, JSONObject().put("operationId", operationId))
        refreshCleanup()
        payload.optInt("count", 0)
    }

    suspend fun executingCleanupOperations(): List<CleanupOperationEntity> = cleanupOperations(10_000, CleanupOperationStatus.EXECUTING.name)

    suspend fun cleanupOperation(operationId: String): CleanupOperationEntity? = cleanupOperations(10_000).firstOrNull { it.id == operationId }

    suspend fun verifySchema() = io { call("domain.schema.verify", 1, JSONObject()) }

    suspend fun importLegacy(
        sessions: List<ScanSessionEntity>,
        entriesByScan: Map<String, List<ScanEntryEntity>>,
        operations: List<CleanupOperationEntity>,
        planItemsByOperation: Map<String, List<CleanupPlanItemEntity>>,
    ) {
        sessions.sortedBy { it.startedAt }.forEach { session ->
            upsertSession(session)
            deleteEntries(session.id)
            entriesByScan[session.id].orEmpty().chunked(ENTRY_WRITE_BATCH).forEach { upsertEntries(it) }
        }
        operations.sortedBy { it.createdAt }.forEach { operation ->
            upsertCleanupOperation(operation)
            replaceCleanupPlanItems(operation.id, planItemsByOperation[operation.id].orEmpty())
        }
        refreshLatest()
        refreshCleanup()
    }

    private suspend fun refreshLatest() {
        val payload = call("domain.scan.latest", 1, JSONObject())
        latest.value = payload.optJSONObjectOrNull("session")?.toScanSession()
    }

    private suspend fun refreshCleanup() {
        cleanup.value = cleanupOperations(50)
    }

    private suspend fun cleanupOperations(limit: Int, status: String = ""): List<CleanupOperationEntity> = io {
        val payload = call(
            "domain.cleanup.operations.list",
            1,
            JSONObject().put("limit", limit).put("status", status),
        )
        payload.getJSONArray("operations").objects().map(JSONObject::toCleanupOperation)
    }

    private fun runtime(): GoRuntimeProcess = synchronized(processLock) {
        process?.takeIf { it.isAlive } ?: GoRuntimeProcess.start(appContext).also { process = it }
    }

    private fun call(type: String, generation: Long, payload: JSONObject): JSONObject {
        val response = runtime().request(
            GoRuntimeRequest(
                requestId = UUID.randomUUID().toString(),
                sequence = sequence.incrementAndGet(),
                generation = max(1L, generation),
                type = type,
                payloadJson = payload.toString(),
            ),
        )
        val decoded = JSONObject(response.payloadJson)
        check(decoded.optBoolean("ok", false)) { decoded.optString("error", "go_domain_request_failed:$type") }
        return decoded
    }

    private suspend inline fun <T> io(crossinline block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    override fun close() = synchronized(processLock) {
        process?.close()
        process = null
    }

    companion object {
        private const val ENTRY_READ_PAGE = 128
        private const val ENTRY_WRITE_BATCH = 128
        private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "gz", "zst")
        private val INSTALLER_EXTENSIONS = setOf("apk", "apex")
    }
}

private fun ScanSessionEntity.toWireJson() = JSONObject()
    .put("id", id).putNullable("presetId", presetId).put("rootPath", rootPath).put("optionsJson", optionsJson)
    .put("status", status).put("backend", backend).put("startedAt", startedAt).putNullable("completedAt", completedAt)
    .put("scannedItems", scannedItems).put("scannedBytes", scannedBytes).put("totalBytes", totalBytes)
    .put("warningCount", warningCount).put("currentPath", currentPath).putNullable("errorMessage", errorMessage)
    .put("generation", generation).put("observedEntryCount", observedEntryCount).put("persistedEntryCount", persistedEntryCount)
    .put("malformedEventCount", malformedEventCount).put("truncated", truncated).put("integrityStatus", integrityStatus)

private fun JSONObject.toScanSession() = ScanSessionEntity(
    id = getString("id"), presetId = optNullableString("presetId"), rootPath = getString("rootPath"),
    optionsJson = optString("optionsJson", "{}"), status = getString("status"), backend = optString("backend", ""),
    startedAt = getLong("startedAt"), completedAt = optNullableLong("completedAt"), scannedItems = optLong("scannedItems"),
    scannedBytes = optLong("scannedBytes"), totalBytes = optLong("totalBytes"), warningCount = optLong("warningCount"),
    currentPath = optString("currentPath", ""), errorMessage = optNullableString("errorMessage"), generation = optLong("generation", 1),
    observedEntryCount = optLong("observedEntryCount"), persistedEntryCount = optLong("persistedEntryCount"),
    malformedEventCount = optLong("malformedEventCount"), truncated = optBoolean("truncated"),
    integrityStatus = optString("integrityStatus", ScanIntegrityStatus.UNKNOWN.name),
)

private fun ScanEntryEntity.toWireJson() = JSONObject()
    .put("scanId", scanId).put("path", path).put("parentPath", parentPath).put("name", name).put("sizeBytes", sizeBytes)
    .put("allocatedBytes", allocatedBytes).put("isDirectory", isDirectory).put("depth", depth).put("modifiedAt", modifiedAt).put("fileCount", fileCount)

private fun JSONObject.toScanEntry() = ScanEntryEntity(
    scanId = getString("scanId"), path = getString("path"), parentPath = getString("parentPath"), name = getString("name"),
    sizeBytes = getLong("sizeBytes"), allocatedBytes = getLong("allocatedBytes"), isDirectory = getBoolean("isDirectory"),
    depth = getInt("depth"), modifiedAt = getLong("modifiedAt"), fileCount = optLong("fileCount"),
)

private fun CleanupOperationEntity.toWireJson() = JSONObject()
    .put("id", id).put("scanId", scanId).put("rootPath", rootPath).put("status", status).put("selectedCount", selectedCount)
    .put("plannedBytes", plannedBytes).put("deletedBytes", deletedBytes).put("createdAt", createdAt).putNullable("completedAt", completedAt)
    .putNullable("message", message).put("reviewId", reviewId).put("scanGeneration", scanGeneration).put("planHash", planHash)
    .put("expiresAt", expiresAt).put("outcomeCertainty", outcomeCertainty)

private fun JSONObject.toCleanupOperation() = CleanupOperationEntity(
    id = getString("id"), scanId = getString("scanId"), rootPath = getString("rootPath"), status = getString("status"),
    selectedCount = getInt("selectedCount"), plannedBytes = getLong("plannedBytes"), deletedBytes = optLong("deletedBytes"),
    createdAt = getLong("createdAt"), completedAt = optNullableLong("completedAt"), message = optNullableString("message"),
    reviewId = optString("reviewId", ""), scanGeneration = optLong("scanGeneration", 1), planHash = optString("planHash", ""),
    expiresAt = optLong("expiresAt"), outcomeCertainty = optString("outcomeCertainty", CleanupOutcomeCertainty.CERTAIN.name),
)

private fun CleanupPlanItemEntity.toWireJson() = JSONObject()
    .put("operationId", operationId).put("path", path).put("role", role).put("itemType", itemType).put("plannedBytes", plannedBytes)
    .putNullable("expectedDevice", expectedDevice).putNullable("expectedMountId", expectedMountId).putNullable("expectedInode", expectedInode)
    .putNullable("expectedMode", expectedMode).putNullable("expectedStatSizeBytes", expectedStatSizeBytes).putNullable("expectedModifiedAt", expectedModifiedAt)
    .putNullable("expectedTreeDigest", expectedTreeDigest).putNullable("expectedEntryCount", expectedEntryCount).putNullable("expectedSha256", expectedSha256)
    .putNullable("retainedPath", retainedPath).putNullable("retainedSha256", retainedSha256).putNullable("packageName", packageName).put("createdAt", createdAt)

private fun RootFileSearchRequest.toWireJson() = JSONObject()
    .put("query", query).put("type", type.name).put("minBytes", minBytes).putNullable("maxBytes", maxBytes)
    .putNullable("category", category?.name).putNullable("packageName", packageName).putNullable("extension", extension).put("limit", limit)

private fun JSONObject.toRootSearchResult() = RootFileSearchResult(
    path = getString("path"), parentPath = getString("parentPath"), name = getString("name"), sizeBytes = getLong("sizeBytes"),
    modifiedAt = getLong("modifiedAt"), isDirectory = getBoolean("isDirectory"), category = StorageCategory.valueOf(getString("category")),
    packageName = optNullableString("packageName"),
)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else getString(name)
private fun JSONObject.optNullableLong(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
private fun JSONObject.optJSONObjectOrNull(name: String): JSONObject? = if (!has(name) || isNull(name)) null else getJSONObject(name)
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
