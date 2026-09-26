package com.mikeyphw.dustui

import android.content.Context
import com.mikeyphw.dustui.data.GoDomainStore
import com.mikeyphw.dustui.data.ScanDiagnosticStore
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.root.RootServiceClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val goDomainStore = GoDomainStore(appContext)
    val rootServiceClient = RootServiceClient(appContext)
    val scanDiagnosticStore = ScanDiagnosticStore(appContext)
    val scanRepository = ScanRepository(appContext, goDomainStore, rootServiceClient, scanDiagnosticStore)
}
