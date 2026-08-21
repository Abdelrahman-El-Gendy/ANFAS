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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * The floating "N selected" bar that appears over a table once rows are picked.
 *
 * A Layer-2 element, so it is one of the two things allowed a shadow — it has to read as
 * hovering above the table rather than being part of it.
 */
@Composable
fun AnfasBulkActionBar(
    selectedCount: Int,
    modifier: Modifier = Modifier,
    label: String = "selected",
    actions: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(AnfasElevation.modal, AnfasShapes.base)
            .clip(AnfasShapes.base)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, AnfasShapes.base)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        scheme.primaryContainer.copy(alpha = 0.20f),
                        AnfasShapes.selection,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatCount(selectedCount),
                    style = AnfasTheme.textStyles.labelCaps,
                    color = scheme.primary,
                )
            }
            Text(
                text = label,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurface,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = actions,
        )
    }
}
