package com.anfas.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme

/**
 * The Stitch theme is `colorMode: DARK` with no light palette anywhere in the project, so
 * there is exactly one scheme. Don't add a light one by guessing inverted values — re-export
 * from Stitch if the design gains a light mode.
 */
internal fun anfasDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = AnfasPalette.Primary,
    onPrimary = AnfasPalette.OnPrimary,
    primaryContainer = AnfasPalette.PrimaryContainer,
    onPrimaryContainer = AnfasPalette.OnPrimaryContainer,
    inversePrimary = AnfasPalette.InversePrimary,
    secondary = AnfasPalette.Secondary,
    onSecondary = AnfasPalette.OnSecondary,
    secondaryContainer = AnfasPalette.SecondaryContainer,
    onSecondaryContainer = AnfasPalette.OnSecondaryContainer,
    tertiary = AnfasPalette.Tertiary,
    onTertiary = AnfasPalette.OnTertiary,
    tertiaryContainer = AnfasPalette.TertiaryContainer,
    onTertiaryContainer = AnfasPalette.OnTertiaryContainer,
    background = AnfasPalette.Background,
    onBackground = AnfasPalette.OnBackground,
    surface = AnfasPalette.Surface,
    onSurface = AnfasPalette.OnSurface,
    surfaceVariant = AnfasPalette.SurfaceVariant,
    onSurfaceVariant = AnfasPalette.OnSurfaceVariant,
    surfaceTint = AnfasPalette.SurfaceTint,
    inverseSurface = AnfasPalette.InverseSurface,
    inverseOnSurface = AnfasPalette.InverseOnSurface,
    error = AnfasPalette.Error,
    onError = AnfasPalette.OnError,
    errorContainer = AnfasPalette.ErrorContainer,
    onErrorContainer = AnfasPalette.OnErrorContainer,
    outline = AnfasPalette.Outline,
    outlineVariant = AnfasPalette.OutlineVariant,
    surfaceBright = AnfasPalette.SurfaceBright,
    surfaceDim = AnfasPalette.SurfaceDim,
    surfaceContainer = AnfasPalette.SurfaceContainer,
    surfaceContainerHigh = AnfasPalette.SurfaceContainerHigh,
    surfaceContainerHighest = AnfasPalette.SurfaceContainerHighest,
    surfaceContainerLow = AnfasPalette.SurfaceContainerLow,
    surfaceContainerLowest = AnfasPalette.SurfaceContainerLowest,
)
