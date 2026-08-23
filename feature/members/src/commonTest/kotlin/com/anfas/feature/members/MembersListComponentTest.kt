package com.anfas.feature.members

import app.cash.turbine.test
import com.anfas.core.auth.Permission
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipNumbers
import com.anfas.core.model.MembershipStatus
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
private const val EMISSION_ALLOWANCE = 6

class MembersListComponentTest {

    @Test
    fun `an empty directory and a blank query is DirectoryEmpty not NoMatches`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            assertIs<MembersListContent.DirectoryEmpty>(awaitItem().content)
        }
    }

    @Test
    fun `a query that matches nothing is NoMatches and carries the term`() = runTest {
        val component = component(members = listOf(member("1", "Ali Hassan")))

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)

            component.onQueryChanged("khaled")
            // The typed text is visible immediately, before the debounced search resolves.
            assertEquals("khaled", awaitItem().query)

            val noMatches = awaitItem().content
            assertIs<MembersListContent.NoMatches>(noMatches)
            assertEquals("khaled", noMatches.query)
        }
    }

    @Test
    fun `clearing the search returns to the full directory`() = runTest {
        val component = component(members = listOf(member("1", "Ali Hassan")))

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)
            component.onQueryChanged("zzz")
            skipItems(1)
            assertIs<MembersListContent.NoMatches>(awaitItem().content)

            component.onClearSearch()
            val restored = awaitItem()
            assertEquals("", restored.query)
            assertIs<MembersListContent.Loaded>(awaitItem().content)
        }
    }

    @Test
    fun `a repository failure surfaces as Failed with the message`() = runTest {
        val component = component(
            members = emptyList(),
            result = AppResult.Failure(AppError.Storage("database is locked")),
        )

        component.state.test {
            val failed = assertIs<MembersListContent.Failed>(awaitItem().content)
            assertEquals("database is locked", failed.message)
        }
    }

    @Test
    fun `search filters by name and by membership number`() = runTest {
        val component = component(
            members = listOf(
                member("1", "Ali Hassan", number = "#88392"),
                member("2", "Zara Ahmed", number = "#12345"),
            ),
        )

        component.state.test {
            assertIs<MembersListContent.Loaded>(awaitItem().content)

            component.onQueryChanged("12345")
            skipItems(1)
            val loaded = assertIs<MembersListContent.Loaded>(awaitItem().content)
            assertEquals(listOf("Zara Ahmed"), loaded.members.map { it.fullName })
        }
    }

    @Test
    fun `the add form opens and registers a member with its number`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            awaitItem()
            component.onAddMember()
            component.onAddNameChanged("Nadia Saleh")
            component.onAddPhoneChanged(" 01001234567 ")
            component.onAddSubmit()

            // The form closes and the directory re-reads itself, so the member appears where
            // staff are already looking rather than behind a dialog.
            val state = awaitStateWhere { it.notice != null }
            assertEquals(null, state.addForm)
            val added = assertIs<MembersNotice.Added>(state.notice)
            assertEquals("Nadia Saleh", added.name)
            assertEquals("#10000", added.membershipNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A name is the only requirement: a walk-in can be registered without a phone. */
    @Test
    fun `a member can be registered without a phone`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            awaitItem()
            component.onAddMember()
            component.onAddNameChanged("Walk In")
            assertTrue(awaitStateWhere { it.addForm?.fullName == "Walk In" }.addForm!!.canSubmit)

            component.onAddSubmit()

            assertIs<MembersNotice.Added>(awaitStateWhere { it.notice != null }.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submitting a blank name marks the field instead of saving`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            awaitItem()
            component.onAddMember()
            component.onAddNameChanged("   ")
            component.onAddSubmit()

            val state = awaitStateWhere { it.addForm?.nameError == true }
            assertEquals(null, state.notice, "nothing was saved, so nothing to report")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing clears the name error`() = runTest {
        val component = component(members = emptyList())

        component.state.test {
            awaitItem()
            component.onAddMember()
            component.onAddSubmit()
            awaitStateWhere { it.addForm?.nameError == true }

            component.onAddNameChanged("N")

            assertTrue(awaitStateWhere { it.addForm?.fullName == "N" }.addForm?.nameError == false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a storage failure keeps the form open and reports it`() = runTest {
        val component = component(
            members = emptyList(),
            createFailure = AppError.Storage("disk full"),
        )

        component.state.test {
            awaitItem()
            component.onAddMember()
            component.onAddNameChanged("Nadia Saleh")
            component.onAddSubmit()

            // Kept open so the typing is not lost, and not stuck submitting.
            val state = awaitStateWhere { it.notice != null }
            assertTrue(state.addForm != null, "the form must stay open")
            assertTrue(state.addForm?.isSubmitting == false)
            assertIs<MembersNotice.Failed>(state.notice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A coach can look a member up but not register one, so the affordance is withheld rather
     * than shown and refused.
     */

    /**
     * A coach can look a member up but not register one, so the affordance is withheld rather
     * than shown and refused. Both directions asserted: with the permission withheld this would
     * otherwise pass trivially, since the initial state has it false.
     */
    @Test
    fun `only a session with EDIT_MEMBERS may add`() = runTest {
        val coach = component(members = emptyList(), permissions = setOf(Permission.VIEW_MEMBERS))
        coach.state.test {
            // DirectoryEmpty is the settled content for no members; reaching it means the
            // session has been folded in too.
            val settled = awaitStateWhere { it.content is MembersListContent.DirectoryEmpty }
            assertTrue(!settled.mayEditMembers, "a coach must not be offered Add member")
            cancelAndIgnoreRemainingEvents()
        }

        val owner = component(members = emptyList())
        owner.state.test {
            val settled = awaitStateWhere { it.content is MembersListContent.DirectoryEmpty }
            assertTrue(settled.mayEditMembers, "an owner must be")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- helpers -------------------------------------------------------------------------

    private fun kotlinx.coroutines.test.TestScope.component(
        members: List<Member>,
        result: AppResult<List<Member>>? = null,
        createFailure: AppError? = null,
        permissions: Set<Permission> = Permission.entries.toSet(),
    ): MembersListComponent {
        val lifecycle = LifecycleRegistry()
        val repository = FakeMemberRepository(members, result, createFailure)
        val component = MembersListComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = repository,
            auth = FakeAuth(permissions),
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onMemberClicked = {},
            onAddMemberClicked = {},
            onScanSheetClicked = {},
        )
        lifecycle.resume()
        return component
    }

    private fun member(id: String, name: String, number: String = "#$id") = Member(
        id = MemberId(id),
        fullName = name,
        membershipNumber = number,
        phone = null,
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = null,
        avatarUrl = null,
    )
}

private class FakeMemberRepository(
    members: List<Member>,
    private val forcedResult: AppResult<List<Member>>?,
    /** Set to exercise the form's failure path. */
    private val createFailure: AppError? = null,
) : MemberRepository {

    private val rows = MutableStateFlow(members)

    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> = rows.map { list ->
        forcedResult ?: AppResult.Success(
            if (query.isBlank()) {
                list
            } else {
                list.filter {
                    it.fullName.contains(query, true) || it.membershipNumber.contains(query, true)
                }
            },
        )
    }

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        rows.map { list -> AppResult.Success(list.firstOrNull { it.id == id }) }

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> {
        createFailure?.let { return AppResult.Failure(it) }
        val member = Member(
            id = MemberId("created-" + rows.value.size),
            fullName = fullName.trim(),
            membershipNumber = MembershipNumbers.next(rows.value.map { it.membershipNumber }),
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            status = MembershipStatus.ACTIVE,
            lastCheckInAt = null,
            avatarUrl = null,
        )
        rows.value = rows.value + member
        return AppResult.Success(member)
    }

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

/**
 * Session source. Defaults to every permission so the pre-existing tests keep exercising what
 * they were written for; the add-form tests narrow it to check the affordance is withheld.
 */
private class FakeAuth(private val permissions: Set<Permission>) : AuthRepository {
    private val roles = when {
        Permission.EDIT_MEMBERS in permissions -> setOf(Role.Owner)
        else -> setOf(Role.Coach)
    }

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
        MutableStateFlow(AppResult.Success(emptyList()))

    override suspend fun createStaff(
        username: String,
        password: String,
        displayName: String,
        roles: Set<Role>,
    ): AppResult<CreateAccountOutcome> = AppResult.Success(CreateAccountOutcome.AlreadyInitialised)

    override suspend fun setStaffEnabled(
        id: String,
        enabled: Boolean,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.NotFound)

    override suspend fun resetStaffPassword(
        id: String,
        newPassword: String,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.NotFound)
}

/**
 * State is a `stateIn` conflating four flows, so a single awaitItem() after an action can arrive
 * before that action has been folded in. Waits for the emission that actually matters.
 */
private suspend fun app.cash.turbine.TurbineTestContext<MembersListState>.awaitStateWhere(
    predicate: (MembersListState) -> Boolean,
): MembersListState {
    repeat(EMISSION_ALLOWANCE) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
    error("no matching state within $EMISSION_ALLOWANCE emissions")
}
