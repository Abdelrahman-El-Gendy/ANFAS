package com.anfas.core.data

import com.anfas.core.common.logger
import com.anfas.core.model.RELAY_RATE_LIMIT_CODE
import com.anfas.core.model.RELAY_SEND_PATH
import com.anfas.core.model.RelayErrorResponse
import com.anfas.core.model.RelaySendRequest
import com.anfas.core.model.RelaySendResponse
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Where the relay is and who this installation is to it.
 *
 * **The credential here is not the Meta token, and that is the whole point.** The Meta token lives
 * in the relay's environment and never reaches a device (`design/whatsapp-send-system.md` §2 and
 * §9). What a device holds is a per-installation credential the owner issues and the relay can
 * revoke, so a lost tablet is cut off without rotating anything at Meta. It is still a secret --
 * which is why it is never logged -- but a revocable one that can do exactly one thing: ask the
 * relay to send. `Settings` is unencrypted on every platform, the same caveat
 * `SettingsSessionStore` states; moving this behind the platform keystore is the upgrade path.
 */
class RelayConfig private constructor(val baseUrl: String, val deviceToken: String) {

    // Not a data class: its generated toString would print the credential into any log line or
    // failed assertion that mentioned the config.
    override fun toString(): String = "RelayConfig(baseUrl=$baseUrl, deviceToken=<redacted>)"

    companion object {
        /**
         * Null unless both parts are present and the URL is one the credential may safely travel
         * to: `https`, or plain `http` only to this machine (a relay under development). A bearer
         * credential over cleartext to anywhere else is a credential handed to the network.
         */
        fun of(baseUrl: String?, deviceToken: String?): RelayConfig? {
            val url = baseUrl?.trim()?.trimEnd('/').orEmpty()
            val token = deviceToken?.trim().orEmpty()
            if (url.isEmpty() || token.isEmpty()) return null
            val secure = url.startsWith("https://", ignoreCase = true)
            val loopback = LOOPBACK_PREFIXES.any { url.startsWith(it, ignoreCase = true) }
            if (!secure && !loopback) return null
            return RelayConfig(url, token)
        }

        private val LOOPBACK_PREFIXES = listOf(
            "http://localhost",
            "http://127.0.0.1",
            "http://[::1]",
        )
    }
}

/** Read afresh on each use, so configuring the relay takes effect without a restart. */
fun interface RelayConfigSource {
    fun current(): RelayConfig?
}

/**
 * Settings-backed [RelayConfigSource].
 *
 * There is **no screen that writes these keys yet** -- an owner-facing setup flow is not part of
 * this phase, and until it exists the gateway reports itself not configured and Run queue stays
 * withheld. [save] is the seam that flow will call.
 */
internal class SettingsRelayConfigSource(private val settings: Settings) : RelayConfigSource {

    override fun current(): RelayConfig? = RelayConfig.of(
        baseUrl = settings.getStringOrNull(KEY_URL),
        deviceToken = settings.getStringOrNull(KEY_TOKEN),
    )

    /** Returns false, writing nothing, when [RelayConfig.of] would refuse the pair. */
    fun save(baseUrl: String, deviceToken: String): Boolean {
        val config = RelayConfig.of(baseUrl, deviceToken) ?: return false
        settings.putString(KEY_URL, config.baseUrl)
        settings.putString(KEY_TOKEN, config.deviceToken)
        return true
    }

    fun clear() {
        settings.remove(KEY_URL)
        settings.remove(KEY_TOKEN)
    }

    private companion object {
        const val KEY_URL = "whatsapp.relay.url"
        const val KEY_TOKEN = "whatsapp.relay.token"
    }
}

/**
 * The live gateway: posts a [TemplateMessage] to the relay on `:server`, which holds the Meta token.
 *
 * **Every response maps to exactly one [GatewayResult], and the mapping is where this class earns
 * its keep:**
 * - 2xx is [GatewayResult.Accepted].
 * - 422 and 429 are [GatewayResult.Rejected] carrying Meta's own code, which `failureReasonFor`
 *   then reads. A 429 with no code is reported as 130429, so a bare throttle still stops the run.
 * - 401/403 and other 4xx are [GatewayResult.Rejected] with no code: the relay refused, so nothing
 *   was sent -- but the reason is ours (a revoked device, a wrong URL), not Meta's.
 * - **5xx, 408, a timeout, or no connection at all is [GatewayResult.Unreachable].** The relay may
 *   have handed the message to Meta before failing, so *we do not know*. That is the one case the
 *   idempotency key exists for, and folding it into `Rejected` would tell a receptionist the
 *   message definitely did not go.
 *
 * Logs the reminder id, an HTTP status and nothing else: no number, no name, no parameters, no
 * credential, and no exception message (a transport error can quote the URL it was dialling).
 */
