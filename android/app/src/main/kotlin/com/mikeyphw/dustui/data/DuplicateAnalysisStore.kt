package com.mikeyphw.dustui.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class DuplicateAnalysisStore(context: Context) {
    private val directory = File(context.filesDir, "duplicate-analysis").apply { mkdirs() }

    fun load(scanId: String): DuplicateAnalysisSnapshot? = runCatching {
        val file = fileFor(scanId)
        if (!file.isFile) return null
        fromJson(JSONObject(file.readText(Charsets.UTF_8)))
    }.getOrNull()

    fun save(snapshot: DuplicateAnalysisSnapshot) {
        directory.mkdirs()
        val destination = fileFor(snapshot.scanId)
        val temporary = File(directory, "${destination.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(toJson(snapshot).toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        check(temporary.renameTo(destination) || runCatching {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
            true
        }.getOrDefault(false)) { "Unable to persist duplicate analysis" }
    }

    fun clear(scanId: String) {
        fileFor(scanId).delete()
        File(directory, "${safeName(scanId)}.json.tmp").delete()
    }

    private fun fileFor(scanId: String) = File(directory, "${safeName(scanId)}.json")

    private fun safeName(scanId: String): String = scanId.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("").take(120).ifBlank { "scan" }

    private fun toJson(snapshot: DuplicateAnalysisSnapshot): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("scanId", snapshot.scanId)
        .put("minSizeBytes", snapshot.minSizeBytes)
        .put("scanGeneration", snapshot.scanGeneration)
        .put("status", snapshot.status)
        .put("totalBuckets", snapshot.totalBuckets)
        .put("completedBuckets", snapshot.completedBuckets)
        .put("checkedFiles", snapshot.checkedFiles)
        .put("skippedFiles", snapshot.skippedFiles)
        .put("partialGroups", snapshot.partialGroups)
        .put("bytesHashed", snapshot.bytesHashed)
        .put("startedAt", snapshot.startedAt)
        .put("updatedAt", snapshot.updatedAt)
        .put("currentSizeBytes", snapshot.currentSizeBytes)
        .put("message", snapshot.message)
        .put("completedSizes", JSONArray(snapshot.completedSizes.sorted()))
        .put("groups", JSONArray().apply {
            snapshot.groups.forEach { group -> put(groupToJson(group)) }
        })

    private fun groupToJson(group: VerifiedDuplicateGroup): JSONObject = JSONObject()
        .put("sha256", group.sha256)
        .put("partialHash", group.partialHash)
        .put("sizeBytes", group.sizeBytes)
        .put("physicalCopyCount", group.physicalCopyCount)
        .put("hardLinkedCopyCount", group.hardLinkedCopyCount)
        .put("reclaimableBytes", group.reclaimableBytes)
        .put("safeReclaimableBytes", group.safeReclaimableBytes)
        .put("paths", JSONArray().apply {
            group.pathRecords.forEach { record ->
                put(JSONObject()
                    .put("path", record.path)
                    .put("deviceId", record.deviceId)
                    .put("inode", record.inode)
                    .put("linkCount", record.linkCount)
                    .put("modifiedAt", record.modifiedAt)
                    .put("copyKey", record.copyKey)
                    .put("hardLinked", record.hardLinked)
                    .put("autoDeleteEligible", record.autoDeleteEligible)
                    .put("deletionBlockReason", record.deletionBlockReason))
            }
        })

    private fun fromJson(json: JSONObject): DuplicateAnalysisSnapshot {
        require(json.optInt("schemaVersion") == 1) { "Unsupported duplicate analysis schema" }
        val completedSizesJson = json.optJSONArray("completedSizes") ?: JSONArray()
        val groupsJson = json.optJSONArray("groups") ?: JSONArray()
        return DuplicateAnalysisSnapshot(
            scanId = json.getString("scanId"),
            minSizeBytes = json.getLong("minSizeBytes"),
            scanGeneration = json.optLong("scanGeneration", 1L),
            status = json.optString("status", "IDLE"),
            totalBuckets = json.optInt("totalBuckets"),
            completedBuckets = json.optInt("completedBuckets"),
            checkedFiles = json.optInt("checkedFiles"),
            skippedFiles = json.optInt("skippedFiles"),
            partialGroups = json.optInt("partialGroups"),
            bytesHashed = json.optLong("bytesHashed"),
            groups = buildList {
                for (index in 0 until groupsJson.length()) add(groupFromJson(groupsJson.getJSONObject(index)))
            },
            completedSizes = buildSet {
                for (index in 0 until completedSizesJson.length()) add(completedSizesJson.getLong(index))
            },
            startedAt = json.optLong("startedAt"),
            updatedAt = json.optLong("updatedAt"),
            currentSizeBytes = if (json.has("currentSizeBytes") && !json.isNull("currentSizeBytes")) json.getLong("currentSizeBytes") else null,
            message = json.optString("message").takeIf(String::isNotBlank),
        )
    }

    private fun groupFromJson(json: JSONObject): VerifiedDuplicateGroup {
        val pathsJson = json.optJSONArray("paths") ?: JSONArray()
        return VerifiedDuplicateGroup(
            sha256 = json.getString("sha256"),
            partialHash = json.optString("partialHash"),
            sizeBytes = json.getLong("sizeBytes"),
            pathRecords = buildList {
                for (index in 0 until pathsJson.length()) {
                    val item = pathsJson.getJSONObject(index)
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
            physicalCopyCount = json.optInt("physicalCopyCount"),
            hardLinkedCopyCount = json.optInt("hardLinkedCopyCount"),
            reclaimableBytes = json.optLong("reclaimableBytes"),
            safeReclaimableBytes = json.optLong("safeReclaimableBytes"),
        )
    }
}
