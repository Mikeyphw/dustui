package com.mikeyphw.dustui.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mikeyphw.dustui.MainActivity
import com.mikeyphw.dustui.R
import java.util.UUID
import java.util.concurrent.TimeUnit

class ScheduledScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REQUEST, null) ?: return Result.success(
            workDataOf(KEY_SCAN_STATUS to STATUS_NO_REQUEST),
        )
        return runCatching {
            val request = ScanRequest.fromJson(raw).copy(scanId = UUID.randomUUID().toString())
            setForeground(createForegroundInfo(request))
            // The worker owns foreground/background admission before delegating to the interactive
            // scan service. This prevents scheduled work from attempting an unqualified background
            // foreground-service launch. Its WorkManager success result means "launch admitted", not
            // terminal scan success; the scan itself remains owned by ScanForegroundService/Room.
            ScanForegroundService.start(applicationContext, request, launchedFromForegroundWorker = true)
            prefs.edit {
                putString(KEY_LAST_SCAN_ID, request.scanId)
                putString(KEY_LAST_OUTCOME, STATUS_LAUNCHED_NOT_TERMINAL)
            }
            Result.success(
                workDataOf(
                    KEY_SCAN_ID to request.scanId,
                    KEY_SCAN_STATUS to STATUS_LAUNCHED_NOT_TERMINAL,
                ),
            )
        }.getOrElse { _ ->
            prefs.edit { putString(KEY_LAST_OUTCOME, STATUS_RETRY_AFTER_FAILURE) }
            Result.retry()
        }
    }

    private fun createForegroundInfo(request: ScanRequest): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.scan_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(applicationContext.getString(R.string.scanning_title))
            .setContentText("Scheduled scan: ${request.rootPath}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val PREFS = "dustui_schedule"
        private const val KEY_REQUEST = "request"
        private const val KEY_LAST_SCAN_ID = "last_scan_id"
        private const val KEY_LAST_OUTCOME = "last_outcome"
        const val KEY_SCAN_ID = "scan_id"
        const val KEY_SCAN_STATUS = "scan_status"
        const val STATUS_NO_REQUEST = "NO_REQUEST"
        const val STATUS_LAUNCHED_NOT_TERMINAL = "LAUNCHED_NOT_TERMINAL"
        const val STATUS_RETRY_AFTER_FAILURE = "RETRY_AFTER_FAILURE"
        private const val WORK = "dustui_periodic_scan"
        private const val CHANNEL_ID = "dustui_scheduled_scans"
        private const val NOTIFICATION_ID = 7102
        fun set(context: Context, request: ScanRequest, hours: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_REQUEST, request.toJson()) }
            val work = PeriodicWorkRequestBuilder<ScheduledScanWorker>(hours.coerceAtLeast(15), TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, work)
        }
        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
