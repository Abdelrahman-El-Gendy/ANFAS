package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RenewalCalculatorTest {

    private val today = LocalDate(2026, 8, 21)
    private val monthly = plan(PlanTier.MONTHLY, 600)
    private val quarterly = plan(PlanTier.QUARTERLY, 1_650)
    private val annual = plan(PlanTier.ANNUAL, 5_800)

    @Test
    fun `starting today ends one tier length later`() {
        val quote = quote(quarterly, RenewalStart.TODAY, currentEnd = null)
        assertEquals(today, quote.startsOn)
        assertEquals(LocalDate(2026, 11, 21), quote.endsOn)
    }

    @Test
    fun `starting when the current term ends extends from that date`() {
        val quote = quote(
            quarterly,
            RenewalStart.WHEN_CURRENT_ENDS,
            currentEnd = LocalDate(2026, 9, 12),
        )
        assertEquals(LocalDate(2026, 9, 12), quote.startsOn)
        assertEquals(LocalDate(2026, 12, 12), quote.endsOn)
    }

    @Test
    fun `an already expired term does not backdate the new one`() {
        val quote = quote(
            monthly,
            RenewalStart.WHEN_CURRENT_ENDS,
            currentEnd = LocalDate(2026, 1, 1),
        )
        assertEquals(today, quote.startsOn)
        assertEquals(LocalDate(2026, 9, 21), quote.endsOn)
    }

    @Test
    fun `a member with no current term falls back to today`() {
        val quote = quote(monthly, RenewalStart.WHEN_CURRENT_ENDS, currentEnd = null)
        assertEquals(today, quote.startsOn)
    }

    @Test
    fun `the discount reduces the total and is not the savings badge`() {
        val quote = quote(
            quarterly,
            RenewalStart.TODAY,
            currentEnd = null,
            discount = Money.of(140),
        )
        // Exactly the export's numbers: 1,650 less 140 is 1,510.
        assertEquals(Money.of(1_510), quote.total)
        assertEquals(Money.of(140), quote.discount)
        // The badge is unchanged by the discount.
        assertEquals(8, quarterly.savingsPercent)
    }

    @Test
    fun `a discount larger than the price is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            quote(monthly, RenewalStart.TODAY, currentEnd = null, discount = Money.of(700))
        }
    }

    @Test
    fun `a negative discount is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            quote(monthly, RenewalStart.TODAY, currentEnd = null, discount = Money(-1))
        }
    }

    @Test
    fun `savings compare against paying monthly for the same span`() {
        // 3 x 600 = 1800 versus 1650 -> 8%.
        assertEquals(8, RenewalCalculator.savingsPercent(quarterly, Money.of(600)))
        // 12 x 600 = 7200 versus 5800 -> 19%.
        assertEquals(19, RenewalCalculator.savingsPercent(annual, Money.of(600)))
    }

    @Test
    fun `monthly has no savings badge and neither does a plan that saves nothing`() {
        assertNull(RenewalCalculator.savingsPercent(monthly, Money.of(600)))
        val overpriced = plan(PlanTier.QUARTERLY, 2_000)
        assertNull(RenewalCalculator.savingsPercent(overpriced, Money.of(600)))
    }

    private fun quote(
        plan: SubscriptionPlan,
        start: RenewalStart,
        currentEnd: LocalDate?,
        discount: Money = Money.zero(),
    ) = RenewalCalculator.quote(
        plan = plan,
        start = start,
        paymentMethod = PaymentMethod.CASH,
        today = today,
        currentTermEndsOn = currentEnd,
        discount = discount,
    )

    private fun plan(tier: PlanTier, price: Long) = SubscriptionPlan(
        id = PlanId(tier.name.lowercase()),
        tier = tier,
        price = Money.of(price),
        perks = "Full access",
        savingsPercent = RenewalCalculator.savingsPercent(
            SubscriptionPlan(PlanId("x"), tier, Money.of(price), "", null),
            Money.of(600),
        ),
    )
}
