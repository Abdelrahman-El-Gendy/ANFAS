package com.anfas.core.data

import com.anfas.core.database.SubscriptionDao
import com.anfas.core.database.SubscriptionEntity
import com.anfas.core.database.SubscriptionPlanEntity
import com.anfas.core.database.SyncOutboxEntity
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import com.anfas.core.model.TemplateLanguage
import com.anfas.core.model.TermProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The scheduler is the only thing in the app that creates a reminder, so every rule it applies is
 * a decision someone could quietly reverse later. Each test here pins one of them.
 *
 * Dates are fixed rather than relative to the real today: "expiring soon" is a seven-day window,
 * so a test built on `Clock.System` would pass or fail depending on when it ran.
 */
class ReminderSchedulerTest {

    @Test
    fun `a consenting member with an expiring term is queued`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(1, outcome.queued)
        val written = reminderDao.current.single()
        assertEquals("renewal:t-1", written.id)
        assertEquals(ReminderStatus.QUEUED.name, written.status)
        assertEquals(0, written.attempts)
        // Denormalised at write time, so the queue reads as a worklist without a join.
        assertEquals("Nour Adel", written.memberName)
        assertEquals("01001112222", written.phone)
    }

    /**
     * The rule that makes the whole feature legal. Consent defaults to false, so this is also the
     * behaviour on a real gym's data the first time the button is pressed.
     */
    @Test
    fun `a member who has not consented is skipped rather than queued`() = runTest {
        val scheduler = scheduler(
            members = listOf(memberEntity("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(0, outcome.queued)
        assertEquals(1, outcome.skippedNoConsent)
        assertTrue(reminderDao.current.isEmpty())
    }

    /** `Reminder.phone` is non-null: a message with nowhere to go must not become a row. */
    @Test
    fun `a consenting member with no phone is skipped`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = null)),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(0, outcome.queued)
        assertEquals(1, outcome.skippedNoPhone)
        assertTrue(reminderDao.current.isEmpty())
    }

    /** An already-lapsed membership is money already lost, so it is chased, not ignored. */
    @Test
    fun `an expired term is queued too`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.minusDays(30))),
        )

        assertEquals(1, scheduler.buildQueue().valueOrFail().queued)
    }

    @Test
    fun `a term with plenty of time left is not queued at all`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(
                term("t-1", "m-1", endsOn = today.plusDays(TermProgress.EXPIRING_SOON_DAYS + 30L)),
            ),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(0, outcome.considered, "a term nowhere near expiry should not be considered")
        assertTrue(reminderDao.current.isEmpty())
    }

    /**
     * Idempotence, and the reason the id is derived from the term rather than generated. Building
     * twice must not message anyone twice.
     */
    @Test
    fun `building twice queues one reminder`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
        )

        assertEquals(1, scheduler.buildQueue().valueOrFail().queued)
        val second = scheduler.buildQueue().valueOrFail()

        assertEquals(0, second.queued)
        assertEquals(1, second.alreadyQueued)
        assertEquals(1, reminderDao.current.size)
    }

    /**
     * The specific damage `@Upsert` would do if the scheduler wrote over existing rows: it replaces
     * every column by id, so a rebuild would reset a failed message to QUEUED and throw away the
     * attempt count and the reason it failed.
     */
    @Test
    fun `rebuilding does not resurrect a failed reminder or lose its history`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
            existingReminders = listOf(
                reminderEntity(
                    id = "renewal:t-1",
                    status = ReminderStatus.FAILED.name,
                    attempts = 3,
                ),
            ),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(0, outcome.queued)
        assertEquals(1, outcome.alreadyQueued)
        val row = reminderDao.current.single()
        assertEquals(ReminderStatus.FAILED.name, row.status, "a failed reminder was requeued")
        assertEquals(3, row.attempts, "the attempt history was overwritten")
    }

    @Test
    fun `a new term makes the member eligible again`() = runTest {
        val scheduler = scheduler(
            members = listOf(consenting("m-1", "Nour Adel", phone = "01001112222")),
            terms = listOf(term("t-1", "m-1", endsOn = today.plusDays(3))),
            existingReminders = listOf(reminderEntity(id = "renewal:t-0")),
        )

        // The old term's reminder exists; this term's does not.
        assertEquals(1, scheduler.buildQueue().valueOrFail().queued)
        assertTrue(reminderDao.current.any { it.id == "renewal:t-1" })
    }

    @Test
    fun `Arabic is the template unless the member is known to read English`() = runTest {
        val scheduler = scheduler(
            members = listOf(
                consenting("m-1", "Nour Adel", phone = "01001112222"),
                consenting(
                    "m-2",
                    "Sara Fahmy",
                    phone = "01002223333",
                    language = TemplateLanguage.ENGLISH,
                ),
                consenting(
                    "m-3",
                    "Omar Zaki",
                    phone = "01003334444",
                    language = TemplateLanguage.ARABIC,
                ),
            ),
            terms = listOf(
                term("t-1", "m-1", endsOn = today.plusDays(2)),
                term("t-2", "m-2", endsOn = today.plusDays(2)),
                term("t-3", "m-3", endsOn = today.plusDays(2)),
            ),
        )

        scheduler.buildQueue().valueOrFail()

        val byId = reminderDao.current.associate { it.id to it.template }
        assertEquals(
            ReminderTemplate.REMINDER_AR.name,
            byId["renewal:t-1"],
            "unknown should default to Arabic",
        )
        assertEquals(ReminderTemplate.REMINDER_EN.name, byId["renewal:t-2"])
        assertEquals(ReminderTemplate.REMINDER_AR.name, byId["renewal:t-3"])
    }

    /**
     * Payment has no trigger in this app and marketing carries an opt-out obligation that cannot be
     * honoured without inbound messages, so neither may ever be chosen automatically.
     */
    @Test
    fun `the scheduler never chooses payment or marketing templates`() = runTest {
        val scheduler = scheduler(
            members = (1..3).map { consenting("m-$it", "Member $it", phone = "0100111222$it") },
            terms = (1..3).map { term("t-$it", "m-$it", endsOn = today.plusDays(it.toLong())) },
        )

        scheduler.buildQueue().valueOrFail()

        val chosen = reminderDao.current.map { it.template }.toSet()
        assertTrue(
            ReminderTemplate.PAYMENT_DUE.name !in chosen &&
                ReminderTemplate.MARKETING_PROMO.name !in chosen,
            "scheduler chose a template it must never send automatically: $chosen",
        )
    }

    @Test
    fun `a term whose member no longer exists is ignored`() = runTest {
        val scheduler = scheduler(
            members = emptyList(),
            terms = listOf(term("t-1", "ghost", endsOn = today.plusDays(3))),
        )

        val outcome = scheduler.buildQueue().valueOrFail()

        assertEquals(0, outcome.considered)
        assertTrue(reminderDao.current.isEmpty())
    }

    // ---- harness ----

    private val today = LocalDate(2026, 8, 26)
    private lateinit var reminderDao: FakeReminderDao

    private fun scheduler(
        members: List<com.anfas.core.database.MemberEntity>,
        terms: List<SubscriptionEntity>,
        existingReminders: List<com.anfas.core.database.ReminderEntity> = emptyList(),
    ): ReminderScheduler {
        reminderDao = FakeReminderDao(existingReminders)
        return DefaultReminderScheduler(
            members = FakeMemberDao(members),
            subscriptions = FakeSchedulerSubscriptions(terms),
            reminders = reminderDao,
            dispatchers = UnconfinedDispatchers,
            clock = FixedDayClock(today),
            zone = TimeZone.UTC,
        )
    }

    private fun consenting(
        id: String,
        name: String,
        phone: String?,
        language: TemplateLanguage? = null,
    ) = memberEntity(
        id = id,
        name = name,
        phone = phone,
        whatsappOptIn = true,
        preferredLanguage = language?.name,
    )

    private fun term(id: String, memberId: String, endsOn: LocalDate) = SubscriptionEntity(
        id = id,
        memberId = memberId,
        planId = "monthly",
        tier = "MONTHLY",
        startsOnEpochDay = endsOn.minusDays(30).toEpochDays(),
        endsOnEpochDay = endsOn.toEpochDays(),
        paymentMethod = "CASH",
        paidMinorUnits = 60_000,
        currency = "EGP",
        createdAtEpochMs = 1_700_000_000_000,
    )
}

