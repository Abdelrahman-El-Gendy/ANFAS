package com.anfas.app

import com.anfas.core.model.RELAY_RATE_LIMIT_CODE
import com.anfas.core.model.RELAY_SEND_PATH
import com.anfas.core.model.RelayErrorResponse
import com.anfas.core.model.RelaySendRequest
import com.anfas.core.model.RelaySendResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

/**
 * What Meta said, reduced to the three things the relay must tell a device apart.
 *
 * [Unknown] is its own case and not a flavour of [Rejected] for the reason the whole contract
 * rests on: a refusal is a decision, but a timeout or a 5xx means Meta may already have accepted
 * the message. Collapsing the two would tell a receptionist it definitely did not go.
 */
sealed interface MetaOutcome {
    data class Accepted(val messageId: String?) : MetaOutcome

    data class Rejected(val providerCode: Int?, val detail: String?, val rateLimited: Boolean) :
        MetaOutcome

    data class Unknown(val detail: String) : MetaOutcome
}

/** The seam to Meta, so the relay's routing and deduplication are testable without it. */
fun interface MetaClient {
    suspend fun sendTemplate(request: RelaySendRequest): MetaOutcome
}

/**
 * Meta's Graph API, over HTTPS, with the token that never leaves this process.
 *
 * [apiVersion] has no default on purpose: Graph versions expire, and a hardcoded one is a send path
 * that silently stops working some months after it shipped. The operator pins it
 * (`WHATSAPP_GRAPH_API_VERSION`, e.g. `v23.0`) and re-confirms it against Meta's current docs.
 */
class GraphApiMetaClient(
    private val http: HttpClient,
    private val accessToken: String,
    private val phoneNumberId: String,
    private val apiVersion: String,
    private val baseUrl: String = "https://graph.facebook.com",
) : MetaClient {

    private val log = LoggerFactory.getLogger("WhatsAppRelay")

    override suspend fun sendTemplate(request: RelaySendRequest): MetaOutcome {
        val response = try {
            http.post("${baseUrl.trimEnd('/')}/$apiVersion/$phoneNumberId/messages") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                setBody(templatePayload(request).toString())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Class name only: a transport exception's message can quote the request URL.
            val cause = failure::class.simpleName
            log.warn("Meta unreachable for {}: {}", request.idempotencyKey, cause)
            return MetaOutcome.Unknown("Could not reach WhatsApp (${failure::class.simpleName}).")
        }

        val status = response.status.value
        val body = parse(response.bodyAsText())
        val error = body?.get("error")?.let { it as? JsonObject }
        val code = error?.get("code")?.jsonPrimitive?.intOrNull
        val details = (error?.get("error_data") as? JsonObject)?.get("details")
        val detail = details?.jsonPrimitive?.contentOrNull
            ?: error?.get("message")?.jsonPrimitive?.contentOrNull

        return when {
            status in 200..299 -> {
                val id = ((body?.get("messages") as? JsonArray)?.firstOrNull() as? JsonObject)
                    ?.get("id")?.jsonPrimitive?.contentOrNull
                MetaOutcome.Accepted(id)
            }

            status == 429 || code in RATE_LIMIT_CODES ->
                MetaOutcome.Rejected(code ?: RELAY_RATE_LIMIT_CODE, detail, rateLimited = true)

            // The relay's own Meta credential is wrong: nothing was sent, but it is not the
            // device's doing and not a verdict on this message, so it must not read as one.
            status == 401 || status == 403 || code == OAUTH_INVALID_TOKEN -> {
                log.error("Meta rejected the relay's credential (HTTP {}, code {})", status, code)
                MetaOutcome.Unknown("The relay's WhatsApp credential was not accepted.")
            }

            status in 400..499 -> MetaOutcome.Rejected(code, detail, rateLimited = false)

            else -> MetaOutcome.Unknown("WhatsApp failed (HTTP $status).")
        }
    }

    private fun templatePayload(request: RelaySendRequest): JsonObject = buildJsonObject {
        put("messaging_product", "whatsapp")
        put("to", request.to)
        put("type", "template")
        put(
            "template",
            buildJsonObject {
                put("name", request.template)
                put("language", buildJsonObject { put("code", request.language) })
                if (request.parameters.isNotEmpty()) {
                    put(
                        "components",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("type", "body")
                                    put(
                                        "parameters",
                                        JsonArray(
                                            request.parameters.map {
                                                buildJsonObject {
                                                    put("type", "text")
                                                    put("text", it)
                                                }
                                            },
                                        ),
                                    )
                                },
                            ),
                        ),
                    )
                }
            },
        )
    }

    private fun parse(text: String): JsonObject? = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) {
        null
    }

    private companion object {
        // 130429 throughput, 131056 per-pair, 4 and 80007 API/app rate limits. Confirm against the
        // pinned Graph version, as the device-side table in :core:data says.
        val RATE_LIMIT_CODES = setOf(130_429, 131_056, 4, 80_007)
        const val OAUTH_INVALID_TOKEN = 190
    }
}

