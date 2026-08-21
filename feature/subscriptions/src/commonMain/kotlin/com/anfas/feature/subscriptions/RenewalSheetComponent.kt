package com.anfas.feature.subscriptions

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.MemberId
import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.RenewalCalculator
import com.anfas.core.model.RenewalStart
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Renewing one member's subscription.
 *
 * [clock] and [timeZone] are injected so "today" is testable — a renewal's dates are the whole
 * point of this screen, and a test must not depend on the machine's calendar.
 *
 * [newTermId] is a parameter for the same reason: the confirmed term's identity is generated,
 * and a test needs it to be predictable.
 */
class RenewalSheetComponent(
    componentContext: ComponentContext,
    private val memberId: MemberId,
    private val members: MemberRepository,
    private val subscriptions: SubscriptionRepository,
    dispatchers: AppDispatchers,
    private val onRenewed: (SubscriptionTerm) -> Unit,
    private val onCancelled: () -> Unit,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val newTermId: () -> String = { Uuid.random().toString() },
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("RenewalSheet"))

    private val choices = MutableStateFlow(Choices())

    val state: StateFlow<RenewalSheetState> = combine(
        choices,
        members.observeMember(memberId),
        subscriptions.observePlans(),
        subscriptions.observeCurrentTerm(memberId),
    ) { chosen, memberResult, plansResult, termResult ->
        val plans = (plansResult as? AppResult.Success)?.value.orEmpty()
        val currentEnd = (termResult as? AppResult.Success)?.value?.endsOn
        // Default to the middle tier the way the export does, but only once plans exist.
        val selectedId = chosen.planId ?: plans.defaultSelection()?.id
        val plan = plans.firstOrNull { it.id == selectedId }

        RenewalSheetState(
            memberName = (memberResult as? AppResult.Success)?.value?.fullName.orEmpty(),
            currentTermEndsOn = currentEnd,
            plans = plans,
            selectedPlanId = selectedId,
            start = chosen.start,
            paymentMethod = chosen.paymentMethod,
            discount = chosen.discount,
            sendWhatsAppConfirmation = chosen.sendWhatsApp,
            quote = plan?.let {
                RenewalCalculator.quote(
                    plan = it,
                    start = chosen.start,
                    paymentMethod = chosen.paymentMethod,
                    today = clock.todayIn(timeZone),
                    currentTermEndsOn = currentEnd,
                    discount = chosen.discount.coerceToCurrencyOf(it),
                )
            },
            isLoading = plansResult is AppResult.Success && plans.isEmpty(),
            isConfirming = chosen.isConfirming,
            error = chosen.error
                ?: (plansResult as? AppResult.Failure)?.error?.message
                ?: (memberResult as? AppResult.Failure)?.error?.message,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RenewalSheetState(),
    )

    fun onPlanSelected(id: PlanId) = choices.update { it.copy(planId = id, error = null) }

    fun onStartSelected(start: RenewalStart) = choices.update { it.copy(start = start) }

    fun onPaymentMethodSelected(method: PaymentMethod) =
        choices.update { it.copy(paymentMethod = method) }

    fun onDiscountChanged(discount: Money) = choices.update { it.copy(discount = discount) }

    fun onSendWhatsAppChanged(send: Boolean) = choices.update { it.copy(sendWhatsApp = send) }

    fun onCancel() = onCancelled()

    fun onConfirm() {
        val quote = state.value.quote ?: return
        if (choices.value.isConfirming) return
        choices.update { it.copy(isConfirming = true, error = null) }
        scope.launch {
            val result = subscriptions.confirmRenewal(
                memberId = memberId,
                quote = quote,
                termId = newTermId(),
                confirmedAtEpochMs = clock.now().toEpochMilliseconds(),
            )
            when (result) {
                is AppResult.Failure -> choices.update {
                    it.copy(isConfirming = false, error = result.error.message)
                }

                is AppResult.Success -> {
                    choices.update { it.copy(isConfirming = false) }
                    // Sending the WhatsApp confirmation is not wired up — there is no send
                    // job yet. The choice is captured in state so the flag is ready when it is.
                    onRenewed(result.value)
                }
            }
        }
    }

    private data class Choices(
        val planId: PlanId? = null,
        val start: RenewalStart = RenewalStart.WHEN_CURRENT_ENDS,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
        val discount: Money = Money.zero(),
        val sendWhatsApp: Boolean = true,
        val isConfirming: Boolean = false,
        val error: String? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The export shows the middle tier pre-selected, which is also the one carrying the savings
 * badge. Falls back to the cheapest when there is no middle.
 */
private fun List<SubscriptionPlan>.defaultSelection(): SubscriptionPlan? =
    firstOrNull { it.savingsPercent != null } ?: firstOrNull()

/** A discount carried over from another plan's currency would throw inside the calculator. */
private fun Money.coerceToCurrencyOf(plan: SubscriptionPlan): Money =
    if (currency == plan.price.currency) this else Money.zero(plan.price.currency)
