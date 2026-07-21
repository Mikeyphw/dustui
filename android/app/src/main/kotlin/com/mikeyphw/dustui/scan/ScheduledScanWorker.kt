package com.mikeyphw.dustui.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ScheduledScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_REQUEST, null) ?: return Result.success()
        return runCatching {
            ScanForegroundService.start(applicationContext, ScanRequest.fromJson(raw).copy(scanId = java.util.UUID.randomUUID().toString()))
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val PREFS = "dustui_schedule"
        private const val KEY_REQUEST = "request"
        private const val WORK = "dustui_periodic_scan"
        fun set(context: Context, request: ScanRequest, hours: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_REQUEST, request.toJson()).apply()
            val work = PeriodicWorkRequestBuilder<ScheduledScanWorker>(hours.coerceAtLeast(15), TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, work)
        }
        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
}
