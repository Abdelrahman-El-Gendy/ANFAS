package com.anfas.core.data

import com.anfas.core.model.FailureReason

/**
 * One template message, ready to hand over.
 *
 * This is the wire contract the relay will carry, defined now so the shape is settled before an
 * HTTP client exists — see `design/whatsapp-send-system.md` §2 for why the token lives on a server
 * and the device only ever posts one of these.
 */
data class TemplateMessage(
    /**
     * Identifies this send, not this attempt.
     *
     * Meta does not deduplicate. If a call times out *after* Meta accepted it, a naive retry sends
     * twice — and a member receiving the same renewal notice twice is what makes a gym stop
     * trusting the app. The reminder's own id serves, because it is already deterministic per term
     * (`renewal:<termId>`), so the same message can never acquire two keys.
     */
    val idempotencyKey: String,
    /** International digits, no `+`. See `PhoneE164`. */
    val toE164: String,
    val templateName: String,
    /** `ar` or `en`, as Meta names template languages. */
    val languageCode: String,
    val parameters: List<String>,
)

/**
 * What the provider said.
 *
 * [Unreachable] is deliberately separate from [Rejected], and the difference is not cosmetic: a
 * rejection is a decision, whereas an unreachable provider means **we do not know whether the
 * message was sent**. Both end as a failed row, but only the second is a case where
 * [TemplateMessage.idempotencyKey] is doing real work on the retry.
 */
sealed interface GatewayResult {

    data class Accepted(val providerMessageId: String?) : GatewayResult

    data class Rejected(
        /** Meta's numeric error code, when it gave one. */
        val providerCode: Int?,
        val detail: String?,
    ) : GatewayResult

    data class Unreachable(val detail: String?) : GatewayResult
}

/**
 * Hands a message to WhatsApp.
 *
 * An interface with a fake behind it, for the same reason `CameraPermissions` and `AppDispatchers`
 * are: a real HTTP call cannot have its rate-limited branch exercised on demand, and every branch
 * here decides whether a member gets messaged.
 */
interface WhatsAppGateway {

    /**
     * Whether there is anything on the other end.
     *
     * False until the live gateway and its credentials land (Phase 3). This exists so the Run
     * queue action can be *withheld with a reason* rather than offered and then failing every row:
     * a run against nothing would mark the whole queue FAILED and spend each reminder's attempts,
     * so the queue would be poisoned before WhatsApp was ever connected.
     */
    val isConfigured: Boolean

    suspend fun send(message: TemplateMessage): GatewayResult
}

/**
 * The gateway until there is a real one.
 *
 * Registered in production deliberately, rather than leaving the binding absent: an absent binding
 * is a Koin resolution failure the first time someone opens the queue, whereas this is a truthful
 * "not connected yet" the UI can explain. `send` is unreachable in practice — [isConfigured] is
 * false, so nothing calls it — and returns [GatewayResult.Unreachable] rather than throwing, on the
 * principle that a wrong answer here must not be an exception on a reception desk.
 */
internal object NoWhatsAppGateway : WhatsAppGateway {
    override val isConfigured: Boolean = false

    override suspend fun send(message: TemplateMessage): GatewayResult =
        GatewayResult.Unreachable("WhatsApp is not connected on this device yet.")
}

/**
 * Meta's error codes, mapped onto the failure taxonomy the queue screen already renders.
 *
 * Kept in one function with a test per row, as `design/whatsapp-send-system.md` §7 asks, so a wrong
 * guess is a one-line fix rather than a hunt through the send path.
 *
 * **These codes must be confirmed against the API version pinned when the live gateway lands.** Meta
 * changes them, and this table is a starting point rather than a specification. That is also why the
 * `else` branch is [FailureReason.UNKNOWN] carrying the provider's own text: an unrecognised code
 * has to degrade into something a human can act on, not vanish.
 */
internal fun failureReasonFor(providerCode: Int?): FailureReason = when (providerCode) {
    // 131047 "Re-engagement message": outside the allowed window, i.e. no live opt-in. The one
    // code the domain model already cited by number.
    131_047 -> FailureReason.NOT_OPTED_IN

    // 131026 undeliverable (often not a WhatsApp account at all); 131030 recipient not in the
    // allowed list, which is what an unverified number hits while the account is in test mode.
    131_026, 131_030 -> FailureReason.INVALID_PHONE_NUMBER

    // 130429 rate limit; 131056 the per-(business, consumer) pair limit; 4 API too many calls.
    // The only retryable rejections -- everything else needs a human to change something.
    130_429, 131_056, 4 -> FailureReason.RATE_LIMITED

    // 132015 paused by quality, 132016 disabled.
    132_015, 132_016 -> FailureReason.TEMPLATE_PAUSED

    // Everything else, including the template-authoring errors 132000 (parameter count),
    // 132001 (no such template) and 132012 (parameter format). Those are deliberately NOT given a
    // friendlier reason: they are developer or template-maintenance mistakes rather than
    // operational ones, and the provider's own text in `detail` is what a maintainer needs. If one
    // ever shows up in practice it earns its own FailureReason with copy aimed at that person.
    else -> FailureReason.UNKNOWN
}
