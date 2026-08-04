package com.anfas.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
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
