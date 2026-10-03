package com.anfas.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The relay credential and member phone numbers must never reach a log. On desktop that log is a
 * plaintext file on a shared reception machine.
 */
class HttpClientLoggingTest {

    private suspend fun loggedLines(block: suspend (HttpClient) -> Unit): List<String> {
        val lines = mutableListOf<String>()
        val capture = object : Logger {
            override fun log(message: String) {
                lines += message
            }
        }
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val client = createHttpClient(HttpClient(engine), capture)
        block(client)
        return lines
    }

    @Test
    fun `the authorization header is redacted`() = runTest {
        val lines = loggedLines { client ->
            client.post("https://relay.example/v1/whatsapp/send") {
                header(HttpHeaders.Authorization, "Bearer SECRET-DEVICE-TOKEN")
                setBody("{}")
            }
        }

        assertTrue(lines.isNotEmpty(), "the logger should have been exercised at all")
        assertTrue(
            lines.none { "SECRET-DEVICE-TOKEN" in it },
            "the credential reached the log: $lines",
        )
    }

    @Test
    fun `request bodies are not logged`() = runTest {
        val lines = loggedLines { client ->
            client.post("https://relay.example/v1/whatsapp/send") {
                setBody("""{"to":"201001112222","parameters":["Mona Ibrahim"]}""")
            }
        }

        assertTrue(lines.isNotEmpty())
        assertTrue(
            lines.none {
                "201001112222" in it || "Mona Ibrahim" in it
            },
            "body logged: $lines",
        )
    }
}
