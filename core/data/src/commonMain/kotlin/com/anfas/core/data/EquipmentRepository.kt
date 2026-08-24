package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentId
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone
import com.anfas.core.model.MaintenanceLog
import com.anfas.core.model.MaintenanceLogEntry
import com.anfas.core.model.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** The gym floor's equipment inventory and its maintenance history. */
interface EquipmentRepository {

    fun observeAll(): Flow<AppResult<List<Equipment>>>

    fun observeDetail(id: EquipmentId): Flow<AppResult<EquipmentDetail?>>

    suspend fun createEquipment(
        name: String,
        assetTag: String,
        zone: EquipmentZone,
        status: EquipmentStatus,
        manufacturer: String?,
        serialNumber: String?,
        purchasedOn: LocalDate?,
        warrantyUntil: LocalDate?,
    ): AppResult<SaveEquipmentOutcome>

    /**
     * Adds a service record or issue report and, in the same write, moves the equipment to
     * [resultingStatus]. The two happen together on purpose — the point of writing up what was
     * done is to say what state it leaves the machine in, the same reasoning `CheckInPolicy`
     * uses for deciding an outcome rather than letting a caller pass one in separately.
     */
    suspend fun logMaintenance(
        equipmentId: EquipmentId,
        occurredAt: Instant,
        summary: String,
        details: String,
        reportedByStaffName: String?,
        technician: String?,
        cost: Money?,
        partsUsed: String?,
        resultingStatus: EquipmentStatus,
    ): AppResult<LogMaintenanceOutcome>

    /**
     * The drawer's quick, standalone action — flips status with no log entry, for the moment
     * something breaks and there is nothing yet to write up. A full write-up still goes through
     * [logMaintenance].
     */
    suspend fun markOutOfOrder(id: EquipmentId): AppResult<Unit>
}

/** An equipment row with its history, the same shape as `AnnouncementDetail`/`TherapyCaseDetail`. */
data class EquipmentDetail(val equipment: Equipment, val log: List<MaintenanceLogEntry>) {
    val lastServiceOn: Instant? get() = MaintenanceLog.lastServiceOn(log)
}

sealed interface SaveEquipmentOutcome {
    data class Saved(val id: EquipmentId) : SaveEquipmentOutcome
    data class Invalid(val problems: Set<EquipmentProblem>) : SaveEquipmentOutcome
}

enum class EquipmentProblem {
    NAME_BLANK,
    ASSET_TAG_BLANK,

    /** Asset tags are how staff identify a physical unit on the floor — two rows sharing one
     * would make "which treadmill" ambiguous the first time someone reads the tag off the unit
     * itself rather than the app. */
    ASSET_TAG_DUPLICATE,
}

sealed interface LogMaintenanceOutcome {
    data object Logged : LogMaintenanceOutcome
    data class Invalid(val problems: Set<MaintenanceProblem>) : LogMaintenanceOutcome

    /** The equipment row is gone — deleted on another device, or the id is stale. */
    data object NotFound : LogMaintenanceOutcome
}

enum class MaintenanceProblem {
    SUMMARY_BLANK,
}
