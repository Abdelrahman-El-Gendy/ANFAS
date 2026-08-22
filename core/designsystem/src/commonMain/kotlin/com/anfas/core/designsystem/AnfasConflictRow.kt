package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One field of a two-sided comparison: what this device holds against what the server holds.
 *
 * Takes strings and a flag, never domain types, so the same row renders a member conflict today
 * and anything else later. Deciding *which* fields differ is domain logic and lives in
 * `:core:model`'s `MemberConflict`.
 *
 * A differing row is marked with a warning glyph **and** a tinted background, not colour alone —
 * this is the screen where someone chooses which copy of a member's phone number survives, so the
 * difference has to survive a colour-blind reader and a sunlit phone.
 */
@Composable
fun AnfasConflictRow(
    label: String,
    localValue: String,
    remoteValue: String,
    differs: Boolean,
    differsContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(
                if (differs) scheme.errorContainer.copy(alpha = 0.30f) else scheme.surface,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(LABEL_WEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (differs) {
                Icon(
                    imageVector = AnfasIcons.Warning,
                    contentDescription = differsContentDescription,
                    tint = scheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        SideValue(value = localValue, emphasise = differs)
        SideValue(value = remoteValue, emphasise = differs)
    }
}

@Composable
private fun RowScope.SideValue(value: String, emphasise: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.weight(VALUE_WEIGHT)) {
        Text(
            text = value,
            style = AnfasTheme.textStyles.bodyMedium,
            color = if (emphasise) scheme.onSurface else scheme.onSurfaceVariant,
            // Three columns on a phone is already tight; a wrapped value would misalign the row
            // against its neighbours, which is the one thing a comparison table must not do.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Headings for the two sides. Separate from the rows so the caller can keep it sticky above a
 * scrolling list without the label column drifting out of alignment.
 */
@Composable
fun AnfasConflictHeader(
    fieldLabel: String,
    localLabel: String,
    remoteLabel: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnfasTableHeaderCell(fieldLabel, Modifier.weight(LABEL_WEIGHT))
        AnfasTableHeaderCell(localLabel, Modifier.weight(VALUE_WEIGHT))
        AnfasTableHeaderCell(remoteLabel, Modifier.weight(VALUE_WEIGHT))
    }
}

private const val LABEL_WEIGHT = 1.2f
private const val VALUE_WEIGHT = 1f
