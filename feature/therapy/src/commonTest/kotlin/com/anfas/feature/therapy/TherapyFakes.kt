package com.anfas.feature.therapy

import com.anfas.core.auth.PasswordHash
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
import com.anfas.core.data.SaveCaseOutcome
import com.anfas.core.data.SaveSessionOutcome
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.data.TherapyCaseDetail
import com.anfas.core.data.TherapyRepository
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.TherapyCaseId
import com.anfas.core.model.TreatmentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

internal class TestDispatchers(dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal fun storageFailure(message: String) = AppResult.Failure(AppError.Storage(message))

internal class FakeMemberRepository(initial: AppResult<Member?>) : MemberRepository {
    val member = MutableStateFlow(initial)

    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> =
        flowOf(AppResult.Success(emptyList()))

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> = member

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        AppResult.Failure(AppError.Storage("not used"))

    override suspend fun upsert(members: List<Member>): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun delete(id: MemberId): AppResult<Unit> = AppResult.Success(Unit)
}

/** One recorded call per write, so a test can say "nothing was sent" and mean it. */
internal class FakeTherapyRepository(initial: TherapyCaseDetail? = null) : TherapyRepository {
    val detail = MutableStateFlow<AppResult<TherapyCaseDetail?>>(AppResult.Success(initial))

    var openResult: AppResult<SaveCaseOutcome> =
        AppResult.Success(SaveCaseOutcome.Saved(TherapyCaseId("c-new")))
    var updateResult: AppResult<SaveCaseOutcome> =
        AppResult.Success(SaveCaseOutcome.Saved(TherapyCaseId("c-1")))
    var closeResult: AppResult<Unit> = AppResult.Success(Unit)
    var sessionResult: AppResult<SaveSessionOutcome> = AppResult.Success(SaveSessionOutcome.Saved)

    class Opened(
        val memberId: MemberId,
        val condition: String,
        val therapist: String?,
        val on: LocalDate,
    )
    class Updated(val caseId: TherapyCaseId, val condition: String)
    class Logged(
        val caseId: TherapyCaseId,
        val therapist: String?,
        val at: Instant,
        val duration: Int,
        val types: Set<TreatmentType>,
        val painScore: Int?,
    )

    val opened = mutableListOf<Opened>()
    val updated = mutableListOf<Updated>()
    val closed = mutableListOf<Pair<TherapyCaseId, LocalDate>>()
    val logged = mutableListOf<Logged>()

    override fun observeLatestCase(memberId: MemberId): Flow<AppResult<TherapyCaseDetail?>> = detail

    override suspend fun openCase(
        memberId: MemberId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
        openedOn: LocalDate,
    ): AppResult<SaveCaseOutcome> {
        opened += Opened(memberId, condition, therapistStaffId, openedOn)
        return openResult
    }

    override suspend fun updateCase(
        caseId: TherapyCaseId,
        condition: String,
        therapistStaffId: String?,
        referredBy: String?,
        onset: String,
        mechanism: String,
        contraindications: String?,
    ): AppResult<SaveCaseOutcome> {
        updated += Updated(caseId, condition)
        return updateResult
    }

    override suspend fun closeCase(caseId: TherapyCaseId, closedOn: LocalDate): AppResult<Unit> {
        closed += caseId to closedOn
        return closeResult
    }

    override suspend fun logSession(
        caseId: TherapyCaseId,
        therapistStaffId: String?,
        at: Instant,
        durationMinutes: Int,
        treatmentTypes: Set<TreatmentType>,
        notes: String,
        painScore: Int?,
    ): AppResult<SaveSessionOutcome> {
        logged += Logged(caseId, therapistStaffId, at, durationMinutes, treatmentTypes, painScore)
        return sessionResult
    }
}

internal fun staff(id: String, name: String, enabled: Boolean = true) = StaffAccount(
    id = id,
    username = id,
    displayName = name,
    roles = setOf(Role.Therapist),
    passwordHash = PasswordHash("PBKDF2", 1, ByteArray(1), ByteArray(1)),
    createdAt = Instant.fromEpochSeconds(0),
    isEnabled = enabled,
)

internal class FakeAuth(session: Session?, staff: List<StaffAccount> = emptyList()) :
    AuthRepository {
    private val sessionFlow = MutableStateFlow(session)
    private val staffFlow =
        MutableStateFlow<AppResult<List<StaffAccount>>>(AppResult.Success(staff))

    override fun observeSession(): Flow<Session?> = sessionFlow

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

    override fun observeStaff(): Flow<AppResult<List<StaffAccount>>> = staffFlow

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
