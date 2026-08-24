package com.anfas.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.core.data.MaintenanceProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.EquipmentStatus

/**
 * No date field -- see `MaintenanceLogEntry`'s KDoc on why an entry is always logged "now".
 */
@Composable
internal fun LogMaintenanceDialog(
    form: LogMaintenanceForm,
    component: EquipmentComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = s.equipment.logMaintenance,
        onDismissRequest = component::onLogFormDismissed,
        closeContentDescription = s.common.close,
        footer = {
            Spacer(Modifier.weight(1f))
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onLogFormDismissed)
            AnfasPrimaryButton(
                text = s.equipment.logMaintenance,
                onClick = component::onSubmitLogForm,
                enabled = form.canSubmit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = form.summary,
                onValueChange = { value ->
                    component.onLogFormChanged { it.copy(summary = value) }
                },
                label = s.equipment.fieldSummary,
                enabled = !form.isSubmitting,
                errorMessage = s.equipment.errorSummaryBlank
                    .takeIf { MaintenanceProblem.SUMMARY_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.details,
                onValueChange = { value ->
                    component.onLogFormChanged { it.copy(details = value) }
                },
                label = s.equipment.fieldDetails,
                singleLine = false,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.technician,
                onValueChange = { value ->
                    component.onLogFormChanged { it.copy(technician = value) }
                },
                label = s.equipment.fieldTechnician,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.costText,
                onValueChange = { value ->
                    component.onLogFormChanged { it.copy(costText = value) }
                },
                label = s.equipment.fieldCost,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.partsUsed,
                onValueChange = { value ->
                    component.onLogFormChanged { it.copy(partsUsed = value) }
                },
                label = s.equipment.fieldPartsUsed,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(s.equipment.resultingStatusTitle)
            ChipRow {
                EquipmentStatus.entries.forEach { status ->
                    AnfasChoiceChip(
                        label = status.label(s),
                        selected = form.resultingStatus == status,
                        onClick = {
                            component.onLogFormChanged { it.copy(resultingStatus = status) }
                        },
                    )
                }
            }
        }
    }
}
