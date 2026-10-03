package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.database.EquipmentDao
import com.anfas.core.database.EquipmentEntity
import com.anfas.core.database.MaintenanceLogEntryEntity
import com.anfas.core.database.SyncOutboxEntity
import com.anfas.core.database.SyncTables
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone
import com.anfas.core.model.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentRepositoryTest {

    @Test
    fun `equipment is saved and read back with the given fields`() = runTest {
        val repository = repository()

        val outcome = repository.createEquipment(
            name = "  Treadmill  ",
            assetTag = "  FH-TRD-004  ",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = EquipmentStatus.OPERATIONAL,
            manufacturer = "Matrix",
            serialNumber = "MX-8892-K",
            purchasedOn = LocalDate.parse("2025-01-12"),
            warrantyUntil = LocalDate.parse("2027-01-12"),
        ).valueOrFail()
        val id = assertIs<SaveEquipmentOutcome.Saved>(outcome).id

        repository.observeAll().test {
            val equipment = awaitItem().valueOrFail().single()
            assertEquals(id, equipment.id)
            assertEquals("Treadmill", equipment.name)
            assertEquals("FH-TRD-004", equipment.assetTag)
            assertEquals(EquipmentZone.CARDIO_FLOOR, equipment.zone)
            assertEquals("Matrix", equipment.manufacturer)
            assertEquals(LocalDate.parse("2025-01-12"), equipment.purchasedOn)
        }
    }

    @Test
    fun `a blank name or asset tag is refused and nothing is written`() = runTest {
        val repository = repository()

        val outcome = repository.createEquipment(
            name = "   ",
            assetTag = "   ",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = EquipmentStatus.ON_ORDER,
            manufacturer = null,
            serialNumber = null,
            purchasedOn = null,
            warrantyUntil = null,
        ).valueOrFail()

        assertEquals(
            setOf(EquipmentProblem.NAME_BLANK, EquipmentProblem.ASSET_TAG_BLANK),
            assertIs<SaveEquipmentOutcome.Invalid>(outcome).problems,
        )
        repository.observeAll().test {
            assertTrue(awaitItem().valueOrFail().isEmpty())
        }
    }

    @Test
    fun `a duplicate asset tag is refused`() = runTest {
        val repository = repository()
        repository.createEquipment(
            name = "Treadmill",
            assetTag = "FH-TRD-004",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = EquipmentStatus.OPERATIONAL,
            manufacturer = null,
            serialNumber = null,
            purchasedOn = null,
            warrantyUntil = null,
        ).valueOrFail()

        val outcome = repository.createEquipment(
            name = "Another treadmill",
            assetTag = "FH-TRD-004",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = EquipmentStatus.OPERATIONAL,
            manufacturer = null,
            serialNumber = null,
            purchasedOn = null,
            warrantyUntil = null,
        ).valueOrFail()

        assertEquals(
            setOf(EquipmentProblem.ASSET_TAG_DUPLICATE),
            assertIs<SaveEquipmentOutcome.Invalid>(outcome).problems,
        )
    }

    @Test
    fun `logging maintenance adds an entry and moves the resulting status`() = runTest {
        val repository = repository()
        val id = createTreadmill(repository, status = EquipmentStatus.NEEDS_SERVICE)

        repository.logMaintenance(
            equipmentId = id,
            occurredAt = Instant.fromEpochSeconds(1_800_000_000),
            summary = "Routine maintenance",
            details = "Lubricated deck, checked motor tension.",
            reportedByStaffName = null,
            technician = "Omar",
            cost = Money.of(350),
            partsUsed = null,
            resultingStatus = EquipmentStatus.OPERATIONAL,
        ).valueOrFail()

        repository.observeDetail(id).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals(EquipmentStatus.OPERATIONAL, detail.equipment.status)
            val entry = detail.log.single()
            assertEquals("Omar", entry.technician)
            assertEquals(Money.of(350), entry.cost)
            assertEquals(Instant.fromEpochSeconds(1_800_000_000), detail.lastServiceOn)
        }
    }

    @Test
    fun `a blank summary is refused and no entry is written`() = runTest {
        val repository = repository()
        val id = createTreadmill(repository)

        val outcome = repository.logMaintenance(
            equipmentId = id,
            occurredAt = Instant.fromEpochSeconds(1_800_000_000),
            summary = "   ",
            details = "",
            reportedByStaffName = null,
            technician = null,
            cost = null,
            partsUsed = null,
            resultingStatus = EquipmentStatus.OPERATIONAL,
        ).valueOrFail()

        assertEquals(
            setOf(MaintenanceProblem.SUMMARY_BLANK),
            assertIs<LogMaintenanceOutcome.Invalid>(outcome).problems,
        )
        repository.observeDetail(id).test {
            assertTrue(awaitItem().valueOrFail()!!.log.isEmpty())
        }
    }

    /** A plain issue report -- no technician -- must not read as a service in the detail. */
    @Test
    fun `a report with no technician does not count as the last service`() = runTest {
        val repository = repository()
        val id = createTreadmill(repository, status = EquipmentStatus.OPERATIONAL)

        repository.logMaintenance(
            equipmentId = id,
            occurredAt = Instant.fromEpochSeconds(1_800_000_000),
            summary = "Reported strange noise",
            details = "Belt squeaks persistently above 8km/h.",
            reportedByStaffName = "Alex",
            technician = null,
            cost = null,
            partsUsed = null,
            resultingStatus = EquipmentStatus.NEEDS_SERVICE,
        ).valueOrFail()

        repository.observeDetail(id).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals(EquipmentStatus.NEEDS_SERVICE, detail.equipment.status)
            assertNull(detail.lastServiceOn)
        }
    }

    @Test
    fun `mark out of order flips status with no log entry`() = runTest {
        val repository = repository()
        val id = createTreadmill(repository, status = EquipmentStatus.OPERATIONAL)

        repository.markOutOfOrder(id).valueOrFail()

        repository.observeDetail(id).test {
            val detail = awaitItem().valueOrFail()!!
            assertEquals(EquipmentStatus.OUT_OF_ORDER, detail.equipment.status)
            assertTrue(detail.log.isEmpty())
        }
    }

    private suspend fun createTreadmill(
        repository: EquipmentRepository,
        status: EquipmentStatus = EquipmentStatus.OPERATIONAL,
    ) = assertIs<SaveEquipmentOutcome.Saved>(
        repository.createEquipment(
            name = "Treadmill",
            assetTag = "FH-TRD-004",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = status,
            manufacturer = null,
            serialNumber = null,
            purchasedOn = null,
            warrantyUntil = null,
        ).valueOrFail(),
    ).id

    /** Every write path here files an outbox entry. See SyncOutboxTest for why that matters. */
    @Test
    fun `each equipment write files an outbox entry`() = runTest {
        val dao = FakeEquipmentDao()
        val repository = OfflineFirstEquipmentRepository(dao, UnconfinedDispatchers)

        val saved = repository.createEquipment(
            name = "Treadmill",
            assetTag = "FH-TRD-004",
            zone = EquipmentZone.CARDIO_FLOOR,
            status = EquipmentStatus.OPERATIONAL,
            manufacturer = null,
            serialNumber = null,
            purchasedOn = null,
            warrantyUntil = null,
        ).valueOrFail()
        val id = assertIs<SaveEquipmentOutcome.Saved>(saved).id
        assertEquals(listOf(id.value), dao.sync.upserts(SyncTables.EQUIPMENT))

        repository.logMaintenance(
            equipmentId = id,
            occurredAt = Instant.fromEpochMilliseconds(0),
            summary = "Belt replaced",
            details = "",
            reportedByStaffName = null,
            technician = "Sam",
            cost = null,
            partsUsed = null,
            resultingStatus = EquipmentStatus.OPERATIONAL,
        ).valueOrFail()
        // Two rows written, so two entries: a push carrying only one of them would leave the
        // other device with a repair note against a machine still marked broken.
        assertEquals(1, dao.sync.upserts(SyncTables.MAINTENANCE_LOG).size)
        assertEquals(listOf(id.value, id.value), dao.sync.upserts(SyncTables.EQUIPMENT))

        repository.markOutOfOrder(id).valueOrFail()
        assertEquals(3, dao.sync.upserts(SyncTables.EQUIPMENT).size)
    }

    private fun repository(): EquipmentRepository = OfflineFirstEquipmentRepository(
        equipment = FakeEquipmentDao(),
        dispatchers = UnconfinedDispatchers,
    )
}

