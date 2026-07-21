package com.mikeyphw.dustui

import android.app.Application
import com.topjohnwu.superuser.Shell

class DustUiApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = BuildConfig.DEBUG
    }
}
