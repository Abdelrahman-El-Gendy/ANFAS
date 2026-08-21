package com.anfas.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens from the export, plus the few values design.md specifies only in prose.
 * Everything is a multiple of [unit].
 */
@Immutable
data class AnfasSpacing(
    val unit: Dp = 4.dp,
    val gutter: Dp = 24.dp,
    val marginMobile: Dp = 16.dp,
    val marginDesktop: Dp = 40.dp,
    val containerMax: Dp = 1440.dp,
    /** Minimum internal padding in a card — the design's "calm" rule. */
    val cardPadding: Dp = 24.dp,
    /** Compressed row padding for staff-facing data tables. */
    val tableRowPaddingVertical: Dp = 12.dp,
)

/**
 * Borders and separators are alphas of one off-white
 * ([AnfasExtendedColors.offWhite]), never separate colours. Using these keeps the whole
 * interface's definition consistent instead of scattering one-off opacities.
 */
@Immutable
data class AnfasAlphas(
    /** 1px border on any Layer-1 surface (cards, inputs). */
    val border: Float = 0.10f,
    /** Row separators in data tables. */
    val tableRule: Float = 0.05f,
    /** Row hover in navigable tables. */
    val hover: Float = 0.02f,
    /** The cheetah-spot texture, in header tops and empty states only. */
    val texture: Float = 0.05f,
    /** Secondary body text, to build hierarchy without adding colours. */
    val secondaryText: Float = 0.80f,
)

/**
 * Layout breakpoints. Column counts come with them because the grid changes, not just the
 * margin: 4 columns on mobile, 8 on tablet, 12 on desktop.
 */
object AnfasBreakpoints {
    val mobileMax: Dp = 600.dp
    val tabletMax: Dp = 1024.dp
    const val COLUMNS_MOBILE = 4
    const val COLUMNS_TABLET = 8
    const val COLUMNS_DESKTOP = 12
}

/**
 * The design is shadow-free except for one case. Layer 1 (cards) defines itself with a 1px
 * border and no shadow at all; only Layer 2 (modals, popovers) gets a shadow, soft and
 * diffused. Don't add elevation to cards to make them "pop" — that is off-spec.
 */
object AnfasElevation {
    val card: Dp = 0.dp
    val modal: Dp = 8.dp
}

internal val LocalAnfasSpacing = staticCompositionLocalOf { AnfasSpacing() }
internal val LocalAnfasAlphas = staticCompositionLocalOf { AnfasAlphas() }
