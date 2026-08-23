package com.anfas.feature.dashboard

import app.cash.turbine.test
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.CheckInRepository
import com.anfas.core.data.ImportOutcome
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.ReminderCounts
import com.anfas.core.data.ReminderRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.Currency
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.SubscriptionId
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class DashboardComponentTest {

    /** Relative to today, so the "expiring soon" window is exercised rather than a fixed date. */
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    @Test
    fun `tiles count active members and the renewal queue`() = runTest {
        val component = component(
            members = listOf(
                member("1", "Omar", MembershipStatus.ACTIVE),
                member("2", "Sara", MembershipStatus.ACTIVE),
                member("3", "Youssef", MembershipStatus.EXPIRED),
            ),
            terms = listOf(term("1", today.plusDays(3)), term("2", today.plusDays(90))),
            counts = ReminderCounts(failed = 2),
        )

        component.state.test {
            val state = awaitSettled()
            assertEquals(2, state.activeMembers)
            assertEquals(3, state.totalMembers)
            assertEquals(1, state.needingRenewal, "only the term inside the week counts")
            assertEquals(2, state.failedReminders)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the queue pairs each expiring term with its member`() = runTest {
        val component = component(
            members = listOf(member("1", "Omar", MembershipStatus.ACTIVE)),
            terms = listOf(term("1", today.plusDays(2))),
        )

        component.state.test {
            val row = awaitSettled().renewalQueue.single()
            assertEquals("Omar", row.member.fullName)
            assertEquals(MemberId("1"), row.expiring.term.memberId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A term whose member is gone cannot be rendered or acted on. Showing a row with no name is
     * worse than omitting it — the dangling term is a data problem to fix elsewhere.
     */
    @Test
    fun `a term whose member is missing is dropped from the queue`() = runTest {
        val component = component(
            members = emptyList(),
            terms = listOf(term("ghost", today.plusDays(1))),
        )

        component.state.test {
            val state = awaitSettled()
            assertEquals(emptyList(), state.renewalQueue)
            // The tile still counts it: the membership really is expiring, and hiding the count
            // would make the data problem invisible.
            assertEquals(1, state.needingRenewal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `all clear when nothing expires and nothing failed`() = runTest {
        val component = component(
            members = listOf(member("1", "Omar", MembershipStatus.ACTIVE)),
            terms = listOf(term("1", today.plusDays(200))),
            counts = ReminderCounts(sent = 10),
        )

        component.state.test {
            assertTrue(awaitSettled().allClear)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * One failing seam fails the whole screen. A dashboard that silently omits a tile is worse
     * than one that says it could not load: the missing number reads as zero, and zero here
     * means "nothing to chase".
     */
    @Test
    fun `a failure in any seam is reported rather than shown as zero`() = runTest {
        val component = component(
            members = listOf(member("1", "Omar", MembershipStatus.ACTIVE)),
            terms = listOf(term("1", today.plusDays(1))),
            countsFailure = AppError.Storage("counts unavailable"),
        )

        component.state.test {
            val state = awaitSettled()
            assertEquals("counts unavailable", state.error)
            assertEquals(0, state.needingRenewal, "no partial figures are offered")
            assertTrue(!state.allClear, "an error must never read as all clear")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping failed reminders routes to the queue`() = runTest {
        var opened = 0
        val component =
            component(counts = ReminderCounts(failed = 1), onOpenReminders = { opened++ })

        component.onFailedRemindersClicked()

        assertEquals(1, opened)
    }

    /** The metric check-in unlocked. Denials are reported separately, not folded into entries. */
    @Test
    fun `today's entries and refusals are reported separately`() = runTest {
        val component = component(checkedIn = 42, turnedAway = 3)

        component.state.test {
            val state = awaitSettled()
            assertEquals(42, state.checkedInToday)
            assertEquals(3, state.turnedAwayToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.TurbineTestContext<DashboardState>.awaitSettled():
        DashboardState {
        repeat(EMISSIONS) {
            val state = awaitItem()
            if (!state.isLoading) return state
        }
        error("still loading after $EMISSIONS emissions")
    }

    private fun TestScope.component(
        members: List<Member> = emptyList(),
        terms: List<SubscriptionTerm> = emptyList(),
        counts: ReminderCounts = ReminderCounts(),
        countsFailure: AppError? = null,
        checkedIn: Int = 0,
        turnedAway: Int = 0,
        onOpenReminders: () -> Unit = {},
    ): DashboardComponent {
        val lifecycle = LifecycleRegistry()
        val component = DashboardComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            members = FakeMembers(members),
            subscriptions = FakeSubscriptions(terms),
            reminders = FakeReminders(counts, countsFailure),
            checkIns = FakeCheckIns(checkedIn = checkedIn, turnedAway = turnedAway),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onMemberClicked = {},
            onOpenReminders = onOpenReminders,
        )
        lifecycle.resume()
        return component
    }

    private fun member(id: String, name: String, status: MembershipStatus) = Member(
        id = MemberId(id),
        fullName = name,
        membershipNumber = "#$id",
        phone = null,
        status = status,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    private fun term(memberId: String, endsOn: LocalDate) = SubscriptionTerm(
        id = SubscriptionId("s-$memberId"),
        memberId = MemberId(memberId),
        planId = PlanId("p"),
        tier = PlanTier.MONTHLY,
        startsOn = today,
        endsOn = endsOn,
        paymentMethod = PaymentMethod.CASH,
        paid = Money(60_000, Currency.EGP),
    )
}

private const val EMISSIONS = 6

private fun LocalDate.plusDays(days: Int): LocalDate =
    kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + days)

private class FakeMembers(private val members: List<Member>) : MemberRepository {
    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> =
        MutableStateFlow(AppResult.Success(members))

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        MutableStateFlow(AppResult.Success(members.firstOrNull { it.id == id }))

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        AppResult.Failure(AppError.Unexpected("not used"))

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeSubscriptions(private val terms: List<SubscriptionTerm>) :
    SubscriptionRepository {
    override fun observePlans(): Flow<AppResult<List<SubscriptionPlan>>> =
        MutableStateFlow(AppResult.Success(emptyList()))

    override fun observeCurrentTerm(memberId: MemberId): Flow<AppResult<SubscriptionTerm?>> =
        MutableStateFlow(AppResult.Success(terms.firstOrNull { it.memberId == memberId }))

    override fun observeCurrentTerms(): Flow<AppResult<List<SubscriptionTerm>>> =
        MutableStateFlow(AppResult.Success(terms))

    override suspend fun confirmRenewal(
        memberId: MemberId,
        quote: RenewalQuote,
        termId: String,
        confirmedAtEpochMs: Long,
    ): AppResult<SubscriptionTerm> = AppResult.Failure(AppError.Unexpected("not used"))

    override suspend fun upsertPlans(plans: List<SubscriptionPlan>): AppResult<Unit> =
        AppResult.Success(Unit)
}

private class FakeReminders(private val counts: ReminderCounts, private val failure: AppError?) :
    ReminderRepository {
    override fun observeCounts(): Flow<AppResult<ReminderCounts>> =
        MutableStateFlow(failure?.let { AppResult.Failure(it) } ?: AppResult.Success(counts))

    override fun observeQueue(
        status: ReminderStatus,
        query: String,
        template: ReminderTemplate?,
    ): Flow<AppResult<List<Reminder>>> = MutableStateFlow(AppResult.Success(emptyList()))

    override fun observeReminder(id: ReminderId): Flow<AppResult<Reminder?>> =
        MutableStateFlow(AppResult.Success(null))

    override suspend fun retry(ids: List<ReminderId>): AppResult<Int> = AppResult.Success(0)

    override suspend fun upsert(reminders: List<Reminder>): AppResult<Unit> =
        AppResult.Success(Unit)
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class FakeCheckIns(private val checkedIn: Int, private val turnedAway: Int) :
    CheckInRepository {
    override suspend fun recordAttempt(memberId: MemberId, today: LocalDate): AppResult<CheckIn> =
        AppResult.Failure(AppError.Unexpected("not used"))

    override fun observeDay(date: LocalDate): Flow<AppResult<List<CheckIn>>> =
        MutableStateFlow(AppResult.Success(emptyList()))

    override fun observeDaySummary(date: LocalDate): Flow<AppResult<CheckInSummary>> =
        MutableStateFlow(
            AppResult.Success(CheckInSummary(granted = checkedIn, denied = turnedAway)),
        )

    override fun observeMonthlyCount(memberId: MemberId, month: LocalDate): Flow<AppResult<Int>> =
        MutableStateFlow(AppResult.Success(0))
}
