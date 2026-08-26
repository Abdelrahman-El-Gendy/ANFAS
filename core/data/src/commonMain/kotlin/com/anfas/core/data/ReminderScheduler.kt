package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.MemberDao
import com.anfas.core.database.ReminderDao
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import com.anfas.core.model.RenewalQueue
import com.anfas.core.model.SubscriptionTerm
import com.anfas.core.model.TemplateLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * What one queue build did, itemised.
 *
 * Itemised rather than a single count because "queued 0" is a legitimate and *common* outcome —
 * consent defaults to false for everyone, so the very first build on a real gym's data queues
 * nothing — and staff need to be told which reason applied rather than being left to conclude the
 * button is broken.
 */
data class ScheduleOutcome(
    val queued: Int = 0,
    val skippedNoConsent: Int = 0,
    val skippedNoPhone: Int = 0,
    val alreadyQueued: Int = 0,
) {
    /** Terms that needed a reminder, whatever happened to them. */
    val considered: Int get() = queued + skippedNoConsent + skippedNoPhone + alreadyQueued
}

/**
 * Turns the renewal queue into actual reminder rows.
 *
 * This is the piece that was missing: `ReminderRepository.upsert` had no production caller, so the
 * queue screen could only ever be empty. Nothing here sends anything — that is Phase 2 — this only
 * decides who is owed a message and writes the row.
 *
 * Deliberately its own seam rather than a method on `ReminderRepository`: it needs members and
 * subscriptions, and a repository reaching sideways into two other tables' DAOs is how the data
 * layer stops being separable.
 */
interface ReminderScheduler {

    /**
     * Builds the queue once, and is safe to call repeatedly.
     *
     * Idempotence is by construction, not by luck: see [DefaultReminderScheduler].
     */
    suspend fun buildQueue(): AppResult<ScheduleOutcome>
}

/**
 * @param zone the bridge between [RenewalQueue], which works in calendar dates, and
 *   [Reminder.scheduledAt], which is an instant. Injected — along with [clock] — for the same
 *   reason `OfflineFirstIntakeRepository` injects both: a scheduler whose behaviour depends on
 *   today's date is untestable if it reads the system clock itself.
 */
internal class DefaultReminderScheduler(
    private val members: MemberDao,
    private val subscriptions: SubscriptionDao,
    private val reminders: ReminderDao,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ReminderScheduler {

    override suspend fun buildQueue(): AppResult<ScheduleOutcome> = withContext(dispatchers.io) {
        runStorage("Could not build the reminder queue") {
            val now = clock.now()
            val today = clock.todayIn(zone)

            // One snapshot each, not a live combine: a build is a single decision taken at a
            // moment, and re-reading mid-pass could mix two views of the data.
            val membersById = members.observeAll().first()
                .associate { entity -> MemberId(entity.id) to entity.toDomain() }
            val terms = subscriptions.observeAllCurrent().first().map { it.toDomain() }

            val due = RenewalQueue.needingAttention(terms, today).map { it.term }

            // Ask storage which of the deterministic ids are already present, in one query rather
            // than one per candidate. Any status counts: a term whose reminder already SENT or
            // FAILED must not be recreated as QUEUED.
            val existing = reminders.existingIds(due.map { reminderIdFor(it).value }).toSet()

            var outcome = ScheduleOutcome()
            val toInsert = mutableListOf<Reminder>()

            for (term in due) {
                val id = reminderIdFor(term)
                val member = membersById[term.memberId]
                outcome = when {
                    // A term whose member has been deleted. Not counted as a skip reason staff can
                    // act on, because there is nobody left to act on.
                    member == null -> outcome

                    id.value in existing -> outcome.copy(alreadyQueued = outcome.alreadyQueued + 1)

                    !member.whatsappOptIn ->
                        outcome.copy(skippedNoConsent = outcome.skippedNoConsent + 1)

                    // Reminder.phone is non-null while Member.phone is nullable, and a message
                    // needs somewhere to go.
                    member.phone.isNullOrBlank() ->
                        outcome.copy(skippedNoPhone = outcome.skippedNoPhone + 1)

                    else -> {
                        toInsert += reminderFor(id, member, now)
                        outcome.copy(queued = outcome.queued + 1)
                    }
                }
            }

            if (toInsert.isNotEmpty()) reminders.upsertAll(toInsert.map { it.toEntity() })
            outcome
        }
    }

    private fun reminderFor(id: ReminderId, member: Member, now: kotlin.time.Instant) = Reminder(
        id = id,
        memberId = member.id,
        // Denormalised at write time on purpose -- see Reminder's own KDoc. The queue has to stay
        // readable as the worklist it is, and has to still name who a message was for after the
        // member row changes.
        memberName = member.fullName,
        phone = member.phone.orEmpty(),
        template = templateFor(member),
        scheduledAt = now,
        attempts = 0,
        status = ReminderStatus.QUEUED,
        failure = null,
    )

    /**
     * Arabic unless the member is known to read English.
     *
     * `PAYMENT_DUE` and `MARKETING_PROMO` are never chosen here, for two different reasons.
     * Payment has no trigger — nothing in this app models an unpaid balance — and marketing carries
     * an opt-out obligation that cannot be honoured without inbound message handling, which does
     * not exist. Both stay in the enum because the queue screen filters by them.
     */
    private fun templateFor(member: Member): ReminderTemplate =
        if (member.preferredLanguage == TemplateLanguage.ENGLISH) {
            ReminderTemplate.REMINDER_EN
        } else {
            ReminderTemplate.REMINDER_AR
        }

    private companion object {
        /**
         * One reminder per term, keyed on the term itself.
         *
         * The id *is* the deduplication, following the pattern `SubscriptionPlanSeed` already sets
         * out: a stable id means re-running cannot duplicate rows. Keying on the term rather than on
         * the day means a member is reminded once about a given renewal however many times the
         * queue is built, and becomes eligible again only when they start a new term. Keying on the
         * template instead would let a language change produce a second reminder for one term.
         *
         * Prefixed so a future reminder kind can key on the same term without colliding.
         */
        fun reminderIdFor(term: SubscriptionTerm) = ReminderId("renewal:${term.id.value}")
    }
}
