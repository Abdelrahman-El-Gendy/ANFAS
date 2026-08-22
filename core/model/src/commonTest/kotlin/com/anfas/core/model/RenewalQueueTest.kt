package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenewalQueueTest {

    private val today = LocalDate(2024, 6, 15)

    private fun term(id: String, endsOn: LocalDate) = SubscriptionTerm(
        id = SubscriptionId(id),
        memberId = MemberId("m-$id"),
        planId = PlanId("p"),
        tier = PlanTier.MONTHLY,
        startsOn = endsOn.minusMonthsApprox(),
        endsOn = endsOn,
        paymentMethod = PaymentMethod.CASH,
        paid = Money(60_000, Currency.EGP),
    )

    private fun LocalDate.minusMonthsApprox() = LocalDate(year, month, 1)

    @Test
    fun `only terms expiring soon or already expired are queued`() {
        val queue = RenewalQueue.needingAttention(
            terms = listOf(
                term("far", LocalDate(2024, 12, 31)),
                term("soon", LocalDate(2024, 6, 18)),
                term("lapsed", LocalDate(2024, 6, 1)),
            ),
            today = today,
        )

        assertEquals(listOf("lapsed", "soon"), queue.map { it.term.id.value })
    }

    /**
     * A membership that lapsed last week is more urgent than one lapsing tomorrow, so it is
     * included and sorted first — money the gym has already lost, which a queue that hid it
     * would leave uncollected.
     */
    @Test
    fun `expired terms sort ahead of merely expiring ones`() {
        val queue = RenewalQueue.needingAttention(
            terms = listOf(
                term("soon", LocalDate(2024, 6, 20)),
                term("lapsed", LocalDate(2024, 5, 1)),
            ),
            today = today,
        )

        assertTrue(queue.first().hasExpired)
        assertEquals("lapsed", queue.first().term.id.value)
    }

    @Test
    fun `a term ending today still counts as needing attention`() {
        val queue = RenewalQueue.needingAttention(listOf(term("t", today)), today)

        assertEquals(1, queue.size)
        assertTrue(!queue.first().hasExpired, "valid until close of business is not expired")
    }

    @Test
    fun `nothing is queued when every membership is comfortable`() {
        assertEquals(
            0,
            RenewalQueue.countNeedingAttention(listOf(term("a", LocalDate(2025, 1, 1))), today),
        )
    }

    /** The same rule as the profile pill, so the two cannot disagree. */
    @Test
    fun `the boundary matches TermProgress expiring-soon`() {
        val boundary = LocalDate(2024, 6, 21) // today + 6, inclusive end -> 7 days remaining
        assertEquals(1, RenewalQueue.countNeedingAttention(listOf(term("b", boundary)), today))

        val justOutside = LocalDate(2024, 6, 22)
        assertEquals(0, RenewalQueue.countNeedingAttention(listOf(term("c", justOutside)), today))
    }
}
