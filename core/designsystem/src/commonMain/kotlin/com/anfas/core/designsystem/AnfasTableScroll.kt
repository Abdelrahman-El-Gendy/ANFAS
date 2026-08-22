package com.anfas.core.designsystem

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Keeps a weight-column table readable on a narrow screen.
 *
 * The tables in this app are laid out with `Modifier.weight` per column, which is right on the
 * desktop widths the design was drawn at. On a phone the same weights divide ~360dp between four
 * to six columns and every cell truncates to a few characters — "Omar Ha…", "Activ e", "ACTIO NS".
 * That is not merely ugly on a review screen: the whole point of the intake table is to show a
 * human what OCR got wrong, and a clipped cell hides exactly that.
 *
 * So a table keeps a usable minimum width and scrolls sideways below it. Wrap only the header and
 * the rows — a footer carrying actions must stay put, or the buttons scroll out of reach.
 */
@Composable
fun AnfasTableScroll(
    modifier: Modifier = Modifier,
    minWidth: Dp = AnfasTableMinWidth,
    /**
     * True when [content] contains a weighted child — a `LazyColumn` claiming the leftover
     * height — which only resolves if this column fills the box. Leave it false when the table
     * should wrap its rows, or it will consume the height its caller's footer needs and push
     * the footer off screen.
     */
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val width = maxOf(maxWidth, minWidth)
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(width)
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
            content = content,
        )
    }
}

/**
 * The narrowest width at which the widest table in the app still renders whole: a full Egyptian
 * mobile number, a "Nov 1, 2023" date and a status chip all fit without truncation.
 */
val AnfasTableMinWidth: Dp = 640.dp
