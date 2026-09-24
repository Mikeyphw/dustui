package com.mikeyphw.dustui

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.dustui.data.GoDomainStore
import com.mikeyphw.dustui.data.LegacyRoomV3Importer
import com.mikeyphw.dustui.data.ScanDiagnosticStore
import com.mikeyphw.dustui.data.ScanRepository
import com.mikeyphw.dustui.root.RootServiceClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val goDomainStore = GoDomainStore(appContext)
    val legacyRoomV3Importer = LegacyRoomV3Importer(appContext, goDomainStore)

    val rootServiceClient = RootServiceClient(appContext)
    val scanDiagnosticStore = ScanDiagnosticStore(appContext)
    val scanRepository = ScanRepository(appContext, goDomainStore, rootServiceClient, scanDiagnosticStore)

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `cleanup_operations` (`id` TEXT NOT NULL, `scanId` TEXT NOT NULL, `rootPath` TEXT NOT NULL, `status` TEXT NOT NULL, `selectedCount` INTEGER NOT NULL, `plannedBytes` INTEGER NOT NULL, `reclaimedBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `message` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cleanup_operations_scanId` ON `cleanup_operations` (`scanId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quarantine_entries` (`id` TEXT NOT NULL, `operationId` TEXT NOT NULL, `originalPath` TEXT NOT NULL, `quarantinePath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `restoredAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quarantine_entries_operationId` ON `quarantine_entries` (`operationId`)")
            }
        }


        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `generation` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `observedEntryCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `persistedEntryCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `malformedEventCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `truncated` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `scan_sessions` ADD COLUMN `integrityStatus` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("UPDATE `scan_sessions` SET `integrityStatus` = 'COMPLETE_WITH_WARNINGS' WHERE `status` = 'COMPLETED'")
                db.execSQL("UPDATE `scan_sessions` SET `integrityStatus` = 'INTERRUPTED' WHERE `status` IN ('STARTING', 'RUNNING', 'CANCELLING')")

                db.execSQL("ALTER TABLE `cleanup_operations` ADD COLUMN `reviewId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cleanup_operations` ADD COLUMN `scanGeneration` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `cleanup_operations` ADD COLUMN `planHash` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `cleanup_operations` ADD COLUMN `expiresAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `cleanup_operations` ADD COLUMN `outcomeCertainty` TEXT NOT NULL DEFAULT 'CERTAIN'")

                db.execSQL("CREATE TABLE IF NOT EXISTS `scan_entries_new` (`scanId` TEXT NOT NULL, `path` TEXT NOT NULL, `parentPath` TEXT NOT NULL, `name` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `allocatedBytes` INTEGER NOT NULL, `isDirectory` INTEGER NOT NULL, `depth` INTEGER NOT NULL, `modifiedAt` INTEGER NOT NULL, `fileCount` INTEGER NOT NULL, PRIMARY KEY(`scanId`, `path`), FOREIGN KEY(`scanId`) REFERENCES `scan_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO `scan_entries_new` (`scanId`, `path`, `parentPath`, `name`, `sizeBytes`, `allocatedBytes`, `isDirectory`, `depth`, `modifiedAt`, `fileCount`) SELECT `scanId`, `path`, `parentPath`, `name`, `sizeBytes`, `allocatedBytes`, `isDirectory`, `depth`, `modifiedAt`, `fileCount` FROM `scan_entries` WHERE `scanId` IN (SELECT `id` FROM `scan_sessions`)")
                db.execSQL("DROP TABLE `scan_entries`")
                db.execSQL("ALTER TABLE `scan_entries_new` RENAME TO `scan_entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_entries_scanId` ON `scan_entries` (`scanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_scan_entries_scanId_sizeBytes` ON `scan_entries` (`scanId`, `sizeBytes`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `cleanup_plan_items` (`operationId` TEXT NOT NULL, `path` TEXT NOT NULL, `role` TEXT NOT NULL, `itemType` TEXT NOT NULL, `plannedBytes` INTEGER NOT NULL, `expectedDevice` INTEGER, `expectedMountId` TEXT, `expectedInode` INTEGER, `expectedMode` INTEGER, `expectedStatSizeBytes` INTEGER, `expectedModifiedAt` INTEGER, `expectedTreeDigest` TEXT, `expectedEntryCount` INTEGER, `expectedSha256` TEXT, `retainedPath` TEXT, `retainedSha256` TEXT, `packageName` TEXT, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`operationId`, `path`), FOREIGN KEY(`operationId`) REFERENCES `cleanup_operations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cleanup_plan_items_operationId` ON `cleanup_plan_items` (`operationId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cleanup_plan_items_role` ON `cleanup_plan_items` (`role`)")
            }
        }
    }
}
