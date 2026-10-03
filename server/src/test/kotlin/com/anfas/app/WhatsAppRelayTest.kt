package com.anfas.app

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.anfas.core.model.RELAY_SEND_PATH
import com.anfas.core.model.RelaySendRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Meta is a MockEngine here. **Nothing in this file has reached the real Graph API**, so it proves
 * the relay's authentication, mapping, deduplication and logging -- not that a message is ever
 * delivered to a phone. That needs a Meta account, an approved template and a token.
 */
private typealias MetaHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

class WhatsAppRelayTest {

    private val metaCalls = mutableListOf<HttpRequestData>()
    private val metaBodies = mutableListOf<String>()

    private fun relay(
        metaHandler: MetaHandler = {
            metaJson(HttpStatusCode.OK, """{"messages":[{"id":"wamid.42"}]}""")
        },
    ): WhatsAppRelay {
        val engine = MockEngine { request ->
            metaCalls += request
            metaBodies += request.body.toByteArray().decodeToString()
            metaHandler(request)
        }
        return WhatsAppRelay(
            deviceTokens = setOf(DEVICE_TOKEN, "second-device"),
            meta = GraphApiMetaClient(
                http = HttpClient(engine),
                accessToken = META_TOKEN,
                phoneNumberId = "1234567890",
                apiVersion = "v99.0",
                baseUrl = "https://graph.test",
            ),
        )
    }

    private fun MockRequestHandleScope.metaJson(status: HttpStatusCode, body: String) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private suspend fun ApplicationTestBuilder.send(
        request: RelaySendRequest = sendRequest(),
        token: String? = DEVICE_TOKEN,
    ): HttpResponse = client.post(RELAY_SEND_PATH) {
        if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(Json.encodeToString(request))
    }

    // ---- authentication ----

    @Test
    fun `an unconfigured relay answers 503 so the device reads it as unknown`() = testApplication {
        application { module(relay = null) }

        assertEquals(HttpStatusCode.ServiceUnavailable, send().status)
    }

    @Test
    fun `a bad device credential is refused and Meta is not called`() = testApplication {
        val relay = relay()
        application { module(relay) }

        assertEquals(HttpStatusCode.Unauthorized, send(token = null).status)
        assertEquals(HttpStatusCode.Unauthorized, send(token = "not-a-device").status)
        assertEquals(HttpStatusCode.Unauthorized, send(token = "").status)

        assertTrue(metaCalls.isEmpty(), "an unauthenticated caller reached Meta")
    }

    @Test
    fun `any configured device credential is accepted`() = testApplication {
        application { module(relay()) }

        assertEquals(HttpStatusCode.OK, send(token = "second-device").status)
    }

