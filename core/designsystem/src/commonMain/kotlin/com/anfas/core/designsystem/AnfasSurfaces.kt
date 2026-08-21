package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A Layer-1 container: 12dp radius, a 1px border, and **no shadow**. The design defines cards
 * by their outline rather than elevation, so don't wrap this in a `Surface` with a tonal or
 * shadow elevation to make it stand out — that is off-spec.
 */
@Composable
fun AnfasCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .clip(AnfasShapes.base)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AnfasShapes.base),
        content = content,
    )
}