/**
 * Remembers recent outcomes by idempotency key, so a send retried after a timeout cannot become a
 * second message. Meta does not deduplicate; this is the only thing that does.
 *
 * Only **definitive** outcomes are kept. An accepted or refused message is final, so repeating it
 * returns the original answer. A rate limit or an unknown outcome is not cached, because the whole
 * point of retrying those is to try again. Concurrent duplicates share one in-flight call.
 *
 * A bounded window of hours, in memory: a relay restart forgets it, which is an accepted gap while
 * one process serves one gym. Anything this ever persists is member data (a phone number is PII)
 * and has to be treated as such -- it deliberately holds only keys and outcomes, never the request.
 */
class IdempotencyCache(
    private val ttl: kotlin.time.Duration = 6.hours,
    private val maxEntries: Int = 10_000,
) {
    private class Entry(
        val outcome: CompletableDeferred<MetaOutcome>,
        val at: TimeSource.Monotonic.ValueTimeMark,
    )

    private val lock = Mutex()
    private val entries = LinkedHashMap<String, Entry>()

    suspend fun once(key: String, compute: suspend () -> MetaOutcome): MetaOutcome {
        val (entry, owner) = lock.withLock {
            evict()
            val existing = entries[key]
            if (existing != null) {
                existing to false
            } else {
                val fresh = Entry(CompletableDeferred(), TimeSource.Monotonic.markNow())
                entries[key] = fresh
                fresh to true
            }
        }
        if (!owner) return entry.outcome.await()

        val outcome = try {
            compute()
        } catch (cancelled: CancellationException) {
            lock.withLock { entries.remove(key) }
            entry.outcome.cancel(cancelled)
            throw cancelled
        } catch (failure: Exception) {
            MetaOutcome.Unknown("The relay failed (${failure::class.simpleName}).")
        }
        if (!outcome.isFinal()) lock.withLock { entries.remove(key) }
        entry.outcome.complete(outcome)
        return outcome
    }

    private fun MetaOutcome.isFinal() = when (this) {
        is MetaOutcome.Accepted -> true
        is MetaOutcome.Rejected -> !rateLimited
        is MetaOutcome.Unknown -> false
    }

    private fun evict() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            // An unfinished entry is in flight and must stay, or a duplicate would race it.
            if (entry.outcome.isCompleted && entry.at.elapsedNow() > ttl) iterator.remove()
        }
        while (entries.size >= maxEntries) {
            val oldest = entries.entries.firstOrNull { it.value.outcome.isCompleted } ?: break
            entries.remove(oldest.key)
        }
    }
}

/**
 * The relay: authenticates a device, deduplicates, and forwards to Meta.
 *
 * **Configuration is environment-only and none of it is ever logged**:
 * `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_GRAPH_API_VERSION` and
 * `RELAY_DEVICE_TOKENS` (comma-separated; one per installation, revoked by removing it and
 * restarting). If any is missing the route answers 503 and the device reports the outcome as
 * unreachable rather than as a verdict on the message.
 */
