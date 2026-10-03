package com.anfas.app.sync

import com.anfas.app.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ADMIN = "an-admin-secret-long-enough"

private fun entry(
    seq: Long,
    table: String = "members",
    row: String = "m$seq",
    op: String = "UPSERT",
) = PushEntry(
    clientSeq = seq,
    table = table,
    rowId = row,
    op = op,
    payload = if (op == "UPSERT") JsonObject(mapOf("n" to JsonPrimitive(seq))) else null,
    capturedAtEpochMs = 5L,
)

class SyncRoutesTest {

    private fun ApplicationTestBuilder.boot(
        adminSecret: String? = ADMIN,
    ): Pair<HttpClient, SqliteChangeStore> {
        val store = SqliteChangeStore("jdbc:sqlite::memory:")
        application { module(store, adminSecret) }
        val client =
            createClient { install(ContentNegotiation) { json(Json { encodeDefaults = true }) } }
        return client to store
    }

    private suspend fun HttpClient.register(label: String = "Front desk"): RegisterDeviceResponse =
        post("/admin/devices") {
            bearerAuth(ADMIN)
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(label))
        }.let { Json.decodeFromString(it.bodyAsText()) }

    private suspend fun HttpClient.push(token: String?, entries: List<PushEntry>): HttpResponse =
        post("/sync/push") {
            if (token != null) bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest(entries))
        }

    @Test
    fun pushWithoutCredentialIsRejected() = testApplication {
        val (client, _) = boot()
        assertEquals(HttpStatusCode.Unauthorized, client.push(null, listOf(entry(1))).status)
        assertEquals(HttpStatusCode.Unauthorized, client.push("guess", listOf(entry(1))).status)
    }

    @Test
    fun registeredDevicePushesAndReceivesAnAck() = testApplication {
        val (client, store) = boot()
        val device = client.register()

        val response = client.push(device.token, listOf(entry(1), entry(2)))

        assertEquals(HttpStatusCode.OK, response.status)
        val ack = Json.decodeFromString<PushResponse>(response.bodyAsText())
        assertEquals(PushResponse(acknowledgedThrough = 2, accepted = 2, duplicates = 0), ack)
        assertEquals(2, store.export(0, 10, latestOnly = false).size)
    }

    @Test
    fun retryingAPushIsSafe() = testApplication {
        val (client, store) = boot()
        val device = client.register()
        client.push(device.token, listOf(entry(1)))

        val retry = client.push(device.token, listOf(entry(1)))

        assertEquals(HttpStatusCode.OK, retry.status)
        assertEquals(1, Json.decodeFromString<PushResponse>(retry.bodyAsText()).duplicates)
        assertEquals(1, store.export(0, 10, latestOnly = false).size)
    }

    @Test
    fun restartedOutboxNumberingIsAConflict() = testApplication {
        val (client, store) = boot()
        val device = client.register()
        client.push(device.token, listOf(entry(1, row = "m1")))

        val response = client.push(device.token, listOf(entry(1, row = "different")))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(1, store.export(0, 10, latestOnly = false).size)
    }

    @Test
    fun revokedDeviceCanNoLongerPush() = testApplication {
        val (client, _) = boot()
        val device = client.register()
        val revoke = client.post("/admin/devices/${device.deviceId}/revoke") { bearerAuth(ADMIN) }
        assertEquals(HttpStatusCode.NoContent, revoke.status)

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.push(device.token, listOf(entry(1))).status,
        )
    }

    @Test
    fun unsyncedTablesAreRefusedAndNothingIsWritten() = testApplication {
        val (client, store) = boot()
        val device = client.register()

        for (table in listOf("reminders", "staff", "sync_outbox", "sync_tombstones", "nonsense")) {
            val response = client.push(
                device.token,
                listOf(entry(1, row = "ok"), entry(2, table = table)),
            )
            assertEquals(HttpStatusCode.BadRequest, response.status, table)
        }
        // The valid first entry was in every one of those requests; none of it may have landed.
        assertEquals(0, store.export(0, 10, latestOnly = false).size)
    }

    @Test
    fun malformedBodyIsABadRequestNotAServerError() = testApplication {
        val (client, _) = boot()
        val device = client.register()

        val response = client.post("/sync/push") {
            bearerAuth(device.token)
            contentType(ContentType.Application.Json)
            setBody("""{"entries": "not a list"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun deviceCredentialCannotUseAdminRoutes() = testApplication {
        val (client, _) = boot()
        val device = client.register()

        val export = client.get("/admin/export") { bearerAuth(device.token) }
        val register = client.post("/admin/devices") {
            bearerAuth(device.token)
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest("evil"))
        }

        assertEquals(HttpStatusCode.Unauthorized, export.status)
        assertEquals(HttpStatusCode.Unauthorized, register.status)
    }

    @Test
    fun adminRoutesAreClosedWhenNoSecretIsConfigured() = testApplication {
        val (client, _) = boot(adminSecret = null)

        val response = client.post("/admin/devices") {
            bearerAuth(ADMIN)
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest("x"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun exportPagesThroughTheLogAndReportsHasMore() = testApplication {
        val (client, _) = boot()
        val device = client.register()
        client.push(device.token, (1L..5L).map { entry(it) })

        val page1 = Json.decodeFromString<ExportPage>(
            client.get("/admin/export?limit=2") { bearerAuth(ADMIN) }.bodyAsText(),
        )
        val page2 = Json.decodeFromString<ExportPage>(
            client.get("/admin/export?limit=10&after_seq=${page1.nextAfterSeq}") {
                bearerAuth(ADMIN)
            }.bodyAsText(),
        )

        assertEquals(2, page1.entries.size)
        assertTrue(page1.hasMore)
        assertEquals(3, page2.entries.size)
        assertFalse(page2.hasMore)
        assertEquals(5, (page1.entries + page2.entries).map { it.serverSeq }.distinct().size)
    }

    @Test
    fun exportLatestOnlyHidesDeletedRows() = testApplication {
        val (client, _) = boot()
        val device = client.register()
        client.push(
            device.token,
            listOf(entry(1, row = "m1"), entry(2, row = "m1", op = "DELETE"), entry(3, row = "m2")),
        )

        val page = Json.decodeFromString<ExportPage>(
            client.get("/admin/export?latest_only=true") { bearerAuth(ADMIN) }.bodyAsText(),
        )

        assertEquals(listOf("m2"), page.entries.map { it.rowId })
    }

    @Test
    fun tokenIsNeverEchoedByTheServerAfterRegistration() = testApplication {
        val (client, _) = boot()
        val device = client.register()
        client.push(device.token, listOf(entry(1)))

        val export = client.get("/admin/export") { bearerAuth(ADMIN) }.bodyAsText()

        assertFalse(device.token in export)
    }
}
