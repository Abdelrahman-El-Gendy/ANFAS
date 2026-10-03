package com.anfas.feature.equipment

import com.anfas.core.auth.Permission
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.EquipmentRepository
import com.anfas.core.data.LogMaintenanceOutcome
import com.anfas.core.data.SaveEquipmentOutcome
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentId
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone
import com.anfas.core.model.Money
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The equipment inventory list, its filters, and the detail drawer -- one component for all
 * three, the same shape as `AnnouncementsComponent`: they always appear together on this screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentComponent(
    componentContext: ComponentContext,
    private val repository: EquipmentRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Equipment"))

    private val ui = MutableStateFlow(UiState())

    val state: StateFlow<EquipmentState> = combine(
        ui,
        auth.observeSession(),
        repository.observeAll(),
        ui.map { it.detailId }.flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeDetail(EquipmentId(id))
        },
    ) { local, session, equipmentResult, detailResult ->
        val content = when (equipmentResult) {
            is AppResult.Failure -> EquipmentContent.Failed(equipmentResult.error.message)

            is AppResult.Success ->
                if (equipmentResult.value.isEmpty()) {
                    EquipmentContent.Empty
                } else {
                    EquipmentContent.Loaded(equipmentResult.value)
                }
        }
        EquipmentState(
            content = content,
            visibleEquipment = (content as? EquipmentContent.Loaded)
                ?.equipment
                ?.filter { it.matches(local.statusFilter, local.zoneFilter, local.searchQuery) }
                .orEmpty(),
            statusFilter = local.statusFilter,
            zoneFilter = local.zoneFilter,
            searchQuery = local.searchQuery,
            addForm = local.addForm,
            detail = local.detailId?.let { id ->
                EquipmentDetailState(
                    id = id,
                    detail = (detailResult as? AppResult.Success)?.value,
                    logForm = local.logForm,
                )
            },
            notice = local.notice,
            mayManage = session?.can(Permission.MANAGE_EQUIPMENT) == true,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = EquipmentState(),
    )

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    fun onStatusFilterChanged(status: EquipmentStatus?) =
        ui.update { it.copy(statusFilter = status) }

    fun onZoneFilterChanged(zone: EquipmentZone?) = ui.update { it.copy(zoneFilter = zone) }

    fun onSearchQueryChanged(query: String) = ui.update { it.copy(searchQuery = query) }

    fun onNewEquipment() = ui.update { it.copy(addForm = AddEquipmentForm(), notice = null) }

    fun onAddFormDismissed() = ui.update { it.copy(addForm = null) }

    fun onAddFormChanged(transform: (AddEquipmentForm) -> AddEquipmentForm) = ui.update { local ->
        local.copy(addForm = local.addForm?.let { transform(it).copy(problems = emptySet()) })
    }

    fun onSubmitAddForm() {
        val form = state.value.addForm ?: return
        if (!form.canSubmit) return
        // Straight through: the calendar picker cannot hand back an unparseable date, so there
        // is nothing to parse and no failure branch to handle.
        val purchasedOn = form.purchasedOn
        val warrantyUntil = form.warrantyUntil

        ui.update { it.copy(addForm = form.copy(isSubmitting = true)) }
        scope.launch {
            val result = repository.createEquipment(
                name = form.name,
                assetTag = form.assetTag,
                zone = form.zone,
                status = form.status,
                manufacturer = form.manufacturer.takeIf { it.isNotBlank() },
                serialNumber = form.serialNumber.takeIf { it.isNotBlank() },
                purchasedOn = purchasedOn,
                warrantyUntil = warrantyUntil,
            )
            when (result) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        addForm = it.addForm?.copy(isSubmitting = false),
                        notice = EquipmentNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is SaveEquipmentOutcome.Invalid -> ui.update {
                        it.copy(
                            addForm = it.addForm?.copy(
                                isSubmitting = false,
                                problems = outcome.problems,
                            ),
                        )
                    }

                    is SaveEquipmentOutcome.Saved -> ui.update {
                        it.copy(addForm = null, notice = EquipmentNotice.EquipmentSaved)
                    }
                }
            }
        }
    }

    fun onEquipmentSelected(id: EquipmentId) = ui.update {
        it.copy(detailId = id.value, logForm = null)
    }

    fun onDetailDismissed() = ui.update { it.copy(detailId = null, logForm = null) }

    fun onRequestLogMaintenance() = ui.update {
        if (it.detailId == null) it else it.copy(logForm = LogMaintenanceForm())
    }

    fun onLogFormDismissed() = ui.update { it.copy(logForm = null) }

    fun onLogFormChanged(transform: (LogMaintenanceForm) -> LogMaintenanceForm) =
        ui.update { local ->
            local.copy(logForm = local.logForm?.let { transform(it).copy(problems = emptySet()) })
        }

    fun onSubmitLogForm() {
        val local = ui.value
        val equipmentId = local.detailId ?: return
        val form = local.logForm ?: return
        if (!form.canSubmit) return

        val cost = form.costText.trim().toLongOrNull()?.let(Money::of)
        ui.update { it.copy(logForm = form.copy(isSubmitting = true)) }
        scope.launch {
            val result = repository.logMaintenance(
                equipmentId = EquipmentId(equipmentId),
                occurredAt = clock.now(),
                summary = form.summary,
                details = form.details,
                reportedByStaffName = form.reportedByStaffName.takeIf { it.isNotBlank() },
                technician = form.technician.takeIf { it.isNotBlank() },
                cost = cost,
                partsUsed = form.partsUsed.takeIf { it.isNotBlank() },
                resultingStatus = form.resultingStatus,
            )
            when (result) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        logForm = it.logForm?.copy(isSubmitting = false),
                        notice = EquipmentNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is LogMaintenanceOutcome.Invalid -> ui.update {
                        it.copy(
                            logForm = it.logForm?.copy(
                                isSubmitting = false,
                                problems = outcome.problems,
                            ),
                        )
                    }

                    LogMaintenanceOutcome.NotFound -> ui.update {
                        it.copy(
                            detailId = null,
                            logForm = null,
                            notice = EquipmentNotice.Failed(
                                "This equipment no longer exists",
                            ),
                        )
                    }

                    LogMaintenanceOutcome.Logged -> ui.update {
                        it.copy(logForm = null, notice = EquipmentNotice.MaintenanceLogged)
                    }
                }
            }
        }
    }

    /** The drawer's quick action -- see `EquipmentRepository.markOutOfOrder`'s KDoc on why this
     * skips the log form entirely. */
    fun onMarkOutOfOrder() {
        val equipmentId = ui.value.detailId ?: return
        scope.launch {
            when (val result = repository.markOutOfOrder(EquipmentId(equipmentId))) {
                is AppResult.Failure ->
                    ui.update { it.copy(notice = EquipmentNotice.Failed(result.error.message)) }

                is AppResult.Success ->
                    ui.update { it.copy(notice = EquipmentNotice.MarkedOutOfOrder) }
            }
        }
    }

    private data class UiState(
        val statusFilter: EquipmentStatus? = null,
        val zoneFilter: EquipmentZone? = null,
        val searchQuery: String = "",
        val addForm: AddEquipmentForm? = null,
        val detailId: String? = null,
        val logForm: LogMaintenanceForm? = null,
        val notice: EquipmentNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun Equipment.matches(
    statusFilter: EquipmentStatus?,
    zoneFilter: EquipmentZone?,
    searchQuery: String,
): Boolean {
    val statusOk = statusFilter == null || status == statusFilter
    val zoneOk = zoneFilter == null || zone == zoneFilter
    val query = searchQuery.trim()
    val queryOk = query.isEmpty() ||
        name.contains(query, ignoreCase = true) ||
        assetTag.contains(query, ignoreCase = true)
    return statusOk && zoneOk && queryOk
}
