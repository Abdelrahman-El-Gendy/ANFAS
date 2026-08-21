package com.anfas.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Theme entry point. Every Compose module routes through this rather than calling
 * MaterialTheme directly.
 *
 * Tokens are derived from the Stitch export in `design/stitch/` — see TOKENS.md there for
 * the extracted values and the conflicts that were resolved to get them. Change a token in
 * Stitch and re-export first; never edit the Kotlin to win an argument with the design.
 *
 * [script] selects the font family and the type-ramp adjustments Arabic needs. It is an
 * [AnfasScript] rather than a language, so this module stays free of any dependency on
 * localisation or the domain — the app shell maps its language onto it.
 *
 * The design has one colour mode (dark), so there is no `darkTheme` parameter to pass.
 * What M3 has no slot for is reachable through [AnfasTheme]: [AnfasTheme.colors] for the
 * brand accents and fixed roles, [AnfasTheme.textStyles] for `labelCaps`/`dataMono`,
 * [AnfasTheme.spacing] and [AnfasTheme.alphas] for layout and border opacities.
 */
@Composable
fun AnfasTheme(script: AnfasScript = AnfasScript.Latin, content: @Composable () -> Unit) {
    val family = anfasFontFamily(script)
    val textStyles = anfasTextStyles(family, script)
    CompositionLocalProvider(
        LocalAnfasExtendedColors provides anfasExtendedColors,
        LocalAnfasTextStyles provides textStyles,
        LocalAnfasSpacing provides AnfasSpacing(),
        LocalAnfasAlphas provides AnfasAlphas(),
    ) {
        MaterialTheme(
            colorScheme = anfasDarkColorScheme(),
            typography = anfasTypography(family, textStyles),
            shapes = anfasShapes,
            content = content,
        )
    }
}

/** Accessors for the parts of the design system M3 cannot hold. */
object AnfasTheme {
    val colors: AnfasExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalAnfasExtendedColors.current

    val textStyles: AnfasTextStyles
        @Composable @ReadOnlyComposable
        get() = LocalAnfasTextStyles.current

    val spacing: AnfasSpacing
        @Composable @ReadOnlyComposable
        get() = LocalAnfasSpacing.current

    val alphas: AnfasAlphas
        @Composable @ReadOnlyComposable
        get() = LocalAnfasAlphas.current
}
