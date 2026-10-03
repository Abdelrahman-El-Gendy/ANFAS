package com.anfas.feature.announcements

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
import com.anfas.core.data.AnnouncementProblem
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasDateField
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTextField
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.asLtrIsolate
import com.anfas.core.i18n.formatLong
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementId
import com.anfas.core.model.DatePickerBoundary
import com.anfas.core.model.minuteOfDayToTime

/**
 * Compose or edit an announcement. [state]'s `liveReach` reflects [form]'s currently selected
 * audience, kept live by the component so a picker change is never a beat behind the number
 * shown beside it.
 */
@Composable
internal fun AnnouncementFormDialog(
    form: AnnouncementForm,
    state: AnnouncementsState,
    component: AnnouncementsComponent,
    s: AppStrings,
) {
    AnfasDialog(
        title = if (form.editing !=
            null
        ) {
            s.announcements.editAnnouncement
        } else {
            s.announcements.newAnnouncement
        },
        onDismissRequest = component::onFormDismissed,
        closeContentDescription = s.common.close,
        // Once published, there is no delete (history is never deleted -- see the repository's
        // KDoc) and no re-publish (there is no unpublish, so the button that already fired
        // once must not still be here to fire again and silently overwrite the frozen reach
        // count and publishedAt). Only Cancel and a save for the fields that are still editable
        // remain.
        footer = {
            if (form.editing != null && !form.wasPublished) {
                AnfasTextAction(
                    text = s.announcements.deleteDraft,
                    onClick = { component.onRequestDelete(AnnouncementId(form.editing)) },
                    emphasis = TextActionEmphasis.Muted,
                )
            }
            Spacer(Modifier.weight(1f))
            AnfasSecondaryButton(text = s.common.cancel, onClick = component::onFormDismissed)
            if (form.wasPublished) {
                // The only action left, so it earns the primary slot rather than reading as a
                // lesser alternative to a Publish button that no longer belongs here.
                AnfasPrimaryButton(
                    text = s.announcements.saveChanges,
                    onClick = component::onSubmitForm,
                    enabled = form.canSubmit,
                )
            } else {
                AnfasSecondaryButton(
                    text = s.announcements.saveDraft,
                    onClick = component::onSubmitForm,
                    enabled = form.canSubmit,
                )
                AnfasPrimaryButton(
                    text = s.announcements.publish,
                    onClick = component::onRequestPublish,
                    enabled = form.canSubmit,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnfasTextField(
                value = form.title,
                onValueChange = { value -> component.onFormChanged { it.copy(title = value) } },
                label = s.announcements.fieldTitle,
                enabled = !form.isSubmitting,
                errorMessage = s.announcements.errorTitleBlank
                    .takeIf { AnnouncementProblem.TITLE_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            AnfasTextField(
                value = form.body,
                onValueChange = { value -> component.onFormChanged { it.copy(body = value) } },
                label = s.announcements.fieldBody,
                singleLine = false,
                enabled = !form.isSubmitting,
                errorMessage = s.announcements.errorBodyBlank
                    .takeIf { AnnouncementProblem.BODY_BLANK in form.problems },
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.wasPublished) {
                FieldLabel(s.announcements.audienceTitle)
                Text(
                    text = form.audience.label(s),
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                FieldLabel(s.announcements.audienceTitle)
                ChipRow {
                    AnnouncementAudience.entries.forEach { audience ->
                        AnfasChoiceChip(
                            label = audience.label(s),
                            selected = form.audience == audience,
                            onClick = { component.onFormChanged { it.copy(audience = audience) } },
                        )
                    }
                }
                state.liveReach?.let {
                    Text(
                        text = s.announcements.reaches(it),
                        style = AnfasTheme.textStyles.labelCaps,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnfasDateField(
                label = s.announcements.fieldEventDate,
                formattedValue = form.eventDate?.let { s.formatLong(it) },
                placeholder = s.common.chooseDate,
                selectedDateMillis = DatePickerBoundary.toEpochMillis(form.eventDate),
                onDateSelected = { millis ->
                    component.onFormChanged {
                        it.copy(eventDate = DatePickerBoundary.toLocalDate(millis))
                    }
                },
                confirmLabel = s.common.confirmDate,
                cancelLabel = s.common.cancel,
                clearLabel = s.common.clearDate,
                openContentDescription = s.common.openDatePicker,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            if (form.eventDate != null) {
                FieldLabel(s.announcements.fieldEventTime)
                ChipRow {
                    EVENT_TIMES.forEach { minuteOfDay ->
                        val hour = minuteOfDay / 60
                        val minute = minuteOfDay % 60
                        AnfasChoiceChip(
                            label = minuteOfDayToTime(minuteOfDay).toString().asLtrIsolate(),
                            selected = form.eventHour == hour && form.eventMinute == minute,
                            onClick = {
                                component.onFormChanged {
                                    it.copy(eventHour = hour, eventMinute = minute)
                                }
                            },
                        )
                    }
                }
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

/** Every half hour from 06:00 to 22:00 — the gym's own operating hours, matching the classes
 * form's START_TIMES window. */
private val EVENT_TIMES: List<Int> = (6 * 60..22 * 60 step 30).toList()
