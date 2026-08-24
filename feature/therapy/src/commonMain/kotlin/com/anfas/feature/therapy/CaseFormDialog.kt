package com.anfas.feature.therapy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.core.data.CaseProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings
import com.anfas.feature.therapy.CaseForm

/** Open (or edit) a case. Free-text intake fields, and a chip picker for the therapist. */
@Composable
internal fun CaseFormDialog(form: CaseForm, component: TherapyComponent, s: AppStrings) {
    val state by component.state.collectAsState()
    AnfasDialog(
        title = if (form.editing) s.therapy.editCase else s.therapy.openCase,
        onDismissRequest = component::onCaseFormDismissed,
        closeContentDescription = s.common.close,
        footer = {
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onCaseFormDismissed)
            AnfasPrimaryButton(
                text = s.therapy.save,
                onClick = component::onSubmitCaseForm,
                enabled = form.canSubmit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = form.condition,
                onValueChange = { value ->
                    component.onCaseFormChanged { it.copy(condition = value) }
                },
                label = s.therapy.fieldCondition,
                enabled = !form.isSubmitting,
                errorMessage = s.therapy.errorConditionBlank
                    .takeIf { CaseProblem.CONDITION_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(s.therapy.fieldTherapist)
            ChipRow {
                AnfasChoiceChip(
                    label = s.therapy.unassignedTherapist,
                    selected = form.therapistStaffId == null,
                    onClick = { component.onCaseFormChanged { it.copy(therapistStaffId = null) } },
                )
                state.staffOptions.forEach { (id, name) ->
                    AnfasChoiceChip(
                        label = name,
                        selected = form.therapistStaffId == id,
                        onClick = {
                            component.onCaseFormChanged { it.copy(therapistStaffId = id) }
                        },
                    )
                }
            }

            AnfasTextField(
                value = form.referredBy,
                onValueChange = { value ->
                    component.onCaseFormChanged { it.copy(referredBy = value) }
                },
                label = s.therapy.fieldReferredBy,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            AnfasTextField(
                value = form.onset,
                onValueChange = { value -> component.onCaseFormChanged { it.copy(onset = value) } },
                label = s.therapy.fieldOnset,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            AnfasTextField(
                value = form.mechanism,
                onValueChange = { value ->
                    component.onCaseFormChanged { it.copy(mechanism = value) }
                },
                label = s.therapy.fieldMechanism,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            AnfasTextField(
                value = form.contraindications,
                onValueChange = { value ->
                    component.onCaseFormChanged { it.copy(contraindications = value) }
                },
                label = s.therapy.fieldContraindications,
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

/** Chips scroll rather than wrap, matching `ClassFormDialog`'s picker rows. */
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
