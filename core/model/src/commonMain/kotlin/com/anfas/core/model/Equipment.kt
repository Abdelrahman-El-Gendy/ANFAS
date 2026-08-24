package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * A physical asset on the gym floor — the export's `equipment-detail`.
 *
 * [manufacturer], [serialNumber], [purchasedOn] and [warrantyUntil] are all nullable: a piece
 * still [EquipmentStatus.ON_ORDER] has none of them yet, matching the export's own "TBD" card.
 */
data class Equipment(
    val id: EquipmentId,
    val name: String,
    val assetTag: String,
    val status: EquipmentStatus,
    val zone: EquipmentZone,
    val manufacturer: String?,
    val serialNumber: String?,
    val purchasedOn: LocalDate?,
    val warrantyUntil: LocalDate?,
)

enum class EquipmentStatus {
    OPERATIONAL,
    NEEDS_SERVICE,
    OUT_OF_ORDER,

    /** Purchased but not yet on the floor — the export's dashed, greyed-out card. */
    ON_ORDER,
}

/** The export's own zone filter has exactly these three; not free text, so a filter chip can
 * enumerate them without ever seeing an unrecognised zone name. */
enum class EquipmentZone {
    CARDIO_FLOOR,
    WEIGHT_ROOM,
    RECOVERY,
}

/**
 * One entry in a piece of equipment's history — an issue report or a completed service, told
 * apart by whether [technician] is set rather than by a separate type field: a report has
 * someone noticing a problem, a service record has someone who did the work.
 *
 * No backdating: [occurredAt] is always the moment it was logged, the same simplification this
 * app already makes for announcements (`createdAt`) and check-ins. Unlike a therapy session,
 * where the treatment date has clinical meaning, nothing here needs a person to log a repair
 * from yesterday, so there is no day-offset picker to build.
 */
data class MaintenanceLogEntry(
    val id: MaintenanceLogId,
    val equipmentId: EquipmentId,
    val occurredAt: Instant,
    val summary: String,
    val details: String,
    /** Who noticed the problem, if this entry is a report. Free text, not a staff id — the
     * export's "Alex (Staff)" reads like a first name, not an account to resolve. */
    val reportedByStaffName: String?,
    /** Who did the work, if this entry is a service record. Free text because the export names
     * both an internal name ("Omar") and an external vendor ("Matrix Service") here — a single
     * staff foreign key could never hold the second case. */
    val technician: String?,
    val cost: Money?,
    val partsUsed: String?,
)

/**
 * Pure, so the detail drawer's "Last service" figure can never be computed one way in the
 * repository and another way on screen.
 */
object MaintenanceLog {
    /**
     * The most recent entry with a [MaintenanceLogEntry.technician] — matches the export's own
     * "Last service" field, which tracks completed work, not a plain issue report. A report
     * logged after the last real service (e.g. "belt squeaks") must not push the date forward;
     * nobody has serviced the machine yet.
     */
    fun lastServiceOn(entries: List<MaintenanceLogEntry>): Instant? =
        entries.filter { it.technician != null }.maxOfOrNull { it.occurredAt }
}
