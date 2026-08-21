package com.anfas.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours the design uses that M3's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Two groups, and the difference matters:
 *  - the M3 *fixed* roles, which are real Stitch tokens but not stable `darkColorScheme`
 *    parameters, so they live here rather than behind an experimental API;
 *  - the brand accents, which are not tokens at all — they appear as raw hex in the exported
 *    markup and are specified only in design.md's prose. See design/stitch/TOKENS.md.
 *
 * [sage] and [rose] carry semantics, not decoration: sage marks recovery/therapy/wellness,
 * rose marks group classes and women's programming. Don't reuse them for anything else.
 */
@Immutable
data class AnfasExtendedColors(
    val primaryFixed: Color,
    val primaryFixedDim: Color,
    val onPrimaryFixed: Color,
    val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color,
    val secondaryFixedDim: Color,
    val onSecondaryFixed: Color,
    val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color,
    val tertiaryFixedDim: Color,
    val onTertiaryFixed: Color,
    val onTertiaryFixedVariant: Color,
    /** Off-white that every border, table rule and hover tint is an alpha of. */
    val offWhite: Color,
    /** Recovery / therapy / wellness. */
    val sage: Color,
    /** Group classes / women's programming. */
    val rose: Color,
    val charcoal: Color,
)

internal val anfasExtendedColors = AnfasExtendedColors(
    primaryFixed = AnfasPalette.PrimaryFixed,
    primaryFixedDim = AnfasPalette.PrimaryFixedDim,
    onPrimaryFixed = AnfasPalette.OnPrimaryFixed,
    onPrimaryFixedVariant = AnfasPalette.OnPrimaryFixedVariant,
    secondaryFixed = AnfasPalette.SecondaryFixed,
    secondaryFixedDim = AnfasPalette.SecondaryFixedDim,
    onSecondaryFixed = AnfasPalette.OnSecondaryFixed,
    onSecondaryFixedVariant = AnfasPalette.OnSecondaryFixedVariant,
    tertiaryFixed = AnfasPalette.TertiaryFixed,
    tertiaryFixedDim = AnfasPalette.TertiaryFixedDim,
    onTertiaryFixed = AnfasPalette.OnTertiaryFixed,
    onTertiaryFixedVariant = AnfasPalette.OnTertiaryFixedVariant,
    offWhite = AnfasPalette.BrandOffWhite,
    sage = AnfasPalette.BrandSage,
    rose = AnfasPalette.BrandRose,
    charcoal = AnfasPalette.BrandCharcoal,
)

internal val LocalAnfasExtendedColors = staticCompositionLocalOf { anfasExtendedColors }
