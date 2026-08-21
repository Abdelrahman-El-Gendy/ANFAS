package com.anfas.core.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Turns a plan choice into the priced, dated quote the renewal sheet shows. Pure — no clock,
 * no storage — so the arithmetic staff rely on is directly testable.
 *
 * The design's two numbers are genuinely different things and must not be conflated:
 *  - [SubscriptionPlan.savingsPercent] is the "Save 8%" badge, comparing a multi-month plan
 *    against paying monthly for the same span. It never changes the price.
 *  - [discount] is an amount a staff member applies to this sale ("Discount −140 EGP").
 *    It is what reduces [RenewalQuote.total].
 */
object RenewalCalculator {

    fun quote(
        plan: SubscriptionPlan,
        start: RenewalStart,
        paymentMethod: PaymentMethod,
        today: LocalDate,
        currentTermEndsOn: LocalDate?,
        discount: Money = Money.zero(plan.price.currency),
    ): RenewalQuote {
        require(discount.currency == plan.price.currency) {
            "Discount currency ${discount.currency.code} does not match plan " +
                "${plan.price.currency.code}"
        }
        require(discount.minorUnits >= 0) { "Discount cannot be negative" }
        require(discount <= plan.price) { "Discount cannot exceed the plan price" }

        val startsOn = when (start) {
            RenewalStart.TODAY -> today

            // Falling back to today when there is no current term is the only sane reading:
            // "when current ends" is meaningless for a member with nothing to extend. Also
            // guards an expired term — never backdate a new term into the past.
            RenewalStart.WHEN_CURRENT_ENDS ->
                currentTermEndsOn?.takeIf { it > today } ?: today
        }

        return RenewalQuote(
            plan = plan,
            start = start,
            paymentMethod = paymentMethod,
            discount = discount,
            total = plan.price - discount,
            startsOn = startsOn,
            endsOn = startsOn.plus(DatePeriod(months = plan.tier.months)),
        )
    }

    /**
     * The "Save 8%" badge: how much cheaper this plan is than paying [MONTHLY] for the same
     * number of months. Returns null when there is nothing to boast about, so no badge is
     * rendered rather than "Save 0%".
     */
    fun savingsPercent(plan: SubscriptionPlan, monthlyPrice: Money): Int? {
        if (plan.tier == PlanTier.MONTHLY) return null
        val payingMonthly = monthlyPrice.minorUnits * plan.tier.months
        if (payingMonthly <= 0) return null
        val saved = payingMonthly - plan.price.minorUnits
        if (saved <= 0) return null
        return ((saved * 100) / payingMonthly).toInt()
    }
}
