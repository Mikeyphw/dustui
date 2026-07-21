package com.mikeyphw.dustui

import android.content.Context
import androidx.room.Room
import com.mikeyphw.dustui.data.DustDatabase
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.root.RootServiceClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: DustDatabase = Room.databaseBuilder(
        appContext,
        DustDatabase::class.java,
        "dustui.db",
    ).build()

    val rootServiceClient = RootServiceClient(appContext)
    val scanRepository = ScanRepository(appContext, database.scanDao(), rootServiceClient)
}
