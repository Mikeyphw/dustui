package com.mikeyphw.dustui.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanSessionEntity::class, ScanEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DustDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
