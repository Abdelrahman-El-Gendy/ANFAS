package com.anfas.core.data

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import com.anfas.core.model.FailureReason
import com.anfas.core.model.ReminderStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Everything here runs against Ktor's MockEngine standing in for the relay. **Nothing in this file
 * has spoken to Meta**: it proves how this app maps what a relay says, not that a real message is
 * ever delivered. That needs a Meta account and token (see CLAUDE.md, WhatsApp Phase 3).
 */
class RelayWhatsAppGatewayTest {

    private val relay = RelayConfig.of("https://relay.example.test/", TOKEN)
    private val requests = mutableListOf<HttpRequestData>()
    private val bodies = mutableListOf<String>()

    private fun gateway(
        config: RelayConfig? = relay,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = RelayWhatsAppGateway(
        client = HttpClient(
            MockEngine { request ->
                requests += request
                bodies += request.body.toByteArray().decodeToString()
                handler(request)
            },
        ),
        config = RelayConfigSource { config },
    )

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @AfterTest
    fun restoreLogging() {
        Logger.setLogWriters(co.touchlab.kermit.platformLogWriter())
    }

    // ---- the request ----

    @Test
    fun `a message is posted to the relay with the credential and the idempotency key`() = runTest {
        val gateway = gateway { json(HttpStatusCode.OK, """{"providerMessageId":"wamid.9"}""") }

        val result = gateway.send(message())

        assertEquals(GatewayResult.Accepted("wamid.9"), result)
        val request = requests.single()
        assertEquals("https://relay.example.test/v1/whatsapp/send", request.url.toString())
        assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
        val body = bodies.single()
        assertTrue("\"idempotencyKey\":\"renewal:t-1\"" in body, body)
        assertTrue("\"to\":\"201001112222\"" in body, body)
        assertTrue("\"template\":\"reminder_ar\"" in body, body)
        assertTrue("\"language\":\"ar\"" in body, body)
        assertTrue("\"parameters\":[\"Mona\"]" in body, body)
    }

    /** A retried HTTP call must present the same identity, or the relay cannot deduplicate it. */
    @Test
    fun `a retry carries the same idempotency key`() = runTest {
        var calls = 0
        val gateway = gateway {
            if (calls++ == 0) {
                json(HttpStatusCode.BadGateway, "{}")
            } else {
                json(HttpStatusCode.OK, "{}")
            }
        }

        assertIs<GatewayResult.Unreachable>(gateway.send(message()))
        assertIs<GatewayResult.Accepted>(gateway.send(message()))

        assertEquals(2, bodies.size)
        assertTrue(bodies.all { "\"idempotencyKey\":\"renewal:t-1\"" in it })
    }

    // ---- the response, one status per assertion ----

    @Test
    fun `a 2xx with an unreadable body is still accepted`() = runTest {
        val gateway = gateway { json(HttpStatusCode.OK, "not json") }

        assertEquals(GatewayResult.Accepted(null), gateway.send(message()))
    }

    @Test
    fun `a 422 is a rejection carrying the providers own code and text`() = runTest {
        val gateway = gateway {
            json(
                HttpStatusCode.UnprocessableEntity,
                """{"providerCode":131047,"detail":"Re-engagement message"}""",
            )
        }

        val result = gateway.send(message())

        assertEquals(GatewayResult.Rejected(131_047, "Re-engagement message"), result)
        assertEquals(
            FailureReason.NOT_OPTED_IN,
            failureReasonFor((result as GatewayResult.Rejected).providerCode),
        )
    }

    @Test
    fun `a 429 is a rate limit even when the body carries no code`() = runTest {
        val gateway = gateway { json(HttpStatusCode.TooManyRequests, "{}") }

        val result = assertIs<GatewayResult.Rejected>(gateway.send(message()))

        assertEquals(130_429, result.providerCode)
        assertEquals(FailureReason.RATE_LIMITED, failureReasonFor(result.providerCode))
    }

    @Test
    fun `a revoked credential is a rejection that is not the providers`() = runTest {
        val gateway = gateway { json(HttpStatusCode.Unauthorized, "{}") }

        val result = assertIs<GatewayResult.Rejected>(gateway.send(message()))

        assertNull(result.providerCode, "this refusal came from the relay, not from Meta")
    }

    @Test
    fun `a wrong relay address is a rejection rather than an unknown outcome`() = runTest {
        val gateway = gateway { json(HttpStatusCode.NotFound, "") }

        assertIs<GatewayResult.Rejected>(gateway.send(message()))
    }

    /**
     * The distinction the whole result type exists for: a rejection is a decision, an unreachable
     * relay means we do not know whether the message went.
     */
    @Test
    fun `server errors are unreachable and never rejected`() = runTest {
        for (status in listOf(500, 502, 503, 504, 408)) {
            val gateway = gateway { json(HttpStatusCode.fromValue(status), "{}") }

            val result = gateway.send(message())

            assertIs<GatewayResult.Unreachable>(result, "HTTP $status must be an unknown outcome")
            assertFalse(result is GatewayResult.Rejected)
        }
    }

    @Test
    fun `a transport failure is unreachable`() = runTest {
        val gateway = gateway { throw RuntimeException("connection reset by $TOKEN") }

        val result = assertIs<GatewayResult.Unreachable>(gateway.send(message()))

        assertTrue("may or may not have been sent" in result.detail.orEmpty())
        // The exception's own text is kept out of what is stored and shown: it can quote the URL.
        assertFalse(TOKEN in result.detail.orEmpty())
    }

    // ---- configuration ----

    @Test
    fun `no relay configured means not configured and nothing is sent`() = runTest {
        val gateway = gateway(config = null) { json(HttpStatusCode.OK, "{}") }

        assertFalse(gateway.isConfigured)
        assertIs<GatewayResult.Unreachable>(gateway.send(message()))
        assertTrue(requests.isEmpty(), "an unconfigured gateway must not touch the network")
    }

    @Test
    fun `a configured relay is configured`() {
        assertTrue(gateway { json(HttpStatusCode.OK, "{}") }.isConfigured)
    }

    @Test
    fun `a relay credential only travels over https or to this machine`() {
        assertNotNull(RelayConfig.of("https://relay.example", "t"))
        assertNotNull(RelayConfig.of("http://localhost:8080", "t"))
        assertNotNull(RelayConfig.of("http://127.0.0.1:8080/", "t"))
        assertNull(RelayConfig.of("http://relay.example", "t"), "cleartext to a remote host")
        assertNull(RelayConfig.of("https://relay.example", "  "))
        assertNull(RelayConfig.of("", "t"))
        assertNull(RelayConfig.of(null, null))
    }

    @Test
    fun `the config never prints its credential`() {
        assertFalse(TOKEN in relay.toString())
    }

    // ---- the whole path: sender over the gateway over a relay ----

    @Test
    fun `a rate limited relay stops the run and leaves the rest queued`() = runTest {
        val gateway = gateway { json(HttpStatusCode.TooManyRequests, "{}") }
        val dao =
            FakeReminderDao(
                listOf(queued("renewal:t-1"), queued("renewal:t-2"), queued("renewal:t-3")),
            )

        val outcome = DefaultReminderSender(
            dao,
            gateway,
            UnconfinedDispatchers,
        ).runQueue().valueOrFail()

        assertTrue(outcome.stoppedEarly)
        assertEquals(1, requests.size, "the throttle should have ended the run after one call")
        assertEquals(2, dao.current.count { it.status == ReminderStatus.QUEUED.name })
    }

    @Test
    fun `an unreachable relay and a rejecting relay leave different records`() = runTest {
        val unreachableDao = FakeReminderDao(listOf(queued("renewal:t-1")))
        DefaultReminderSender(
            unreachableDao,
            gateway { json(HttpStatusCode.ServiceUnavailable, "{}") },
            UnconfinedDispatchers,
        ).runQueue().valueOrFail()

        val rejectedDao = FakeReminderDao(listOf(queued("renewal:t-1")))
        DefaultReminderSender(
            rejectedDao,
            gateway {
                json(
                    HttpStatusCode.UnprocessableEntity,
                    """{"providerCode":131026,"detail":"Undeliverable"}""",
                )
            },
            UnconfinedDispatchers,
        ).runQueue().valueOrFail()

        val unreachable = unreachableDao.current.single()
        val rejected = rejectedDao.current.single()
        assertNull(
            unreachable.failureProviderCode,
            "no provider spoke, so there is no code to show",
        )
        assertEquals(FailureReason.UNKNOWN.name, unreachable.failureReason)
        assertTrue("may or may not have been sent" in unreachable.failureDetail.orEmpty())
        assertEquals(131_026, rejected.failureProviderCode)
        assertEquals(FailureReason.INVALID_PHONE_NUMBER.name, rejected.failureReason)
    }

    @Test
    fun `an unconfigured relay gateway runs nothing and spends no attempts`() = runTest {
        val gateway = gateway(config = null) { json(HttpStatusCode.OK, "{}") }
        val dao = FakeReminderDao(listOf(queued("renewal:t-1")))

        DefaultReminderSender(dao, gateway, UnconfinedDispatchers).runQueue().valueOrFail()

        assertEquals(0, dao.current.single().attempts)
        assertTrue(requests.isEmpty())
    }

    // ---- secrets and PII never reach a log ----

    @Test
    fun `nothing sensitive is logged on any path`() = runTest {
        val lines = mutableListOf<String>()
        Logger.setLogWriters(
            object : LogWriter() {
                override fun log(
                    severity: Severity,
                    message: String,
                    tag: String,
                    throwable: Throwable?,
                ) {
                    lines += message
                    throwable?.let { lines += it.message.orEmpty() }
                }
            },
        )
        val paths: List<
            suspend MockRequestHandleScope.(
                HttpRequestData,
            ) -> HttpResponseData,
            > = listOf(
            { json(HttpStatusCode.OK, """{"providerMessageId":"wamid.9"}""") },
            {
                json(
                    HttpStatusCode.UnprocessableEntity,
                    """{"providerCode":131026,"detail":"201001112222 is not on WhatsApp"}""",
                )
            },
            { json(HttpStatusCode.TooManyRequests, "{}") },
            { json(HttpStatusCode.Unauthorized, "{}") },
            { json(HttpStatusCode.BadGateway, "{}") },
            {
                throw RuntimeException(
                    "reset talking to relay.example.test with $TOKEN for 201001112222",
                )
            },
        )

        for (path in paths) gateway(handler = path).send(message())

        assertTrue(lines.isNotEmpty(), "the gateway should log its outcome")
        for (line in lines) {
            assertFalse(TOKEN in line, "credential logged: $line")
            assertFalse("201001112222" in line, "phone number logged: $line")
            assertFalse("Mona" in line, "member name logged: $line")
            assertFalse("relay.example.test" in line, "exception text logged: $line")
        }
    }

    private fun message() = TemplateMessage(
        idempotencyKey = "renewal:t-1",
        toE164 = "201001112222",
        templateName = "reminder_ar",
        languageCode = "ar",
        parameters = listOf("Mona"),
    )

    private fun queued(id: String) = reminderEntity(
        id = id,
        phone = "01001112222",
        status = ReminderStatus.QUEUED.name,
        attempts = 0,
        failureReason = null,
    )

    private companion object {
        const val TOKEN = "SECRET-DEVICE-TOKEN-xyz"
    }
}
