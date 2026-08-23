package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.CheckInDao
import com.anfas.core.database.CheckInEntity
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.database.SubscriptionEntity
import com.anfas.core.database.SubscriptionPlanEntity
import com.anfas.core.model.CheckInOutcome
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CheckInRepositoryTest {

    private val zone = TimeZone.UTC
    private val today = LocalDate(2024, 6, 15)

    /** 09:00 UTC on [today], so day bounds and peak hour are both exercised. */
    private val at = Instant.fromEpochSeconds(1_718_442_000)

    @Test
    fun `an active member inside their term is granted and their last-seen stamp moves`() =
        runTest {
            val members = FakeMemberDao(listOf(memberEntity("m1", "Omar")))
            val repo = repository(
                members = members,
                terms = listOf(term("m1", LocalDate(2024, 6, 1), LocalDate(2024, 7, 1))),
            )

            val checkIn = repo.recordAttempt(MemberId("m1"), today).valueOrFail()

            assertEquals(CheckInOutcome.GRANTED, checkIn.outcome)
            assertEquals(at.toEpochMilliseconds(), members.current.single().lastCheckInAtEpochMs)
        }

    /**
     * The record that matters most. A refused attempt is the moment someone was turned away,
     * which staff get asked about later — and it must not move the last-seen stamp, or the
     * directory would claim an expired member trained today.
     */
    @Test
    fun `a refused attempt is still recorded but is not counted as a visit`() = runTest {
        val members = FakeMemberDao(listOf(memberEntity("m1", "Omar")))
        val log = FakeCheckInDao()
        val repo = repository(
            members = members,
            checkIns = log,
            terms = listOf(term("m1", LocalDate(2024, 4, 1), LocalDate(2024, 5, 1))),
        )

        val checkIn = repo.recordAttempt(MemberId("m1"), today).valueOrFail()

        assertEquals(CheckInOutcome.EXPIRED, checkIn.outcome)
        assertEquals(1, log.rows.value.size, "the attempt is logged")
        assertNull(members.current.single().lastCheckInAtEpochMs, "but it is not a visit")
    }

    /** Copied onto the row, so deleting the member cannot rewrite who walked in. */
    @Test
    fun `the entry keeps the name and number it was recorded with`() = runTest {
        val members = FakeMemberDao(listOf(memberEntity("m1", "Omar Hassan")))
        val log = FakeCheckInDao()
        val repo = repository(members = members, checkIns = log)

        repo.recordAttempt(MemberId("m1"), today).valueOrFail()
        members.deleteById("m1")

        val row = log.rows.value.single()
        assertEquals("Omar Hassan", row.memberName)
        assertEquals("#m1", row.membershipNumber)
    }

    @Test
    fun `a suspended member is refused whatever their dates say`() = runTest {
        val repo = repository(
            members = FakeMemberDao(
                listOf(memberEntity("m1", "Omar", status = MembershipStatus.SUSPENDED.name)),
            ),
            terms = listOf(term("m1", LocalDate(2024, 6, 1), LocalDate(2024, 7, 1))),
        )

        assertEquals(
            CheckInOutcome.SUSPENDED,
            repo.recordAttempt(MemberId("m1"), today).valueOrFail().outcome,
        )
    }

    @Test
    fun `the day summary counts granted and denied and finds the peak hour`() = runTest {
        val log = FakeCheckInDao(
            listOf(
                entity("1", hourUtc = 9, CheckInOutcome.GRANTED),
                entity("2", hourUtc = 9, CheckInOutcome.GRANTED),
                entity("3", hourUtc = 9, CheckInOutcome.EXPIRED),
                entity("4", hourUtc = 18, CheckInOutcome.GRANTED),
            ),
        )

        val summary = repository(checkIns = log).observeDaySummary(today).first().valueOrFail()

        assertEquals(3, summary.granted)
        assertEquals(1, summary.denied)
        assertEquals(9, summary.peakHour)
    }

    /**
     * Yesterday's evening must not reappear in today's log. The bounds are half-open in the
     * device's zone, which is why they are computed rather than stored as a date column.
     */
    @Test
    fun `only entries inside the local day are returned`() = runTest {
        val log = FakeCheckInDao(
            listOf(
                entity("yesterday", hourUtc = -1, CheckInOutcome.GRANTED),
                entity("today", hourUtc = 9, CheckInOutcome.GRANTED),
                entity("tomorrow", hourUtc = 24, CheckInOutcome.GRANTED),
            ),
        )

        val day = repository(checkIns = log).observeDay(today).first().valueOrFail()

        assertEquals(listOf("today"), day.map { it.id.value })
    }

    /** A newer build could write an outcome this one does not know; never read it as entry. */
    @Test
    fun `an unrecognised outcome is read as a denial`() = runTest {
        val log = FakeCheckInDao(
            listOf(entity("1", hourUtc = 9, CheckInOutcome.GRANTED).copy(outcome = "FUTURE")),
        )

        val entry = repository(checkIns = log).observeDay(today).first().valueOrFail().single()

        assertTrue(!entry.wasGranted)
    }

    private fun repository(
        members: FakeMemberDao = FakeMemberDao(listOf(memberEntity("m1", "Omar"))),
        checkIns: FakeCheckInDao = FakeCheckInDao(),
        terms: List<SubscriptionEntity> = emptyList(),
    ): CheckInRepository {
        var next = 0
        return OfflineFirstCheckInRepository(
            dao = checkIns,
            memberDao = members,
            subscriptionDao = FakeSubscriptionDao(terms),
            dispatchers = UnconfinedDispatchers,
            zone = zone,
            newId = { "c-${next++}" },
            now = { at },
        )
    }

    private fun term(memberId: String, startsOn: LocalDate, endsOn: LocalDate) = SubscriptionEntity(
        id = "s-$memberId",
        memberId = memberId,
        planId = "p",
        tier = "MONTHLY",
        startsOnEpochDay = startsOn.toEpochDays(),
        endsOnEpochDay = endsOn.toEpochDays(),
        paymentMethod = "CASH",
        paidMinorUnits = 60_000,
        currency = "EGP",
        createdAtEpochMs = 0,
    )

    private fun entity(id: String, hourUtc: Int, outcome: CheckInOutcome) = CheckInEntity(
        id = id,
        memberId = "m1",
        memberName = "Omar",
        membershipNumber = "#m1",
        atEpochMs = at.toEpochMilliseconds() + (hourUtc - 9) * 3_600_000L,
        outcome = outcome.name,
    )
}

