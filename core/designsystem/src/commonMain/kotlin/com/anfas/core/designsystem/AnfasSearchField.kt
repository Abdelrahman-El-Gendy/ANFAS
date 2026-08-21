package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Search input. Sits on `background` rather than `surface` so it reads as recessed inside a
 * surface-coloured header, and the border switches to amber on focus — both straight from the
 * export (`bg-background border-outline-variant … focus:border-primary`).
 *
 * A trailing clear affordance appears only when there is something to clear, which is what
 * `search-no-results` shows.
 */
@Composable
fun AnfasSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onClear: () -> Unit = { onValueChange("") },
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (focused) scheme.primaryContainer else scheme.outlineVariant

    Row(
        modifier = modifier
            .height(42.dp)
            .background(scheme.background, AnfasShapes.base)
            .border(1.dp, borderColor, AnfasShapes.base)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = AnfasIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (focused) scheme.primaryContainer else scheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                interactionSource = interaction,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    AnfasTheme.textStyles.bodyMedium.copy(color = scheme.onSurface),
                ),
                cursorBrush = SolidColor(scheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (value.isNotEmpty()) {
            AnfasIconButton(
                icon = AnfasIcons.Close,
                contentDescription = "Clear search",
                onClick = onClear,
                size = 18.dp,
            )
        }
    }
}
