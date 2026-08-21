package com.anfas.core.data

import com.anfas.core.model.Money
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.RenewalCalculator
import com.anfas.core.model.SubscriptionPlan

/**
 * The initial plan catalogue, taken from the renewal sheet in the Stitch export.
 *
 * Plans live in the database so a price change needs no app build, but the table has to start
 * with something — there is no plan-management screen and no remote catalogue yet. Seeding is
 * an upsert keyed by [PlanId], so re-running it cannot duplicate rows, and once a real source
 * of truth exists this becomes its first sync.
 *
 * One departure from the export: it badges only Quarterly with "Save 8%", but Annual saves
 * more (19% against paying monthly). Savings are computed for every tier here rather than
 * hardcoded, because hiding a genuine 19% discount would cost the member money. Suppress it
 * by storing null if the design really means to show only one badge.
 */
object SubscriptionPlanSeed {

    private val monthlyPrice = Money.of(600)

    val plans: List<SubscriptionPlan> by lazy {
        val base = listOf(
            draft(PlanTier.MONTHLY, monthlyPrice, "Full access"),
            draft(PlanTier.QUARTERLY, Money.of(1_650), "Full access"),
            draft(PlanTier.ANNUAL, Money.of(5_800), "Full access + 2 guest passes"),
        )
        base.map { plan ->
            plan.copy(savingsPercent = RenewalCalculator.savingsPercent(plan, monthlyPrice))
        }
    }

    private fun draft(tier: PlanTier, price: Money, perks: String) = SubscriptionPlan(
        // Stable ids so the upsert updates rather than inserting a parallel row.
        id = PlanId(tier.name.lowercase()),
        tier = tier,
        price = price,
        perks = perks,
        savingsPercent = null,
    )
}
