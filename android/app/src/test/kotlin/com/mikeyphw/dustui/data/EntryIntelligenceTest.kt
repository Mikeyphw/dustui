package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryIntelligenceTest {
    @Test fun extractsPrivateDataPackage() {
        assertEquals("com.example.app", EntryIntelligenceResolver.packageFromPath("/data/user/0/com.example.app/cache/a"))
    }
    @Test fun extractsExternalPackage() {
        assertEquals("com.example.app", EntryIntelligenceResolver.packageFromPath("/storage/emulated/0/Android/data/com.example.app/files"))
    }
    @Test fun leavesOrdinaryPathUnowned() {
        assertNull(EntryIntelligenceResolver.packageFromPath("/storage/emulated/0/Download/archive.zip"))
    }
    @Test fun classifiesRootModule() {
        assertEquals(StorageCategory.ROOT_MODULE, EntryIntelligenceResolver.classify("/data/adb/modules/example/system.prop"))
    }
    @Test fun classifiesDatabaseBeforeGenericAppData() {
        assertEquals(StorageCategory.DATABASE, EntryIntelligenceResolver.classify("/data/user/0/com.example.app/databases/main.db"))
    }
}
