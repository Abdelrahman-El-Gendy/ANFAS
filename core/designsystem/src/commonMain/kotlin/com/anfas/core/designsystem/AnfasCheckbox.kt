package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

/**
 * A checkbox with a **4dp** radius, not a circle and not the 12dp base.
 *
 * That is deliberate in the design: selection controls stay square-ish "to match the technical
 * aesthetic of IBM Plex". Using M3's stock `Checkbox` would round it differently and ignore
 * the palette, so this is drawn directly.
 */
@Composable
fun AnfasCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CheckboxBox(if (checked) ToggleableState.On else ToggleableState.Off, enabled)
    }
}

/**
 * Header "select all" needs a third state: some rows selected, not all. Rendering that as
 * unchecked would invite staff to click twice to clear a partial selection.
 */
@Composable
fun AnfasTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .then(
                if (onClick != null) {
                    Modifier.triStateToggleable(
                        state = state,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CheckboxBox(state, enabled)
    }
}

@Composable
private fun CheckboxBox(state: ToggleableState, enabled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val selected = state != ToggleableState.Off
    val alpha = if (enabled) 1f else 0.38f
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(
                if (selected) {
                    scheme.primaryContainer.copy(
                        alpha = alpha,
                    )
                } else {
                    Color.Transparent
                },
                AnfasShapes.selection,
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    scheme.primaryContainer.copy(alpha = alpha)
                } else {
                    scheme.outline.copy(alpha = alpha)
                },
                shape = AnfasShapes.selection,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> Icon(
                imageVector = AnfasIcons.Check,
                contentDescription = null,
                tint = scheme.background,
                modifier = Modifier.size(13.dp),
            )

            ToggleableState.Indeterminate -> Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 2.dp)
                    .background(scheme.background),
            )

            ToggleableState.Off -> Unit
        }
    }
}
