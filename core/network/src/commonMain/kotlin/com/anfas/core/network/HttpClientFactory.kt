package com.anfas.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * INTENTIONALLY INERT: nothing calls this yet and :composeApp deliberately does not depend on
 * the module. Keeping it is worthwhile because this build file is the only place the three
 * per-platform Ktor engines (OkHttp / Darwin / CIO) are wired to the right source sets, which is
 * non-obvious KMP knowledge. Cutting the edge also keeps those engines out of every release
 * artifact and avoids a transitive AAR contributing android.permission.INTERNET.
 *
 * The engine is supplied per platform (OkHttp on Android, Darwin on iOS, CIO on desktop),
 * so this configures only engine-agnostic plugins. No routes or endpoints yet.
 */
fun createHttpClient(engineClient: HttpClient): HttpClient = engineClient.config {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(Logging) {
        level = LogLevel.HEADERS
    }
}
