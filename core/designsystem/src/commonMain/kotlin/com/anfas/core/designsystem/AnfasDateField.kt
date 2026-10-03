package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A labelled date input backed by the Material 3 calendar picker.
 *
 * **Not an [AnfasTextField] with a date in it.** A free-text date has to be parsed, which means it
 * can fail, which means every screen using one needs an "unreadable date" error state and a
 * caller-side parser. A picker cannot produce an unparseable value, so those error paths stop
 * existing rather than being handled — and staff stop having to know which of "1/11/26" and
 * "11/1/26" this app accepts (it accepts neither: `IntakeValidator.parseDate` deliberately
 * refuses ambiguous numeric forms).
 *
 * The one place free text is still right is correcting a date **read off a photographed sheet**:
 * there the text is a transcription of what a human wrote, and the confidence stripe on the cell
 * is the point. That is `AnfasInlineEditField`, not this.
 *
 * Works in **UTC start-of-day epoch milliseconds**, which is exactly what `DatePickerState`
 * stores, so nothing is converted twice and no timezone can shift the chosen day. The caller
 * converts to its own date type at the boundary.
 *
 * Deliberately dependency-free, like the rest of this module: no `kotlinx.datetime` type in the
 * signature, and [formattedValue] is supplied by the caller because formatting a date is an i18n
 * concern and `:core:designsystem` must not depend on `:core:i18n`. Same reasoning as
 * `AnfasSearchField` taking its own placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnfasDateField(
    label: String,
    /** The chosen date, already localised by the caller. Null shows [placeholder] instead. */
    formattedValue: String?,
    placeholder: String,
    selectedDateMillis: Long?,
    onDateSelected: (Long?) -> Unit,
    confirmLabel: String,
    cancelLabel: String,
    /** Offered only when a date is already set, and only when [nullable]. */
    clearLabel: String,
    openContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** False for a date the record cannot exist without, so Clear is withheld. */
    nullable: Boolean = true,
    errorMessage: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var showPicker by remember { mutableStateOf(false) }
    val borderColor = when {
        errorMessage != null -> scheme.error
        showPicker -> scheme.primaryContainer
        else -> scheme.outlineVariant
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.labelCaps,
            color = if (errorMessage != null) scheme.error else scheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // heightIn, matching AnfasTextField: a larger system font size must grow the
                // field rather than clip what is inside it.
                .heightIn(min = FIELD_HEIGHT)
                .background(scheme.background, AnfasShapes.base)
                .border(1.dp, borderColor, AnfasShapes.base)
                .clickable(enabled = enabled) { showPicker = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formattedValue ?: placeholder,
                style = AnfasTheme.textStyles.bodyMedium,
                // The placeholder must read as absent rather than as a chosen value.
                color = if (formattedValue == null) scheme.onSurfaceVariant else scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = AnfasIcons.CalendarToday,
                contentDescription = openContentDescription,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        errorMessage?.let {
            Text(text = it, style = AnfasTheme.textStyles.bodyMedium, color = scheme.error)
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                AnfasTextAction(
                    text = confirmLabel,
                    onClick = {
                        onDateSelected(pickerState.selectedDateMillis)
                        showPicker = false
                    },
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (nullable && selectedDateMillis != null) {
                        AnfasTextAction(
                            text = clearLabel,
                            onClick = {
                                onDateSelected(null)
                                showPicker = false
                            },
                            emphasis = TextActionEmphasis.Muted,
                        )
                    }
                    AnfasTextAction(
                        text = cancelLabel,
                        onClick = { showPicker = false },
                        emphasis = TextActionEmphasis.Muted,
                    )
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = scheme.surfaceContainerHigh,
            ),
        ) {
            DatePicker(
                state = pickerState,
                // The export has no date picker to copy, so this stays stock Material 3 rather
                // than a hand-restyled calendar: a wrong-but-custom calendar is worse than a
                // correct standard one, and the theme's own colours already carry the brand.
                colors = DatePickerDefaults.colors(
                    containerColor = scheme.surfaceContainerHigh,
                ),
            )
        }
    }
}

private val FIELD_HEIGHT = 46.dp
