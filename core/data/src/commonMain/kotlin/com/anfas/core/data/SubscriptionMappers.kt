package com.anfas.core.data

import com.anfas.core.database.SubscriptionEntity
import com.anfas.core.database.SubscriptionPlanEntity
import com.anfas.core.model.Currency
import com.anfas.core.model.MemberId
import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.SubscriptionId
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import kotlinx.datetime.LocalDate

internal fun SubscriptionPlanEntity.toDomain(): SubscriptionPlan = SubscriptionPlan(
    id = PlanId(id),
    tier = PlanTier.entries.firstOrNull { it.name == tier } ?: PlanTier.MONTHLY,
    price = Money(priceMinorUnits, currency.toCurrency()),
    perks = perks,
    // A stored 0 means "no badge", same as null — never render "Save 0%".
    savingsPercent = savingsPercent?.takeIf { it > 0 },
)

internal fun SubscriptionPlan.toEntity(): SubscriptionPlanEntity = SubscriptionPlanEntity(
    id = id.value,
    tier = tier.name,
    priceMinorUnits = price.minorUnits,
    currency = price.currency.code,
    perks = perks,
    savingsPercent = savingsPercent,
)

internal fun SubscriptionEntity.toDomain(): SubscriptionTerm = SubscriptionTerm(
    id = SubscriptionId(id),
    memberId = MemberId(memberId),
    planId = PlanId(planId),
    tier = PlanTier.entries.firstOrNull { it.name == tier } ?: PlanTier.MONTHLY,
    startsOn = LocalDate.fromEpochDays(startsOnEpochDay),
    endsOn = LocalDate.fromEpochDays(endsOnEpochDay),
    paymentMethod = PaymentMethod.entries.firstOrNull { it.name == paymentMethod }
        ?: PaymentMethod.CASH,
    paid = Money(paidMinorUnits, currency.toCurrency()),
)

private fun String.toCurrency(): Currency =
    Currency.entries.firstOrNull { it.code == this } ?: Currency.EGP
