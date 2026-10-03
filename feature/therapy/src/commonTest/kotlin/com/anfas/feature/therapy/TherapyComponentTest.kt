package com.anfas.feature.therapy

import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.common.AppResult
import com.anfas.core.data.CaseProblem
import com.anfas.core.data.SaveCaseOutcome
import com.anfas.core.data.SaveSessionOutcome
import com.anfas.core.data.SessionProblem
import com.anfas.core.data.TherapyCaseDetail
import com.anfas.core.i18n.ArabicStrings
import com.anfas.core.i18n.EnglishStrings
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.TherapyCase
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TherapySession
import com.anfas.core.model.TherapySessionId
import com.anfas.core.model.TreatmentType
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * `VIEW_THERAPY` is deliberately not asserted here: this component holds no permission check by
 * design (its KDoc says the router enforces it before the screen composes). The enforcement is
 * pinned where it lives, in `NavigationPermissionTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TherapyComponentTest {

    private val now = Instant.parse("2026-08-26T10:00:00Z")
    private val today = LocalDate(2026, 8, 26)

    // --- loading ------------------------------------------------------------------------

    @Test
    fun `a member with no case is Loaded with null detail and may open one`() = runTest {
        val h = harness(detail = null)

        val loaded = assertIs<TherapyContent.Loaded>(h.component.state.value.content)
        assertNull(loaded.detail)
        assertTrue(h.component.state.value.canOpenNewCase)
        assertFalse(h.component.state.value.canLogSession)
        assertFalse(h.component.state.value.canCloseCase)
    }

    @Test
    fun `a deleted member reads as MemberMissing rather than an empty case file`() = runTest {
        val h = harness(member = AppResult.Success(null))

        assertIs<TherapyContent.MemberMissing>(h.component.state.value.content)
    }

    @Test
    fun `a member read failure is Failed with its message`() = runTest {
        val h = harness(member = storageFailure("locked"))

        assertEquals(
            "locked",
            assertIs<TherapyContent.Failed>(h.component.state.value.content).message,
        )
    }

    @Test
    fun `an open case allows logging and closing but not a second case`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))

        assertTrue(h.component.state.value.canLogSession)
        assertTrue(h.component.state.value.canCloseCase)
        assertFalse(h.component.state.value.canOpenNewCase)
    }

    @Test
    fun `a closed case is history so a new one may be opened and nothing else is offered`() =
        runTest {
            val h = harness(detail = detail(CaseStatus.CLOSED))

            assertTrue(h.component.state.value.canOpenNewCase)
            assertFalse(h.component.state.value.canLogSession)
            assertFalse(h.component.state.value.canCloseCase)
        }

    @Test
    fun `the therapist picker lists enabled staff only alphabetically`() = runTest {
        val h = harness(
            staff = listOf(
                staff("s-2", "Zed"),
                staff("s-3", "Off", enabled = false),
                staff("s-1", "Amal"),
            ),
        )

        assertEquals(listOf("s-1" to "Amal", "s-2" to "Zed"), h.component.state.value.staffOptions)
    }

    // --- opening a case -----------------------------------------------------------------

    @Test
    fun `the open-case form defaults the therapist to whoever is signed in`() = runTest {
        val h = harness(session = Session("s-me", setOf(Role.Therapist)))

        h.component.onOpenCaseForm()

        assertEquals("s-me", assertNotNull(h.component.state.value.caseForm).therapistStaffId)
    }

    @Test
    fun `opening a case sends the member and today closes the form and names the condition`() =
        runTest {
            val h = harness(session = Session("s-me", setOf(Role.Therapist)))
            h.component.onOpenCaseForm()
            h.component.onCaseFormChanged { it.copy(condition = " Shoulder impingement ") }

            h.component.onSubmitCaseForm()

            val call = h.therapy.opened.single()
            assertEquals(MemberId("m-1"), call.memberId)
            assertEquals(today, call.on)
            assertEquals("s-me", call.therapist)
            assertNull(h.component.state.value.caseForm)
            assertEquals(
                TherapyNotice.CaseOpened("Shoulder impingement"),
                h.component.state.value.notice,
            )
        }

    /** The refusal comes from the repository, and the form must stay open so nothing typed is lost. */
    @Test
    fun `AlreadyOpen keeps the form says so and leaves it submittable again`() = runTest {
        val h = harness()
        h.therapy.openResult = AppResult.Success(SaveCaseOutcome.AlreadyOpen)
        h.component.onOpenCaseForm()
        h.component.onCaseFormChanged { it.copy(condition = "Knee") }

        h.component.onSubmitCaseForm()

        val form = assertNotNull(h.component.state.value.caseForm)
        assertEquals("Knee", form.condition)
        assertFalse(form.isSubmitting)
        assertEquals(TherapyNotice.AlreadyOpen, h.component.state.value.notice)
    }

    @Test
    fun `an invalid case keeps the form with the problems marked until the next edit`() = runTest {
        val h = harness()
        h.therapy.openResult =
            AppResult.Success(SaveCaseOutcome.Invalid(setOf(CaseProblem.CONDITION_BLANK)))
        h.component.onOpenCaseForm()
        h.component.onCaseFormChanged { it.copy(condition = "x") }
        h.component.onSubmitCaseForm()
        assertEquals(
            setOf(CaseProblem.CONDITION_BLANK),
            assertNotNull(h.component.state.value.caseForm).problems,
        )

        h.component.onCaseFormChanged { it.copy(condition = "xy") }

        assertTrue(assertNotNull(h.component.state.value.caseForm).problems.isEmpty())
    }

    @Test
    fun `a blank condition cannot be submitted and writes nothing`() = runTest {
        val h = harness()
        h.component.onOpenCaseForm()
        h.component.onCaseFormChanged { it.copy(condition = "  ") }

        assertFalse(assertNotNull(h.component.state.value.caseForm).canSubmit)
        h.component.onSubmitCaseForm()

        assertTrue(h.therapy.opened.isEmpty())
    }

    @Test
    fun `a repository failure while opening is reported and the form survives`() = runTest {
        val h = harness()
        h.therapy.openResult = storageFailure("locked")
        h.component.onOpenCaseForm()
        h.component.onCaseFormChanged { it.copy(condition = "Knee") }

        h.component.onSubmitCaseForm()

        assertNotNull(h.component.state.value.caseForm)
        assertEquals(TherapyNotice.Failed("locked"), h.component.state.value.notice)
    }

    @Test
    fun `editing a case updates it by id and never opens a second one`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))

        h.component.onEditCaseForm()
        val form = assertNotNull(h.component.state.value.caseForm)
        assertTrue(form.editing)
        assertEquals("Right shoulder", form.condition)

        h.component.onCaseFormChanged { it.copy(condition = "Left shoulder") }
        h.component.onSubmitCaseForm()

        assertTrue(h.therapy.opened.isEmpty())
        assertEquals(TherapyCaseId("c-1"), h.therapy.updated.single().caseId)
        assertEquals("Left shoulder", h.therapy.updated.single().condition)
    }

    // --- closing ------------------------------------------------------------------------

    @Test
    fun `closing is a two-step confirm and dismissing it closes nothing`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))

        h.component.onCloseCaseRequested()
        assertTrue(h.component.state.value.closeConfirmVisible)

        h.component.onCloseCaseDismissed()
        assertFalse(h.component.state.value.closeConfirmVisible)
        assertTrue(h.therapy.closed.isEmpty())
    }

    @Test
    fun `confirming stamps today on the case and says it was closed`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))
        h.component.onCloseCaseRequested()

        h.component.onCloseCaseConfirmed()

        assertEquals(listOf(TherapyCaseId("c-1") to today), h.therapy.closed)
        assertFalse(h.component.state.value.closeConfirmVisible)
        assertEquals(TherapyNotice.CaseClosed, h.component.state.value.notice)
    }

    @Test
    fun `confirming a close with no case on screen does nothing`() = runTest {
        val h = harness(detail = null)

        h.component.onCloseCaseConfirmed()

        assertTrue(h.therapy.closed.isEmpty())
    }

    @Test
    fun `a failed close is reported`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))
        h.therapy.closeResult = storageFailure("locked")

        h.component.onCloseCaseConfirmed()

        assertEquals(TherapyNotice.Failed("locked"), h.component.state.value.notice)
    }

    // --- logging a session --------------------------------------------------------------

    @Test
    fun `a logged session carries the case the typed fields and a parsed pain score`() = runTest {
        val h = harness(
            detail = detail(CaseStatus.ACTIVE),
            session = Session("s-me", setOf(Role.Therapist)),
        )
        h.component.onLogSession()
        h.component.onSessionFormChanged {
            it.copy(
                durationMinutes = 30,
                treatmentTypes = setOf(TreatmentType.EXERCISE),
                painScoreText = " 4 ",
            )
        }

        h.component.onSubmitSessionForm()

        val call = h.therapy.logged.single()
        assertEquals(TherapyCaseId("c-1"), call.caseId)
        assertEquals("s-me", call.therapist)
        assertEquals(30, call.duration)
        assertEquals(setOf(TreatmentType.EXERCISE), call.types)
        assertEquals(4, call.painScore)
        assertNull(h.component.state.value.sessionForm)
        assertEquals(TherapyNotice.SessionLogged, h.component.state.value.notice)
    }

    @Test
    fun `a blank pain score is not asked which is null and never zero`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))
        h.component.onLogSession()

        h.component.onSubmitSessionForm()

        assertNull(h.therapy.logged.single().painScore)
    }

    @Test
    fun `a session can be back-dated by whole days from now`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))
        h.component.onLogSession()
        h.component.onSessionFormChanged { it.copy(dayOffset = 3) }

        h.component.onSubmitSessionForm()

        assertEquals(now - 3.days, h.therapy.logged.single().at)
    }

    @Test
    fun `an invalid session keeps the form open with the problems`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE))
        h.therapy.sessionResult = AppResult.Success(
            SaveSessionOutcome.Invalid(setOf(SessionProblem.PAIN_SCORE_OUT_OF_RANGE)),
        )
        h.component.onLogSession()
        h.component.onSessionFormChanged { it.copy(painScoreText = "11") }

        h.component.onSubmitSessionForm()

        val form = assertNotNull(h.component.state.value.sessionForm)
        assertEquals(setOf(SessionProblem.PAIN_SCORE_OUT_OF_RANGE), form.problems)
        assertEquals("11", form.painScoreText)
        assertFalse(form.isSubmitting)
    }

    @Test
    fun `with no case on screen there is nothing to log a session against`() = runTest {
        val h = harness(detail = null)
        h.component.onLogSession()

        h.component.onSubmitSessionForm()

        assertTrue(h.therapy.logged.isEmpty())
    }

    // --- progress and the unassigned therapist ------------------------------------------

    @Test
    fun `a single scored session is not a trend`() = runTest {
        val h = harness(detail = detail(CaseStatus.ACTIVE, sessions = listOf(session("a", 0, 7))))

        assertNull(h.component.state.value.progress)
    }

    @Test
    fun `two scored sessions give a trend oldest to newest whatever the list order`() = runTest {
        // Newest first, as the repository returns them.
        val h = harness(
            detail = detail(
                CaseStatus.ACTIVE,
                sessions = listOf(session("b", 0, 3), session("a", 7, 7)),
            ),
        )

        val trend = assertNotNull(h.component.state.value.progress)
        assertEquals(7, trend.first)
        assertEquals(3, trend.latest)
        assertTrue(trend.isImproving)
    }

    /**
     * An arrow inside translatable text reads backwards in an Arabic paragraph, so the sentence is
     * built from words in both languages. Falsify by putting "→" back into either string.
     */
    @Test
    fun `the pain trend sentence is words never a directional glyph and carries both numbers`() {
        val arrows = listOf("→", "←", "⟶", "⟵", "->", "<-", "↦", "⇒")
        listOf("English" to EnglishStrings, "Arabic" to ArabicStrings).forEach { (name, strings) ->
            val text = strings.therapy.painScoreTrend(7, 3)
            arrows.forEach { arrow ->
                assertFalse(arrow in text, "$name trend \"$text\" contains $arrow")
            }
            assertTrue("7" in text && "3" in text, "$name trend \"$text\" lost a number")
        }
    }

    @Test
    fun `a therapist id that no longer resolves reads as unassigned not as an error`() = runTest {
        val h = harness(
            detail = detail(CaseStatus.ACTIVE, therapist = "s-gone", staffNames = emptyMap()),
        )

        val detail = assertNotNull(h.component.state.value.detail)
        assertNull(detail.therapistName(detail.case.therapistStaffId))
        assertNull(detail.therapistName(null))
        assertIs<TherapyContent.Loaded>(h.component.state.value.content)
        // The screen falls back to this string, so it must exist in both languages.
        assertTrue(EnglishStrings.therapy.unassignedTherapist.isNotBlank())
        assertTrue(ArabicStrings.therapy.unassignedTherapist.isNotBlank())
    }

    // --- harness ------------------------------------------------------------------------

    private class Harness(val component: TherapyComponent, val therapy: FakeTherapyRepository)

    private fun TestScope.harness(
        detail: TherapyCaseDetail? = null,
        member: AppResult<Member?> = AppResult.Success(member()),
        session: Session? = Session("s-1", setOf(Role.Therapist)),
        staff: List<com.anfas.core.auth.StaffAccount> = emptyList(),
    ): Harness {
        val therapy = FakeTherapyRepository(detail)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val lifecycle = LifecycleRegistry()
        val component = TherapyComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            memberId = MemberId("m-1"),
            members = FakeMemberRepository(member),
            therapy = therapy,
            auth = FakeAuth(session, staff),
            dispatchers = TestDispatchers(dispatcher),
            clock = FixedClock(now),
            zone = TimeZone.UTC,
            onBackClicked = {},
        )
        lifecycle.resume()
        backgroundScope.launch(dispatcher) { component.state.collect { } }
        return Harness(component, therapy)
    }

    private fun member() = Member(
        id = MemberId("m-1"),
        fullName = "Omar Hassan",
        membershipNumber = "#10000",
        phone = null,
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    private fun detail(
        status: CaseStatus,
        sessions: List<TherapySession> = emptyList(),
        therapist: String? = "s-1",
        staffNames: Map<String, String> = mapOf("s-1" to "Amal"),
    ) = TherapyCaseDetail(
        case = TherapyCase(
            id = TherapyCaseId("c-1"),
            memberId = MemberId("m-1"),
            condition = "Right shoulder",
            status = status,
            openedOn = LocalDate(2026, 8, 1),
            closedOn = null,
            therapistStaffId = therapist,
            referredBy = null,
            onset = "May",
            mechanism = "Lifting",
            contraindications = null,
        ),
        sessions = sessions,
        staffNames = staffNames,
    )

    private fun session(id: String, daysAgo: Int, pain: Int?) = TherapySession(
        id = TherapySessionId(id),
        caseId = TherapyCaseId("c-1"),
        therapistStaffId = "s-1",
        at = now - daysAgo.days,
        durationMinutes = 45,
        treatmentTypes = emptySet(),
        notes = "",
        painScore = pain,
    )
}
