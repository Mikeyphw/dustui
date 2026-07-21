package com.mikeyphw.dustui

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.dustui.data.DustDatabase
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.root.RootServiceClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: DustDatabase = Room.databaseBuilder(
        appContext,
        DustDatabase::class.java,
        "dustui.db",
    ).addMigrations(MIGRATION_1_2).build()

    val rootServiceClient = RootServiceClient(appContext)
    val scanRepository = ScanRepository(appContext, database.scanDao(), rootServiceClient)

    private companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cleanup_operations` (`id` TEXT NOT NULL, `scanId` TEXT NOT NULL, `rootPath` TEXT NOT NULL, `status` TEXT NOT NULL, `selectedCount` INTEGER NOT NULL, `plannedBytes` INTEGER NOT NULL, `reclaimedBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `message` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cleanup_operations_createdAt` ON `cleanup_operations` (`createdAt`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quarantine_entries` (`id` TEXT NOT NULL, `operationId` TEXT NOT NULL, `originalPath` TEXT NOT NULL, `quarantinePath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `restoredAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quarantine_entries_operationId` ON `quarantine_entries` (`operationId`)")
            }
        }
    }
}
