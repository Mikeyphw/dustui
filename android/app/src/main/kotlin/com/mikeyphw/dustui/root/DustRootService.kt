package com.mikeyphw.dustui.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DustRootService : RootService() {
    private val executor = ThreadPoolExecutor(
        MIN_PRIVILEGED_WORKERS,
        MAX_PRIVILEGED_WORKERS,
        PRIVILEGED_WORKER_KEEPALIVE_SECONDS,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(MAX_PRIVILEGED_QUEUE_DEPTH),
    ) { runnable ->
        Thread(runnable, "DustRootService-worker").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }
    private val scans = ConcurrentHashMap<String, ActiveScan>()
    private val duplicateCancellations = ConcurrentHashMap<String, AtomicBoolean>()
    private val cleanupCancellations = ConcurrentHashMap<String, AtomicBoolean>()
    private val cleanupProcesses = ConcurrentHashMap<String, java.lang.Process>()

    private val binder = object : IDustRootService.Stub() {
        override fun serviceInfo(): String {
            val uid = Process.myUid()
            val worker = runCatching { GoRootWorker.version(this@DustRootService) }.getOrDefault("")
            val ready = uid == 0 && worker == "dustui-go-root-worker-v1"
            return JSONObject()
                .put("ok", ready)
                .put("uid", uid)
                .put("gid", runCatching { Os.getgid() }.getOrDefault(-1))
                .put("pid", Process.myPid())
                .put("scanner", worker)
                .put("backend", detectRootBackend())
                .put("selinuxEnforcing", readSelinuxEnforcing())
                .put("selinuxContext", readSelinuxContext())
                .put("mountNamespace", readMountNamespace())
                .put("message", when {
                    uid != 0 -> "Root service is running as UID $uid instead of UID 0"
                    worker != "dustui-go-root-worker-v1" -> "Go root worker is unavailable"
                    else -> "Root service is ready with Go scan/mutation authority"
                })
                .toString()
        }

        override fun preflightScan(requestJson: String): String {
            val request = runCatching { ScanRequest.fromJson(requestJson) }.getOrElse { error ->
                return preflightFailure("INVALID_REQUEST", "", error.message ?: "Invalid scan request").toString()
            }
            return preflightScanRoot(request.rootPath).toString()
        }

        override fun startScan(requestJson: String, output: ParcelFileDescriptor): String {
            val request = runCatching { ScanRequest.fromJson(requestJson) }.getOrElse { error ->
                output.close()
                return failure(error.message ?: "Invalid scan request")
            }
            val preflight = preflightScanRoot(request.rootPath)
            if (!preflight.optBoolean("ok")) {
                output.close()
                return preflight.toString()
            }
            val ownedFdResult = runCatching { ParcelFileDescriptor.dup(output.fileDescriptor).detachFd() }
            runCatching { output.close() }
            val ownedFd = ownedFdResult.getOrElse { return failure(it.message ?: "Unable to duplicate output descriptor") }
            pruneCompletedScans()
            val active = ActiveScan(request.scanId)
            if (scans.putIfAbsent(request.scanId, active) != null) {
                runCatching { ParcelFileDescriptor.adoptFd(ownedFd).close() }
                return failure("Scan identifier is already active")
            }
            active.status.set("running")
            active.future = runCatching { executor.submit {
                val result = GoRootWorker.runScan(
                    context = this@DustRootService,
                    requestJson = requestJson,
                    ownedOutputFd = ownedFd,
                ) { process -> active.process.set(process) }
                active.exitCode.set(result.exitCode)
                if (result.error.isNotBlank()) active.error.set(result.error)
                active.status.set(
                    when {
                        result.exitCode == 0 -> "completed"
                        result.exitCode == 130 || active.status.get() == "cancelling" -> "cancelled"
                        else -> "failed"
                    },
                )
                active.completedAt.set(System.currentTimeMillis())
                active.process.set(null)
            } }.getOrElse { error ->
                runCatching { ParcelFileDescriptor.adoptFd(ownedFd).close() }
                scans.remove(request.scanId)
                return failure(error.message ?: "Unable to schedule Go root scan")
            }
            return state(active)
        }

        override fun scanState(scanId: String): String = scans[scanId]?.let(::state)
            ?: failure("Unknown scan identifier")

        override fun cancelScan(scanId: String): String {
            val active = scans[scanId] ?: return failure("Unknown scan identifier")
            val process = active.process.get()
            val accepted = process?.isAlive == true
            if (accepted) {
                active.status.set("cancelling")
                process?.destroy()
            }
            return state(active).let { raw -> JSONObject(raw).put("cancelAccepted", accepted).toString() }
        }

        override fun reviewCleanup(planJson: String): String {
            val enriched = runCatching {
                JSONObject(planJson)
                    .put("packageInventory", JSONObject(installedPackageInventory()))
                    .toString()
            }.getOrElse { error -> return failure(error.message ?: "Invalid cleanup review") }
            val result = GoRootWorker.reviewCleanup(this@DustRootService, enriched)
            return result.output.takeIf(String::isNotBlank)
                ?: deletionFailure(JSONObject(planJson).optString("id"), result.error.ifBlank { "Go cleanup review failed" })
        }

        override fun executeCleanup(planJson: String): String {
            val request = runCatching { JSONObject(planJson) }.getOrElse { error ->
                return failure(error.message ?: "Invalid deletion plan request")
            }
            val operationId = request.optString("operationId")
            if (operationId.isBlank()) return deletionFailure(operationId, "Cleanup operation identifier is required")
            val cancellation = AtomicBoolean(false)
            if (cleanupCancellations.putIfAbsent(operationId, cancellation) != null) {
                return deletionFailure(operationId, "Cleanup operation is already executing")
            }
            return try {
                val execution = runCatching { claimCleanupExecutionPlan(request) }.getOrElse { error ->
                    return deletionFailure(operationId, error.message ?: "Unable to claim reviewed deletion plan")
                }
                execution
                    .put("packageInventory", JSONObject(installedPackageInventory()))
                    .put("journalPath", cleanupJournalFile(operationId).absolutePath)
                val result = GoRootWorker.executeCleanup(this@DustRootService, execution.toString()) { process ->
                    cleanupProcesses[operationId] = process
                    if (cancellation.get()) process.destroy()
                }
                result.output.takeIf(String::isNotBlank)
                    ?: runCatching { cleanupJournalFile(operationId).readText() }.getOrNull()
                    ?: deletionFailure(operationId, result.error.ifBlank { "Go cleanup worker exited without a reconciliable result" })
            } finally {
                cleanupProcesses.remove(operationId)
                cleanupCancellations.remove(operationId, cancellation)
            }
        }

        override fun cancelCleanup(operationId: String): String {
            val cancellation = cleanupCancellations[operationId]
            val process = cleanupProcesses[operationId]
            val accepted = cancellation != null || process?.isAlive == true
            cancellation?.set(true)
            if (process?.isAlive == true) process.destroy()
            return JSONObject()
                .put("ok", accepted)
                .put("operationId", operationId)
                .put("cancelAccepted", accepted)
                .put("message", if (accepted) "Go cleanup cancellation requested" else "Cleanup operation is not active")
                .toString()
        }

        override fun reconcileCleanup(operationId: String): String {
            if (operationId.isBlank()) return deletionFailure(operationId, "Cleanup operation identifier is required")
            val request = JSONObject()
                .put("operationId", operationId)
                .put("journalPath", cleanupJournalFile(operationId).absolutePath)
                .toString()
            val result = GoRootWorker.reconcileCleanup(this@DustRootService, request)
            return result.output.takeIf(String::isNotBlank)
                ?: deletionFailure(operationId, result.error.ifBlank { "Cleanup reconciliation journal unavailable" })
        }

        override fun verifyDuplicates(requestJson: String): String = runCatching {
            val request = JSONObject(requestJson)
            val paths = request.getJSONArray("paths")
            require(paths.length() in 2..MAX_DUPLICATE_BUCKET_PATHS) {
                "Duplicate verification accepts 2 to $MAX_DUPLICATE_BUCKET_PATHS files per size bucket"
            }
            val scanId = request.getString("scanId")
            require(scanId.isNotBlank()) { "Duplicate verification requires a scan identifier" }
            val operationId = request.optString("operationId").ifBlank { scanId }
            require(operationId.isNotBlank()) { "Duplicate verification requires an operation identifier" }
            val cancellation = AtomicBoolean(false)
            require(duplicateCancellations.putIfAbsent(operationId, cancellation) == null) { "Duplicate verification operation is already active" }
            val expectedSize = request.getLong("sizeBytes")
            require(expectedSize > 0L) { "Duplicate verification requires a positive exact size" }
            val scanRoot = Paths.get(request.getString("scanRoot")).normalize()
            require(scanRoot.isAbsolute) { "Duplicate verification scan root must be absolute" }
            val partialBytes = request.optInt("partialBytes", DEFAULT_PARTIAL_HASH_BYTES)
                .coerceIn(4 * 1024, MAX_PARTIAL_HASH_BYTES)
            val probes = mutableListOf<DuplicateProbe>()
            val skipped = JSONArray()
            var bytesHashed = 0L

            for (index in 0 until paths.length()) {
                ensureDuplicateActive(cancellation)
                val rawPath = paths.getString(index)
                val path = runCatching { Paths.get(rawPath).normalize() }.getOrNull()
                val reason = when {
                    path == null || !path.isAbsolute -> "Path must be absolute"
                    Files.isSymbolicLink(path) -> "Symbolic links are not duplicate candidates"
                    !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) -> "Path is not a regular file"
                    else -> null
                }
                if (reason != null || path == null) {
                    skipped.put(JSONObject().put("path", rawPath).put("reason", reason ?: "Invalid path"))
                    continue
                }
                val statResult = runCatching { Os.lstat(path.toString()) }
                val stat = statResult.getOrNull()
                if (stat == null) {
                    val error = statResult.exceptionOrNull()
                    skipped.put(JSONObject().put("path", rawPath).put("reason", error?.message ?: "Unable to inspect file"))
                    continue
                }
                if (stat.st_size != expectedSize) {
                    skipped.put(JSONObject().put("path", rawPath).put("reason", "File size changed since scan"))
                    continue
                }
                val partial = hashFileEdges(path, expectedSize, partialBytes) { cancellation.get() }
                bytesHashed += partial.bytesRead
                probes += DuplicateProbe(
                    path = path.toString(),
                    modifiedAt = stat.st_mtime,
                    deviceId = stat.st_dev,
                    inode = stat.st_ino,
                    linkCount = stat.st_nlink,
                    partialHash = partial.hash,
                    deletionBlockReason = CleanupSafetyPolicy.blockReason(path.toString(), scanRoot.toString()),
                )
            }

            val partialGroups = probes.groupBy { it.partialHash }.values.filter { it.size > 1 }
            val fullGroups = linkedMapOf<String, MutableList<DuplicateProbe>>()
            partialGroups.forEach { partialGroup ->
                partialGroup.forEach { probe ->
                    ensureDuplicateActive(cancellation)
                    val path = Paths.get(probe.path)
                    val full = hashWholeFile(path) { cancellation.get() }
                    bytesHashed += full.bytesRead
                    val after = runCatching { Os.lstat(probe.path) }.getOrNull()
                    if (after == null || after.st_dev != probe.deviceId || after.st_ino != probe.inode ||
                        after.st_size != expectedSize || after.st_mtime != probe.modifiedAt
                    ) {
                        skipped.put(JSONObject().put("path", probe.path).put("reason", "File changed while hashing"))
                    } else {
                        fullGroups.getOrPut(full.hash) { mutableListOf() }.add(probe)
                    }
                }
            }

            val verified = JSONArray()
            fullGroups.filterValues { it.size > 1 }.toSortedMap().forEach { (sha256, matches) ->
                val physicalCopies = matches.groupBy { "${it.deviceId}:${it.inode}" }
                if (physicalCopies.size < 2) return@forEach
                val hardLinkedCopies = physicalCopies.values.count { owned ->
                    owned.size > 1 || owned.maxOf { it.linkCount } > 1L
                }
                val eligibleCopies = physicalCopies.values.count { owned ->
                    owned.size == 1 && owned.single().linkCount == 1L && owned.single().deletionBlockReason == null
                }
                val retainedByPolicyCopies = physicalCopies.size - eligibleCopies
                val copiesToDeleteSafely = (eligibleCopies - if (retainedByPolicyCopies == 0) 1 else 0).coerceAtLeast(0)
                val records = JSONArray()
                physicalCopies.toSortedMap().forEach { (copyKey, owned) ->
                    val hardLinked = owned.size > 1 || owned.maxOf { it.linkCount } > 1L
                    owned.sortedBy { it.path }.forEach { probe ->
                        records.put(JSONObject()
                            .put("path", probe.path)
                            .put("deviceId", probe.deviceId)
                            .put("inode", probe.inode)
                            .put("linkCount", probe.linkCount)
                            .put("modifiedAt", probe.modifiedAt)
                            .put("copyKey", copyKey)
                            .put("hardLinked", hardLinked)
                            .put("autoDeleteEligible", !hardLinked && owned.size == 1 && probe.deletionBlockReason == null)
                            .put("deletionBlockReason", probe.deletionBlockReason))
                    }
                }
                verified.put(JSONObject()
                    .put("sha256", sha256)
                    .put("partialHash", matches.first().partialHash)
                    .put("sizeBytes", expectedSize)
                    .put("paths", records)
                    .put("physicalCopyCount", physicalCopies.size)
                    .put("hardLinkedCopyCount", hardLinkedCopies)
                    .put("reclaimableBytes", expectedSize * (physicalCopies.size - 1L))
                    .put("safeReclaimableBytes", expectedSize * copiesToDeleteSafely))
            }
            duplicateCancellations.remove(operationId, cancellation)
            JSONObject()
                .put("ok", true)
                .put("operationId", operationId)
                .put("cancelled", false)
                .put("checkedFiles", probes.size)
                .put("skipped", skipped)
                .put("partialGroups", partialGroups.size)
                .put("bytesHashed", bytesHashed)
                .put("groups", verified)
        }.getOrElse { error ->
            val request = runCatching { JSONObject(requestJson) }.getOrNull()
            val operationId = request?.optString("operationId")?.takeIf { it.isNotBlank() }
                ?: request?.optString("scanId")?.takeIf { it.isNotBlank() }
            operationId?.let { duplicateCancellations.remove(it) }
            JSONObject()
                .put("ok", false)
                .put("operationId", operationId ?: "")
                .put("cancelled", error is DuplicateVerificationCancelled)
                .put("checkedFiles", 0)
                .put("skipped", JSONArray())
                .put("partialGroups", 0)
                .put("bytesHashed", 0)
                .put("groups", JSONArray())
                .put("message", error.message ?: error.javaClass.simpleName)
        }.toString()


        override fun cancelDuplicateVerification(scanId: String): String {
            val cancelled = duplicateCancellations[scanId]?.also { it.set(true) } != null
            return JSONObject().put("ok", true).put("operationId", scanId).put("cancelled", cancelled).toString()
        }

        override fun installedPackages(): String = installedPackageInventory()


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

    private fun installedPackageInventory(): String = runCatching {
        val userCommand = runFixedCommand("/system/bin/cmd", "user", "list")
        val discoveredUsers = USER_INFO_PATTERN.findAll(userCommand.output)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
            .toCollection(sortedSetOf())
            .ifEmpty { sortedSetOf(0) }
        var complete = userCommand.ok
        val users = JSONArray()
        val failures = mutableListOf<String>()
        discoveredUsers.forEach { userId ->
            val packageCommand = runFixedCommand(
                "/system/bin/cmd",
                "package",
                "list",
                "packages",
                "--user",
                userId.toString(),
            )
            if (!packageCommand.ok) {
                complete = false
                failures += "user $userId"
            }
            val packages = packageCommand.output.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").substringBefore('=') }
                .filter { PACKAGE_NAME_PATTERN.matches(it) }
                .distinct()
                .sorted()
                .toList()
            users.put(JSONObject().put("id", userId).put("packages", JSONArray(packages)))
        }
        JSONObject()
            .put("ok", users.length() > 0)
            .put("complete", complete && failures.isEmpty())
            .put("users", users)
            .put(
                "message",
                when {
                    failures.isNotEmpty() -> "Package visibility failed for ${failures.joinToString()}"
                    !userCommand.ok -> "Android user discovery was incomplete"
                    else -> JSONObject.NULL
                },
            )
            .toString()
    }.getOrElse { error ->
        JSONObject()
            .put("ok", false)
            .put("complete", false)
            .put("users", JSONArray())
            .put("message", error.message ?: error.javaClass.simpleName)
            .toString()
    }

    private fun preflightScanRoot(rawPath: String): JSONObject {
        val requested = rawPath.trim()
        val path = runCatching { Paths.get(requested).normalize() }.getOrElse { error ->
            return preflightFailure("INVALID_PATH", requested, error.message ?: "Invalid scan-root path")
        }
        if (!path.isAbsolute) return preflightFailure("PATH_NOT_ABSOLUTE", requested, "Scan root must be an absolute path")
        val metadata = try {
            Os.lstat(path.toString())
        } catch (error: ErrnoException) {
            return preflightFailure(errnoCode("ROOT_LSTAT", error.errno), requested, error.message ?: "Unable to inspect scan root", error.errno)
        } catch (error: Throwable) {
            return preflightFailure("ROOT_LSTAT_FAILED", requested, error.message ?: error.javaClass.simpleName)
        }
        val symbolicLink = OsConstants.S_ISLNK(metadata.st_mode)
        if (symbolicLink) {
            val canonical = runCatching { path.toFile().canonicalPath }.getOrNull()
            return preflightFailure(
                "ROOT_SYMLINK",
                requested,
                "Scan root is a symbolic link${canonical?.let { "; use resolved path $it" }.orEmpty()}",
            ).put("canonicalPath", canonical).put("exists", true).put("symbolicLink", true)
        }
        if (!OsConstants.S_ISDIR(metadata.st_mode)) {
            return preflightFailure("ROOT_NOT_DIRECTORY", requested, "Scan root is not a directory")
                .put("exists", true).put("directory", false)
        }
        val canonical = runCatching { path.toFile().canonicalPath }.getOrDefault(path.toString())
        val descriptor = try {
            Os.open(path.toString(), OsConstants.O_RDONLY, 0)
        } catch (error: ErrnoException) {
            return preflightFailure(errnoCode("ROOT_OPEN", error.errno), requested, error.message ?: "Unable to open scan root", error.errno)
                .put("canonicalPath", canonical).put("exists", true).put("directory", true)
        } catch (error: Throwable) {
            return preflightFailure("ROOT_OPEN_FAILED", requested, error.message ?: error.javaClass.simpleName)
                .put("canonicalPath", canonical).put("exists", true).put("directory", true)
        }
        runCatching { Os.close(descriptor) }
        val hasChildren = runCatching {
            Files.newDirectoryStream(path).use { stream -> stream.iterator().hasNext() }
        }.getOrNull()
        val mount = mountFor(canonical)
        return JSONObject()
            .put("ok", Process.myUid() == 0)
            .put("code", if (Process.myUid() == 0) "READY" else "ROOT_UID_MISMATCH")
            .put("requestedPath", requested)
            .put("canonicalPath", canonical)
            .put("exists", true)
            .put("directory", true)
            .put("symbolicLink", false)
            .put("opened", true)
            .put("hasChildren", hasChildren)
            .put("uid", Process.myUid())
            .put("selinuxContext", readSelinuxContext())
            .put("mountPoint", mount?.optString("mountPoint"))
            .put("filesystem", mount?.optString("filesystem"))
            .put("mountOptions", mount?.optString("options"))
            .put("message", if (Process.myUid() == 0) "Scan root opened successfully" else "Root service is not running as UID 0")
    }

    private fun preflightFailure(code: String, path: String, message: String, errno: Int? = null): JSONObject = JSONObject()
        .put("ok", false)
        .put("code", code)
        .put("requestedPath", path)
        .put("exists", false)
        .put("directory", false)
        .put("symbolicLink", false)
        .put("opened", false)
        .put("errno", errno)
        .put("uid", Process.myUid())
        .put("selinuxContext", readSelinuxContext())
        .put("message", message)

    private fun errnoCode(prefix: String, errno: Int): String = when (errno) {
        OsConstants.EACCES, OsConstants.EPERM -> "${prefix}_PERMISSION_DENIED"
        OsConstants.ENOENT -> "${prefix}_NOT_FOUND"
        OsConstants.ENOTDIR -> "${prefix}_NOT_DIRECTORY"
        OsConstants.ELOOP -> "${prefix}_SYMLINK_LOOP"
        else -> "${prefix}_ERRNO_$errno"
    }

    private fun readSelinuxContext(): String? = runCatching { File("/proc/self/attr/current").readText().trim().takeIf { it.isNotBlank() } }.getOrNull()

    private fun readMountNamespace(): String? = runCatching { Os.readlink("/proc/self/ns/mnt") }.getOrNull()

    private fun runFixedCommand(vararg command: String): FixedCommandResult {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val finished = process.waitFor(FIXED_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        val output = runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
        return FixedCommandResult(
            ok = finished && process.exitValue() == 0,
            output = output,
        )
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
        scans.values.forEach { active -> active.process.get()?.destroy() }
        cleanupProcesses.values.forEach { process -> process.destroy() }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun claimCleanupExecutionPlan(request: JSONObject): JSONObject {
        val operationId = request.optString("operationId")
        require(operationId.isNotBlank()) { "Cleanup execution requires an operation identifier" }
        val directory = File(cacheDir, CLEANUP_PLAN_DIRECTORY)
        val source = File(directory, "${safeOperationFileName(operationId)}.json")
        require(source.isFile) { "Reviewed cleanup plan is missing, expired, or already claimed" }
        require(source.length() in 1..MAX_CLEANUP_PLAN_BYTES) { "Reviewed cleanup plan size is invalid" }
        val claimed = File(directory, "${source.name}.claimed-${System.nanoTime()}")
        require(source.renameTo(claimed)) { "Unable to atomically claim reviewed cleanup plan" }
        return try {
            JSONObject(claimed.readText(Charsets.UTF_8)).also { execution ->
                require(execution.optString("operationId") == operationId) { "Reviewed cleanup operation mismatch" }
            }
        } finally {
            claimed.delete()
        }
    }

    private fun cleanupJournalFile(operationId: String): File {
        val directory = File(cacheDir, CLEANUP_JOURNAL_DIRECTORY).apply { mkdirs() }
        return File(directory, "${safeOperationFileName(operationId)}.json")
    }

    private fun safeOperationFileName(operationId: String): String = operationId.map { character ->
        if (character.isLetterOrDigit() || character == '-' || character == '_') character else '_'
    }.joinToString("").take(120).ifBlank { "operation" }

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

    private fun hashFileEdges(
        path: Path,
        sizeBytes: Long,
        partialBytes: Int,
        cancelled: () -> Boolean = { false },
    ): HashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sizeBytes.toString().toByteArray(Charsets.UTF_8))
        var bytesRead = 0L
        Files.newByteChannel(path, java.nio.file.StandardOpenOption.READ).use { channel ->
            fun readAt(position: Long, requested: Int) {
                ensureDuplicateActive(cancelled)
                channel.position(position)
                val buffer = java.nio.ByteBuffer.allocate(requested)
                while (buffer.hasRemaining()) {
                    ensureDuplicateActive(cancelled)
                    val read = channel.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    bytesRead += read
                }
                digest.update(buffer.array(), 0, buffer.position())
            }
            val firstLength = minOf(sizeBytes, partialBytes.toLong()).toInt()
            readAt(0L, firstLength)
            if (sizeBytes > firstLength) {
                val lastLength = minOf(sizeBytes - firstLength, partialBytes.toLong()).toInt()
                readAt((sizeBytes - lastLength).coerceAtLeast(0L), lastLength)
            }
        }
        return HashResult(digest.digest().toHex(), bytesRead)
    }

    private fun hashWholeFile(path: Path, cancelled: () -> Boolean = { false }): HashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesRead = 0L
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                ensureDuplicateActive(cancelled)
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
                bytesRead += read
            }
        }
        return HashResult(digest.digest().toHex(), bytesRead)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun ensureDuplicateActive(cancelled: AtomicBoolean) = ensureDuplicateActive { cancelled.get() }

    private fun ensureDuplicateActive(cancelled: () -> Boolean) {
        if (cancelled()) throw DuplicateVerificationCancelled()
    }

    private class DuplicateVerificationCancelled : java.io.IOException("Duplicate verification cancelled")

    private data class HashResult(val hash: String, val bytesRead: Long)

    private data class DuplicateProbe(
        val path: String,
        val modifiedAt: Long,
        val deviceId: Long,
        val inode: Long,
        val linkCount: Long,
        val partialHash: String,
        val deletionBlockReason: String?,
    )

    private data class FixedCommandResult(val ok: Boolean, val output: String)

    private class ActiveScan(val scanId: String) {
        val status = AtomicReference("starting")
        val exitCode = AtomicReference<Int?>(null)
        val error = AtomicReference<String?>(null)
        val completedAt = AtomicReference<Long?>(null)
        val process = AtomicReference<java.lang.Process?>(null)
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
        val USER_INFO_PATTERN = Regex("UserInfo\\{(\\d+):")
        val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")
        const val FIXED_COMMAND_TIMEOUT_SECONDS = 15L
        const val COMPLETED_RETENTION_MS = 24 * 60 * 60 * 1000L
        const val MAX_DUPLICATE_BUCKET_PATHS = 2_000
        const val DEFAULT_PARTIAL_HASH_BYTES = 64 * 1024
        const val MAX_PARTIAL_HASH_BYTES = 1024 * 1024
        private const val MIN_PRIVILEGED_WORKERS = 1
        private const val MAX_PRIVILEGED_WORKERS = 2
        private const val MAX_PRIVILEGED_QUEUE_DEPTH = 8
        private const val PRIVILEGED_WORKER_KEEPALIVE_SECONDS = 30L
        const val CLEANUP_PLAN_DIRECTORY = "dustui_cleanup_plans"
        const val CLEANUP_JOURNAL_DIRECTORY = "dustui_cleanup_journals"
        const val MAX_CLEANUP_PLAN_BYTES = 8 * 1024 * 1024L
    }
}
