package com.anfas.feature.checkin

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.CheckInRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInId
import com.anfas.core.model.CheckInPolicy
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.Currency
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.Money
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.PlanId
import com.anfas.core.model.PlanTier
import com.anfas.core.model.SubscriptionId
import com.anfas.core.model.SubscriptionTerm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant

internal class TestDispatchers(dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

internal fun member(
    id: String,
    name: String,
    status: MembershipStatus = MembershipStatus.ACTIVE,
    lastCheckInAt: Instant? = null,
) = Member(
    id = MemberId(id),
    fullName = name,
    membershipNumber = "#$id",
    phone = null,
    status = status,
    lastCheckInAt = lastCheckInAt,
    avatarUrl = null,
)

internal fun term(memberId: String, startsOn: LocalDate, endsOn: LocalDate) = SubscriptionTerm(
    id = SubscriptionId("t-$memberId"),
    memberId = MemberId(memberId),
    planId = PlanId("p"),
    tier = PlanTier.MONTHLY,
    startsOn = startsOn,
    endsOn = endsOn,
    paymentMethod = PaymentMethod.CASH,
    paid = Money(60_000, Currency.EGP),
)

/** Searches by name or number, like the real directory. */
internal class FakeMemberRepository(initial: List<Member>) : MemberRepository {
    val rows = MutableStateFlow(initial)

    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> = rows.map { list ->
        AppResult.Success(
            list.filter {
                it.fullName.contains(query, ignoreCase = true) ||
                    it.membershipNumber.contains(query, ignoreCase = true)
            },
        )
    }

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> =
        rows.map { list -> AppResult.Success(list.firstOrNull { it.id == id }) }

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        AppResult.Failure(AppError.Storage("not used"))

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * A repository that decides outcomes the way the real one does: by running the real
 * [CheckInPolicy] over the member and their term. Nothing a caller passes can influence it, which
 * is the property the component tests lean on.
 */
internal class FakeCheckInRepository(
    private val members: FakeMemberRepository,
    private val terms: Map<String, SubscriptionTerm> = emptyMap(),
) : CheckInRepository {
    private val log = MutableStateFlow<AppResult<List<CheckIn>>>(AppResult.Success(emptyList()))
    private var counter = 0

    /** When set, [recordAttempt] fails with this instead of deciding. */
    var failure: AppError? = null

    /** When set, [recordAttempt] suspends until this completes. */
    var gate: CompletableDeferred<Unit>? = null

    val attempts = mutableListOf<MemberId>()

    fun failLog(message: String) {
        log.value = AppResult.Failure(AppError.Storage(message))
    }

    override suspend fun recordAttempt(memberId: MemberId, today: LocalDate): AppResult<CheckIn> {
        attempts += memberId
        gate?.await()
        failure?.let { return AppResult.Failure(it) }

        val member = members.rows.value.first { it.id == memberId }
        val outcome = CheckInPolicy.decide(member, terms[memberId.value], today)
        val at = Instant.fromEpochSeconds(1_000L + counter)
        val entry = CheckIn(
            id = CheckInId("ci-${counter++}"),
            memberId = member.id,
            memberName = member.fullName,
            membershipNumber = member.membershipNumber,
            at = at,
            outcome = outcome,
        )
        // Only a granted entry counts as a visit, mirroring the repository's contract.
        if (outcome.grantsEntry) {
            members.rows.value = members.rows.value.map {
                if (it.id == memberId) it.copy(lastCheckInAt = at) else it
            }
        }
        log.value = AppResult.Success(listOf(entry) + (log.value as AppResult.Success).value)
        return AppResult.Success(entry)
    }

    override fun observeDay(date: LocalDate): Flow<AppResult<List<CheckIn>>> = log

    override fun observeDaySummary(date: LocalDate): Flow<AppResult<CheckInSummary>> =
        log.map { result ->
            when (result) {
                is AppResult.Failure -> result
                is AppResult.Success -> AppResult.Success(CheckInSummary.of(result.value) { 9 })
            }
        }

    override fun observeMonthlyCount(memberId: MemberId, month: LocalDate): Flow<AppResult<Int>> =
        log.map { AppResult.Success(0) }
}

internal fun LocalDate.daysFrom(offset: Int): LocalDate =
    this.plus(offset, kotlinx.datetime.DateTimeUnit.DAY)