class WhatsAppRelay(
    deviceTokens: Set<String>,
    private val meta: MetaClient,
    private val cache: IdempotencyCache = IdempotencyCache(),
) {
    private val tokens: List<ByteArray> =
        deviceTokens.filter { it.isNotBlank() }.map { it.toByteArray() }

    init {
        require(tokens.isNotEmpty()) { "A relay with no device credentials would accept nobody." }
    }

    /** Constant-time against every configured token, with no early exit on a match. */
    fun accepts(presented: String?): Boolean {
        val candidate = presented?.toByteArray() ?: return false
        var match = false
        for (token in tokens) {
            if (MessageDigest.isEqual(sha256(token), sha256(candidate))) match = true
        }
        return match
    }

    suspend fun send(request: RelaySendRequest): MetaOutcome =
        cache.once(request.idempotencyKey) { meta.sendTemplate(request) }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private val log = LoggerFactory.getLogger("WhatsAppRelay")

        /** Null when anything needed is absent; names what is missing, never a value. */
        fun fromEnvironment(
            env: Map<String, String> = System.getenv(),
            http: () -> HttpClient = { HttpClient(io.ktor.client.engine.cio.CIO) },
        ): WhatsAppRelay? {
            val required = listOf(
                "WHATSAPP_ACCESS_TOKEN",
                "WHATSAPP_PHONE_NUMBER_ID",
                "WHATSAPP_GRAPH_API_VERSION",
                "RELAY_DEVICE_TOKENS",
            )
            val missing = required.filter { env[it].isNullOrBlank() }
            if (missing.isNotEmpty()) {
                log.warn("WhatsApp relay disabled; not set: {}", missing.joinToString())
                return null
            }
            val deviceTokens = env.getValue("RELAY_DEVICE_TOKENS").split(',').map { it.trim() }
                .filter { it.isNotEmpty() }.toSet()
            if (deviceTokens.isEmpty()) {
                log.warn("WhatsApp relay disabled; RELAY_DEVICE_TOKENS lists no tokens")
                return null
            }
            return WhatsAppRelay(
                deviceTokens = deviceTokens,
                meta = GraphApiMetaClient(
                    http = http(),
                    accessToken = env.getValue("WHATSAPP_ACCESS_TOKEN"),
                    phoneNumberId = env.getValue("WHATSAPP_PHONE_NUMBER_ID"),
                    apiVersion = env.getValue("WHATSAPP_GRAPH_API_VERSION"),
                ),
            )
        }
    }
}

private val routeLog = LoggerFactory.getLogger("WhatsAppRelay")

/**
 * `POST /v1/whatsapp/send`. Status codes carry the verdict -- see `RelayWire.kt`.
 *
 * Logs the reminder id and the outcome class. Never the number, the parameters (a member's name),
 * or a header: this server's log is a file someone will eventually attach to a bug report.
 */
fun Route.whatsAppRelay(relay: WhatsAppRelay?) {
    post(RELAY_SEND_PATH) {
        if (relay == null) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                RelayErrorResponse(detail = "The WhatsApp relay is not configured."),
            )
            return@post
        }

        val presented = call.request.header(HttpHeaders.Authorization)
            ?.removePrefix("Bearer ")
            ?.trim()
        if (!relay.accepts(presented)) {
            call.respond(
                HttpStatusCode.Unauthorized,
                RelayErrorResponse(detail = "Unknown device credential."),
            )
            return@post
        }

        val request = try {
            call.receiveNullable<RelaySendRequest>()
        } catch (_: Exception) {
            null
        }
        val problem = if (request == null) NOT_A_REQUEST else request.problem()
        if (request == null || problem != null) {
            call.respond(HttpStatusCode.BadRequest, RelayErrorResponse(detail = problem))
            return@post
        }

        when (val outcome = relay.send(request)) {
            is MetaOutcome.Accepted -> {
                routeLog.info("Relayed {}: accepted", request.idempotencyKey)
                call.respond(HttpStatusCode.OK, RelaySendResponse(outcome.messageId))
            }

            is MetaOutcome.Rejected -> {
                routeLog.info(
                    "Relayed {}: {} (code {})",
                    request.idempotencyKey,
                    if (outcome.rateLimited) "rate limited" else "rejected",
                    outcome.providerCode,
                )
                call.respond(
                    if (outcome.rateLimited) {
                        HttpStatusCode.TooManyRequests
                    } else {
                        HttpStatusCode.UnprocessableEntity
                    },
                    RelayErrorResponse(outcome.providerCode, outcome.detail),
                )
            }

            is MetaOutcome.Unknown -> {
                routeLog.warn("Relayed {}: outcome unknown", request.idempotencyKey)
                call.respond(HttpStatusCode.BadGateway, RelayErrorResponse(detail = outcome.detail))
            }
        }
    }
}

private const val NOT_A_REQUEST = "The request body was not a send request."

private val DIGITS = Regex("^[0-9]{8,15}$")

private fun RelaySendRequest.problem(): String? = when {
    idempotencyKey.isBlank() || idempotencyKey.length > 200 -> "idempotencyKey is required."
    !DIGITS.matches(to) -> "to must be 8-15 digits, international form, no plus."
    template.isBlank() -> "template is required."
    language.isBlank() -> "language is required."
    parameters.size > 10 || parameters.any { it.length > 1024 } -> "parameters are out of range."
    else -> null
}
