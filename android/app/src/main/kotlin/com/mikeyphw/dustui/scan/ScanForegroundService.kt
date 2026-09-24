package com.mikeyphw.dustui.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mikeyphw.dustui.DustUiApplication
import com.mikeyphw.dustui.MainActivity
import com.mikeyphw.dustui.R
import com.mikeyphw.dustui.data.ScanDiagnosticIssue
import com.mikeyphw.dustui.data.ScanDiagnosticSeverity
import com.mikeyphw.dustui.data.ScanDiagnostics
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanIntegrityStatus
import com.mikeyphw.dustui.data.ScanSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    @Volatile private var activeScanId: String? = null
    private val container by lazy { (application as DustUiApplication).container }
    private val domainStore by lazy { container.goDomainStore }
    private val rootClient by lazy { container.rootServiceClient }
    private val diagnosticStore by lazy { container.scanDiagnosticStore }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.scan_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val raw = intent.getStringExtra(EXTRA_REQUEST) ?: return START_NOT_STICKY
                val request = runCatching { ScanRequest.fromJson(raw) }.getOrElse {
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                if (scanJob?.isActive == true) {
                    scope.launch {
                        domainStore.upsertSession(
                            ScanSessionEntity(
                                id = request.scanId,
                                presetId = request.presetId,
                                rootPath = request.rootPath,
                                optionsJson = request.toJson(),
                                status = ScanStatus.FAILED.name,
                                startedAt = System.currentTimeMillis(),
                                completedAt = System.currentTimeMillis(),
                                errorMessage = "Another scan is already running",
                                integrityStatus = ScanIntegrityStatus.FAILED_INTEGRITY.name,
                            ),
                        )
                    }
                    return START_NOT_STICKY
                }
                activeScanId = request.scanId
                scanJob = scope.launch { execute(request, startId) }
            }
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_SCAN_ID)?.let { scanId ->
                    if (activeScanId == scanId) {
                        scanJob?.cancel()
                    } else {
                        scope.launch { rootClient.cancelScan(scanId) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun execute(request: ScanRequest, startId: Int) {
        var session = ScanSessionEntity(
            id = request.scanId,
            presetId = request.presetId,
            rootPath = request.rootPath,
            optionsJson = request.toJson(),
            status = ScanStatus.STARTING.name,
            startedAt = System.currentTimeMillis(),
        )
        var diagnostics = ScanDiagnostics(
            scanId = request.scanId,
            rootPath = request.rootPath,
            startedAt = session.startedAt,
        )
        var foregroundStarted = false

        fun saveDiagnostics() = runCatching { diagnosticStore.save(diagnostics) }
        fun issue(
            code: String,
            severity: ScanDiagnosticSeverity,
            phase: String,
            message: String,
            path: String? = request.rootPath,
            errno: Int? = null,
        ) {
            diagnostics = diagnostics.append(
                ScanDiagnosticIssue(
                    code = code,
                    severity = severity,
                    phase = phase,
                    message = message,
                    path = path,
                    errno = errno,
                ),
            )
            saveDiagnostics()
        }

        try {
            domainStore.deleteEntries(request.scanId)
            domainStore.upsertSession(session)
            saveDiagnostics()
            startForeground(NOTIFICATION_ID, notification(session, running = true))
            foregroundStarted = true

            val serviceInfo = rootClient.serviceInfo()
            diagnostics = diagnostics.copy(
                rootServiceOk = serviceInfo.ok && serviceInfo.uid == 0,
                rootUid = serviceInfo.uid,
                rootGid = serviceInfo.gid,
                rootPid = serviceInfo.pid,
                rootBackend = serviceInfo.backend,
                scannerVersion = serviceInfo.scanner,
                selinuxEnforcing = serviceInfo.selinuxEnforcing,
                selinuxContext = serviceInfo.selinuxContext,
                mountNamespace = serviceInfo.mountNamespace,
            )
            saveDiagnostics()
            if (!serviceInfo.ok || serviceInfo.uid != 0) {
                throw ScanStartFailure(
                    code = "ROOT_SERVICE_NOT_PRIVILEGED",
                    path = request.rootPath,
                    message = serviceInfo.message ?: "Root service is not running as UID 0",
                )
            }

            val preflight = rootClient.preflightScan(request)
            diagnostics = diagnostics.copy(
                preflightOk = preflight.ok,
                canonicalRoot = preflight.canonicalPath,
                rootOpened = preflight.opened,
                rootHasChildren = preflight.hasChildren,
                mountPoint = preflight.mountPoint,
                filesystem = preflight.filesystem,
                mountOptions = preflight.mountOptions,
            )
            saveDiagnostics()
            if (!preflight.ok) {
                throw ScanStartFailure(preflight.code, preflight.requestedPath, preflight.message, preflight.errno)
            }

            val streamDirectory = File(filesDir, "scan-streams")
            check(streamDirectory.exists() || streamDirectory.mkdirs()) { "Unable to create scanner stream directory" }
            val stream = File(streamDirectory, "${request.scanId}.ndjson")
            if (stream.exists()) check(stream.delete()) { "Unable to replace previous scanner stream" }
            check(stream.createNewFile()) { "Unable to create scanner stream" }

            val rootState = ParcelFileDescriptor.open(
                stream,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { descriptor -> rootClient.startScan(request, descriptor) }
            if (!rootState.ok) {
                throw ScanStartFailure(
                    code = rootState.code ?: "ROOT_SCANNER_REJECTED",
                    path = rootState.path ?: request.rootPath,
                    message = rootState.message ?: rootState.error ?: "Root scanner rejected the request",
                    errno = rootState.errno,
                )
            }

            session = session.copy(status = ScanStatus.RUNNING.name)
            domainStore.upsertSession(session)
            val pending = ArrayList<ScanEntryEntity>(ENTRY_BATCH_SIZE)
            var persistedEntries = 0L
            var terminalEvent = false
            var emptyAfterTerminal = 0

            NdjsonTailReader(stream).use { reader ->
                while (true) {
                    val lines = reader.readAvailable()
                    for (line in lines) {
                        val eventResult = runCatching { ScanEventParser.parse(line) }
                        val event = eventResult.getOrNull()
                        if (event == null) {
                            session = session.copy(warningCount = session.warningCount + 1)
                            issue(
                                code = "MALFORMED_SCANNER_EVENT",
                                severity = ScanDiagnosticSeverity.WARNING,
                                phase = "event-parser",
                                message = eventResult.exceptionOrNull()?.message ?: "Unable to parse scanner event",
                                path = null,
                            )
                            continue
                        }
                        when (event) {
                            is ScanEvent.Start -> session = session.copy(
                                backend = event.backend,
                                status = ScanStatus.RUNNING.name,
                            )
                            is ScanEvent.Entry -> {
                                pending += ScanEntryEntity(
                                    scanId = request.scanId,
                                    path = event.path,
                                    parentPath = event.parentPath,
                                    name = event.name,
                                    sizeBytes = event.sizeBytes,
                                    allocatedBytes = event.allocatedBytes,
                                    isDirectory = event.directory,
                                    depth = event.depth,
                                    modifiedAt = event.modifiedAt,
                                    fileCount = event.fileCount,
                                )
                                if (pending.size >= ENTRY_BATCH_SIZE) persistedEntries += flush(pending)
                            }
                            is ScanEvent.Progress -> session = session.copy(
                                scannedItems = event.scannedItems,
                                scannedBytes = event.scannedBytes,
                                currentPath = event.currentPath,
                            )
                            is ScanEvent.Warning -> {
                                session = session.copy(
                                    warningCount = session.warningCount + 1,
                                    currentPath = event.path,
                                )
                                issue(
                                    code = event.code,
                                    severity = ScanDiagnosticSeverity.WARNING,
                                    phase = event.phase,
                                    message = event.message,
                                    path = event.path,
                                    errno = event.errno,
                                )
                            }
                            is ScanEvent.Complete -> {
                                terminalEvent = true
                                diagnostics = diagnostics.copy(
                                    rootOpened = event.rootOpened,
                                    emittedEntries = event.emittedEntries,
                                    emittedFiles = event.emittedFiles,
                                    emittedDirectories = event.emittedDirectories,
                                    truncated = event.truncated,
                                    depthLimitedDirectories = event.depthLimitedDirectories,
                                    skippedFilesystemEntries = event.skippedFilesystemEntries,
                                    completedAt = System.currentTimeMillis(),
                                )
                                if (event.truncated) issue(
                                    code = "RESULT_LIMIT_REACHED",
                                    severity = ScanDiagnosticSeverity.WARNING,
                                    phase = "completion",
                                    message = "The scanner reached the ${request.maxEntries}-entry result limit; some entries were not indexed.",
                                )
                                if (event.depthLimitedDirectories > 0) issue(
                                    code = "DEPTH_LIMIT_REACHED",
                                    severity = ScanDiagnosticSeverity.INFO,
                                    phase = "completion",
                                    message = "${event.depthLimitedDirectories} director${if (event.depthLimitedDirectories == 1L) "y" else "ies"} reached a depth boundary; this is informational unless paired with truncation or zero-file evidence.",
                                )
                                if (event.skippedFilesystemEntries > 0) issue(
                                    code = "FILESYSTEM_BOUNDARIES_SKIPPED",
                                    severity = ScanDiagnosticSeverity.INFO,
                                    phase = "completion",
                                    message = "${event.skippedFilesystemEntries} entr${if (event.skippedFilesystemEntries == 1L) "y was" else "ies were"} skipped at filesystem boundaries.",
                                )
                                if (event.emittedFiles == 0L) {
                                    val reason = when {
                                        diagnostics.rootHasChildren == false -> "The scan root opened successfully and appears empty."
                                        request.mode == EntryMode.DIRECTORIES -> "This scan was configured to emit directories only, so the file count is zero."
                                        request.minSize > 0L -> "The root opened successfully, but no files matched the minimum-size filter."
                                        request.maxDepth == 0 -> "The root opened successfully, but maximum depth is zero."
                                        else -> "The root opened successfully, but no file records were emitted. Review hidden-file, exclusion, result-limit, and depth-policy settings."
                                    }
                                    issue(
                                        code = "ZERO_FILES_EMITTED",
                                        severity = if (diagnostics.rootHasChildren == true) ScanDiagnosticSeverity.WARNING else ScanDiagnosticSeverity.INFO,
                                        phase = "completion",
                                        message = reason,
                                    )
                                }
                                saveDiagnostics()
                                session = session.copy(
                                    status = ScanStatus.COMPLETED.name,
                                    completedAt = diagnostics.completedAt,
                                    totalBytes = event.totalBytes,
                                    scannedItems = event.scannedItems,
                                    scannedBytes = event.scannedBytes,
                                    warningCount = maxOf(session.warningCount, event.warnings),
                                    observedEntryCount = event.emittedEntries,
                                    persistedEntryCount = persistedEntries + pending.size,
                                    truncated = event.truncated,
                                    integrityStatus = if (event.truncated) ScanIntegrityStatus.TRUNCATED.name else ScanIntegrityStatus.COMPLETE.name,
                                    currentPath = "",
                                )
                            }
                            is ScanEvent.Cancelled -> {
                                terminalEvent = true
                                diagnostics = diagnostics.copy(completedAt = System.currentTimeMillis())
                                saveDiagnostics()
                                session = session.copy(
                                    status = ScanStatus.CANCELLED.name,
                                    completedAt = diagnostics.completedAt,
                                    scannedItems = event.scannedItems,
                                    scannedBytes = event.scannedBytes,
                                )
                            }
                            is ScanEvent.Error -> {
                                terminalEvent = true
                                diagnostics = diagnostics.copy(completedAt = System.currentTimeMillis())
                                issue(
                                    code = event.code,
                                    severity = ScanDiagnosticSeverity.ERROR,
                                    phase = event.phase,
                                    message = event.message,
                                    path = event.path.ifBlank { request.rootPath },
                                    errno = event.errno,
                                )
                                session = session.copy(
                                    status = ScanStatus.FAILED.name,
                                    completedAt = diagnostics.completedAt,
                                    errorMessage = diagnosticMessage(event.code, event.message, event.path, event.errno),
                                )
                            }
                        }
                    }
                    persistedEntries += flush(pending)
                    session = session.copy(persistedEntryCount = persistedEntries)
                    domainStore.upsertSession(session)
                    notifications.notify(NOTIFICATION_ID, notification(session, running = !terminalEvent))

                    val remote = rootClient.scanState(request.scanId)
                    if (terminalEvent || remote.terminal || !remote.ok) {
                        emptyAfterTerminal = if (lines.isEmpty()) emptyAfterTerminal + 1 else 0
                        if (emptyAfterTerminal >= 2) {
                            if (!terminalEvent) {
                                diagnostics = diagnostics.copy(completedAt = System.currentTimeMillis())
                                val status = if (remote.status == "cancelled") ScanStatus.CANCELLED else ScanStatus.FAILED
                                if (status == ScanStatus.FAILED) issue(
                                    code = remote.code ?: "MISSING_TERMINAL_EVENT",
                                    severity = ScanDiagnosticSeverity.ERROR,
                                    phase = "root-service",
                                    message = remote.error ?: remote.message ?: "Root scanner ended without a terminal event",
                                    path = remote.path ?: request.rootPath,
                                    errno = remote.errno,
                                )
                                session = session.copy(
                                    status = status.name,
                                    completedAt = diagnostics.completedAt,
                                    errorMessage = if (status == ScanStatus.FAILED) {
                                        diagnosticMessage(
                                            remote.code ?: "MISSING_TERMINAL_EVENT",
                                            remote.error ?: remote.message ?: "Root scanner ended without a terminal event",
                                            remote.path ?: request.rootPath,
                                            remote.errno,
                                        )
                                    } else null,
                                )
                                domainStore.upsertSession(session)
                                saveDiagnostics()
                            }
                            break
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching { rootClient.cancelScan(request.scanId) }
                diagnostics = diagnostics.copy(completedAt = System.currentTimeMillis())
                saveDiagnostics()
                session = session.copy(
                    status = ScanStatus.CANCELLED.name,
                    completedAt = diagnostics.completedAt,
                    integrityStatus = ScanIntegrityStatus.INTERRUPTED.name,
                )
                runCatching { domainStore.upsertSession(session) }
            }
            throw cancelled
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching { rootClient.cancelScan(request.scanId) }
                diagnostics = diagnostics.copy(completedAt = System.currentTimeMillis())
                val failure = error as? ScanStartFailure
                issue(
                    code = failure?.code ?: "SCAN_PIPELINE_FAILED",
                    severity = ScanDiagnosticSeverity.ERROR,
                    phase = if (failure != null) "preflight" else "scan-service",
                    message = error.message ?: error.javaClass.simpleName,
                    path = failure?.path ?: request.rootPath,
                    errno = failure?.errno,
                )
                session = session.copy(
                    status = ScanStatus.FAILED.name,
                    completedAt = diagnostics.completedAt,
                    errorMessage = diagnosticMessage(
                        failure?.code ?: "SCAN_PIPELINE_FAILED",
                        error.message ?: error.javaClass.simpleName,
                        failure?.path ?: request.rootPath,
                        failure?.errno,
                    ),
                    integrityStatus = ScanIntegrityStatus.FAILED_INTEGRITY.name,
                )
                runCatching { domainStore.upsertSession(session) }
                saveDiagnostics()
            }
        } finally {
            withContext(NonCancellable) {
                if (activeScanId == request.scanId) activeScanId = null
                if (session.status == ScanStatus.COMPLETED.name) runCatching { StorageAlertNotifier.evaluate(this@ScanForegroundService, domainStore) }
                if (foregroundStarted) finishNotification(session)
                stopSelfResult(startId)
            }
        }
    }

    private fun diagnosticMessage(code: String, message: String, path: String?, errno: Int?): String = buildString {
        append(code)
        append(": ")
        append(message)
        path?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
        errno?.let { append(" · errno ").append(it) }
    }

    private suspend fun flush(entries: MutableList<ScanEntryEntity>): Long {
        if (entries.isEmpty()) return 0L
        val copy = entries.toList()
        entries.clear()
        domainStore.upsertEntries(copy)
        return copy.size.toLong()
    }

    private fun notification(session: ScanSessionEntity, running: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_scan)
        .setContentTitle(if (running) getString(R.string.scanning_title) else getString(R.string.scan_finished_title))
        .setContentText(
            when {
                session.errorMessage != null -> session.errorMessage
                session.currentPath.isNotBlank() -> session.currentPath
                else -> "${session.scannedItems} items"
            },
        )
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                session.errorMessage ?: session.currentPath.ifBlank {
                    if (session.warningCount > 0) "${session.rootPath} · ${session.warningCount} warning(s)" else session.rootPath
                },
            ),
        )
        .setOngoing(running)
        .setOnlyAlertOnce(true)
        .setProgress(0, 0, running)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .apply {
            if (running) {
                addAction(
                    0,
                    getString(R.string.cancel),
                    PendingIntent.getService(
                        this@ScanForegroundService,
                        session.id.hashCode(),
                        Intent(this@ScanForegroundService, ScanForegroundService::class.java)
                            .setAction(ACTION_CANCEL)
                            .putExtra(EXTRA_SCAN_ID, session.id),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
        .build()

    private fun finishNotification(session: ScanSessionEntity) {
        stopForeground(STOP_FOREGROUND_DETACH)
        notifications.notify(NOTIFICATION_ID, notification(session, running = false))
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val scanId = activeScanId
        if (scanId != null) {
            scanJob?.cancel()
            scope.launch { runCatching { rootClient.cancelScan(scanId) } }
        }
        stopSelfResult(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private class ScanStartFailure(
        val code: String,
        val path: String?,
        message: String,
        val errno: Int? = null,
    ) : IllegalStateException(message)

    companion object {
        private const val CHANNEL_ID = "dustui_scans"
        private const val NOTIFICATION_ID = 7101
        private const val ENTRY_BATCH_SIZE = 128
        private const val POLL_INTERVAL_MS = 250L
        private const val ACTION_START = "com.mikeyphw.dustui.action.START_SCAN"
        private const val ACTION_CANCEL = "com.mikeyphw.dustui.action.CANCEL_SCAN"
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_SCAN_ID = "scanId"
        private const val EXTRA_FOREGROUND_WORKER = "foregroundWorker"

        fun start(context: Context, request: ScanRequest, launchedFromForegroundWorker: Boolean = false) {
            val intent = Intent(context, ScanForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_REQUEST, request.validated().toJson())
                .putExtra(EXTRA_FOREGROUND_WORKER, launchedFromForegroundWorker)
            if (launchedFromForegroundWorker) {
                context.startService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun cancel(context: Context, scanId: String) {
            context.startService(
                Intent(context, ScanForegroundService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_SCAN_ID, scanId),
            )
        }
    }
}
