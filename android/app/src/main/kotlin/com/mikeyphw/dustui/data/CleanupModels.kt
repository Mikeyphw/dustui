package com.mikeyphw.dustui.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "cleanup_operations", primaryKeys = ["id"], indices = [Index("scanId")])
data class CleanupOperationEntity(
    val id: String,
    val scanId: String,
    val rootPath: String,
    val status: String,
    val selectedCount: Int,
    val plannedBytes: Long,
    @ColumnInfo(name = "reclaimedBytes") val deletedBytes: Long = 0,
    val createdAt: Long,
    val completedAt: Long? = null,
    val message: String? = null,
    @ColumnInfo(defaultValue = "''") val reviewId: String = "",
    @ColumnInfo(defaultValue = "1") val scanGeneration: Long = 1L,
    @ColumnInfo(defaultValue = "''") val planHash: String = "",
    @ColumnInfo(defaultValue = "0") val expiresAt: Long = 0L,
    @ColumnInfo(defaultValue = "'CERTAIN'") val outcomeCertainty: String = CleanupOutcomeCertainty.CERTAIN.name,
)

/** Retained only so existing version-2 databases remain readable. New cleanup never writes this table. */
@Entity(tableName = "quarantine_entries", primaryKeys = ["id"], indices = [Index("operationId")])
data class QuarantineEntryEntity(
    val id: String,
    val operationId: String,
    val originalPath: String,
    val quarantinePath: String,
    val sizeBytes: Long,
    val restoredAt: Long? = null,
)

