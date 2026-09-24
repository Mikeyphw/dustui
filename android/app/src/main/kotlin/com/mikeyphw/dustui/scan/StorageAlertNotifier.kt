package com.mikeyphw.dustui.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.mikeyphw.dustui.R
import com.mikeyphw.dustui.data.GoDomainStore
import kotlin.math.abs
import java.util.Locale

object StorageAlertNotifier {
    private const val CHANNEL = "dustui_storage_alerts"
    suspend fun evaluate(context: Context, domainStore: GoDomainStore) {
        val sessions = domainStore.completedSessions(2)
        val current = sessions.firstOrNull() ?: return
        val previous = sessions.getOrNull(1)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Storage alerts", NotificationManager.IMPORTANCE_DEFAULT))

        val stat = runCatching { StatFs(current.rootPath) }.getOrNull()
        if (stat != null && stat.totalBytes > 0 && stat.availableBytes * 100 / stat.totalBytes < 10) {
            manager.notify(7201, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("Storage is running low")
                .setContentText("Less than 10% free on ${current.rootPath}")
                .setAutoCancel(true).build())
        }
        if (previous != null && current.rootPath == previous.rootPath) {
            val delta = current.totalBytes - previous.totalBytes
            if (delta > 512L * 1024 * 1024) {
                manager.notify(7202, NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_scan)
                    .setContentTitle("Storage grew since the last scan")
                    .setContentText("${formatDelta(delta)} added under ${current.rootPath}")
                    .setAutoCancel(true).build())
            }
        }
    }

    private fun formatDelta(bytes: Long): String {
        val mib = abs(bytes) / (1024 * 1024)
        return if (mib >= 1024) String.format(Locale.US, "%.1f GiB", mib / 1024.0) else "$mib MiB"
    }
}
