package com.anfas.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createPlatformHttpClient(): HttpClient = createHttpClient(HttpClient(CIO))
