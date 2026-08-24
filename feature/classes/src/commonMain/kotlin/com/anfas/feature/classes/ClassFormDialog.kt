package com.anfas.feature.classes

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.anfas.core.data.ClassProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.asLtrIsolate
import com.anfas.core.model.ClassCategory
import com.anfas.core.model.minuteOfDayToTime
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

/**
 * Add or edit a class.
 *
 * Times and durations are **chips, not text fields**. A gym timetable runs on the half hour, and
 * a free-text time box means parsing "6", "6pm", "١٨:٣٠" and deciding what "6" meant — the same
 * ambiguity `IntakeValidator.parseDate` refuses to guess at for dates. Chips also make the form
 * usable one-handed at a desk, which a keyboard time entry is not.
 */
@Composable
internal fun ClassFormDialog(form: ClassForm, component: ClassesComponent, s: AppStrings) {
    AnfasDialog(
        title = if (form.isEditing) s.classes.editClass else s.classes.addClass,
        onDismissRequest = component::onFormDismissed,
        closeContentDescription = s.common.close,
        footer = {
            // Deleting lives in the edit form rather than on the row: a delete affordance on
            // every row of a dense grid is a mis-tap waiting to happen, and this is already the
            // screen you open to change the class.
            if (form.isEditing) {
                AnfasTextAction(
                    text = s.classes.deleteClass,
                    onClick = { form.editing?.let(component::onDeleteClass) },
                    emphasis = TextActionEmphasis.Muted,
                )
                Spacer(Modifier.weight(1f))
            }
            AnfasSecondaryButton(
                text = s.common.cancel,
                onClick = component::onFormDismissed,
            )
            AnfasPrimaryButton(
                text = s.classes.save,
                onClick = component::onSubmitForm,
                enabled = form.canSubmit,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = form.name,
                onValueChange = { value -> component.onFormChanged { it.copy(name = value) } },
                label = s.classes.fieldName,
                enabled = !form.isSubmitting,
                errorMessage = s.classes.errorNameBlank
                    .takeIf { ClassProblem.NAME_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(s.classes.fieldCategory)
            ChipRow {
                ClassCategory.entries.forEach { category ->
                    AnfasChoiceChip(
                        label = category.label(s),
                        selected = form.category == category,
                        onClick = { component.onFormChanged { it.copy(category = category) } },
                    )
                }
            }

            AnfasTextField(
                value = form.room,
                onValueChange = { value -> component.onFormChanged { it.copy(room = value) } },
                label = s.classes.fieldRoom,
                enabled = !form.isSubmitting,
                errorMessage = s.classes.errorRoomBlank
                    .takeIf { ClassProblem.ROOM_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.capacity,
                // Digits only, folded to ASCII on the way in: an Arabic keyboard produces
                // Arabic-Indic digits, and toIntOrNull does not read them.
                onValueChange = { value ->
                    component.onFormChanged { it.copy(capacity = value.digitsOnly()) }
                },
                label = s.classes.fieldCapacity,
                enabled = !form.isSubmitting,
                keyboardType = KeyboardType.Number,
                errorMessage = s.classes.errorCapacity
                    .takeIf { ClassProblem.CAPACITY_OUT_OF_RANGE in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel(s.classes.fieldInstructor)
            ChipRow {
                AnfasChoiceChip(
                    label = s.classes.unassigned,
                    selected = form.instructorStaffId == null,
                    onClick = { component.onFormChanged { it.copy(instructorStaffId = null) } },
                )
                form.instructorOptions.forEach { (id, name) ->
                    AnfasChoiceChip(
                        label = name,
                        selected = form.instructorStaffId == id,
                        onClick = {
                            component.onFormChanged { it.copy(instructorStaffId = id) }
                        },
                    )
                }
            }

            FieldLabel(s.classes.fieldDay)
            ChipRow {
                DayOfWeek.entries.forEach { day ->
                    AnfasChoiceChip(
                        label = s.common.dayNameShort(day.isoDayNumber),
                        selected = form.dayOfWeek == day,
                        onClick = { component.onFormChanged { it.copy(dayOfWeek = day) } },
                    )
                }
            }

            FieldLabel(s.classes.fieldStart)
            ChipRow {
                START_TIMES.forEach { minuteOfDay ->
                    val hour = minuteOfDay / 60
                    val minute = minuteOfDay % 60
                    AnfasChoiceChip(
                        label = minuteOfDayToTime(minuteOfDay).toString().asLtrIsolate(),
                        selected = form.startHour == hour && form.startMinute == minute,
                        onClick = {
                            component.onFormChanged {
                                it.copy(startHour = hour, startMinute = minute)
                            }
                        },
                    )
                }
            }

            FieldLabel(s.classes.fieldDuration)
            ChipRow {
                DURATIONS.forEach { minutes ->
                    AnfasChoiceChip(
                        label = s.classes.durationMinutes(minutes),
                        selected = form.durationMinutes == minutes,
                        onClick = {
                            component.onFormChanged { it.copy(durationMinutes = minutes) }
                        },
                    )
                }
            }

            if (ClassProblem.DURATION_OUT_OF_RANGE in form.problems) {
                Text(
                    text = s.classes.errorDuration,
                    style = AnfasTheme.textStyles.labelCaps,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = AnfasTheme.textStyles.labelCaps,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Chips scroll rather than wrap: wrapping seven days to two rows makes the dialog jump height. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/**
 * Folds Arabic-Indic digits to ASCII before filtering, for the same reason
 * `IntakeValidator.normalisePhone` does: `Char.isDigit()` is true for '٥', so filtering without
 * folding lets them through and `toIntOrNull` then returns null on a number the user can see.
 */
private fun String.digitsOnly(): String = map { char ->
    when (char) {
        in '٠'..'٩' -> char - '٠' + '0'.code
        in '۰'..'۹' -> char - '۰' + '0'.code
        else -> char.code
    }
}.map { it.toChar() }.filter { it in '0'..'9' }.joinToString("")

/** Every half hour from 05:00 to 22:30 — the window a gym timetable actually uses. */
private val START_TIMES: List<Int> = (5 * 60..22 * 60 + 30 step 30).toList()

/** The lengths classes are actually booked in. */
private val DURATIONS = listOf(30, 45, 60, 75, 90, 120)