private class FakeCheckInDao(initial: List<CheckInEntity> = emptyList()) : CheckInDao {
    val rows = MutableStateFlow(initial)

    override fun observeBetween(fromEpochMs: Long, untilEpochMs: Long): Flow<List<CheckInEntity>> =
        rows.map { list ->
            list.filter { it.atEpochMs >= fromEpochMs && it.atEpochMs < untilEpochMs }
                .sortedByDescending { it.atEpochMs }
        }

    override fun observeForMember(memberId: String, limit: Int): Flow<List<CheckInEntity>> =
        rows.map { list ->
            list.filter { it.memberId == memberId }.sortedByDescending { it.atEpochMs }.take(limit)
        }

    override fun observeGrantedCount(
        memberId: String,
        fromEpochMs: Long,
        untilEpochMs: Long,
    ): Flow<Int> = rows.map { list ->
        list.count {
            it.memberId == memberId &&
                it.outcome == "GRANTED" &&
                it.atEpochMs >= fromEpochMs &&
                it.atEpochMs < untilEpochMs
        }
    }

    override suspend fun insert(checkIn: CheckInEntity) {
        rows.value = rows.value + checkIn
    }
}

private class FakeSubscriptionDao(private val terms: List<SubscriptionEntity>) : SubscriptionDao {
    override fun observePlans(): Flow<List<SubscriptionPlanEntity>> = MutableStateFlow(emptyList())

    override suspend fun upsertPlans(plans: List<SubscriptionPlanEntity>) = Unit

    override fun observeCurrent(memberId: String): Flow<SubscriptionEntity?> =
        MutableStateFlow(terms.filter { it.memberId == memberId }.maxByOrNull { it.endsOnEpochDay })

    override fun observeAllCurrent(): Flow<List<SubscriptionEntity>> = MutableStateFlow(terms)

    override suspend fun upsert(subscription: SubscriptionEntity) = Unit
}
