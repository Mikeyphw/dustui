package com.mikeyphw.dustui.data

import androidx.room.Entity
import androidx.room.Index
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "cleanup_operations", primaryKeys = ["id"], indices = [Index("createdAt")])
data class CleanupOperationEntity(
    val id: String,
    val scanId: String,
    val rootPath: String,
    val status: String,
    val selectedCount: Int,
    val plannedBytes: Long,
    val reclaimedBytes: Long = 0,
    val createdAt: Long,
    val completedAt: Long? = null,
    val message: String? = null,
)

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
            if (roots.none { candidate.path == it.path || candidate.path.startsWith(it.path.trimEnd('/') + "/") }) roots += candidate
        }
        return copy(items = roots)
    }

    fun toJson(): String = JSONObject()
        .put("id", id)
        .put("scanId", scanId)
        .put("rootPath", rootPath)
        .put("items", JSONArray(items.map { JSONObject().put("path", it.path).put("sizeBytes", it.sizeBytes) }))
        .toString()
}

data class CleanupPlanItem(val path: String, val sizeBytes: Long)

data class CleanupReview(
    val ok: Boolean,
    val operationId: String,
    val scanId: String,
    val rootPath: String,
    val reviewedAt: Long,
    val accepted: List<CleanupPlanItem>,
    val blocked: List<CleanupBlockedItem>,
    val plannedBytes: Long,
    val message: String? = null,
) {
    val executable: Boolean get() = ok && accepted.isNotEmpty() && blocked.isEmpty()

    fun matches(scanId: String, rootPath: String): Boolean =
        this.scanId == scanId && this.rootPath == rootPath
}

data class CleanupBlockedItem(val path: String, val reason: String)

data class CleanupExecutionResult(
    val ok: Boolean,
    val operationId: String,
    val status: String,
    val reclaimedBytes: Long,
    val entries: List<QuarantineEntryEntity>,
    val message: String? = null,
)

object CleanupSafetyPolicy {
    private val protectedExact = setOf(
        "/", "/data", "/system", "/vendor", "/product", "/metadata", "/persist",
        "/proc", "/sys", "/dev", "/apex", "/odm", "/system_ext",
        "/data/adb", "/data/system", "/data/misc", "/data/local/tmp/dustui",
    )

    fun blockReason(path: String, scanRoot: String): String? {
        val clean = normalize(path) ?: return "Invalid or relative path"
        val root = normalize(scanRoot) ?: return "Invalid scan root"
        if (clean == root) return "The scan root cannot be quarantined"
        if (clean in protectedExact) return "Protected Android path"
        if (protectedExact.any { protected -> clean.startsWith(protected.trimEnd('/') + "/") && protected in setOf("/proc", "/sys", "/dev", "/apex") }) return "Virtual filesystem content is protected"
        if (!clean.startsWith(root.trimEnd('/') + "/")) return "Path is outside the reviewed scan root"
        if (clean.contains("/.dustui-quarantine/")) return "Item is already in quarantine"
        return null
    }

    private fun normalize(path: String): String? {
        if (!path.startsWith('/')) return null
        val parts = ArrayDeque<String>()
        path.split('/').forEach { part ->
            when (part) { "", "." -> Unit; ".." -> if (parts.isEmpty()) return null else parts.removeLast(); else -> parts.addLast(part) }
        }
        return "/" + parts.joinToString("/")
    }
}
