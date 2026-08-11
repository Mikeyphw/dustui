package com.mikeyphw.dustui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScanSessionEntity::class,
        ScanEntryEntity::class,
        CleanupOperationEntity::class,
        CleanupPlanItemEntity::class,
        QuarantineEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class DustDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
