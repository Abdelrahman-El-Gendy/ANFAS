package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A table cell that can be corrected in place.
 *
 * Three visual states, each meaning something different:
 *  - **plain** — OCR was confident, nothing to do;
 *  - **needs review** — an amber stripe on the leading edge, which is how the export marks a
 *    cell the engine was unsure about;
 *  - **error** — a red stripe plus the message underneath, for a value that blocks the import.
 *
 * The field is always editable, not just when flagged. Staff correcting a value the engine was
 * wrongly confident about is exactly the case a read-only cell would block.
 */
@Composable
fun AnfasInlineEditField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    needsReview: Boolean = false,
    error: String? = null,
    placeholder: String = "—",
    textStyle: TextStyle? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val stripe = when {
        error != null -> scheme.error
        needsReview -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val effectiveStyle = textStyle ?: AnfasTheme.textStyles.dataMono

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        // A 2dp leading stripe rather than a full border: it marks the cell without boxing in
        // every value in the table, which at eight rows by five columns would be unreadable.
        Box(
            modifier = Modifier
                .width(2.dp)
                .padding(vertical = 2.dp)
                .background(stripe),
        ) {
            // Zero-width content; the Box only needs its background.
            Text(text = "", style = effectiveStyle)
        }
        // Column, not Box. A Box *stacks* its children, so the error message was drawn on top
        // of the value it was complaining about -- "Duplicate" over "01001234567". The KDoc has
        // always said "underneath"; the table's narrow cells just made the overlap read as
        // clutter rather than as a bug, and the row cards made it obvious.
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Box(
                modifier = Modifier
                    .then(
                        if (focused) {
                            Modifier
                                .background(scheme.background, AnfasShapes.selection)
                                .border(1.dp, scheme.primaryContainer, AnfasShapes.selection)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = if (focused) 6.dp else 0.dp, vertical = 2.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    interactionSource = interaction,
                    singleLine = true,
                    textStyle = effectiveStyle.copy(
                        color = if (error != null) scheme.error else scheme.onSurface,
                    ),
                    cursorBrush = SolidColor(scheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = effectiveStyle,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            if (error != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = error,
                        style = AnfasTheme.textStyles.labelCaps,
                        color = scheme.error,
                    )
                }
            }
        }
    }
}
