package com.anfas.feature.announcements

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.data.AnnouncementDetail
import com.anfas.core.data.AnnouncementRepository
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.SaveAnnouncementOutcome
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.model.Announcement
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementId
import com.anfas.core.model.AnnouncementStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Only what this component itself owns: permission gating and the publish-once guard. Save,
 * publish and delete's actual outcomes are `AnnouncementRepositoryTest`'s job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsComponentTest {

    @Test
    fun `an owner may manage announcements`() = runTest {
        val component = component(roles = setOf(Role.Owner))
        component.state.test {
            assertTrue(awaitItem().mayManage)
        }
    }

    @Test
    fun `a coach may not manage announcements`() = runTest {
        val component = component(roles = setOf(Role.Coach))
        component.state.test {
            assertTrue(!awaitItem().mayManage)
        }
    }

    /**
     * The bug this pins: opening a *published* announcement's form and tapping the (now
     * withheld) publish action must never fire a second time. Before this test existed, only
     * the dialog's own visibility hid the button -- the component itself had no guard, so any
     * other caller of `onRequestPublish()` could still silently overwrite the frozen
     * `publishedAt` and `recipientCountAtPublish`.
     */
    @Test
    fun `onRequestPublish is a no-op for an already-published announcement`() = runTest {
        val detail = publishedDetail()
        val component = component(roles = setOf(Role.Owner), announcements = listOf(detail))

        component.state.test {
            awaitItem()
            component.onEditAnnouncement(detail.announcement.id)
            val editing = awaitSettled()
            assertEquals(detail.announcement.id.value, editing.form?.editing)

            component.onRequestPublish()

            // No further emission: the guard returns before touching `ui` at all, so the form
            // stays exactly as it was rather than emitting a copy of itself.
            expectNoEvents()
        }
    }

    @Test
    fun `onRequestPublish opens the confirm dialog for a draft`() = runTest {
        val detail = draftDetail()
        val component = component(roles = setOf(Role.Owner), announcements = listOf(detail))

        component.state.test {
            awaitItem()
            component.onEditAnnouncement(detail.announcement.id)
            awaitSettled()

            component.onRequestPublish()

            val state = awaitSettled()
            assertNull(state.form)
            assertEquals(detail.announcement.id.value, state.publishConfirm?.editing)
        }
    }

    /**
     * `liveReach` is fed by `ui.flatMapLatest { ... repository.observeReach(audience) }`, a
     * second combine source switched by the same `ui` update that carries the new form. combine
     * emits the two upstream changes separately, so a form change surfaces first with the old
     * (possibly null) `liveReach` and then again once the new reach flow delivers its value.
     * Callers only care about the settled one.
     */
    private suspend fun ReceiveTurbine<AnnouncementsState>.awaitSettled(): AnnouncementsState {
        var state = awaitItem()
        while (state.liveReach == null && (state.form != null || state.publishConfirm != null)) {
            state = awaitItem()
        }
        return state
    }

    private fun draftDetail() = AnnouncementDetail(
        announcement = Announcement(
            id = AnnouncementId("a-1"),
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            status = AnnouncementStatus.DRAFT,
            createdByStaffId = null,
            createdAt = Instant.fromEpochSeconds(100),
            publishedAt = null,
            recipientCountAtPublish = null,
        ),
        createdByName = null,
    )

    private fun publishedDetail() = draftDetail().let {
        it.copy(
            announcement = it.announcement.copy(
                status = AnnouncementStatus.PUBLISHED,
                publishedAt = Instant.fromEpochSeconds(200),
                recipientCountAtPublish = 3,
            ),
        )
    }

    private fun TestScope.component(
        roles: Set<Role>,
        announcements: List<AnnouncementDetail> = emptyList(),
    ): AnnouncementsComponent {
        val lifecycle = LifecycleRegistry()
        val component = AnnouncementsComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = FakeAnnouncementsRepository(announcements),
            auth = FakeAnnouncementsAuth(roles),
            dispatchers = AnnouncementsTestDispatchers(UnconfinedTestDispatcher(testScheduler)),
        )
        lifecycle.resume()
        return component
    }
}

private class AnnouncementsTestDispatchers(private val dispatcher: CoroutineDispatcher) :
    AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class FakeAnnouncementsRepository(initial: List<AnnouncementDetail>) :
    AnnouncementRepository {
    private val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<AppResult<List<AnnouncementDetail>>> =
        MutableStateFlow(AppResult.Success(rows.value))

    override fun observeReach(audience: AnnouncementAudience): Flow<AppResult<Int>> =
        flowOf(AppResult.Success(0))

    override suspend fun createDraft(
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
        createdByStaffId: String?,
        createdAt: Instant,
    ): AppResult<SaveAnnouncementOutcome> =
        AppResult.Success(SaveAnnouncementOutcome.Saved(AnnouncementId("new")))

    override suspend fun updateDraft(
        id: AnnouncementId,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
    ): AppResult<SaveAnnouncementOutcome> = AppResult.Success(SaveAnnouncementOutcome.Saved(id))

    override suspend fun publish(id: AnnouncementId, publishedAt: Instant): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun deleteDraft(id: AnnouncementId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeAnnouncementsAuth(private val roles: Set<Role>) : AuthRepository {
    override fun observeSession(): Flow<Session?> =
        MutableStateFlow(Session(userId = "s-1", roles = roles))

    override fun observeCurrentStaff(): Flow<StaffAccount?> = flowOf(null)
    override suspend fun hasAnyAccount(): AppResult<Boolean> = AppResult.Success(true)

    override suspend fun signIn(username: String, password: String): AppResult<SignInResult> =
        AppResult.Success(SignInResult.InvalidCredentials)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun createFirstOwner(
        username: String,
        password: String,
        displayName: String,
    ): AppResult<CreateAccountOutcome> = AppResult.Success(CreateAccountOutcome.AlreadyInitialised)

    override fun observeStaff(): Flow<AppResult<List<StaffAccount>>> =
        flowOf(AppResult.Success(emptyList()))

    override suspend fun createStaff(
        username: String,
        password: String,
        displayName: String,
        roles: Set<Role>,
    ): AppResult<CreateAccountOutcome> = AppResult.Success(CreateAccountOutcome.AlreadyInitialised)

    override suspend fun setStaffEnabled(
        id: String,
        enabled: Boolean,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.Changed)

    override suspend fun resetStaffPassword(
        id: String,
        newPassword: String,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.Changed)
}
