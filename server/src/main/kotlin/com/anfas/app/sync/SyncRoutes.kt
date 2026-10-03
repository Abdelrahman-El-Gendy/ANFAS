package com.anfas.app.sync

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.security.MessageDigest

const val DEVICE_AUTH = "device"
const val ADMIN_AUTH = "admin"

object PushLimits {
    const val MAX_ENTRIES = 500
    const val MAX_PAYLOAD_CHARS = 256 * 1024
    const val MAX_BODY_BYTES = 16L * 1024 * 1024
    const val MAX_ROW_ID_CHARS = 128
    const val MAX_LABEL_CHARS = 100
    const val MAX_EXPORT_PAGE = 1000
}

/**
 * Why a push is refused, or null when it is fine. The whole request is judged before anything is
 * written: an outbox drains in order, so a half-applied batch would leave the device unable to say
 * what the box has, and one bad entry would otherwise get the good ones before it acknowledged
 * and the bad one retried forever.
 */
fun validatePush(request: PushRequest): String? {
    val entries = request.entries
    if (entries.isEmpty()) return "entries must not be empty"
    if (entries.size >
        PushLimits.MAX_ENTRIES
    ) {
        return "at most ${PushLimits.MAX_ENTRIES} entries per push"
    }
    var previous = Long.MIN_VALUE
    entries.forEachIndexed { i, e ->
        if (e.clientSeq <= previous) return "entry $i: client_seq must be strictly increasing"
        previous = e.clientSeq
        if (e.table !in SyncedTables.ALL) return "entry $i: table '${e.table}' is not synced"
        if (e.rowId.isBlank() || e.rowId.length > PushLimits.MAX_ROW_ID_CHARS) {
            return "entry $i: row_id must be 1..${PushLimits.MAX_ROW_ID_CHARS} characters"
        }
        val op =
            PushOp.entries.firstOrNull { it.name == e.op }
                ?: return "entry $i: unknown op '${e.op}'"
        when (op) {
            PushOp.UPSERT -> {
                val payload = e.payload ?: return "entry $i: UPSERT requires a payload"
                if (payload.toString().length > PushLimits.MAX_PAYLOAD_CHARS) {
                    return "entry $i: payload exceeds ${PushLimits.MAX_PAYLOAD_CHARS} characters"
                }
            }

            PushOp.DELETE -> if (e.payload !=
                null
            ) {
                return "entry $i: DELETE must not carry a payload"
            }
        }
    }
    return null
}

/**
 * The Stage 2 routes: register a device, push, and export for a manual restore.
 *
 * There is deliberately **no device-facing read**. Pull is Stage 3, and until then the only way
 * data leaves the box is the admin export, so a stolen device credential can add rows but cannot
 * read the gym's members back out — a property worth having while the box has no TLS of its own.
 */
fun Application.syncRoutes(store: ChangeStore) {
    routing {
        authenticate(ADMIN_AUTH) {
            route("/admin") {
                post("/devices") {
                    val body = call.receive<RegisterDeviceRequest>()
                    val label = body.label.trim()
                    if (label.isEmpty() || label.length > PushLimits.MAX_LABEL_CHARS) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorBody("label must be 1..${PushLimits.MAX_LABEL_CHARS} characters"),
                        )
                        return@post
                    }
                    val device = store.registerDevice(label)
                    // The device id is logged, the token never is.
                    call.application.environment.log.info("registered device {}", device.deviceId)
                    call.respond(
                        HttpStatusCode.Created,
                        RegisterDeviceResponse(device.deviceId, device.token),
                    )
                }
                post("/devices/{id}/revoke") {
                    val id = call.parameters["id"].orEmpty()
                    if (store.revokeDevice(id)) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorBody("no such device"))
                    }
                }
                get("/export") {
                    val after = call.request.queryParameters["after_seq"]?.toLongOrNull() ?: 0L
                    val limit = (
                        call.request.queryParameters["limit"]?.toIntOrNull()
                            ?: PushLimits.MAX_EXPORT_PAGE
                        )
                        .coerceIn(1, PushLimits.MAX_EXPORT_PAGE)
                    val latestOnly = call.request.queryParameters["latest_only"] == "true"
                    // One more than asked for, so has_more is known rather than guessed.
                    val rows = store.export(after, limit + 1, latestOnly)
                    val page = rows.take(limit)
                    call.respond(
                        ExportPage(
                            entries = page,
                            nextAfterSeq = page.lastOrNull()?.serverSeq ?: after,
                            hasMore = rows.size > limit,
                        ),
                    )
                }
            }
        }

        authenticate(DEVICE_AUTH) {
            post("/sync/push") {
                val deviceId = call.principal<UserIdPrincipal>()!!.name
                // Checked before the body is parsed: the cap is only worth anything if the
                // oversized request is never read into memory to find out it was oversized.
                val declared = call.request.contentLength()
                if (declared != null && declared > PushLimits.MAX_BODY_BYTES) {
                    call.respond(
                        HttpStatusCode.PayloadTooLarge,
                        ErrorBody("request body too large"),
                    )
                    return@post
                }
                val request = call.receive<PushRequest>()
                val problem = validatePush(request)
                if (problem != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorBody(problem))
                    return@post
                }
                when (val result = store.append(deviceId, request.entries)) {
                    is AppendResult.Accepted -> {
                        call.application.environment.log.info(
                            "push from {}: {} accepted, {} duplicate",
                            deviceId,
                            result.accepted,
                            result.duplicates,
                        )
                        call.respond(
                            PushResponse(result.through, result.accepted, result.duplicates),
                        )
                    }

                    is AppendResult.SequenceReused -> call.respond(
                        HttpStatusCode.Conflict,
                        ErrorBody(
                            "client_seq ${result.clientSeq} was already pushed as a different " +
                                "change; this device's outbox numbering has restarted and it " +
                                "must be re-registered",
                        ),
                    )
                }
            }
        }
    }
}

/** Constant-time comparison, so the admin secret cannot be recovered a byte at a time. */
fun secretMatches(expected: String, supplied: String): Boolean =
    MessageDigest.isEqual(expected.toByteArray(), supplied.toByteArray())
