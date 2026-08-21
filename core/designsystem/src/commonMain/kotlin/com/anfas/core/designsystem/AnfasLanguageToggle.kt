package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The "EN / ع" switch from the export's login screen.
 *
 * Takes an opaque pair of labels rather than knowing about languages, so the design system keeps
 * its rule of depending on neither the domain nor localisation. The shell supplies the endonyms.
 */
@Composable
fun AnfasLanguageToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .border(1.dp, scheme.outlineVariant, AnfasShapes.base)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                style = AnfasTheme.textStyles.labelCaps,
                color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        if (selected) {
                            scheme.primaryContainer.copy(
                                alpha = 0.20f,
                            )
                        } else {
                            Color.Transparent
                        },
                        AnfasShapes.selection,
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