    @Test
    fun `a relay with no credentials refuses to exist`() {
        val failure = runCatching { WhatsAppRelay(emptySet(), { error("unused") }) }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `missing environment disables the relay and names only what is missing`() {
        val env = mapOf("WHATSAPP_ACCESS_TOKEN" to META_TOKEN)
        val relay = WhatsAppRelay.fromEnvironment(env = env)
        assertEquals(null, relay)
    }

    // ---- the forward to Meta ----

    @Test
    fun `an accepted send returns the message id and posts the template`() = testApplication {
        application { module(relay()) }

        val response = send()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("\"providerMessageId\":\"wamid.42\"" in response.bodyAsText())
        val call = metaCalls.single()
        assertEquals("https://graph.test/v99.0/1234567890/messages", call.url.toString())
        assertEquals("Bearer $META_TOKEN", call.headers[HttpHeaders.Authorization])
        val body = metaBodies.single()
        assertTrue("\"messaging_product\":\"whatsapp\"" in body, body)
        assertTrue("\"to\":\"201001112222\"" in body, body)
        assertTrue("\"name\":\"reminder_ar\"" in body, body)
        assertTrue("\"code\":\"ar\"" in body, body)
        assertTrue("\"text\":\"Mona\"" in body, body)
    }

    @Test
    fun `the Meta token never appears in anything sent back to a device`() = testApplication {
        application { module(relay()) }

        val ok = send()
        val refused = send(token = "nope")

        for (response in listOf(ok, refused)) {
            assertFalse(META_TOKEN in response.bodyAsText())
            val headerValues = response.headers.entries().flatMap { it.value }
            assertFalse(headerValues.any { META_TOKEN in it })
        }
    }

    @Test
    fun `a Meta refusal is a 422 carrying Metas code`() = testApplication {
        application {
            module(
                relay {
                    metaJson(
                        HttpStatusCode.BadRequest,
                        """{"error":{"code":131047,"error_data":{"details":"Re-engagement message"}}}""",
                    )
                },
            )
        }

        val response = send()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue("\"providerCode\":131047" in body, body)
        assertTrue("Re-engagement message" in body, body)
    }

    @Test
    fun `a rate limit signalled by status is a 429`() = testApplication {
        application { module(relay { metaJson(HttpStatusCode.TooManyRequests, "{}") }) }

        assertEquals(HttpStatusCode.TooManyRequests, send().status)
    }

    @Test
    fun `a rate limit signalled only by code is a 429`() = testApplication {
        application {
            module(
                relay {
                    metaJson(HttpStatusCode.BadRequest, """{"error":{"code":130429}}""")
                },
            )
        }

        assertEquals(HttpStatusCode.TooManyRequests, send().status)
    }

    /** The line the whole contract rests on: failing to learn the outcome is not a verdict. */
    @Test
    fun `Meta answering 500 is a 502 and never a rejection`() = testApplication {
        application { module(relay { metaJson(HttpStatusCode.InternalServerError, "{}") }) }

        assertEquals(HttpStatusCode.BadGateway, send().status)
    }

    @Test
    fun `Meta being unreachable is a 502 and never a rejection`() = testApplication {
        application { module(relay { throw RuntimeException("boom") }) }

        assertEquals(HttpStatusCode.BadGateway, send().status)
    }

    @Test
    fun `the relays own rejected Meta credential is an unknown outcome not a message verdict`() =
        testApplication {
            application {
                module(
                    relay { metaJson(HttpStatusCode.Unauthorized, """{"error":{"code":190}}""") },
                )
            }

            assertEquals(HttpStatusCode.BadGateway, send().status)
        }

    // ---- validation ----

    @Test
    fun `a malformed request is a 400 and Meta is not called`() = testApplication {
        application { module(relay()) }

        assertEquals(HttpStatusCode.BadRequest, send(sendRequest(to = "+201001112222")).status)
        assertEquals(HttpStatusCode.BadRequest, send(sendRequest(key = " ")).status)
        assertEquals(HttpStatusCode.BadRequest, send(sendRequest(template = "")).status)
        val garbage = client.post(RELAY_SEND_PATH) {
            header(HttpHeaders.Authorization, "Bearer $DEVICE_TOKEN")
            contentType(ContentType.Application.Json)
            setBody("not json")
        }
        assertEquals(HttpStatusCode.BadRequest, garbage.status)
        assertTrue(metaCalls.isEmpty())
    }

    // ---- idempotency: the reason a timeout cannot become a second message ----

    @Test
    fun `the same key sent twice reaches Meta once and gets the same answer`() = testApplication {
        application { module(relay()) }

        val first = send()
        val second = send()

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(first.bodyAsText(), second.bodyAsText())
        assertEquals(1, metaCalls.size, "a repeat produced a second message")
    }

    @Test
    fun `a refusal is remembered too`() = testApplication {
        application {
            module(relay { metaJson(HttpStatusCode.BadRequest, """{"error":{"code":131026}}""") })
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, send().status)
        assertEquals(HttpStatusCode.UnprocessableEntity, send().status)
        assertEquals(1, metaCalls.size)
    }

    @Test
    fun `different keys are different messages`() = testApplication {
        application { module(relay()) }

        send(sendRequest("renewal:a"))
        send(sendRequest("renewal:b"))

        assertEquals(2, metaCalls.size)
    }

    /** Retrying an unknown outcome has to be able to try again, or retry would be a no-op. */
    @Test
    fun `an unknown outcome is not remembered so the retry reaches Meta`() = testApplication {
        var calls = 0
        application {
            module(
                relay {
                    if (calls++ == 0) {
                        metaJson(HttpStatusCode.BadGateway, "{}")
                    } else {
                        metaJson(HttpStatusCode.OK, """{"messages":[{"id":"wamid.1"}]}""")
                    }
                },
            )
        }

        assertEquals(HttpStatusCode.BadGateway, send().status)
        assertEquals(HttpStatusCode.OK, send().status)
        assertEquals(2, metaCalls.size)
    }

    @Test
    fun `a rate limited key is not remembered so the retry reaches Meta`() = testApplication {
        var calls = 0
        application {
            module(
                relay {
                    if (calls++ == 0) {
                        metaJson(HttpStatusCode.TooManyRequests, "{}")
                    } else {
                        metaJson(HttpStatusCode.OK, """{"messages":[{"id":"wamid.1"}]}""")
                    }
                },
            )
        }

        assertEquals(HttpStatusCode.TooManyRequests, send().status)
        assertEquals(HttpStatusCode.OK, send().status)
    }

    @Test
    fun `concurrent duplicates share one call to Meta`() {
        var calls = 0
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val cache = IdempotencyCache()
        val outcomes = runBlocking {
            (1..5).map {
                async {
                    cache.once("renewal:t-1") {
                        calls++
                        gate.await()
                        MetaOutcome.Accepted("wamid.1")
                    }
                }
            }.also { gate.complete(Unit) }.awaitAll()
        }

        assertEquals(1, calls, "duplicates raced each other to Meta")
        assertTrue(outcomes.all { it == MetaOutcome.Accepted("wamid.1") })
    }

    // ---- secrets and PII never reach the server log ----

    @Test
    fun `nothing sensitive is logged on any path`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            val handlers: List<MetaHandler> = listOf(
                { metaJson(HttpStatusCode.OK, """{"messages":[{"id":"wamid.1"}]}""") },
                {
                    metaJson(
                        HttpStatusCode.BadRequest,
                        """{"error":{"code":131026,"message":"$PHONE"}}""",
                    )
                },
                { metaJson(HttpStatusCode.TooManyRequests, "{}") },
                {
                    metaJson(HttpStatusCode.Unauthorized, """{"error":{"message":"$META_TOKEN"}}""")
                },
                { metaJson(HttpStatusCode.BadGateway, "{}") },
                { throw RuntimeException("reset talking to graph.test") },
            )
            handlers.forEachIndexed { index, handler ->
                testApplication {
                    application { module(relay(handler)) }
                    send(sendRequest("renewal:log-$index"))
                    send(sendRequest("renewal:log-$index"), token = "wrong-$DEVICE_TOKEN")
                }
            }
        } finally {
            root.detachAppender(appender)
        }

        val lines = appender.list.map { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        // The relay's own lines name a transport failure by class only. Ktor's internal loggers may
        // quote an exception's text, which is theirs and carries only what the engine put there.
        appender.list.filter { it.loggerName == "WhatsAppRelay" }.forEach {
            val text = it.formattedMessage
            assertFalse("graph.test" in text, "exception text logged: $text")
        }
        assertTrue(lines.any { "renewal:log-" in it }, "the relay should log its outcome")
        for (line in lines) {
            assertFalse(META_TOKEN in line, "Meta token logged: $line")
            assertFalse(DEVICE_TOKEN in line, "device credential logged: $line")
            assertFalse("201001112222" in line, "phone number logged: $line")
            assertFalse("Mona" in line, "member name logged: $line")
        }
    }

    private fun sendRequest(
        key: String = "renewal:t-1",
        to: String = "201001112222",
        template: String = "reminder_ar",
    ) = RelaySendRequest(
        idempotencyKey = key,
        to = to,
        template = template,
        language = "ar",
        parameters = listOf("Mona"),
    )

    private companion object {
        const val META_TOKEN = "EAAG-META-ACCESS-TOKEN"
        const val DEVICE_TOKEN = "device-credential-abc"
        const val PHONE = "201001112222"
    }
}