internal class RelayWhatsAppGateway(
    private val client: HttpClient,
    private val config: RelayConfigSource,
) : WhatsAppGateway {

    private val log = logger("WhatsApp")

    override val isConfigured: Boolean get() = config.current() != null

    override suspend fun send(message: TemplateMessage): GatewayResult {
        // Belt and braces with isConfigured: the sender already declines to run, but a gateway
        // that is called anyway must answer rather than throw.
        val relay = config.current()
            ?: return GatewayResult.Unreachable(
                "The WhatsApp relay is not configured on this device.",
            )

        val response = try {
            client.post(relay.baseUrl + RELAY_SEND_PATH) {
                header(HttpHeaders.Authorization, "Bearer ${relay.deviceToken}")
                contentType(ContentType.Application.Json)
                setBody(wire.encodeToString(RelaySendRequest.serializer(), message.toRequest()))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // The exception class names the failure ("HttpRequestTimeoutException") without its
            // message, which can carry the URL.
            log.w("Relay unreachable for ${message.idempotencyKey}: ${failure::class.simpleName}")
            return GatewayResult.Unreachable(
                "Could not reach the WhatsApp relay (${failure::class.simpleName}). " +
                    "The message may or may not have been sent.",
            )
        }

        log.i("Relay answered ${response.status.value} for ${message.idempotencyKey}")
        return response.toResult()
    }

    private suspend fun HttpResponse.toResult(): GatewayResult {
        val code = status.value
        return when {
            status.isSuccess2xx() -> GatewayResult.Accepted(
                parse<RelaySendResponse>(RelaySendResponse.serializer())?.providerMessageId,
            )

            code == HttpStatusCode.UnprocessableEntity.value -> {
                val error = parse<RelayErrorResponse>(RelayErrorResponse.serializer())
                GatewayResult.Rejected(error?.providerCode, error?.detail ?: "Refused by WhatsApp.")
            }

            code == HttpStatusCode.TooManyRequests.value -> {
                val error = parse<RelayErrorResponse>(RelayErrorResponse.serializer())
                GatewayResult.Rejected(
                    error?.providerCode ?: RELAY_RATE_LIMIT_CODE,
                    error?.detail ?: "Rate limited.",
                )
            }

            code == HttpStatusCode.Unauthorized.value || code == HttpStatusCode.Forbidden.value ->
                GatewayResult.Rejected(null, "The relay does not accept this device's credential.")

            // Not a verdict: the relay (or something in front of it) failed, possibly after Meta
            // had already accepted the message.
            code >= SERVER_ERROR || code == HttpStatusCode.RequestTimeout.value ->
                GatewayResult.Unreachable(
                    "The WhatsApp relay failed (HTTP $code). " +
                        "The message may or may not have been sent.",
                )

            // Remaining 4xx: the relay said no, definitely nothing sent, and the reason is ours --
            // most often a wrong relay URL (404).
            else -> GatewayResult.Rejected(null, "The relay refused the request (HTTP $code).")
        }
    }

    private suspend fun <T> HttpResponse.parse(
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? = try {
        wire.decodeFromString(serializer, bodyAsText())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // A body we cannot read must not turn a clear status into an exception.
        null
    }

    private fun HttpStatusCode.isSuccess2xx() = value in HTTP_OK_START..HTTP_OK_END

    private fun TemplateMessage.toRequest() = RelaySendRequest(
        idempotencyKey = idempotencyKey,
        to = toE164,
        template = templateName,
        language = languageCode,
        parameters = parameters,
    )

    private companion object {
        const val HTTP_OK_START = 200
        const val HTTP_OK_END = 299
        const val SERVER_ERROR = 500
        val wire = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
