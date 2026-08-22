package com.anfas.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A determinate bar, for the profile's "Time remaining".
 *
 * Not Material's `LinearProgressIndicator`: that one animates indefinitely at track edges and
 * carries its own colour roles, and the export draws a plain filled track at the base radius.
 *
 * [fraction] is coerced rather than asserted. A caller computing 1.02 from date arithmetic should
 * get a full bar, not a crash on a screen whose job is to report on a membership.
 */
@Composable
fun AnfasProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: ProgressTone = ProgressTone.Normal,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when (tone) {
        ProgressTone.Normal -> scheme.primary
        ProgressTone.Critical -> scheme.error
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .background(scheme.surfaceContainerHighest, AnfasShapes.chip),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(fill, AnfasShapes.chip),
        )
    }
}

/**
 * Meaning, not colour. The call site knows a membership is nearly over; it should not also have
 * to know which hex that implies.
 *
 * Only two tones, because the palette has only two that mean this. There is no separate "warning"
 * colour in the export, and the primary is already amber — inventing one would mean a hex outside
 * AnfasPalette, which is the one place colour is allowed to live.
 */
enum class ProgressTone { Normal, Critical }

private val BAR_HEIGHT = 6.dp
