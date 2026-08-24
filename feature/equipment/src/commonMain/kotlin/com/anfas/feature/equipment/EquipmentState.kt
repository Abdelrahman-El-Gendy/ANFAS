package com.anfas.feature.equipment

import com.anfas.core.data.EquipmentDetail
import com.anfas.core.data.EquipmentProblem
import com.anfas.core.data.MaintenanceProblem
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone

sealed interface EquipmentContent {
    data object Loading : EquipmentContent
    data class Loaded(val equipment: List<Equipment>) : EquipmentContent
    data object Empty : EquipmentContent
    data class Failed(val message: String) : EquipmentContent
}

/**
 * [visibleEquipment] is [EquipmentContent.Loaded]'s list narrowed by [statusFilter]/[zoneFilter]/
 * [searchQuery], computed by the component rather than derived here — a small, in-memory
 * inventory (a gym's worth of equipment, not a member directory) needs no repository-level
 * search the way `MembersListComponent`'s debounced query does.
 */
data class EquipmentState(
    val content: EquipmentContent = EquipmentContent.Loading,
    val visibleEquipment: List<Equipment> = emptyList(),
    /** Null means the "All" chip. */
    val statusFilter: EquipmentStatus? = null,
    /** Null means the "All zones" option. */
    val zoneFilter: EquipmentZone? = null,
    val searchQuery: String = "",
    val addForm: AddEquipmentForm? = null,
    val detail: EquipmentDetailState? = null,
    val notice: EquipmentNotice? = null,
    /** Whether this session holds `Permission.MANAGE_EQUIPMENT`. */
    val mayManage: Boolean = false,
)

/** Non-null while the detail drawer is open for [id]. [detail] is null until it loads. */
data class EquipmentDetailState(
    val id: String,
    val detail: EquipmentDetail? = null,
    val logForm: LogMaintenanceForm? = null,
)

data class AddEquipmentForm(
    val name: String = "",
    val assetTag: String = "",
    val zone: EquipmentZone = EquipmentZone.CARDIO_FLOOR,
    val status: EquipmentStatus = EquipmentStatus.OPERATIONAL,
    val manufacturer: String = "",
    val serialNumber: String = "",
    /** Free text, parsed with `IntakeValidator.parseDate` the same way the announcement
     * composer's event date is -- blank means unknown, not "on order with no date". */
    val purchasedOnText: String = "",
    val warrantyUntilText: String = "",
    val isSubmitting: Boolean = false,
    val problems: Set<EquipmentProblem> = emptySet(),
) {
    val canSubmit: Boolean get() = !isSubmitting && name.isNotBlank() && assetTag.isNotBlank()
}

/**
 * No date field on purpose -- see `MaintenanceLogEntry`'s KDoc on why an entry is always logged
 * "now" rather than backdated.
 */
data class LogMaintenanceForm(
    val summary: String = "",
    val details: String = "",
    val reportedByStaffName: String = "",
    val technician: String = "",
    val costText: String = "",
    val partsUsed: String = "",
    val resultingStatus: EquipmentStatus = EquipmentStatus.OPERATIONAL,
    val isSubmitting: Boolean = false,
    val problems: Set<MaintenanceProblem> = emptySet(),
) {
    val canSubmit: Boolean get() = !isSubmitting && summary.isNotBlank()
}

sealed interface EquipmentNotice {
    data object EquipmentSaved : EquipmentNotice
    data object MaintenanceLogged : EquipmentNotice
    data object MarkedOutOfOrder : EquipmentNotice
    data class Failed(val message: String) : EquipmentNotice
}
