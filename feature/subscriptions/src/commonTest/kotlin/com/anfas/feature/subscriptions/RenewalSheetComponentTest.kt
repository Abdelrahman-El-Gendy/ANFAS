package com.anfas.feature.subscriptions

import app.cash.turbine.test
import com.anfas.core.model.MemberId
import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.RenewalStart
import com.anfas.core.model.SubscriptionId
import com.anfas.core.model.SubscriptionTerm
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RenewalSheetComponentTest {

    /** 2026-08-21T09:00Z — fixed so the quoted dates are assertable. */
    private val fixedNow = Instant.parse("2026-08-21T09:00:00Z")
    private val zone = TimeZone.UTC

    private val monthly = plan(PlanTier.MONTHLY, 600)
    private val quarterly = plan(PlanTier.QUARTERLY, 1_650, savings = 8)
    private val annual = plan(PlanTier.ANNUAL, 5_800, savings = 19)

    @Test
    fun `preselects the plan that carries a savings badge`() = runTest {
        val component = component()

        component.state.test {
            val state = awaitItem()
            assertEquals(PlanId("quarterly"), state.selectedPlanId)
            assertEquals(Money.of(1_650), assertNotNull(state.quote).total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the default start extends the current term rather than starting today`() = runTest {
        val component = component(currentEnd = LocalDate(2026, 9, 12))

        component.state.test {
            val quote = assertNotNull(awaitItem().quote)
            assertEquals(RenewalStart.WHEN_CURRENT_ENDS, quote.start)
            assertEquals(LocalDate(2026, 9, 12), quote.startsOn)
            assertEquals(LocalDate(2026, 12, 12), quote.endsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing start today recomputes the quote from today`() = runTest {
        val component = component(currentEnd = LocalDate(2026, 9, 12))

        component.state.test {
            awaitItem()
            component.onStartSelected(RenewalStart.TODAY)
            val quote = assertNotNull(awaitItem().quote)
            assertEquals(LocalDate(2026, 8, 21), quote.startsOn)
            assertEquals(LocalDate(2026, 11, 21), quote.endsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the plan reprices and redates in one step`() = runTest {
        val component = component(currentEnd = LocalDate(2026, 9, 12))

        component.state.test {
            awaitItem()
            component.onPlanSelected(PlanId("annual"))
            val quote = assertNotNull(awaitItem().quote)
            assertEquals(Money.of(5_800), quote.total)
            assertEquals(LocalDate(2027, 9, 12), quote.endsOn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a discount reduces the total without touching the plan price`() = runTest {
        val component = component()

        component.state.test {
            awaitItem()
            component.onDiscountChanged(Money.of(140))
            val quote = assertNotNull(awaitItem().quote)
            assertEquals(Money.of(1_650), quote.plan.price)
            assertEquals(Money.of(140), quote.discount)
            assertEquals(Money.of(1_510), quote.total)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirming hands the repository exactly the quote that was displayed`() = runTest {
        val subscriptions = FakeSubscriptionRepository(
            plans = listOf(monthly, quarterly, annual),
            currentTerm = term(LocalDate(2026, 9, 12)),
        )
        var renewed: SubscriptionTerm? = null
        val component = component(subscriptions = subscriptions, onRenewed = { renewed = it })

        component.state.test {
            val shown = assertNotNull(awaitItem().quote)
            component.onConfirm()
            cancelAndIgnoreRemainingEvents()

            val (memberId, sent) = assertNotNull(subscriptions.confirmed)
            assertEquals(MemberId("m-1"), memberId)
            assertEquals(shown, sent)
            assertEquals(shown.endsOn, assertNotNull(renewed).endsOn)
            assertEquals(shown.total, assertNotNull(renewed).paid)
        }
    }

    @Test
    fun `payment method is carried into the quote`() = runTest {
        val component = component()

        component.state.test {
            awaitItem()
            component.onPaymentMethodSelected(PaymentMethod.INSTAPAY)
            assertEquals(PaymentMethod.INSTAPAY, assertNotNull(awaitItem().quote).paymentMethod)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirm is unavailable when there are no plans to price`() = runTest {
        val component = component(plans = emptyList())

        component.state.test {
            val state = awaitItem()
            assertTrue(state.quote == null)
            assertTrue(!state.canConfirm)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun TestScope.component(
        plans: List<com.anfas.core.model.SubscriptionPlan> = listOf(monthly, quarterly, annual),
        currentEnd: LocalDate? = null,
        subscriptions: FakeSubscriptionRepository = FakeSubscriptionRepository(
            plans = plans,
            currentTerm = currentEnd?.let(::term),
        ),
        onRenewed: (SubscriptionTerm) -> Unit = {},
    ): RenewalSheetComponent {
        val lifecycle = LifecycleRegistry()
        val component = RenewalSheetComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            memberId = MemberId("m-1"),
            members = FakeMemberRepository(member()),
            subscriptions = subscriptions,
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onRenewed = onRenewed,
            onCancelled = {},
            clock = FixedClock(fixedNow),
            timeZone = zone,
            newTermId = { "term-1" },
        )
        lifecycle.resume()
        return component
    }

    private fun term(endsOn: LocalDate) = SubscriptionTerm(
        id = SubscriptionId("existing"),
        memberId = MemberId("m-1"),
        planId = PlanId("monthly"),
        tier = PlanTier.MONTHLY,
        startsOn = endsOn,
        endsOn = endsOn,
        paymentMethod = PaymentMethod.CASH,
        paid = Money.of(600),
    )

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }
}
