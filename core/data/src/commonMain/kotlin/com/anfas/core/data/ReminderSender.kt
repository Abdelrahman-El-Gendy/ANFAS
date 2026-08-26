package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.database.ReminderDao
import com.anfas.core.model.FailureReason
import com.anfas.core.model.PhoneE164
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderFailure
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.TemplateLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * What one run of the queue did.
 *
 * [stoppedEarly] is its own field rather than being inferred from the counts, because "sent 12 of
 * 40 and stopped" and "sent 12 of 12" are different things to tell someone at a desk.
 */
data class SendRunOutcome(
    val sent: Int = 0,
    val failed: Int = 0,
    /** Rows left alone because they had already used up their attempts. */
    val exhausted: Int = 0,
    /** True when rate limiting cut the run short, leaving the remainder queued. */
    val stoppedEarly: Boolean = false,
) {
    val attempted: Int get() = sent + failed
}

/**
 * Sends what the scheduler queued.
 *
 * Staff-triggered, one run at a time — there is no background scheduler, and pretending otherwise
 * is what `design/whatsapp-send-system.md` §8 refuses to do. Nothing here decides *who* is owed a
 * message; that is `ReminderScheduler`.
 */
interface ReminderSender {

    /**
     * Whether sending is possible at all. The UI withholds the Run queue action when false, with an
     * explanation — see [WhatsAppGateway.isConfigured] for why offering it would poison the queue.
     */
    val isConfigured: Boolean

    suspend fun runQueue(): AppResult<SendRunOutcome>
}

internal class DefaultReminderSender(
    private val reminders: ReminderDao,
    private val gateway: WhatsAppGateway,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
) : ReminderSender {

    private val log = logger("Reminders")

    override val isConfigured: Boolean get() = gateway.isConfigured

    override suspend fun runQueue(): AppResult<SendRunOutcome> = withContext(dispatchers.io) {
        runStorage("Could not run the reminder queue") {
            // Belt and braces with the UI's own check: a component method is callable from
            // anywhere, and running against nothing would fail every row.
            if (!gateway.isConfigured) return@runStorage SendRunOutcome()

            val queued = reminders.observeByStatus(ReminderStatus.QUEUED.name).first()
            var outcome = SendRunOutcome()

            for (entity in queued) {
                val reminder = entity.toDomain()

                // A row that has used its attempts stays put rather than being retried forever.
                // It is still visible in the Failed tab, where a human can decide.
                if (reminder.attempts >= MAX_ATTEMPTS) {
                    outcome = outcome.copy(exhausted = outcome.exhausted + 1)
                    continue
                }

                val result = attempt(reminder)
                outcome = outcome.record(result)

                // Rate limiting means the *next* send would fail too, so the run stops and leaves
                // the remainder QUEUED for later rather than burning an attempt on each.
                if (result == Attempt.RateLimited) {
                    outcome = outcome.copy(stoppedEarly = true)
                    break
                }

                // Serial with a pause. Meta's per-second throughput is the real constraint at a
                // gym's volume, not the 24-hour tier, and a serial run also keeps the queue
                // screen's counts legible as it progresses.
                delay(BETWEEN_SENDS_MS)
            }

            // Ids and counts only. Never a name, a number or a message body: this is a shared
            // reception device, the same reasoning as "Staff signed in" carrying no username.
            log.i("Reminder run finished: ${outcome.sent} sent, ${outcome.failed} failed")
            outcome
        }
    }

    private suspend fun attempt(reminder: Reminder): Attempt {
        val e164 = PhoneE164.of(reminder.phone)
            ?: return reminder.fail(FailureReason.INVALID_PHONE_NUMBER, null, UNREADABLE_NUMBER)

        // Incremented before the call, never after. A crash mid-send must not leave the row looking
        // untried, or it is retried forever.
        val attempted = reminder.copy(attempts = reminder.attempts + 1)
        reminders.upsertAll(listOf(attempted.toEntity()))

        val message = TemplateMessage(
            idempotencyKey = reminder.id.value,
            toE164 = e164,
            templateName = reminder.template.templateName,
            languageCode = reminder.template.language.metaCode,
            // One parameter, the member's name. The real list is fixed when Meta approves the
            // templates (Phase 3), so committing to a second value now would be guessing at a
            // template that does not exist yet -- and the name is the one value every candidate
            // template will certainly carry.
            parameters = listOf(reminder.memberName),
        )

        return when (val result = gateway.send(message)) {
            is GatewayResult.Accepted -> {
                reminders.upsertAll(
                    listOf(attempted.copy(status = ReminderStatus.SENT, failure = null).toEntity()),
                )
                Attempt.Sent
            }

            is GatewayResult.Rejected -> {
                val reason = failureReasonFor(result.providerCode)
                attempted.fail(reason, result.providerCode, result.detail)
            }

            // We do not know whether it arrived, which is exactly the case the idempotency key
            // exists for. Recorded as retryable so the run can be repeated safely.
            is GatewayResult.Unreachable ->
                attempted.fail(FailureReason.UNKNOWN, null, result.detail ?: UNREACHABLE)
        }
    }

    private suspend fun Reminder.fail(
        reason: FailureReason,
        providerCode: Int?,
        detail: String?,
    ): Attempt {
        reminders.upsertAll(
            listOf(
                copy(
                    status = ReminderStatus.FAILED,
                    failure = ReminderFailure(
                        reason = reason,
                        providerCode = providerCode,
                        lastAttemptAt = clock.now(),
                        detail = detail,
                    ),
                ).toEntity(),
            ),
        )
        return if (reason == FailureReason.RATE_LIMITED) Attempt.RateLimited else Attempt.Failed
    }

    private fun SendRunOutcome.record(attempt: Attempt) = when (attempt) {
        Attempt.Sent -> copy(sent = sent + 1)
        Attempt.Failed, Attempt.RateLimited -> copy(failed = failed + 1)
    }

    private enum class Attempt { Sent, Failed, RateLimited }

    private companion object {
        /**
         * After this many tries a reminder stops consuming runs. Four is enough to ride out a
         * transient throttle without a permanently-broken row being retried at every press.
         */
        const val MAX_ATTEMPTS = 4
        const val BETWEEN_SENDS_MS = 250L
        const val UNREADABLE_NUMBER =
            "The stored number could not be read as an international number."
        const val UNREACHABLE =
            "The provider could not be reached. The message may or may not have been sent."
    }
}

/** Meta names template languages `ar` / `en`, not by our enum's name. */
private val TemplateLanguage.metaCode: String
    get() = when (this) {
        TemplateLanguage.ARABIC -> "ar"
        TemplateLanguage.ENGLISH -> "en"
    }
