package com.mikeyphw.dustui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RootExplorerModelsTest {
    @Test fun `inspection parser retains root metadata`() {
        val parsed = RootPathInspection.fromJson("""{"ok":true,"path":"/data/test","type":"file","inode":42,"uid":1000,"gid":1000,"permissions":"rw-------","selinuxContext":"u:object_r:app_data_file:s0","bindMount":true,"preview":"hello"}""")
        assertTrue(parsed.ok)
        assertEquals(42L, parsed.inode)
        assertEquals("u:object_r:app_data_file:s0", parsed.selinuxContext)
        assertEquals("hello", parsed.preview)
        assertTrue(parsed.bindMount)
    }
}
