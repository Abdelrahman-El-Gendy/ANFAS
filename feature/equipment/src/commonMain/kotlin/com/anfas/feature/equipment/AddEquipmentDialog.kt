package com.anfas.feature.equipment

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.core.data.EquipmentProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone

@Composable
internal fun AddEquipmentDialog(
    form: AddEquipmentForm,
    component: EquipmentComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = s.equipment.newEquipment,
        onDismissRequest = component::onAddFormDismissed,
        closeContentDescription = s.common.close,
        footer = {
            Spacer(Modifier.weight(1f))
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onAddFormDismissed)
            AnfasPrimaryButton(
                text = s.equipment.addEquipment,
                onClick = component::onSubmitAddForm,
                enabled = form.canSubmit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = form.name,
                onValueChange = { value -> component.onAddFormChanged { it.copy(name = value) } },
                label = s.equipment.fieldName,
                enabled = !form.isSubmitting,
                errorMessage = s.equipment.errorNameBlank
                    .takeIf { EquipmentProblem.NAME_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.assetTag,
                onValueChange = { value ->
                    component.onAddFormChanged { it.copy(assetTag = value) }
                },
                label = s.equipment.fieldAssetTag,
                enabled = !form.isSubmitting,
                errorMessage = when {
                    EquipmentProblem.ASSET_TAG_BLANK in form.problems ->
                        s.equipment.errorAssetTagBlank

                    EquipmentProblem.ASSET_TAG_DUPLICATE in form.problems ->
                        s.equipment.errorAssetTagDuplicate

                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(s.equipment.fieldZone)
            ChipRow {
                EquipmentZone.entries.forEach { zone ->
                    AnfasChoiceChip(
                        label = zone.label(s),
                        selected = form.zone == zone,
                        onClick = { component.onAddFormChanged { it.copy(zone = zone) } },
                    )
                }
            }

            FieldLabel(s.equipment.fieldStatus)
            ChipRow {
                EquipmentStatus.entries.forEach { status ->
                    AnfasChoiceChip(
                        label = status.label(s),
                        selected = form.status == status,
                        onClick = { component.onAddFormChanged { it.copy(status = status) } },
                    )
                }
            }

            AnfasTextField(
                value = form.manufacturer,
                onValueChange = { value ->
                    component.onAddFormChanged { it.copy(manufacturer = value) }
                },
                label = s.equipment.fieldManufacturer,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.serialNumber,
                onValueChange = { value ->
                    component.onAddFormChanged { it.copy(serialNumber = value) }
                },
                label = s.equipment.fieldSerialNumber,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.purchasedOnText,
                onValueChange = { value ->
                    component.onAddFormChanged { it.copy(purchasedOnText = value) }
                },
                label = s.equipment.fieldPurchasedOn,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.warrantyUntilText,
                onValueChange = { value ->
                    component.onAddFormChanged { it.copy(warrantyUntilText = value) }
                },
                label = s.equipment.fieldWarrantyUntil,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = AnfasTheme.textStyles.labelCaps,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
