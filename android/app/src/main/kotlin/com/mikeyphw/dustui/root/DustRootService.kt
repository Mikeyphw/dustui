package com.mikeyphw.dustui.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import com.mikeyphw.dustui.scan.ScanRequest
import com.mikeyphw.dustui.data.CleanupSafetyPolicy
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
            .put("backend", detectRootBackend())
            .put("selinuxEnforcing", readSelinuxEnforcing())
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
            val execution = runCatching { JSONObject(planJson) }.getOrElse { error ->
                return failure(error.message ?: "Invalid deletion plan")
            }
            val operationId = execution.optString("operationId")
            val scanId = execution.optString("scanId")
            val rootPath = runCatching { Paths.get(execution.getString("rootPath")).normalize() }.getOrElse { error ->
                return deletionFailure(operationId, error.message ?: "Invalid deletion root")
            }
            val accepted = execution.optJSONArray("accepted") ?: JSONArray()
            if (operationId.isBlank() || scanId.isBlank() || !rootPath.isAbsolute || accepted.length() == 0) {
                return deletionFailure(operationId, "Deletion requires a reviewed operation, scan, root, and at least one approved item")
            }

            val results = JSONArray()
            var deletedBytes = 0L
            var failedCount = 0
            for (index in 0 until accepted.length()) {
                val item = accepted.getJSONObject(index)
                val rawPath = item.optString("path")
                val plannedBytes = item.optLong("plannedBytes")
                val target = runCatching { Paths.get(rawPath).normalize() }.getOrNull()
                val rejection = when {
                    target == null -> "Invalid path"
                    CleanupSafetyPolicy.blockReason(target.toString(), rootPath.toString()) != null ->
                        CleanupSafetyPolicy.blockReason(target.toString(), rootPath.toString())
                    !Files.exists(target, LinkOption.NOFOLLOW_LINKS) -> "Path no longer exists"
                    Files.isSymbolicLink(target) -> "Selected symbolic links are not deleted"
                    !fingerprintMatches(target, rootPath, item.optJSONObject("fingerprint")) ->
                        "Path contents changed after review; create a new deletion plan"
                    else -> null
                }
                val outcome = if (target == null || rejection != null) {
                    DeleteOutcome(0, false, rejection ?: "Deletion was rejected")
                } else {
                    deletePath(target, rootPath)
                }
                val itemStatus = when {
                    outcome.complete -> "DELETED"
                    outcome.deletedBytes > 0 -> "PARTIAL"
                    else -> "FAILED"
                }
                if (!outcome.complete) failedCount++
                deletedBytes += outcome.deletedBytes
                results.put(
                    JSONObject()
                        .put("path", rawPath)
                        .put("plannedBytes", plannedBytes)
                        .put("deletedBytes", outcome.deletedBytes)
                        .put("status", itemStatus)
                        .put("message", outcome.message),
                )
            }

            val status = when {
                failedCount == 0 -> "COMPLETED"
                deletedBytes > 0 -> "PARTIAL"
                else -> "FAILED"
            }
            val message = when (status) {
                "COMPLETED" -> "Permanent deletion completed"
                "PARTIAL" -> "$failedCount item(s) were not fully deleted"
                else -> "No approved items were deleted"
            }
            return JSONObject()
                .put("ok", failedCount == 0)
                .put("operationId", operationId)
                .put("status", status)
                .put("deletedBytes", deletedBytes)
                .put("failedCount", failedCount)
                .put("items", results)
                .put("message", message)
                .toString()
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

        override fun inspectPath(path: String, previewBytes: Int): String = runCatching {
            val target = Paths.get(path).normalize()
            require(target.isAbsolute) { "Path must be absolute" }
            val stat = Os.lstat(target.toString())
            val symbolic = Files.isSymbolicLink(target)
            val type = when {
                symbolic -> "symlink"
                Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) -> "directory"
                Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) -> "file"
                else -> "special"
            }
            val mount = mountFor(target.toString())
            val previewLimit = previewBytes.coerceIn(0, 32 * 1024)
            var preview: String? = null
            var truncated = false
            if (type == "file" && previewLimit > 0) {
                val size = stat.st_size
                val bytes = Files.newInputStream(target).use { input ->
                    val buffer = ByteArray(previewLimit + 1)
                    var offset = 0
                    while (offset < buffer.size) {
                        val read = input.read(buffer, offset, buffer.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                    buffer.copyOf(offset)
                }
                truncated = size > previewLimit || bytes.size > previewLimit
                val owned = bytes.copyOf(minOf(bytes.size, previewLimit))
                val binary = owned.take(1024).any { it == 0.toByte() }
                preview = if (binary) "Binary file. Preview unavailable." else owned.toString(Charsets.UTF_8)
            }
            JSONObject()
                .put("ok", true)
                .put("path", target.toString())
                .put("type", type)
                .put("sizeBytes", stat.st_size)
                .put("inode", stat.st_ino)
                .put("uid", stat.st_uid)
                .put("gid", stat.st_gid)
                .put("mode", stat.st_mode)
                .put("permissions", modeString(stat.st_mode))
                .put("selinuxContext", runCatching { Os.getxattr(target.toString(), "security.selinux").toString(Charsets.UTF_8).trimEnd('\u0000') }.getOrNull())
                .put("symlinkTarget", if (symbolic) runCatching { Os.readlink(target.toString()) }.getOrNull() else null)
                .put("mountPoint", mount?.getString("mountPoint"))
                .put("filesystem", mount?.getString("filesystem"))
                .put("mountSource", mount?.getString("source"))
                .put("bindMount", mount?.optBoolean("bind") ?: false)
                .put("overlayMount", mount?.optBoolean("overlay") ?: false)
                .put("readOnly", mount?.optBoolean("readOnly") ?: false)
                .put("preview", preview)
                .put("previewTruncated", truncated)
                .toString()
        }.getOrElse { error -> failure(error.message ?: error.javaClass.simpleName) }


    }

    private fun detectRootBackend(): String = when {
        File("/data/adb/ksu").exists() -> "KernelSU"
        File("/data/adb/ap").exists() -> "APatch"
        File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists() -> "Magisk"
        else -> "Generic su"
    }

    private fun readSelinuxEnforcing(): Boolean? = runCatching {
        File("/sys/fs/selinux/enforce").readText().trim() == "1"
    }.getOrNull()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        scans.keys.forEach(NativeDustScanner::cancel)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun reviewPlan(planJson: String): JSONObject = runCatching {
        val plan = JSONObject(planJson)
        val operationId = plan.getString("id")
        val rootPath = Paths.get(plan.getString("rootPath")).normalize()
        require(rootPath.isAbsolute) { "Deletion root must be absolute" }
        require(Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS)) { "Scan root no longer exists" }
        val accepted = JSONArray()
        val blocked = JSONArray()
        val items = plan.getJSONArray("items")
        var plannedBytes = 0L
        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val rawPath = item.getString("path")
            val path = runCatching { Paths.get(rawPath).normalize() }.getOrNull()
            val plannedDirectory = item.optBoolean("directory")
            val plannedModifiedAt = item.optLong("modifiedAt")
            val plannedItemBytes = item.optLong("sizeBytes")
            val policyReason = if (path == null) "Invalid path" else CleanupSafetyPolicy.blockReason(path.toString(), rootPath.toString())
            val selectedSymlink = path != null && Files.isSymbolicLink(path)
            val inspection = if (policyReason == null && path != null && !selectedSymlink) inspectDeletionTree(path, rootPath) else null
            val reason = when {
                policyReason != null -> policyReason
                selectedSymlink -> "Selected symbolic links are not deleted"
                inspection == null -> "Path no longer exists or cannot be inspected"
                inspection.error != null -> inspection.error
                inspection.isDirectory != plannedDirectory -> "Path type changed since scan"
                plannedModifiedAt > 0 && inspection.modifiedAt != plannedModifiedAt -> "Path changed since scan"
                !inspection.isDirectory && plannedItemBytes >= 0 && inspection.statSizeBytes != plannedItemBytes -> "File size changed since scan"
                else -> null
            }
            if (reason == null && path != null && inspection != null) {
                accepted.put(
                    JSONObject()
                        .put("path", path.toString())
                        .put("plannedBytes", inspection.contentBytes)
                        .put("directory", inspection.isDirectory)
                        .put("fingerprint", inspection.toJson()),
                )
                plannedBytes += inspection.contentBytes
            } else {
                blocked.put(JSONObject().put("path", rawPath).put("reason", reason ?: "Unable to plan deletion"))
            }
        }
        JSONObject()
            .put("ok", true)
            .put("operationId", operationId)
            .put("accepted", accepted)
            .put("blocked", blocked)
            .put("plannedBytes", plannedBytes)
    }.getOrElse { error ->
        JSONObject()
            .put("ok", false)
            .put("operationId", "")
            .put("accepted", JSONArray())
            .put("blocked", JSONArray())
            .put("plannedBytes", 0)
            .put("message", error.message ?: error.javaClass.simpleName)
    }

    private fun inspectDeletionTree(target: Path, rootPath: Path): TreeFingerprint? {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) return null
        val rootDevice = runCatching { Os.lstat(rootPath.toString()).st_dev }.getOrNull() ?: return null
        val topStat = runCatching { Os.lstat(target.toString()) }.getOrNull() ?: return null
        if (topStat.st_dev != rootDevice) {
            return TreeFingerprint.from(topStat, Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS), error = "Selected path crosses a filesystem boundary")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var entryCount = 0L
        var contentBytes = 0L
        var error: String? = null
        runCatching {
            Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
                private fun record(path: Path, stat: android.system.StructStat, regularFile: Boolean) {
                    if (stat.st_dev != rootDevice) throw java.io.IOException("Nested filesystem boundary: $path")
                    entryCount++
                    if (entryCount > MAX_DELETE_PLAN_ENTRIES) throw java.io.IOException("Selection exceeds $MAX_DELETE_PLAN_ENTRIES planned entries; choose smaller items")
                    val relative = if (path == target) "." else target.relativize(path).toString()
                    val marker = "$relative\u0000${stat.st_dev}\u0000${stat.st_ino}\u0000${stat.st_mode}\u0000${stat.st_size}\u0000${stat.st_mtime}\n"
                    digest.update(marker.toByteArray(Charsets.UTF_8))
                    if (regularFile) contentBytes += stat.st_size.coerceAtLeast(0L)
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    record(dir, Os.lstat(dir.toString()), regularFile = false)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val stat = Os.lstat(file.toString())
                    record(file, stat, regularFile = attrs.isRegularFile)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    throw java.io.IOException("Unable to inspect $file: ${exc.message ?: exc.javaClass.simpleName}", exc)
                }
            })
        }.onFailure { failure -> error = failure.message ?: failure.javaClass.simpleName }
        return TreeFingerprint.from(
            stat = topStat,
            isDirectory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS),
            treeDigest = digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') },
            entryCount = entryCount,
            contentBytes = contentBytes,
            error = error,
        )
    }

    private fun fingerprintMatches(path: Path, rootPath: Path, expected: JSONObject?): Boolean {
        if (expected == null) return false
        val actual = inspectDeletionTree(path, rootPath) ?: return false
        if (actual.error != null) return false
        return actual.device == expected.optLong("device") &&
            actual.inode == expected.optLong("inode") &&
            actual.mode == expected.optInt("mode") &&
            actual.statSizeBytes == expected.optLong("statSizeBytes") &&
            actual.modifiedAt == expected.optLong("modifiedAt") &&
            actual.treeDigest == expected.optString("treeDigest") &&
            actual.entryCount == expected.optLong("entryCount")
    }

    private fun deletePath(target: Path, rootPath: Path): DeleteOutcome {
        val rootDevice = runCatching { Os.lstat(rootPath.toString()).st_dev }.getOrElse { error ->
            return DeleteOutcome(0, false, error.message ?: "Unable to inspect deletion root")
        }
        var deletedBytes = 0L
        val failures = mutableListOf<String>()
        runCatching {
            Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val device = runCatching { Os.lstat(dir.toString()).st_dev }.getOrNull()
                    return if (device == null || device != rootDevice) {
                        failures += "Filesystem boundary or unreadable directory skipped: $dir"
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val device = runCatching { Os.lstat(file.toString()).st_dev }.getOrNull()
                    if (device == null || device != rootDevice) {
                        failures += "Filesystem boundary or unreadable item skipped: $file"
                        return FileVisitResult.CONTINUE
                    }
                    val bytes = if (attrs.isRegularFile) attrs.size() else 0L
                    runCatching { Files.delete(file) }
                        .onSuccess { deletedBytes += bytes }
                        .onFailure { failures += "$file: ${it.message ?: it.javaClass.simpleName}" }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    failures += "$file: ${exc.message ?: exc.javaClass.simpleName}"
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) failures += "$dir: ${exc.message ?: exc.javaClass.simpleName}"
                    runCatching { Files.delete(dir) }
                        .onFailure { failures += "$dir: ${it.message ?: it.javaClass.simpleName}" }
                    return FileVisitResult.CONTINUE
                }
            })
        }.onFailure { failures += it.message ?: it.javaClass.simpleName }
        val complete = !Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        return DeleteOutcome(
            deletedBytes = deletedBytes,
            complete = complete,
            message = failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    private fun deletionFailure(operationId: String, message: String): String = JSONObject()
        .put("ok", false)
        .put("operationId", operationId)
        .put("status", "FAILED")
        .put("deletedBytes", 0)
        .put("failedCount", 0)
        .put("items", JSONArray())
        .put("message", message)
        .toString()


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

    private data class TreeFingerprint(
        val device: Long,
        val inode: Long,
        val mode: Int,
        val statSizeBytes: Long,
        val modifiedAt: Long,
        val isDirectory: Boolean,
        val treeDigest: String,
        val entryCount: Long,
        val contentBytes: Long,
        val error: String?,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("device", device)
            .put("inode", inode)
            .put("mode", mode)
            .put("statSizeBytes", statSizeBytes)
            .put("modifiedAt", modifiedAt)
            .put("treeDigest", treeDigest)
            .put("entryCount", entryCount)

        companion object {
            fun from(
                stat: android.system.StructStat,
                isDirectory: Boolean,
                treeDigest: String = "",
                entryCount: Long = 0,
                contentBytes: Long = 0,
                error: String? = null,
            ): TreeFingerprint = TreeFingerprint(
                device = stat.st_dev,
                inode = stat.st_ino,
                mode = stat.st_mode,
                statSizeBytes = stat.st_size,
                modifiedAt = stat.st_mtime,
                isDirectory = isDirectory,
                treeDigest = treeDigest,
                entryCount = entryCount,
                contentBytes = contentBytes,
                error = error,
            )
        }
    }

    private data class DeleteOutcome(val deletedBytes: Long, val complete: Boolean, val message: String?)

    private class ActiveScan(val scanId: String) {
        val status = AtomicReference("starting")
        val exitCode = AtomicReference<Int?>(null)
        val error = AtomicReference<String?>(null)
        val completedAt = AtomicReference<Long?>(null)
        val startedAt = System.currentTimeMillis()
        @Volatile var future: java.util.concurrent.Future<*>? = null
    }

    private fun mountFor(path: String): JSONObject? = runCatching {
        File("/proc/mounts").readLines().mapNotNull { line ->
            val parts = line.split(' ')
            if (parts.size < 4) null else JSONObject()
                .put("source", parts[0])
                .put("mountPoint", parts[1].replace("\\040", " "))
                .put("filesystem", parts[2])
                .put("options", parts[3])
        }.filter { path == it.getString("mountPoint") || path.startsWith(it.getString("mountPoint").trimEnd('/') + "/") }
            .maxByOrNull { it.getString("mountPoint").length }
            ?.also { mount ->
                val options = mount.getString("options").split(',')
                mount.put("readOnly", "ro" in options)
                mount.put("bind", "bind" in options || "rbind" in options)
                mount.put("overlay", mount.getString("filesystem") == "overlay")
            }
    }.getOrNull()

    private fun modeString(mode: Int): String {
        val masks = intArrayOf(256, 128, 64, 32, 16, 8, 4, 2, 1)
        val chars = charArrayOf('r','w','x','r','w','x','r','w','x')
        return masks.indices.joinToString("") { if (mode and masks[it] != 0) chars[it].toString() else "-" }
    }

    private companion object {
        const val COMPLETED_RETENTION_MS = 24 * 60 * 60 * 1000L
        const val MAX_DELETE_PLAN_ENTRIES = 100_000L
    }
}
