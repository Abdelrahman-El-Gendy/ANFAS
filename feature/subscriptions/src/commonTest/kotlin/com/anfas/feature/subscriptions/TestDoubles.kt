package com.anfas.feature.subscriptions

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
import com.anfas.core.data.ReminderCounts
import com.anfas.core.data.ReminderRepository
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.FailureReason
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.Money
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.Reminder
import com.anfas.core.model.ReminderFailure
import com.anfas.core.model.ReminderId
import com.anfas.core.model.ReminderStatus
import com.anfas.core.model.ReminderTemplate
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.SubscriptionPlan
import com.anfas.core.model.SubscriptionTerm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

internal class FakeReminderRepository(
    reminders: List<Reminder> = emptyList(),
    private val forcedQueueResult: AppResult<List<Reminder>>? = null,
) : ReminderRepository {

    private val rows = MutableStateFlow(reminders)

    /** Records what retry was asked to do, so the component's notice can be checked. */
    var lastRetryIds: List<ReminderId> = emptyList()
        private set

    override fun observeQueue(
        status: ReminderStatus,
        query: String,
        template: ReminderTemplate?,
    ): Flow<AppResult<List<Reminder>>> = rows.map { list ->
        forcedQueueResult ?: AppResult.Success(
            list.filter {
                it.status == status &&
                    (
                        query.isBlank() ||
                            it.memberName.contains(query, true) ||
                            it.phone.contains(query)
                        ) &&
                    (template == null || it.template == template)
            },
        )
    }

    override fun observeCounts(): Flow<AppResult<ReminderCounts>> = rows.map { list ->
        AppResult.Success(
            ReminderCounts(
                queued = list.count { it.status == ReminderStatus.QUEUED },
                sent = list.count { it.status == ReminderStatus.SENT },
                failed = list.count { it.status == ReminderStatus.FAILED },
            ),
        )
    }

    override fun observeReminder(id: ReminderId): Flow<AppResult<Reminder?>> =
        rows.map { list -> AppResult.Success(list.firstOrNull { it.id == id }) }

    /** Mirrors the real repository: only retryable failures count. */
    override suspend fun retry(ids: List<ReminderId>): AppResult<Int> {
        lastRetryIds = ids
        val retryable = rows.value.filter { it.id in ids && it.canRetry }
        rows.value = rows.value.map { r ->
            if (r in retryable) r.copy(status = ReminderStatus.QUEUED, failure = null) else r
        }
        return AppResult.Success(retryable.size)
    }

    override suspend fun upsert(reminders: List<Reminder>): AppResult<Unit> =
        AppResult.Success(Unit)
}

internal class FakeSubscriptionRepository(
    private val plans: List<SubscriptionPlan>,
    private val currentTerm: SubscriptionTerm? = null,
) : SubscriptionRepository {

    var confirmed: Pair<MemberId, RenewalQuote>? = null
        private set

    override fun observePlans(): Flow<AppResult<List<SubscriptionPlan>>> =
        MutableStateFlow(AppResult.Success(plans))

    override fun observeCurrentTerm(memberId: MemberId): Flow<AppResult<SubscriptionTerm?>> =
        MutableStateFlow(AppResult.Success(currentTerm))

    // Read by the dashboard, not by the renewal sheet these tests exercise.
    override fun observeCurrentTerms(): Flow<AppResult<List<SubscriptionTerm>>> =
        MutableStateFlow(AppResult.Success(listOfNotNull(currentTerm)))

    override suspend fun confirmRenewal(
        memberId: MemberId,
        quote: RenewalQuote,
        termId: String,
        confirmedAtEpochMs: Long,
    ): AppResult<SubscriptionTerm> {
        confirmed = memberId to quote
        return AppResult.Success(
            SubscriptionTerm(
                id = com.anfas.core.model.SubscriptionId(termId),
                memberId = memberId,
                planId = quote.plan.id,
                tier = quote.plan.tier,
                startsOn = quote.startsOn,
                endsOn = quote.endsOn,
                paymentMethod = quote.paymentMethod,
                paid = quote.total,
            ),
        )
    }

    override suspend fun upsertPlans(plans: List<SubscriptionPlan>): AppResult<Unit> =
        AppResult.Success(Unit)
}

internal class FakeMemberRepository(private val member: Member?) : MemberRepository {
    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> =
        MutableStateFlow(AppResult.Success(listOfNotNull(member)))

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        MutableStateFlow(AppResult.Success(member))

    // Nothing in the subscriptions feature registers a member; the members feature's own test
    // exercises this properly.
    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        AppResult.Failure(AppError.Unexpected("not used by these tests"))

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

internal fun reminder(
    id: String,
    name: String = "Omar Khaled",
    phone: String = "+20 100 123 4567",
    status: ReminderStatus = ReminderStatus.FAILED,
    reason: FailureReason? = FailureReason.RATE_LIMITED,
    template: ReminderTemplate = ReminderTemplate.REMINDER_AR,
) = Reminder(
    id = ReminderId(id),
    memberId = MemberId("m-$id"),
    memberName = name,
    phone = phone,
    template = template,
    scheduledAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
    attempts = 1,
    status = status,
    failure = reason?.let {
        ReminderFailure(reason = it, providerCode = 131047, lastAttemptAt = null)
    },
)

internal fun member(name: String = "Omar Khaled") = Member(
    id = MemberId("m-1"),
    fullName = name,
    membershipNumber = "#88392",
    phone = "+20 100 123 4567",
    status = MembershipStatus.ACTIVE,
    lastCheckInAt = null,
    avatarUrl = null,
)

internal fun plan(tier: PlanTier, price: Long, savings: Int? = null) = SubscriptionPlan(
    id = PlanId(tier.name.lowercase()),
    tier = tier,
    price = Money.of(price),
    perks = "Full access",
    savingsPercent = savings,
)

/**
 * Session source. `mayRetry` maps to Receptionist (holds RETRY_REMINDERS) or Coach (does not),
 * so the test states the *capability* rather than a role it does not care about.
 */
internal class FakeAuth(mayRetry: Boolean) : AuthRepository {
    private val session = Session(
        userId = "s-1",
        roles = setOf(if (mayRetry) Role.Receptionist else Role.Coach),
    )

    override fun observeSession(): Flow<Session?> = MutableStateFlow(session)

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
