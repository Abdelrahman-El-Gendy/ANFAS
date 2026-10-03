package com.anfas.core.model

import kotlinx.serialization.Serializable

/**
 * The wire contract between a device and the WhatsApp relay on `:server`.
 *
 * Lives in `:core:model` because that is the one module both ends already depend on (`:server` may
 * depend on nothing else), so the two cannot disagree about the shape. See
 * `design/whatsapp-send-system.md` §2 for why the relay exists at all: the Meta token stays on the
 * server and the device only ever posts one of these.
 *
 * **Status codes carry the verdict, and the body carries the detail:**
 *
 * | HTTP | Meaning | Body |
 * |---|---|---|
 * | 200 | Meta accepted the message | [RelaySendResponse] |
 * | 422 | Meta refused it -- a decision | [RelayErrorResponse] with Meta's code |
 * | 429 | Rate limited, by Meta or by the relay | [RelayErrorResponse] |
 * | 401 / 403 | The relay does not accept this device's credential | [RelayErrorResponse] |
 * | 5xx | The relay could not reach Meta, or is not configured: **outcome unknown** | any |
 *
 * 422 versus 5xx is the line that matters: a 422 is certain the message did not go, a 5xx is not.
 */
const val RELAY_SEND_PATH: String = "/v1/whatsapp/send"

/** Code reported for a rate limit the relay itself applied, matching Meta's own 130429. */
const val RELAY_RATE_LIMIT_CODE: Int = 130_429

@Serializable
data class RelaySendRequest(
    /** The reminder's own id. The relay returns the original outcome for a repeat. */
    val idempotencyKey: String,
    /** International digits, no `+`. */
    val to: String,
    val template: String,
    /** `ar` or `en`, as Meta names template languages. */
    val language: String,
    val parameters: List<String>,
)

@Serializable
data class RelaySendResponse(val providerMessageId: String? = null)

@Serializable
data class RelayErrorResponse(
    /** Meta's numeric error code, when the failure came from Meta. */
    val providerCode: Int? = null,
    val detail: String? = null,
)
