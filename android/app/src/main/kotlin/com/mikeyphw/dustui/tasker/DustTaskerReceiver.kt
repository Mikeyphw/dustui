package com.mikeyphw.dustui.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.mikeyphw.dustui.scan.ScanForegroundService
import com.mikeyphw.dustui.scan.ScanRequest

/** Locale/Tasker-compatible, read-only automation entry point. */
class DustTaskerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE_SETTING) return
        val plugin = intent.getBundleExtra(EXTRA_BUNDLE) ?: Bundle.EMPTY
        when (plugin.getString(KEY_ACTION, ACTION_SCAN)) {
            ACTION_SCAN -> {
                val request = runCatching {
                    ScanRequest(
                        rootPath = plugin.getString(KEY_PATH, DEFAULT_PATH).orEmpty().trim(),
                        maxDepth = plugin.getInt(KEY_DEPTH, DEFAULT_DEPTH).coerceIn(0, 64),
                        presetId = "tasker",
                    ).validated()
                }.getOrNull() ?: return
                ScanForegroundService.start(context, request)
            }
            ACTION_CANCEL -> plugin.getString(KEY_SCAN_ID)?.takeIf(String::isNotBlank)?.let {
                ScanForegroundService.cancel(context, it)
            }
        }
    }

    companion object {
        const val ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        const val EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
        const val KEY_ACTION = "dustui_action"
        const val KEY_PATH = "dustui_path"
        const val KEY_DEPTH = "dustui_depth"
        const val KEY_SCAN_ID = "dustui_scan_id"
        const val ACTION_SCAN = "scan"
        const val ACTION_CANCEL = "cancel"
        const val DEFAULT_PATH = "/storage/emulated/0"
        const val DEFAULT_DEPTH = 5
    }
}