private fun LocalDate.plusDays(days: Long) = LocalDate.fromEpochDays(toEpochDays() + days)

private fun LocalDate.minusDays(days: Long) = LocalDate.fromEpochDays(toEpochDays() - days)

/** Pins "today" so the seven-day expiry window does not depend on when the suite runs. */
private class FixedDayClock(private val day: LocalDate) : Clock {
    override fun now(): Instant = day.atStartOfDayIn(TimeZone.UTC)
}

private class FakeSchedulerSubscriptions(private val terms: List<SubscriptionEntity>) :
    SubscriptionDao {
    override fun observePlans(): Flow<List<SubscriptionPlanEntity>> = MutableStateFlow(emptyList())

    override suspend fun upsertPlans(plans: List<SubscriptionPlanEntity>) = Unit

    override fun observeCurrent(memberId: String): Flow<SubscriptionEntity?> =
        MutableStateFlow(terms.filter { it.memberId == memberId }.maxByOrNull { it.endsOnEpochDay })

    override fun observeAllCurrent(): Flow<List<SubscriptionEntity>> = MutableStateFlow(terms)

    override suspend fun upsert(subscription: SubscriptionEntity) = Unit

    // --- sync bookkeeping
    val sync = OutboxRecorder()

    override suspend fun recordChange(entry: SyncOutboxEntity) = sync.record(entry)

    override suspend fun insertPlansIfAbsent(plans: List<SubscriptionPlanEntity>) {
        val known = planRowsForSeed.map { it.id }.toSet()
        planRowsForSeed += plans.filterNot { it.id in known }
    }

    override suspend fun planIds(): List<String> = planRowsForSeed.map { it.id }

    /** Plan rows as the seed sees them. Separate from whatever the fake models for reads. */
    val planRowsForSeed = mutableListOf<SubscriptionPlanEntity>()
}
