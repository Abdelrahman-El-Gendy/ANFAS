package com.anfas.feature.equipment

import app.cash.turbine.test
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.EquipmentDetail
import com.anfas.core.data.EquipmentRepository
import com.anfas.core.data.LogMaintenanceOutcome
import com.anfas.core.data.SaveEquipmentOutcome
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentId
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone
import com.anfas.core.model.Money
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Only what this component itself owns: permission gating and the client-side status/zone/
 * search filtering. Save/log/mark-out-of-order outcomes are `EquipmentRepositoryTest`'s job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentComponentTest {

    @Test
    fun `an owner may manage equipment`() = runTest {
        val component = component(roles = setOf(Role.Owner))
        component.state.test {
            assertTrue(awaitItem().mayManage)
        }
    }

    @Test
    fun `a coach may not manage equipment`() = runTest {
        val component = component(roles = setOf(Role.Coach))
        component.state.test {
            assertTrue(!awaitItem().mayManage)
        }
    }

    @Test
    fun `a status filter narrows the visible list`() = runTest {
        val treadmill = equipment("Treadmill", "FH-TRD-004", EquipmentStatus.NEEDS_SERVICE)
        val rower = equipment("Rower", "FH-ROW-001", EquipmentStatus.OPERATIONAL)
        val component = component(equipment = listOf(treadmill, rower))

        component.state.test {
            awaitItem()
            component.onStatusFilterChanged(EquipmentStatus.OPERATIONAL)
            val state = awaitItem()
            assertEquals(listOf(rower), state.visibleEquipment)
        }
    }

    @Test
    fun `a zone filter narrows the visible list`() = runTest {
        val treadmill = equipment(
            "Treadmill",
            "FH-TRD-004",
            zone = EquipmentZone.CARDIO_FLOOR,
        )
        val bench = equipment("Bench", "FH-BEN-001", zone = EquipmentZone.WEIGHT_ROOM)
        val component = component(equipment = listOf(treadmill, bench))

        component.state.test {
            awaitItem()
            component.onZoneFilterChanged(EquipmentZone.WEIGHT_ROOM)
            val state = awaitItem()
            assertEquals(listOf(bench), state.visibleEquipment)
        }
    }

    @Test
    fun `search matches name or asset tag case-insensitively`() = runTest {
        val treadmill = equipment("Treadmill", "FH-TRD-004")
        val rower = equipment("Rowing machine", "FH-ROW-001")
        val component = component(equipment = listOf(treadmill, rower))

        component.state.test {
            awaitItem()
            component.onSearchQueryChanged("row")
            val state = awaitItem()
            assertEquals(listOf(rower), state.visibleEquipment)
        }
    }

    private fun equipment(
        name: String,
        assetTag: String,
        status: EquipmentStatus = EquipmentStatus.OPERATIONAL,
        zone: EquipmentZone = EquipmentZone.CARDIO_FLOOR,
    ) = Equipment(
        id = EquipmentId(assetTag),
        name = name,
        assetTag = assetTag,
        status = status,
        zone = zone,
        manufacturer = null,
        serialNumber = null,
        purchasedOn = null,
        warrantyUntil = null,
    )

    private fun TestScope.component(
        roles: Set<Role> = setOf(Role.Owner),
        equipment: List<Equipment> = emptyList(),
    ): EquipmentComponent {
        val lifecycle = LifecycleRegistry()
        val component = EquipmentComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = FakeEquipmentRepository(equipment),
            auth = FakeEquipmentAuth(roles),
            dispatchers = EquipmentTestDispatchers(UnconfinedTestDispatcher(testScheduler)),
        )
        lifecycle.resume()
        return component
    }
}

private class EquipmentTestDispatchers(private val dispatcher: CoroutineDispatcher) :
    AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class FakeEquipmentRepository(initial: List<Equipment>) : EquipmentRepository {
    private val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<AppResult<List<Equipment>>> =
        MutableStateFlow(AppResult.Success(rows.value))

    override fun observeDetail(id: EquipmentId): Flow<AppResult<EquipmentDetail?>> =
        flowOf(AppResult.Success(null))

    override suspend fun createEquipment(
        name: String,
        assetTag: String,
        zone: EquipmentZone,
        status: EquipmentStatus,
        manufacturer: String?,
        serialNumber: String?,
        purchasedOn: LocalDate?,
        warrantyUntil: LocalDate?,
    ): AppResult<SaveEquipmentOutcome> =
        AppResult.Success(SaveEquipmentOutcome.Saved(EquipmentId("new")))

    override suspend fun logMaintenance(
        equipmentId: EquipmentId,
        occurredAt: Instant,
        summary: String,
        details: String,
        reportedByStaffName: String?,
        technician: String?,
        cost: Money?,
        partsUsed: String?,
        resultingStatus: EquipmentStatus,
    ): AppResult<LogMaintenanceOutcome> = AppResult.Success(LogMaintenanceOutcome.Logged)

    override suspend fun markOutOfOrder(id: EquipmentId): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakeEquipmentAuth(private val roles: Set<Role>) : AuthRepository {
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
