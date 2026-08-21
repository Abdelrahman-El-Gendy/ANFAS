package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A single-choice chip — "Start today" / "Start when current ends", and the payment methods.
 *
 * Selection is shown with the amber border and amber text plus a faint amber wash, exactly as
 * the export does. It is `selectable` with [Role.RadioButton] rather than a plain clickable so
 * screen readers announce it as one option among several.
 */
@Composable
fun AnfasChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val border = if (selected) scheme.primaryContainer else scheme.outlineVariant
    val content = if (selected) scheme.primaryContainer else scheme.onSurface
    val fill = if (selected) scheme.primaryContainer.copy(alpha = 0.10f) else Color.Transparent

    Row(
        modifier = modifier
            .clip(AnfasShapes.base)
            .background(fill)
            .border(1.dp, border, AnfasShapes.base)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = label, style = AnfasTheme.textStyles.bodyMedium, color = content)
    }
}

/**
 * A full-width selectable row for a list of mutually exclusive options — the plan picker.
 *
 * The whole row is the target, not just the dot: these are 56dp+ tall rows on a touch screen
 * and hunting for a 20dp radio is the wrong interaction.
 */
@Composable
fun AnfasSelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(
                if (selected) scheme.primaryContainer.copy(alpha = 0.08f) else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) scheme.primaryContainer else scheme.outlineVariant,
                shape = AnfasShapes.base,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioDot(selected)
        content()
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(20.dp)
            .border(
                width = if (selected) 6.dp else 1.dp,
                color = if (selected) scheme.primaryContainer else scheme.outline,
                shape = CircleShape,
            ),
    )
}
