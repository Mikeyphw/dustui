package com.mikeyphw.dustui.data

import android.app.KeyguardManager
import android.content.Context

/** Blocks destructive operations while Android reports the device as locked. */
class CleanupAuthorization(context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    fun authorize(): Result<Unit> = if (keyguard?.isDeviceLocked == true) {
        Result.failure(SecurityException("Unlock the device before permanent deletion"))
    } else Result.success(Unit)
}