internal class FakeEquipmentDao : EquipmentDao {
    private val rows = MutableStateFlow<List<EquipmentEntity>>(emptyList())
    private val log = MutableStateFlow<List<MaintenanceLogEntryEntity>>(emptyList())

    override fun observeAll(): Flow<List<EquipmentEntity>> = rows

    override fun observeById(id: String): Flow<EquipmentEntity?> =
        rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun findById(id: String): EquipmentEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun findByAssetTag(assetTag: String): EquipmentEntity? =
        rows.value.firstOrNull { it.assetTag == assetTag }

    override suspend fun upsert(equipment: EquipmentEntity) {
        rows.value = rows.value.filterNot { it.id == equipment.id } + equipment
    }

    override fun observeLog(equipmentId: String): Flow<List<MaintenanceLogEntryEntity>> =
        log.map { list ->
            list.filter { it.equipmentId == equipmentId }
                .sortedByDescending { it.occurredAtEpochMs }
        }

    override suspend fun insertLogEntry(entry: MaintenanceLogEntryEntity) {
        log.value = log.value + entry
    }

    // --- sync bookkeeping. The tracked writes are default methods on the DAO, so implementing
    // these two gives this fake the production sequencing rather than a re-implementation of it.
    val sync = OutboxRecorder()

    override suspend fun recordChange(entry: SyncOutboxEntity) = sync.record(entry)
}
