package com.mikeyphw.dustui.go

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** M05 release-only Go/SQLite test: never scans files, binds root, or executes cleanup. */
@RunWith(AndroidJUnit4::class)
class M05ReleaseReadOnlyInstrumentedTest {
    @Test
    fun productionGoRuntimeOpensAppPrivateSqliteAndVerifiesSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = File(context.noBackupFilesDir, "go-state/dustui-go.db")
        GoRuntimeProcess.start(context).use { process ->
            assertTrue("production Go child failed to start", process.isAlive)
            assertEquals("go-sqlite", process.readiness.optString("persistenceAuthority"))
            val request = GoRuntimeRequest(
                requestId = UUID.randomUUID().toString(),
                sequence = 1L,
                generation = 1L,
                type = "domain.schema.verify",
                payloadJson = "{}",
            )
            val result = process.request(request)
            assertEquals(request.requestId, result.requestId)
            val payload = JSONObject(result.payloadJson)
            assertTrue("Go SQLite schema verification rejected", payload.getBoolean("ok"))
            assertEquals(13, payload.getInt("schemaVersion"))
            assertTrue("app-owned SQLite database missing", db.isFile && db.length() > 0)
        }
    }
}
