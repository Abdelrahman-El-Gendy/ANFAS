package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A titled explanation block with a 4dp accent stripe down its leading edge — how the design
 * presents a failure reason and its remedy.
 *
 * The stripe carries the severity, so the body text stays `on-surface-variant` rather than
 * being recoloured; error-coloured paragraphs are hard to read at length.
 */
@Composable
fun AnfasCallout(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.error,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(scheme.surfaceContainerLowest),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = AnfasTheme.textStyles.bodyLarge,
                color = scheme.onSurface,
            )
            Box(Modifier.height(8.dp))
            Text(
                text = message,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