@Entity(
    tableName = "cleanup_plan_items",
    primaryKeys = ["operationId", "path"],
    indices = [Index("operationId"), Index("role")],
    foreignKeys = [
        ForeignKey(
            entity = CleanupOperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CleanupPlanItemEntity(
    val operationId: String,
    val path: String,
    val role: String,
    val itemType: String,
    val plannedBytes: Long,
    val expectedDevice: Long? = null,
    val expectedMountId: String? = null,
    val expectedInode: Long? = null,
    val expectedMode: Int? = null,
    val expectedStatSizeBytes: Long? = null,
    val expectedModifiedAt: Long? = null,
    val expectedTreeDigest: String? = null,
    val expectedEntryCount: Long? = null,
    val expectedSha256: String? = null,
    val retainedPath: String? = null,
    val retainedSha256: String? = null,
    val packageName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class CleanupOperationStatus { REVIEWED, EXECUTING, VERIFYING, COMPLETED, PARTIAL, FAILED, BLOCKED, UNKNOWN_OUTCOME, INTERRUPTED }

enum class CleanupOutcomeCertainty { CERTAIN, UNKNOWN }

data class CleanupPlan(
    val id: String = UUID.randomUUID().toString(),
    val scanId: String,
    val rootPath: String,
    val items: List<CleanupPlanItem>,
    val scanGeneration: Long = 1L,
) {
    fun normalized(): CleanupPlan {
        val normalizedItems = items.mapNotNull { item ->
            PathSafety.normalize(item.path)?.let { normalized -> item.copy(path = normalized) }
        }.distinctBy { it.path }
        val byPath = normalizedItems.associateBy { it.path }
        return copy(items = PathSafety.collapseRoots(byPath.keys).mapNotNull(byPath::get))
    }

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
        .put("scanGeneration", scanGeneration)
        .put(
            "items",
            JSONArray(items.map { item ->
                JSONObject()
                    .put("path", item.path)
                    .put("sizeBytes", item.sizeBytes)
                    .put("directory", item.isDirectory)
                    .put("modifiedAt", item.modifiedAt)
                    .apply { item.expectedSha256?.let { put("expectedSha256", it) } }
                    .apply { item.retainedPath?.let { put("retainedPath", it) } }
                    .apply { item.retainedSha256?.let { put("retainedSha256", it) } }
                    .apply { item.packageName?.let { put("packageName", it) } }
            }),
        )
        .toString()
}

data class CleanupPlanItem(
    val path: String,
    val sizeBytes: Long,
    val isDirectory: Boolean = false,
    val modifiedAt: Long = 0,
    val expectedSha256: String? = null,
    val retainedPath: String? = null,
    val retainedSha256: String? = null,
    val packageName: String? = null,
)

data class CleanupPathFingerprint(
    val device: Long,
    val inode: Long,
    val mode: Int,
    val statSizeBytes: Long,
    val modifiedAt: Long,
    val treeDigest: String,
    val entryCount: Long,
    val contentSha256: String? = null,
    val mountId: String? = null,
)

data class CleanupApprovedItem(
    val path: String,
    val plannedBytes: Long,
    val isDirectory: Boolean,
    val fingerprint: CleanupPathFingerprint,
    val retainedPath: String? = null,
    val retainedSha256: String? = null,
    val packageName: String? = null,
)

data class CleanupReview(
    val ok: Boolean,
    val operationId: String,
    val scanId: String,
    val rootPath: String,
    val reviewedAt: Long,
    val accepted: List<CleanupApprovedItem>,
    val blocked: List<CleanupBlockedItem>,
    val plannedBytes: Long,
    val message: String? = null,
    val scanGeneration: Long = 1L,
    val goManifestJson: String? = null,
    val goCapabilityJson: String? = null,
    val mutationProtocol: String = "",
) {
    val executable: Boolean get() = ok && accepted.isNotEmpty() && !goManifestJson.isNullOrBlank() && !goCapabilityJson.isNullOrBlank() && mutationProtocol == "dustui.cleanup.mutation.v1"

    fun matches(scanId: String, rootPath: String, scanGeneration: Long): Boolean =
        this.scanId == scanId && this.rootPath == rootPath && this.scanGeneration == scanGeneration

    fun matches(scanId: String, rootPath: String): Boolean =
        this.scanId == scanId && this.rootPath == rootPath

    fun toExecutionJson(): String = JSONObject()
        .put("operationId", operationId)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
        .put("scanGeneration", scanGeneration)
        .apply { goManifestJson?.let { put("goManifest", JSONObject(it)) } }
        .apply { goCapabilityJson?.let { put("goCapability", JSONObject(it)) } }
        .put("mutationProtocol", mutationProtocol)
        .put(
            "accepted",
            JSONArray(accepted.map { item ->
                JSONObject()
                    .put("path", item.path)
                    .put("plannedBytes", item.plannedBytes)
                    .put("directory", item.isDirectory)
                    .put(
                        "fingerprint",
                        JSONObject()
                            .put("device", item.fingerprint.device)
                            .apply { item.fingerprint.mountId?.let { put("mountId", it) } }
                            .put("inode", item.fingerprint.inode)
                            .put("mode", item.fingerprint.mode)
                            .put("statSizeBytes", item.fingerprint.statSizeBytes)
                            .put("modifiedAt", item.fingerprint.modifiedAt)
                            .put("treeDigest", item.fingerprint.treeDigest)
                            .put("entryCount", item.fingerprint.entryCount)
                            .apply { item.fingerprint.contentSha256?.let { put("contentSha256", it) } },
                    )
                    .apply { item.retainedPath?.let { put("retainedPath", it) } }
                    .apply { item.retainedSha256?.let { put("retainedSha256", it) } }
                    .apply { item.packageName?.let { put("packageName", it) } }
            }),
        )
        .toString()
}

data class CleanupBlockedItem(val path: String, val reason: String)

data class CleanupItemResult(
    val path: String,
    val plannedBytes: Long,
    val deletedBytes: Long,
    val status: String,
    val message: String? = null,
)

data class CleanupExecutionResult(
    val ok: Boolean,
    val operationId: String,
    val status: String,
    val deletedBytes: Long,
    val failedCount: Int,
    val items: List<CleanupItemResult>,
    val message: String? = null,
)

object CleanupSafetyPolicy {
    private val protectedExact = setOf("/", "/data")
    private val protectedSubtrees = setOf(
        "/system", "/vendor", "/product", "/metadata", "/persist", "/odm", "/system_ext",
        "/proc", "/sys", "/dev", "/apex",
        "/data/adb", "/data/system", "/data/misc", "/data/local/tmp/dustui",
    )

    fun blockReason(
        path: String,
        scanRoot: String,
        protectedPaths: Collection<String> = emptyList(),
        excludedPaths: Collection<String> = emptyList(),
    ): String? {
        val clean = normalize(path) ?: return "Invalid or relative path"
        val root = normalize(scanRoot) ?: return "Invalid scan root"
        if (clean == root) return "The scan root cannot be deleted"
        if (clean in protectedExact) return "Protected Android path"
        if (protectedSubtrees.any { protected -> PathSafety.isSameOrDescendant(clean, protected) }) {
            return "Protected Android path"
        }
        if (!PathSafety.isSameOrDescendant(clean, root)) return "Path is outside the reviewed scan root"
        if (protectedPaths.any { PathSafety.intersects(clean, it) }) return "Path intersects a protected selection"
        if (excludedPaths.any { PathSafety.intersects(clean, it) }) return "Path intersects an excluded selection"
        return null
    }

    fun normalize(path: String): String? = PathSafety.normalize(path)
}
