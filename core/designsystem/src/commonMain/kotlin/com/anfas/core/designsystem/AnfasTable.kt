package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Data-table primitives for the staff-facing screens.
 *
 * The design's table rules are deliberately faint — separators at 5% and hover at 2% of the
 * off-white, from [AnfasAlphas] — and there are **no alternating row colours**; hover is the
 * only row feedback. Row padding is the compressed 12dp staff-view value, not the 24dp used
 * inside cards.
 */
private val CellPaddingHorizontal = 20.dp

@Composable
fun AnfasTableHeaderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.30f))
            .padding(horizontal = CellPaddingHorizontal, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
    AnfasTableDivider()
}

/** A header label. `label-caps` is the design's table-header role. */
@Composable
fun RowScope.AnfasTableHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        style = AnfasTheme.textStyles.labelCaps,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
fun AnfasTableRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverTint = AnfasTheme.colors.offWhite.copy(alpha = AnfasTheme.alphas.hover)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .background(if (hovered) hoverTint else Color.Transparent)
            .heightIn(min = 56.dp)
            .padding(horizontal = CellPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
    if (showDivider) AnfasTableDivider()
}

/** The 5%-off-white rule between rows. */
@Composable
fun AnfasTableDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = AnfasTheme.colors.offWhite.copy(alpha = AnfasTheme.alphas.tableRule),
    )
}

/** Footer strip — the export puts the result count and pagination here. */
@Composable
fun AnfasTableFooter(content: @Composable RowScope.() -> Unit) {
    AnfasTableDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.10f))
            .padding(horizontal = CellPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
