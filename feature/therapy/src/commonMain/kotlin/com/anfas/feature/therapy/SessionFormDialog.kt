package com.anfas.feature.therapy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anfas.core.data.SessionProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.TherapySession
import com.anfas.core.model.TreatmentType

/**
 * Log a session. [DAY_OFFSETS] bounds the date to the last week, the same reasoning as
 * `ClassFormDialog`'s `START_TIMES`/`DURATIONS` chip sets: sessions are logged same-week almost
 * always, and a raw date picker is more machinery than the real workflow needs.
 */
@Composable
internal fun SessionFormDialog(form: SessionForm, component: TherapyComponent, s: AppStrings) {
    val state by component.state.collectAsState()
    AnfasDialog(
        title = s.therapy.logSession,
        onDismissRequest = component::onSessionFormDismissed,
        closeContentDescription = s.common.close,
        footer = {
            AnfasSecondaryButton(
                text = s.common.cancel,
                onClick = component::onSessionFormDismissed,
            )
            AnfasPrimaryButton(
                text = s.therapy.save,
                onClick = component::onSubmitSessionForm,
                enabled = form.canSubmit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FieldLabel(s.therapy.fieldDate)
            ChipRow {
                DAY_OFFSETS.forEach { offset ->
                    AnfasChoiceChip(
                        label = dayOffsetLabel(offset, s),
                        selected = form.dayOffset == offset,
                        onClick = {
                            component.onSessionFormChanged { it.copy(dayOffset = offset) }
                        },
                    )
                }
            }

            FieldLabel(s.therapy.fieldTherapist)
            ChipRow {
                AnfasChoiceChip(
                    label = s.therapy.unassignedTherapist,
                    selected = form.therapistStaffId == null,
                    onClick = {
                        component.onSessionFormChanged { it.copy(therapistStaffId = null) }
                    },
                )
                state.staffOptions.forEach { (id, name) ->
                    AnfasChoiceChip(
                        label = name,
                        selected = form.therapistStaffId == id,
                        onClick = {
                            component.onSessionFormChanged { it.copy(therapistStaffId = id) }
                        },
                    )
                }
            }

            FieldLabel(s.therapy.fieldDuration)
            ChipRow {
                DURATIONS.forEach { minutes ->
                    AnfasChoiceChip(
                        label = s.therapy.durationMinutes(minutes),
                        selected = form.durationMinutes == minutes,
                        onClick = {
                            component.onSessionFormChanged { it.copy(durationMinutes = minutes) }
                        },
                    )
                }
            }

            FieldLabel(s.therapy.fieldTreatmentTypes)
            ChipRow {
                TreatmentType.entries.forEach { type ->
                    val selected = type in form.treatmentTypes
                    AnfasChoiceChip(
                        label = type.label(s),
                        selected = selected,
                        onClick = {
                            component.onSessionFormChanged {
                                it.copy(
                                    treatmentTypes = if (selected) {
                                        it.treatmentTypes - type
                                    } else {
                                        it.treatmentTypes + type
                                    },
                                )
                            }
                        },
                    )
                }
            }

            AnfasTextField(
                value = form.painScoreText,
                // Digits only, folded from Arabic-Indic on input -- the same guard the classes
                // capacity field and the intake phone field use, so a non-blank value always
                // parses and only its *range* is a validation concern.
                onValueChange = { value ->
                    component.onSessionFormChanged { it.copy(painScoreText = value.digitsOnly()) }
                },
                label = s.therapy.fieldPainScore,
                keyboardType = KeyboardType.Number,
                errorMessage = s.therapy.errorPainScore
                    .takeIf { SessionProblem.PAIN_SCORE_OUT_OF_RANGE in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.notes,
                onValueChange = { value ->
                    component.onSessionFormChanged { it.copy(notes = value) }
                },
                label = s.therapy.fieldNotes,
                modifier = Modifier.fillMaxWidth(),
            )

            if (SessionProblem.DURATION_OUT_OF_RANGE in form.problems) {
                FieldLabel(s.therapy.errorDuration)
            }
        }
    }
}

/**
 * Folds Arabic-Indic digits to ASCII before filtering, for the same reason
 * `IntakeValidator.normalisePhone` and `ClassFormDialog`'s capacity field do.
 */
private fun String.digitsOnly(): String = map { char ->
    when (char) {
        in '٠'..'٩' -> char - '٠' + '0'.code
        in '۰'..'۹' -> char - '۰' + '0'.code
        else -> char.code
    }
}.map { it.toChar() }.filter { it in '0'..'9' }.joinToString("")

private fun dayOffsetLabel(offset: Int, s: AppStrings): String = when (offset) {
    0 -> s.therapy.sessionToday
    1 -> s.therapy.sessionYesterday
    else -> s.therapy.sessionDaysAgo(offset)
}

/** Today through a week ago — see the file KDoc. */
private val DAY_OFFSETS = 0..7

/** The lengths a session is actually booked in, matching `ClassFormDialog`'s DURATIONS. */
private val DURATIONS = listOf(
    TherapySession.MIN_DURATION_MINUTES.coerceAtLeast(15),
    30,
    45,
    60,
    90,
    120,
).distinct().sorted()
