package com.anfas.app

import com.anfas.app.sync.SqliteChangeStore
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun healthEndpointReportsOk() = testApplication {
        val store = SqliteChangeStore("jdbc:sqlite::memory:")
        application { module(store, adminSecret = null) }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(""""status":"ok"""" in body, "unexpected body: $body")
        assertTrue(""""service":"anfas-server"""" in body, "unexpected body: $body")
    }

    @Test
    fun shortAdminSecretIsTreatedAsUnset() {
        val config = ServerConfig.fromEnvironment(mapOf("ANFAS_ADMIN_SECRET" to "too-short"))
        assertNull(config.adminSecret)
    }

    @Test
    fun longEnoughAdminSecretIsKept() {
        val secret = "a-secret-of-sixteen+"
        val config = ServerConfig.fromEnvironment(mapOf("ANFAS_ADMIN_SECRET" to secret))
        assertEquals(secret, config.adminSecret)
    }
}
