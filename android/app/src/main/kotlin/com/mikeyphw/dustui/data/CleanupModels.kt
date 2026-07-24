package com.mikeyphw.dustui.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque
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

data class CleanupPlan(
    val id: String = UUID.randomUUID().toString(),
    val scanId: String,
    val rootPath: String,
    val items: List<CleanupPlanItem>,
) {
    fun normalized(): CleanupPlan {
        val sorted = items.distinctBy { it.path }.sortedBy { it.path.length }
        val roots = mutableListOf<CleanupPlanItem>()
        sorted.forEach { candidate ->
            if (roots.none { candidate.path == it.path || candidate.path.startsWith(it.path.trimEnd('/') + "/") }) {
                roots += candidate
            }
        }
        return copy(items = roots)
    }

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
        .put(
            "items",
            JSONArray(items.map { item ->
                JSONObject()
                    .put("path", item.path)
                    .put("sizeBytes", item.sizeBytes)
                    .put("directory", item.isDirectory)
                    .put("modifiedAt", item.modifiedAt)
                    .apply { item.expectedSha256?.let { put("expectedSha256", it) } }
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
)

data class CleanupApprovedItem(
    val path: String,
    val plannedBytes: Long,
    val isDirectory: Boolean,
    val fingerprint: CleanupPathFingerprint,
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
) {
    val executable: Boolean get() = ok && accepted.isNotEmpty()

    fun matches(scanId: String, rootPath: String): Boolean =
        this.scanId == scanId && this.rootPath == rootPath

    fun toExecutionJson(): String = JSONObject()
        .put("operationId", operationId)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
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
                            .put("inode", item.fingerprint.inode)
                            .put("mode", item.fingerprint.mode)
                            .put("statSizeBytes", item.fingerprint.statSizeBytes)
                            .put("modifiedAt", item.fingerprint.modifiedAt)
                            .put("treeDigest", item.fingerprint.treeDigest)
                            .put("entryCount", item.fingerprint.entryCount)
                            .apply { item.fingerprint.contentSha256?.let { put("contentSha256", it) } },
                    )
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

    fun blockReason(path: String, scanRoot: String): String? {
        val clean = normalize(path) ?: return "Invalid or relative path"
        val root = normalize(scanRoot) ?: return "Invalid scan root"
        if (clean == root) return "The scan root cannot be deleted"
        if (clean in protectedExact) return "Protected Android path"
        if (protectedSubtrees.any { protected -> clean == protected || clean.startsWith(protected.trimEnd('/') + "/") }) {
            return "Protected Android path"
        }
        if (!clean.startsWith(root.trimEnd('/') + "/")) return "Path is outside the reviewed scan root"
        return null
    }

    fun normalize(path: String): String? {
        if (!path.startsWith('/')) return null
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isEmpty()) return null else parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return "/" + parts.joinToString("/")
    }
}
