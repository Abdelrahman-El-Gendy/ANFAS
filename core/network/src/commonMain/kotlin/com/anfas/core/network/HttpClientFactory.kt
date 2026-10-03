package com.anfas.core.network

import com.anfas.core.common.logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Configures an engine-specific client with the plugins every caller here needs.
 *
 * The engine is supplied per platform (OkHttp on Android, Darwin on iOS, CIO on desktop) by
 * [createPlatformHttpClient], so this configures only engine-agnostic plugins.
 *
 * **Logging is capped at [LogLevel.HEADERS] and the `Authorization` header is redacted.** Both
 * matter. `BODY` would write member phone numbers and names into the log -- on desktop a plaintext
 * file -- and Ktor logs request headers verbatim unless told to sanitise them, which would put the
 * relay credential in every line. Kermit output is routed through [AppLog][logger] rather than
 * Ktor's default logger so it obeys the same sinks and severity as everything else.
 *
 * [logger] is a parameter so a test can read what would have been written; production passes
 * nothing.
 */
fun createHttpClient(engineClient: HttpClient, logger: Logger = KermitHttpLogger): HttpClient =
    engineClient.config {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }
        // Without bounds a dead connection waits for the OS, which is minutes -- long enough for a
        // reception desk to think the app has hung. A timeout surfaces as "unreachable", which is the
        // truthful state: the message may or may not have gone.
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(Logging) {
            this.logger = logger
            level = LogLevel.HEADERS
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }

private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 20_000L

private object KermitHttpLogger : Logger {
    private val log = logger("Http")
    override fun log(message: String) = log.d(message)
}

/**
 * The app's real client: this platform's engine, configured by [createHttpClient].
 *
 * One `expect` per target rather than `HttpClient()` and engine auto-discovery, because discovery
 * rests on `ServiceLoader` -- exactly the reflective lookup R8 has already stripped once in this
 * project (ML Kit's `ComponentDiscovery`). Naming the engine makes it a plain reference.
 */
expect fun createPlatformHttpClient(): HttpClient
