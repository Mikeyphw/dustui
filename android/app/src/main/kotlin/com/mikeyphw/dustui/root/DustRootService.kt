package com.mikeyphw.dustui.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.mikeyphw.dustui.scan.ScanRequest
import com.mikeyphw.dustui.data.CleanupSafetyPolicy
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class DustRootService : RootService() {
    private val executor = Executors.newCachedThreadPool()
    private val scans = ConcurrentHashMap<String, ActiveScan>()

    private val binder = object : IDustRootService.Stub() {
        override fun serviceInfo(): String = JSONObject()
            .put("ok", true)
            .put("uid", android.os.Process.myUid())
            .put("scanner", NativeDustScanner.version())
            .toString()

        override fun startScan(requestJson: String, output: ParcelFileDescriptor): String {
            val request = runCatching { ScanRequest.fromJson(requestJson) }.getOrElse { error ->
                output.close()
                return failure(error.message ?: "Invalid scan request")
            }
            if (scans.containsKey(request.scanId)) {
                output.close()
                return failure("Scan identifier is already active")
            }

            val ownedFdResult = runCatching { ParcelFileDescriptor.dup(output.fileDescriptor).detachFd() }
            runCatching { output.close() }
            val ownedFd = ownedFdResult.getOrElse {
                return failure(it.message ?: "Unable to duplicate output descriptor")
            }
            pruneCompletedScans()
            val active = ActiveScan(request.scanId)
            scans[request.scanId] = active
            active.status.set("running")
            active.future = runCatching { executor.submit {
                val exitCode = runCatching {
                    NativeDustScanner.scan(
                        request.scanId,
                        request.rootPath,
                        ownedFd,
                        request.maxDepth,
                        request.includeHidden,
                        request.apparentSize,
                        request.sameFilesystem,
                        request.mode.nativeValue,
                        request.minSize,
                        request.maxEntries,
                        request.excludes.toTypedArray(),
                    )
                }.getOrElse { error ->
                    active.error.set(error.message ?: error.javaClass.simpleName)
                    -1
                }
                active.exitCode.set(exitCode)
                active.status.set(
                    when {
                        exitCode == 0 -> "completed"
                        exitCode == 130 -> "cancelled"
                        else -> "failed"
                    },
                )
                active.completedAt.set(System.currentTimeMillis())
            } }.getOrElse { error ->
                runCatching { ParcelFileDescriptor.adoptFd(ownedFd).close() }
                scans.remove(request.scanId)
                return failure(error.message ?: "Unable to schedule native scan")
            }
            return state(active)
        }

        override fun scanState(scanId: String): String = scans[scanId]?.let(::state)
            ?: failure("Unknown scan identifier")

        override fun cancelScan(scanId: String): String {
            val active = scans[scanId] ?: return failure("Unknown scan identifier")
            val accepted = NativeDustScanner.cancel(scanId)
            if (accepted) active.status.set("cancelling")
            return state(active).let { raw -> JSONObject(raw).put("cancelAccepted", accepted).toString() }
        }


        override fun reviewCleanup(planJson: String): String = reviewPlan(planJson).toString()

        override fun executeCleanup(planJson: String): String {
            val review = reviewPlan(planJson)
            if (!review.optBoolean("ok") || review.getJSONArray("blocked").length() > 0) return review.toString()
            val plan = JSONObject(planJson)
            val operationId = plan.getString("id")
            val rootPath = Paths.get(plan.getString("rootPath")).normalize()
            val vault = rootPath.resolve(".dustui-quarantine").resolve(operationId)
            val moved = JSONArray()
            var reclaimed = 0L
            return runCatching {
                Files.createDirectories(vault)
                val manifestPath = vault.resolve("manifest.json")
                val manifest = JSONObject().put("operationId", operationId).put("rootPath", rootPath.toString()).put("entries", moved)
                Files.write(manifestPath, manifest.toString().toByteArray(Charsets.UTF_8))
                val accepted = review.getJSONArray("accepted")
                for (index in 0 until accepted.length()) {
                    val item = accepted.getJSONObject(index)
                    val source = Paths.get(item.getString("path")).normalize()
                    val relative = rootPath.relativize(source)
                    val destination = vault.resolve("payload").resolve(relative).normalize()
                    require(destination.startsWith(vault)) { "Unsafe quarantine destination" }
                    Files.createDirectories(destination.parent)
                    runCatching { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE) }
                        .recoverCatching { Files.move(source, destination) }
                        .getOrThrow()
                    val size = item.optLong("sizeBytes")
                    reclaimed += size
                    moved.put(JSONObject().put("id", "$operationId:$index").put("operationId", operationId).put("originalPath", source.toString()).put("quarantinePath", destination.toString()).put("sizeBytes", size))
                    Files.write(manifestPath, manifest.toString().toByteArray(Charsets.UTF_8))
                }
                JSONObject().put("ok", true).put("operationId", operationId).put("status", "QUARANTINED").put("reclaimedBytes", reclaimed).put("entries", moved)
            }.getOrElse { error ->
                JSONObject().put("ok", false).put("operationId", operationId).put("status", if (moved.length() > 0) "PARTIAL" else "FAILED").put("reclaimedBytes", reclaimed).put("entries", moved).put("message", error.message ?: error.javaClass.simpleName)
            }.toString()
        }

        override fun verifyDuplicates(requestJson: String): String = runCatching {
            val request = JSONObject(requestJson)
            val paths = request.getJSONArray("paths")
            require(paths.length() in 2..200) { "Duplicate verification accepts 2 to 200 files" }
            val expectedSize = request.optLong("sizeBytes", -1L)
            val groups = linkedMapOf<String, MutableList<String>>()
            var checked = 0
            var skipped = 0
            for (index in 0 until paths.length()) {
                val path = Paths.get(paths.getString(index)).normalize()
                if (!path.isAbsolute || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { skipped++; continue }
                val size = Files.size(path)
                if (expectedSize >= 0 && size != expectedSize) { skipped++; continue }
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val hash = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                groups.getOrPut(hash) { mutableListOf() }.add(path.toString())
                checked++
            }
            val verified = JSONArray()
            groups.filterValues { it.size > 1 }.forEach { (hash, owned) ->
                verified.put(JSONObject().put("sha256", hash).put("sizeBytes", expectedSize).put("paths", JSONArray(owned)).put("reclaimableBytes", expectedSize.coerceAtLeast(0) * (owned.size - 1L)))
            }
            JSONObject().put("ok", true).put("checkedFiles", checked).put("skippedFiles", skipped).put("groups", verified)
        }.getOrElse { error -> JSONObject().put("ok", false).put("checkedFiles", 0).put("skippedFiles", 0).put("groups", JSONArray()).put("message", error.message ?: error.javaClass.simpleName) }.toString()

        override fun restoreCleanup(operationId: String, rootPath: String): String {
            val root = Paths.get(rootPath).normalize()
            val vault = root.resolve(".dustui-quarantine").resolve(operationId)
            return runCatching {
                val manifest = JSONObject(String(Files.readAllBytes(vault.resolve("manifest.json")), Charsets.UTF_8))
                val entries = manifest.getJSONArray("entries")
                var restored = 0
                for (index in entries.length() - 1 downTo 0) {
                    val entry = entries.getJSONObject(index)
                    val source = Paths.get(entry.getString("quarantinePath")).normalize()
                    val destination = Paths.get(entry.getString("originalPath")).normalize()
                    require(destination.startsWith(root) && !Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) { "Restore destination is unsafe or already exists: $destination" }
                    Files.createDirectories(destination.parent)
                    runCatching { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE) }
                        .recoverCatching { Files.move(source, destination) }
                        .getOrThrow()
                    restored++
                }
                Files.deleteIfExists(vault.resolve("manifest.json"))
                JSONObject().put("ok", true).put("operationId", operationId).put("status", "RESTORED").put("restoredCount", restored)
            }.getOrElse { error -> JSONObject().put("ok", false).put("operationId", operationId).put("status", "RESTORE_FAILED").put("message", error.message ?: error.javaClass.simpleName) }.toString()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        scans.keys.forEach(NativeDustScanner::cancel)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun reviewPlan(planJson: String): JSONObject = runCatching {
        val plan = JSONObject(planJson)
        val operationId = plan.getString("id")
        val rootPath = plan.getString("rootPath")
        val accepted = JSONArray()
        val blocked = JSONArray()
        val items = plan.getJSONArray("items")
        var plannedBytes = 0L
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val rawPath = item.getString("path")
            val path = Paths.get(rawPath).normalize()
            val policyReason = CleanupSafetyPolicy.blockReason(path.toString(), rootPath)
            val reason = when {
                policyReason != null -> policyReason
                !Files.exists(path, LinkOption.NOFOLLOW_LINKS) -> "Path no longer exists"
                Files.isSymbolicLink(path) -> "Symbolic links cannot be quarantined"
                else -> null
            }
            if (reason == null) {
                accepted.put(JSONObject().put("path", path.toString()).put("sizeBytes", item.optLong("sizeBytes")))
                plannedBytes += item.optLong("sizeBytes")
            } else blocked.put(JSONObject().put("path", rawPath).put("reason", reason))
        }
        JSONObject().put("ok", true).put("operationId", operationId).put("accepted", accepted).put("blocked", blocked).put("plannedBytes", plannedBytes)
    }.getOrElse { error -> JSONObject().put("ok", false).put("operationId", "").put("accepted", JSONArray()).put("blocked", JSONArray()).put("plannedBytes", 0).put("message", error.message ?: error.javaClass.simpleName) }

    private fun state(scan: ActiveScan): String = JSONObject()
        .put("ok", true)
        .put("scanId", scan.scanId)
        .put("status", scan.status.get())
        .put("exitCode", scan.exitCode.get())
        .put("error", scan.error.get())
        .put("startedAt", scan.startedAt)
        .put("completedAt", scan.completedAt.get())
        .toString()

    private fun failure(message: String): String = JSONObject()
        .put("ok", false)
        .put("message", message)
        .toString()

    private fun pruneCompletedScans() {
        val cutoff = System.currentTimeMillis() - COMPLETED_RETENTION_MS
        scans.entries.removeIf { (_, scan) ->
            scan.completedAt.get()?.let { it < cutoff } == true
        }
    }

    private class ActiveScan(val scanId: String) {
        val status = AtomicReference("starting")
        val exitCode = AtomicReference<Int?>(null)
        val error = AtomicReference<String?>(null)
        val completedAt = AtomicReference<Long?>(null)
        val startedAt = System.currentTimeMillis()
        @Volatile var future: java.util.concurrent.Future<*>? = null
    }

    private companion object {
        const val COMPLETED_RETENTION_MS = 24 * 60 * 60 * 1000L
    }
}
