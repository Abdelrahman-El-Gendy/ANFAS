package com.anfas.feature.members

import app.cash.turbine.test
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Only the behaviour this component itself owns: `mayViewTherapy` and the navigation callbacks.
 * Identity/term rendering is `MemberProfileScreen`'s job and has no logic worth a component test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileComponentTest {

    private val member = Member(
        id = MemberId("m-1"),
        fullName = "Mona Khalil",
        membershipNumber = "10003",
        phone = null,
        status = MembershipStatus.ACTIVE,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    @Test
    fun `a therapist sees the therapy affordance`() = runTest {
        val component = component(roles = setOf(Role.Therapist))
        component.state.test {
            assertTrue(awaitItem().mayViewTherapy)
        }
    }

    @Test
    fun `a coach does not see the therapy affordance`() = runTest {
        val component = component(roles = setOf(Role.Coach))
        component.state.test {
            assertTrue(!awaitItem().mayViewTherapy)
        }
    }

    @Test
    fun `the owner sees the therapy affordance`() = runTest {
        val component = component(roles = setOf(Role.Owner))
        component.state.test {
            assertTrue(awaitItem().mayViewTherapy)
        }
    }

    @Test
    fun `onTherapy reports this member's id`() = runTest {
        var reported: MemberId? = null
        val component =
            component(roles = setOf(Role.Therapist), onTherapyClicked = { reported = it })

        component.onTherapy()

        assertEquals(member.id, reported)
    }

    @Test
    fun `onRenew reports this member's id`() = runTest {
        var reported: MemberId? = null
        val component = component(roles = setOf(Role.Owner), onRenewClicked = { reported = it })

        component.onRenew()

        assertEquals(member.id, reported)
    }

    private fun TestScope.component(
        roles: Set<Role>,
        onRenewClicked: (MemberId) -> Unit = {},
        onTherapyClicked: (MemberId) -> Unit = {},
    ): MemberProfileComponent {
        val lifecycle = LifecycleRegistry()
        val component = MemberProfileComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            memberId = member.id,
            members = FakeMembers(member),
            subscriptions = FakeSubscriptions(),
            auth = FakeProfileAuth(roles),
            dispatchers = ProfileTestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onRenewClicked = onRenewClicked,
            onTherapyClicked = onTherapyClicked,
            onBackClicked = {},
        )
        lifecycle.resume()
        return component
    }
}

private class ProfileTestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class FakeMembers(private val member: Member) : MemberRepository {
    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> =
        flowOf(AppResult.Success(listOf(member)))

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        flowOf(AppResult.Success(member.takeIf { it.id == id }))

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        AppResult.Success(member)

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeSubscriptions : SubscriptionRepository {
    override fun observePlans(): Flow<AppResult<List<SubscriptionPlan>>> =
        flowOf(AppResult.Success(emptyList()))

    override fun observeCurrentTerm(memberId: MemberId): Flow<AppResult<SubscriptionTerm?>> =
        flowOf(AppResult.Success(null))

    override fun observeCurrentTerms(): Flow<AppResult<List<SubscriptionTerm>>> =
        flowOf(AppResult.Success(emptyList()))

    override suspend fun confirmRenewal(
        memberId: MemberId,
        quote: RenewalQuote,
        termId: String,
        confirmedAtEpochMs: Long,
    ): AppResult<SubscriptionTerm> = AppResult.Failure(
        com.anfas.core.common.AppError.Storage("not used"),
    )

    override suspend fun upsertPlans(plans: List<SubscriptionPlan>): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun seedPlans(plans: List<SubscriptionPlan>): AppResult<Unit> =
        AppResult.Success(Unit)
}

private class FakeProfileAuth(private val roles: Set<Role>) : AuthRepository {
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
