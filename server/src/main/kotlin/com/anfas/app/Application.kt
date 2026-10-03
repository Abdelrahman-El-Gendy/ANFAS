package com.anfas.app

import com.anfas.app.sync.ADMIN_AUTH
import com.anfas.app.sync.ChangeStore
import com.anfas.app.sync.DEVICE_AUTH
import com.anfas.app.sync.ErrorBody
import com.anfas.app.sync.SqliteChangeStore
import com.anfas.app.sync.secretMatches
import com.anfas.app.sync.syncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val PORT: Int = 8080

/**
 * Everything the process reads from its environment, in one place so tests can supply it
 * directly instead of mutating process-wide state.
 *
 * [adminSecret] null means the admin routes are **closed**, not open: no secret configured, no
 * device can be registered and nothing can be exported. A box that has not been set up should
 * refuse, never fall back to a default anyone could guess.
 */
data class ServerConfig(val databaseUrl: String, val adminSecret: String?) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig =
            ServerConfig(
                databaseUrl = "jdbc:sqlite:" + (env["ANFAS_DB_PATH"] ?: "anfas-server.db"),
                adminSecret = env["ANFAS_ADMIN_SECRET"]?.takeIf { it.length >= MIN_SECRET_LENGTH },
            )

        /** A shorter value is treated as unset rather than accepted: a weak secret is worse than none. */
        const val MIN_SECRET_LENGTH = 16
    }
}

fun main() {
    val config = ServerConfig.fromEnvironment()
    val store = SqliteChangeStore(config.databaseUrl)
    embeddedServer(Netty, port = PORT, host = "0.0.0.0") { module(store, config.adminSecret) }
        .start(wait = true)
}

@Serializable
data class HealthResponse(val status: String, val service: String)

fun Application.module(store: ChangeStore, adminSecret: String?) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }

    install(Authentication) {
        // A registered device's credential. It authorises pushing and nothing else.
        bearer(DEVICE_AUTH) {
            authenticate { cred -> store.authenticate(cred.token)?.let(::UserIdPrincipal) }
        }
        // The owner's secret, for registering devices and exporting for a restore.
        bearer(ADMIN_AUTH) {
            authenticate { cred ->
                if (adminSecret != null && secretMatches(adminSecret, cred.token)) {
                    UserIdPrincipal("admin")
                } else {
                    null
                }
            }
        }
    }

    install(StatusPages) {
        // A body that is not valid JSON, or not the declared shape, is the caller's mistake.
        exception<ContentTransformationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("malformed request body"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("malformed request body"))
        }
        exception<SerializationException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("malformed request body"))
        }
        exception<Throwable> { call, cause ->
            // The message is logged, never returned: a SQL or driver message can quote a row.
            call.application.environment.log.error("unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("internal error"))
        }
    }

    install(CallLogging)

    routing {
        get("/health") {
            call.respond(HealthResponse(status = "ok", service = "anfas-server"))
        }
    }

    syncRoutes(store)
}
