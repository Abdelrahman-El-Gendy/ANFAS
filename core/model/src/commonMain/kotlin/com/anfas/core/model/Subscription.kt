package com.anfas.core.model

import kotlinx.datetime.LocalDate

/**
 * A purchasable membership duration. Prices live on the plan rather than being computed from a
 * monthly rate, because the design's quarterly and annual tiers are not exact multiples —
 * they carry their own discount ("Save 8%").
 */
data class SubscriptionPlan(
    val id: PlanId,
    val tier: PlanTier,
    val price: Money,
    /** "Full access", "Full access + 2 guest passes". */
    val perks: String,
    /**
     * Percentage saved versus paying monthly for the same span, when the design surfaces it.
     * Null means no badge is shown — not zero, which would render "Save 0%".
     */
    val savingsPercent: Int?,
)

enum class PlanTier(val months: Int) {
    MONTHLY(1),
    QUARTERLY(3),
    ANNUAL(12),
}

enum class PaymentMethod {
    CASH,
    CARD,
    INSTAPAY,
    VODAFONE_CASH,
}

/**
 * When a renewal takes effect. Renewing before the current term ends must not throw away the
 * days already paid for, which is what [WHEN_CURRENT_ENDS] protects.
 */
enum class RenewalStart {
    TODAY,
    WHEN_CURRENT_ENDS,
}

/**
 * A priced, dated renewal the user is about to confirm — the summary block at the bottom of
 * the renewal sheet.
 *
 * [total] is stored rather than derived on read so that what the member was quoted is what
 * gets charged, even if a plan's price changes between quoting and confirming.
 */
data class RenewalQuote(
    val plan: SubscriptionPlan,
    val start: RenewalStart,
    val paymentMethod: PaymentMethod,
    val discount: Money,
    val total: Money,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
)

/**
 * A term a member has bought. Append-only history: the "current" term is simply the one with
 * the latest [endsOn], which is what makes "start when current ends" computable and preserves
 * what was actually charged.
 */
data class SubscriptionTerm(
    val id: SubscriptionId,
    val memberId: MemberId,
    val planId: PlanId,
    val tier: PlanTier,
    val startsOn: LocalDate,
    val endsOn: LocalDate,
    val paymentMethod: PaymentMethod,
    val paid: Money,
)
