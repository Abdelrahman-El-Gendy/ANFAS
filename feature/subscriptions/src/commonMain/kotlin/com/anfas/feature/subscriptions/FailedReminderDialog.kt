package com.anfas.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasCallout
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.model.Reminder

/**
 * "Message not delivered" — why one reminder failed and what can be done about it.
 *
 * The Retry button is disabled for failures a retry cannot fix (opt-in, bad number, paused
 * template) and the reason is stated inline rather than in a hover tooltip: the export uses a
 * tooltip, which is unreachable on touch.
 */
@Composable
internal fun FailedReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenMember: () -> Unit,
) {
    val failure = reminder.failure ?: return
    val scheme = MaterialTheme.colorScheme

    AnfasDialog(
        title = "Message not delivered",
        icon = AnfasIcons.ErrorOutline,
        iconTint = scheme.error,
        onDismissRequest = onDismiss,
        footer = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnfasTextAction(
                    text = "Open member",
                    icon = AnfasIcons.Chat,
                    onClick = onOpenMember,
                )
            }
            if (reminder.canRetry) {
                AnfasPrimaryButton(text = "RETRY NOW", onClick = onRetry)
            } else {
                // Explaining the block beats a disabled control with no reason given.
                Text(
                    text = "Retry unavailable",
                    style = AnfasTheme.textStyles.labelCaps,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(scheme.surfaceVariant, CircleShape)
                    .border(1.dp, scheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = reminder.memberName.initials(),
                    style = AnfasTheme.textStyles.labelCaps,
                    color = scheme.onSurfaceVariant,
                )
            }
            Column {
                Text(
                    text = reminder.memberName,
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = reminder.phone,
                    style = AnfasTheme.textStyles.dataMono,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        AnfasCallout(
            title = failure.reason.title,
            message = failure.reason.explanation,
        )

        TechnicalDetails(reminder)
    }
}

/**
 * Collapsed by default. The provider's error code matters when contacting support and is
 * noise the rest of the time.
 */
@Composable
private fun TechnicalDetails(reminder: Reminder) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val failure = reminder.failure ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .border(1.dp, scheme.outlineVariant, AnfasShapes.base),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Technical details",
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurface,
            )
            AnfasTextAction(
                text = if (expanded) "Hide" else "Show",
                onClick = { expanded = !expanded },
                emphasis = TextActionEmphasis.Muted,
            )
        }
        if (expanded) {
            Text(
                text = technicalSummary(reminder),
                style = AnfasTheme.textStyles.dataMono,
                color = scheme.error.copy(alpha = 0.80f),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.error.copy(alpha = 0.05f))
                    .padding(16.dp),
            )
        }
    }
}

private fun technicalSummary(reminder: Reminder): String {
    val failure = reminder.failure ?: return ""
    return buildList {
        failure.providerCode?.let { add("Error code: $it") }
        add("${reminder.attempts} attempt${if (reminder.attempts == 1) "" else "s"}")
        failure.lastAttemptAt?.let { add("last ${reminder.scheduledLabel()}") }
        failure.detail?.takeIf { it != failure.reason.title }?.let { add(it) }
    }.joinToString(" · ")
}

/** Same two-initials rule the members avatar uses. */
private fun String.initials(): String =
    trim().split(' ').filter { it.isNotBlank() }.take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
