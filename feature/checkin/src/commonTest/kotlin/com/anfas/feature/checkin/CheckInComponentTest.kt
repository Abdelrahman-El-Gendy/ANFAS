package com.anfas.feature.checkin

import com.anfas.core.common.AppError
import com.anfas.core.model.CheckInOutcome
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The policy itself is pinned in `:core:model` and the repository's stamping in `:core:data`. What
 * belongs here is the part only the component can get wrong: that it reports whatever the
 * repository decided, never supplies an outcome of its own, and treats a refusal as a result
 * rather than a failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckInComponentTest {

    private val today: LocalDate = Clock.System.todayIn(TimeZone.UTC)

    // --- search -------------------------------------------------------------------------

    @Test
    fun `nothing typed is the idle prompt and lists nobody`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))

        assertEquals(CheckInSearch.Idle, h.component.state.value.search)
    }

    @Test
    fun `typing finds members after the debounce and a miss carries the term`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))

        h.component.onQueryChanged("omar")
        advanceTimeBy(300)
        runCurrent()
        val results = assertIs<CheckInSearch.Results>(h.component.state.value.search)
        assertEquals(listOf("Omar Hassan"), results.members.map { it.fullName })

        h.component.onQueryChanged("zzz")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(CheckInSearch.NoMatches("zzz"), h.component.state.value.search)
    }

    @Test
    fun `clearing the search returns to idle`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))
        h.component.onQueryChanged("omar")
        advanceTimeBy(300)
        runCurrent()

        h.component.onClearSearch()
        runCurrent()

        assertEquals("", h.component.state.value.query)
        assertEquals(CheckInSearch.Idle, h.component.state.value.search)
    }

    @Test
    fun `selecting a member forwards their id for the profile`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))

        h.component.onMemberSelected(MemberId("1"))

        assertEquals(listOf(MemberId("1")), h.opened)
    }

    // --- outcomes come from the repository, never from the caller -------------------------

    @Test
    fun `a member inside their term is let in the log shows it and the search resets`() = runTest {
        val h = harness(
            members = listOf(member("1", "Omar Hassan")),
            terms = mapOf("1" to term("1", today.daysFrom(-5), today.daysFrom(20))),
        )
        h.component.onQueryChanged("omar")
        advanceTimeBy(300)
        runCurrent()

        h.component.onCheckIn(MemberId("1"))

        val notice = assertIs<CheckInNotice.Recorded>(h.component.state.value.notice)
        assertEquals(CheckInOutcome.GRANTED, notice.checkIn.outcome)
        assertEquals(1, h.component.state.value.log.size)
        assertEquals(1, h.component.state.value.summary.granted)
        assertEquals("", h.component.state.value.query, "the next person is a new lookup")
        assertNull(h.component.state.value.recordingId)
    }

    /**
     * A member left ACTIVE whose term lapsed is refused. The component has to surface that as a
     * recorded refusal: not a failure, not a grant, and not dropped from the log.
     */
    @Test
    fun `a lapsed term is refused even though the member is marked ACTIVE and is still logged`() =
        runTest {
            val h = harness(
                members = listOf(member("1", "Omar Hassan", MembershipStatus.ACTIVE)),
                terms = mapOf("1" to term("1", today.daysFrom(-40), today.daysFrom(-10))),
            )

            h.component.onCheckIn(MemberId("1"))

            val notice = assertIs<CheckInNotice.Recorded>(h.component.state.value.notice)
            assertEquals(CheckInOutcome.EXPIRED, notice.checkIn.outcome)
            assertFalse(notice.checkIn.wasGranted)
            assertEquals(1, h.component.state.value.log.size, "a refusal is part of the log")
            assertEquals(1, h.component.state.value.summary.denied)
            assertEquals(0, h.component.state.value.summary.granted)
        }

    @Test
    fun `a refused attempt does not move the member's last check-in`() = runTest {
        val h = harness(
            members = listOf(member("1", "Omar Hassan", MembershipStatus.ACTIVE)),
            terms = mapOf("1" to term("1", today.daysFrom(-40), today.daysFrom(-10))),
        )

        h.component.onCheckIn(MemberId("1"))

        assertNull(h.members.rows.value.single().lastCheckInAt)
    }

    @Test
    fun `status outranks dates so a suspended member with a valid term is refused`() = runTest {
        val h = harness(
            members = listOf(member("1", "Omar Hassan", MembershipStatus.SUSPENDED)),
            terms = mapOf("1" to term("1", today.daysFrom(-5), today.daysFrom(20))),
        )

        h.component.onCheckIn(MemberId("1"))

        val notice = assertIs<CheckInNotice.Recorded>(h.component.state.value.notice)
        assertEquals(CheckInOutcome.SUSPENDED, notice.checkIn.outcome)
    }

    @Test
    fun `a term that has not started yet still grants entry`() = runTest {
        val h = harness(
            members = listOf(member("1", "Omar Hassan")),
            terms = mapOf("1" to term("1", today.daysFrom(3), today.daysFrom(33))),
        )

        h.component.onCheckIn(MemberId("1"))

        val notice = assertIs<CheckInNotice.Recorded>(h.component.state.value.notice)
        assertEquals(CheckInOutcome.GRANTED, notice.checkIn.outcome)
        assertNotNull(h.members.rows.value.single().lastCheckInAt)
    }

    @Test
    fun `an active member with no term at all is NO_MEMBERSHIP rather than expired`() = runTest {
        val h = harness(members = listOf(member("1", "Omar Hassan")))

        h.component.onCheckIn(MemberId("1"))

        val notice = assertIs<CheckInNotice.Recorded>(h.component.state.value.notice)
        assertEquals(CheckInOutcome.NO_MEMBERSHIP, notice.checkIn.outcome)
    }

    // --- failure and re-entrancy --------------------------------------------------------

    @Test
    fun `a storage failure is a Failed notice that keeps the search and frees the row`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))
        h.repository.failure = AppError.Storage("locked")
        h.component.onQueryChanged("omar")
        advanceTimeBy(300)
        runCurrent()

        h.component.onCheckIn(MemberId("1"))

        assertEquals(CheckInNotice.Failed("locked"), h.component.state.value.notice)
        assertEquals("omar", h.component.state.value.query, "a retry should not need retyping")
        assertNull(h.component.state.value.recordingId)
        assertTrue(h.component.state.value.log.isEmpty())
    }

    @Test
    fun `a second tap while one is being recorded is ignored`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan"), member("2", "Sara Ali")))
        val gate = CompletableDeferred<Unit>()
        h.repository.gate = gate

        h.component.onCheckIn(MemberId("1"))
        assertEquals("1", h.component.state.value.recordingId)
        h.component.onCheckIn(MemberId("1"))
        h.component.onCheckIn(MemberId("2"))
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(MemberId("1")), h.repository.attempts, "no double check-in")
        assertEquals(1, h.component.state.value.log.size)
    }

    @Test
    fun `a failing log surfaces as an error instead of an empty gym`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))

        h.repository.failLog("log unreadable")

        assertEquals("log unreadable", h.component.state.value.error)
    }

    @Test
    fun `typing dismisses the previous notice`() = runTest {
        val h = harness(listOf(member("1", "Omar Hassan")))
        h.component.onCheckIn(MemberId("1"))
        assertNotNull(h.component.state.value.notice)

        h.component.onQueryChanged("s")

        assertNull(h.component.state.value.notice)
    }

    // --- harness ------------------------------------------------------------------------

    private class Harness(
        val component: CheckInComponent,
        val repository: FakeCheckInRepository,
        val members: FakeMemberRepository,
        val opened: MutableList<MemberId>,
    )

    private fun TestScope.harness(
        members: List<com.anfas.core.model.Member>,
        terms: Map<String, com.anfas.core.model.SubscriptionTerm> = emptyMap(),
    ): Harness {
        val memberRepository = FakeMemberRepository(members)
        val repository = FakeCheckInRepository(memberRepository, terms)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lifecycle = LifecycleRegistry()
        val opened = mutableListOf<MemberId>()
        val component = CheckInComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            members = memberRepository,
            checkIns = repository,
            dispatchers = TestDispatchers(dispatcher),
            zone = TimeZone.UTC,
            onMemberClicked = { opened += it },
        )
        lifecycle.resume()
        backgroundScope.launch(dispatcher) { component.state.collect { } }
        return Harness(component, repository, memberRepository, opened)
    }
}
