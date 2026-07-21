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
import com.mikeyphw.dustui.data.ScanEntryEntity
import com.mikeyphw.dustui.data.ScanSessionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class ScanForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null
    @Volatile private var activeScanId: String? = null
    private val container by lazy { (application as DustUiApplication).container }
    private val dao by lazy { container.database.scanDao() }
    private val rootClient by lazy { container.rootServiceClient }
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
                if (scanJob?.isActive == true) return START_NOT_STICKY
                val raw = intent.getStringExtra(EXTRA_REQUEST) ?: return START_NOT_STICKY
                val request = runCatching { ScanRequest.fromJson(raw) }.getOrElse {
                    stopSelf()
                    return START_NOT_STICKY
                }
                activeScanId = request.scanId
                scanJob = scope.launch { execute(request) }
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

    private suspend fun execute(request: ScanRequest) {
        var session = ScanSessionEntity(
            id = request.scanId,
            presetId = request.presetId,
            rootPath = request.rootPath,
            optionsJson = request.toJson(),
            status = ScanStatus.STARTING.name,
            startedAt = System.currentTimeMillis(),
        )
        var foregroundStarted = false

        try {
            dao.deleteEntries(request.scanId)
            dao.upsertSession(session)
            startForeground(NOTIFICATION_ID, notification(session, running = true))
            foregroundStarted = true

            val streamDirectory = File(filesDir, "scan-streams")
            check(streamDirectory.exists() || streamDirectory.mkdirs()) { "Unable to create scanner stream directory" }
            val stream = File(streamDirectory, "${request.scanId}.ndjson")
            if (stream.exists()) check(stream.delete()) { "Unable to replace previous scanner stream" }
            check(stream.createNewFile()) { "Unable to create scanner stream" }

            val rootState = ParcelFileDescriptor.open(
                stream,
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE,
            ).use { descriptor -> rootClient.startScan(request, descriptor) }
            check(rootState.ok) {
                rootState.message ?: rootState.error ?: "Root scanner rejected the request"
            }

            session = session.copy(status = ScanStatus.RUNNING.name)
            dao.upsertSession(session)
            val pending = ArrayList<ScanEntryEntity>(ENTRY_BATCH_SIZE)
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
                                if (pending.size >= ENTRY_BATCH_SIZE) flush(pending)
                            }
                            is ScanEvent.Progress -> session = session.copy(
                                scannedItems = event.scannedItems,
                                scannedBytes = event.scannedBytes,
                                currentPath = event.currentPath,
                            )
                            is ScanEvent.Warning -> session = session.copy(
                                warningCount = session.warningCount + 1,
                                currentPath = event.path,
                            )
                            is ScanEvent.Complete -> {
                                terminalEvent = true
                                session = session.copy(
                                    status = ScanStatus.COMPLETED.name,
                                    completedAt = System.currentTimeMillis(),
                                    totalBytes = event.totalBytes,
                                    scannedItems = event.scannedItems,
                                    scannedBytes = event.scannedBytes,
                                    warningCount = event.warnings,
                                    currentPath = "",
                                )
                            }
                            is ScanEvent.Cancelled -> {
                                terminalEvent = true
                                session = session.copy(
                                    status = ScanStatus.CANCELLED.name,
                                    completedAt = System.currentTimeMillis(),
                                    scannedItems = event.scannedItems,
                                    scannedBytes = event.scannedBytes,
                                )
                            }
                            is ScanEvent.Error -> {
                                terminalEvent = true
                                session = session.copy(
                                    status = ScanStatus.FAILED.name,
                                    completedAt = System.currentTimeMillis(),
                                    errorMessage = event.message,
                                )
                            }
                        }
                    }
                    flush(pending)
                    dao.upsertSession(session)
                    notifications.notify(NOTIFICATION_ID, notification(session, running = !terminalEvent))

                    val remote = rootClient.scanState(request.scanId)
                    if (terminalEvent || remote.terminal || !remote.ok) {
                        emptyAfterTerminal = if (lines.isEmpty()) emptyAfterTerminal + 1 else 0
                        if (emptyAfterTerminal >= 2) {
                            if (!terminalEvent) {
                                session = session.copy(
                                    status = when (remote.status) {
                                        "cancelled" -> ScanStatus.CANCELLED.name
                                        "completed" -> ScanStatus.COMPLETED.name
                                        else -> ScanStatus.FAILED.name
                                    },
                                    completedAt = System.currentTimeMillis(),
                                    errorMessage = remote.error ?: remote.message,
                                )
                                dao.upsertSession(session)
                            }
                            break
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
        } catch (cancelled: CancellationException) {
            runCatching { rootClient.cancelScan(request.scanId) }
            session = session.copy(
                status = ScanStatus.CANCELLED.name,
                completedAt = System.currentTimeMillis(),
            )
            runCatching { dao.upsertSession(session) }
            throw cancelled
        } catch (error: Throwable) {
            runCatching { rootClient.cancelScan(request.scanId) }
            session = session.copy(
                status = ScanStatus.FAILED.name,
                completedAt = System.currentTimeMillis(),
                errorMessage = error.message ?: error.javaClass.simpleName,
            )
            runCatching { dao.upsertSession(session) }
        } finally {
            activeScanId = null
            if (foregroundStarted) finishNotification(session)
            stopSelf()
        }
    }

    private suspend fun flush(entries: MutableList<ScanEntryEntity>) {
        if (entries.isEmpty()) return
        val copy = entries.toList()
        entries.clear()
        dao.upsertEntries(copy)
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
        .setStyle(NotificationCompat.BigTextStyle().bigText(session.currentPath.ifBlank { session.rootPath }))
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "dustui_scans"
        private const val NOTIFICATION_ID = 7101
        private const val ENTRY_BATCH_SIZE = 128
        private const val POLL_INTERVAL_MS = 250L
        private const val ACTION_START = "com.mikeyphw.dustui.action.START_SCAN"
        private const val ACTION_CANCEL = "com.mikeyphw.dustui.action.CANCEL_SCAN"
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_SCAN_ID = "scanId"

        fun start(context: Context, request: ScanRequest) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScanForegroundService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_REQUEST, request.validated().toJson()),
            )
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
