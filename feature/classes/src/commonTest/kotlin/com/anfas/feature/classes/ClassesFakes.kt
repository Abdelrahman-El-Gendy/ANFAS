package com.anfas.feature.classes

import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.ClassRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.SaveClassOutcome
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.data.Timetable
import com.anfas.core.model.GymClass
import com.anfas.core.model.GymClassId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/**
 * Records what reached the repository, because "the save was refused" and "the save was never
 * attempted" are different claims and the permission tests need the second one.
 */
internal class FakeClassRepository(
    initial: AppResult<Timetable> = AppResult.Success(Timetable.EMPTY),
) : ClassRepository {
    val timetable = MutableStateFlow(initial)

    /** What the next [save] answers. */
    var saveResult: AppResult<SaveClassOutcome> = AppResult.Success(SaveClassOutcome.Saved)
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

    val saved = mutableListOf<GymClass>()
    val deleted = mutableListOf<GymClassId>()

    override fun observeTimetable(): Flow<AppResult<Timetable>> = timetable

    override suspend fun save(gymClass: GymClass): AppResult<SaveClassOutcome> {
        saved += gymClass
        return saveResult
    }

    override suspend fun delete(id: GymClassId): AppResult<Unit> {
        deleted += id
        return deleteResult
    }
}

internal fun storageFailure(message: String) = AppResult.Failure(AppError.Storage(message))

/** A session whose roles can change under a live component, like a demotion on another device. */
internal class FakeAuth(initial: Session?) : AuthRepository {
    val session = MutableStateFlow(initial)

    override fun observeSession(): Flow<Session?> = session

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
