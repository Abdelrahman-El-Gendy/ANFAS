package com.anfas.feature.subscriptions

import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.RenewalStart
import com.anfas.core.model.SubscriptionPlan
import kotlinx.datetime.LocalDate

/**
 * The renewal bottom sheet.
 *
 * [quote] is derived, not chosen — it is recomputed from the plan, start and discount by
 * [com.anfas.core.model.RenewalCalculator] whenever any of those change, so the summary block
 * and the confirm action can never disagree about the price.
 */
data class RenewalSheetState(
    val memberName: String = "",
    val currentTermEndsOn: LocalDate? = null,
    val plans: List<SubscriptionPlan> = emptyList(),
    val selectedPlanId: PlanId? = null,
    val start: RenewalStart = RenewalStart.WHEN_CURRENT_ENDS,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val discount: Money = Money.zero(),
    val sendWhatsAppConfirmation: Boolean = true,
    val quote: RenewalQuote? = null,
    val isLoading: Boolean = true,
    val isConfirming: Boolean = false,
    val error: String? = null,
) {
    val selectedPlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.id == selectedPlanId }

    /** Confirm stays disabled until there is something priced to confirm. */
    val canConfirm: Boolean get() = quote != null && !isConfirming && !isLoading
}
