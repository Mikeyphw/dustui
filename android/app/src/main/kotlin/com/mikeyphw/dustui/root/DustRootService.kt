package com.mikeyphw.dustui.root

import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.mikeyphw.dustui.scan.ScanRequest
import com.topjohnwu.superuser.ipc.RootService
import org.json.JSONObject
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
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        scans.keys.forEach(NativeDustScanner::cancel)
        executor.shutdownNow()
        super.onDestroy()
    }

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
