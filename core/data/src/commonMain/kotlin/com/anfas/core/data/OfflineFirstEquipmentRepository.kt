package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.EquipmentDao
import com.anfas.core.database.EquipmentEntity
import com.anfas.core.database.MaintenanceLogEntryEntity
import com.anfas.core.database.SyncTables
import com.anfas.core.model.Currency
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentId
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone
import com.anfas.core.model.MaintenanceLogEntry
import com.anfas.core.model.MaintenanceLogId
import com.anfas.core.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class OfflineFirstEquipmentRepository(
    private val equipment: EquipmentDao,
    private val dispatchers: AppDispatchers,
) : EquipmentRepository {

    override fun observeAll(): Flow<AppResult<List<Equipment>>> =
        equipment.observeAll().asAppResult("Could not load equipment") { rows ->
            rows.map { it.toDomain() }
        }

    override fun observeDetail(id: EquipmentId): Flow<AppResult<EquipmentDetail?>> =
        combine(equipment.observeById(id.value), equipment.observeLog(id.value)) { row, entries ->
            row?.let { EquipmentDetail(it.toDomain(), entries.map { entry -> entry.toDomain() }) }
        }.asAppResult("Could not load equipment") { it }

    override suspend fun createEquipment(
        name: String,
        assetTag: String,
        zone: EquipmentZone,
        status: EquipmentStatus,
        manufacturer: String?,
        serialNumber: String?,
        purchasedOn: LocalDate?,
        warrantyUntil: LocalDate?,
    ): AppResult<SaveEquipmentOutcome> = withContext(dispatchers.io) {
        runStorage("Could not save the equipment") {
            val trimmedTag = assetTag.trim()
            val problems = buildSet {
                if (name.isBlank()) add(EquipmentProblem.NAME_BLANK)
                if (trimmedTag.isEmpty()) add(EquipmentProblem.ASSET_TAG_BLANK)
            }
            if (problems.isNotEmpty()) return@runStorage SaveEquipmentOutcome.Invalid(problems)
            if (equipment.findByAssetTag(trimmedTag) != null) {
                return@runStorage SaveEquipmentOutcome.Invalid(
                    setOf(EquipmentProblem.ASSET_TAG_DUPLICATE),
                )
            }

            val id = EquipmentId(Uuid.random().toString())
            equipment.upsertTracked(
                equipment = EquipmentEntity(
                    id = id.value,
                    name = name.trim(),
                    assetTag = trimmedTag,
                    status = status.name,
                    zone = zone.name,
                    manufacturer = manufacturer?.trim()?.takeIf { it.isNotEmpty() },
                    serialNumber = serialNumber?.trim()?.takeIf { it.isNotEmpty() },
                    purchasedOnEpochDay = purchasedOn?.toEpochDays(),
                    warrantyUntilEpochDay = warrantyUntil?.toEpochDays(),
                ),
                change = changeFor(SyncTables.EQUIPMENT, id.value),
            )
            SaveEquipmentOutcome.Saved(id)
        }
    }

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
    ): AppResult<LogMaintenanceOutcome> = withContext(dispatchers.io) {
        runStorage("Could not log maintenance") {
            if (summary.isBlank()) {
                return@runStorage LogMaintenanceOutcome.Invalid(
                    setOf(MaintenanceProblem.SUMMARY_BLANK),
                )
            }
            val existing = equipment.findById(equipmentId.value)
                ?: return@runStorage LogMaintenanceOutcome.NotFound

            val logId = Uuid.random().toString()
            val at = capturedAt()
            equipment.insertLogEntryTracked(
                entry = MaintenanceLogEntryEntity(
                    id = logId,
                    equipmentId = equipmentId.value,
                    occurredAtEpochMs = occurredAt.toEpochMilliseconds(),
                    summary = summary.trim(),
                    details = details.trim(),
                    reportedByStaffName = reportedByStaffName?.trim()?.takeIf { it.isNotEmpty() },
                    technician = technician?.trim()?.takeIf { it.isNotEmpty() },
                    costMinorUnits = cost?.minorUnits,
                    partsUsed = partsUsed?.trim()?.takeIf { it.isNotEmpty() },
                ),
                equipment = existing.copy(status = resultingStatus.name),
                // Two rows, two entries. The log entry and the machine's new status are separate
                // records that happen to be written together, and a push that carried only one of
                // them would leave the other device with a repair note against a machine still
                // marked broken.
                changes = listOf(
                    changeFor(SyncTables.MAINTENANCE_LOG, logId, at),
                    changeFor(SyncTables.EQUIPMENT, equipmentId.value, at),
                ),
            )
            LogMaintenanceOutcome.Logged
        }
    }

    override suspend fun markOutOfOrder(id: EquipmentId): AppResult<Unit> =
        withContext(dispatchers.io) {
            runStorage("Could not update the equipment") {
                val existing = equipment.findById(id.value) ?: return@runStorage Unit
                equipment.upsertTracked(
                    equipment = existing.copy(status = EquipmentStatus.OUT_OF_ORDER.name),
                    change = changeFor(SyncTables.EQUIPMENT, id.value),
                )
            }
        }
}

private fun EquipmentEntity.toDomain() = Equipment(
    id = EquipmentId(id),
    name = name,
    assetTag = assetTag,
    status = EquipmentStatus.entries.firstOrNull { it.name == status }
        ?: EquipmentStatus.OPERATIONAL,
    zone = EquipmentZone.entries.firstOrNull { it.name == zone } ?: EquipmentZone.CARDIO_FLOOR,
    manufacturer = manufacturer,
    serialNumber = serialNumber,
    purchasedOn = purchasedOnEpochDay?.let { LocalDate.fromEpochDays(it) },
    warrantyUntil = warrantyUntilEpochDay?.let { LocalDate.fromEpochDays(it) },
)

private fun MaintenanceLogEntryEntity.toDomain() = MaintenanceLogEntry(
    id = MaintenanceLogId(id),
    equipmentId = EquipmentId(equipmentId),
    occurredAt = Instant.fromEpochMilliseconds(occurredAtEpochMs),
    summary = summary,
    details = details,
    reportedByStaffName = reportedByStaffName,
    technician = technician,
    cost = costMinorUnits?.let { Money(it, Currency.EGP) },
    partsUsed = partsUsed,
)
