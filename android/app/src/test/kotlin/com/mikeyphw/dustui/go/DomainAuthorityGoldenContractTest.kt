package com.mikeyphw.dustui.go

import com.mikeyphw.dustui.data.GoDomainStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainAuthorityGoldenContractTest {
    private fun fixture(): JSONObject {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "migration/contracts/v2/fixtures/domain_authority_golden_v1.json").isFile }
        return JSONObject(File(root, "migration/contracts/v2/fixtures/domain_authority_golden_v1.json").readText())
    }

    @Test fun sharedAuthorityFixtureMatchesAndroidProtocol() {
        val fixture = fixture()
        val request = fixture.getJSONObject("request")
        val authority = fixture.getJSONObject("responseAuthority")
        assertEquals(GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA, request.getString("schema"))
        assertEquals(GoDomainStore.DOMAIN_AUTHORITY_RESPONSE_SCHEMA, authority.getString("schema"))
        assertEquals(request.getString("domain"), authority.getString("domain"))
        assertEquals(request.getString("scanId"), authority.getString("scanId"))
        assertEquals(request.getLong("generation"), authority.getLong("generation"))
        assertTrue(authority.getBoolean("bounded"))
        assertTrue(authority.getBoolean("durable"))
        assertEquals("partial", authority.getString("evidenceState"))
        val oldSchemas = fixture.getJSONArray("rejectedRequestSchemas")
        assertTrue(oldSchemas.length() > 0)
        for (index in 0 until oldSchemas.length()) {
            assertFalse(oldSchemas.getString(index) == GoDomainStore.DOMAIN_AUTHORITY_REQUEST_SCHEMA)
        }
    }
}
