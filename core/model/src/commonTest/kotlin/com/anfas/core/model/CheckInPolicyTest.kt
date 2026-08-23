package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CheckInPolicyTest {

    private val today = LocalDate(2024, 6, 15)

    private fun member(status: MembershipStatus = MembershipStatus.ACTIVE) = Member(
        id = MemberId("m-1"),
        fullName = "Omar Hassan",
        membershipNumber = "#10000",
        phone = null,
        status = status,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    private fun term(startsOn: LocalDate, endsOn: LocalDate) = SubscriptionTerm(
        id = SubscriptionId("s-1"),
        memberId = MemberId("m-1"),
        planId = PlanId("p"),
        tier = PlanTier.MONTHLY,
        startsOn = startsOn,
        endsOn = endsOn,
        paymentMethod = PaymentMethod.CASH,
        paid = Money(60_000, Currency.EGP),
    )

    @Test
    fun `an active member inside their term is let in`() {
        val outcome = CheckInPolicy.decide(
            member = member(),
            term = term(LocalDate(2024, 6, 1), LocalDate(2024, 7, 1)),
            today = today,
        )

        assertEquals(CheckInOutcome.GRANTED, outcome)
        assertTrue(outcome.grantsEntry)
    }

    /**
     * The disagreement that costs money. A member left marked ACTIVE whose term lapsed last
     * month must still be refused, or the gym gives away the renewal it is trying to sell.
     */
    @Test
    fun `an active member whose term has lapsed is refused`() {
        assertEquals(
            CheckInOutcome.EXPIRED,
            CheckInPolicy.decide(
                member = member(MembershipStatus.ACTIVE),
                term = term(LocalDate(2024, 4, 1), LocalDate(2024, 5, 1)),
                today = today,
            ),
        )
    }

    /**
     * A term bought in advance means they have paid. Turning away a paying member because their
     * plan starts on Monday is not a rule anyone would defend at the desk.
     */
    @Test
    fun `a term that has not started yet still grants entry`() {
        assertEquals(
            CheckInOutcome.GRANTED,
            CheckInPolicy.decide(
                member = member(),
                term = term(LocalDate(2024, 7, 1), LocalDate(2024, 8, 1)),
                today = today,
            ),
        )
    }

    /** Imported from a paper sheet: active, but nothing sold yet. Not the same as lapsed. */
    @Test
    fun `a member with no term at all is NO_MEMBERSHIP rather than EXPIRED`() {
        assertEquals(
            CheckInOutcome.NO_MEMBERSHIP,
            CheckInPolicy.decide(member = member(), term = null, today = today),
        )
    }

    /** A deliberate decision about this person outranks whatever the dates say. */
    @Test
    fun `suspension and pause outrank a valid term`() {
        val valid = term(LocalDate(2024, 6, 1), LocalDate(2024, 7, 1))

        assertEquals(
            CheckInOutcome.SUSPENDED,
            CheckInPolicy.decide(member(MembershipStatus.SUSPENDED), valid, today),
        )
        assertEquals(
            CheckInOutcome.PAUSED,
            CheckInPolicy.decide(member(MembershipStatus.PAUSED), valid, today),
        )
    }

    @Test
    fun `the last day of a term still grants entry`() {
        assertEquals(
            CheckInOutcome.GRANTED,
            CheckInPolicy.decide(member(), term(LocalDate(2024, 6, 1), today), today),
        )
    }

    @Test
    fun `no outcome other than GRANTED lets anybody in`() {
        CheckInOutcome.entries.filter { it != CheckInOutcome.GRANTED }.forEach {
            assertTrue(!it.grantsEntry, "$it must not grant entry")
        }
    }
}

class CheckInSummaryTest {

    private fun checkIn(id: String, hour: Int, outcome: CheckInOutcome) = CheckIn(
        id = CheckInId(id),
        memberId = MemberId("m-$id"),
        memberName = "Member $id",
        membershipNumber = "#$id",
        at = Instant.fromEpochMilliseconds(hour * 3_600_000L),
        outcome = outcome,
    )

    private val hourOf: (Instant) -> Int = { (it.toEpochMilliseconds() / 3_600_000L).toInt() }

    @Test
    fun `an empty day reports nothing rather than zero-hour`() {
        val summary = CheckInSummary.of(emptyList(), hourOf)

        assertEquals(0, summary.total)
        assertEquals(null, summary.peakHour, "before anyone arrives there is no peak")
    }

    @Test
    fun `granted and denied are counted separately`() {
        val summary = CheckInSummary.of(
            listOf(
                checkIn("1", 9, CheckInOutcome.GRANTED),
                checkIn("2", 9, CheckInOutcome.EXPIRED),
                checkIn("3", 17, CheckInOutcome.GRANTED),
            ),
            hourOf,
        )

        assertEquals(2, summary.granted)
        assertEquals(1, summary.denied)
        assertEquals(3, summary.total)
    }

    /** Denials do not make an hour busy — nobody trained then. */
    @Test
    fun `the peak hour counts only granted entries`() {
        val summary = CheckInSummary.of(
            listOf(
                checkIn("1", 6, CheckInOutcome.EXPIRED),
                checkIn("2", 6, CheckInOutcome.EXPIRED),
                checkIn("3", 6, CheckInOutcome.SUSPENDED),
                checkIn("4", 18, CheckInOutcome.GRANTED),
            ),
            hourOf,
        )

        assertEquals(18, summary.peakHour)
    }

    /** "Busiest since" reads better than an arbitrary pick, and is when the queue formed. */
    @Test
    fun `a tie resolves to the earlier hour`() {
        val summary = CheckInSummary.of(
            listOf(
                checkIn("1", 7, CheckInOutcome.GRANTED),
                checkIn("2", 19, CheckInOutcome.GRANTED),
            ),
            hourOf,
        )

        assertEquals(7, summary.peakHour)
    }
}
