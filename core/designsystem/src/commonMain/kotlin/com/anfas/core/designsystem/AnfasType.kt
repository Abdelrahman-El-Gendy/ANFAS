package com.anfas.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import anfas.core.designsystem.generated.resources.Res
import anfas.core.designsystem.generated.resources.ibmplexsans_medium
import anfas.core.designsystem.generated.resources.ibmplexsans_regular
import anfas.core.designsystem.generated.resources.ibmplexsans_semibold
import org.jetbrains.compose.resources.Font

/**
 * IBM Plex Sans, the design system's only family. Three static weights are bundled because
 * those are the three the ramp actually uses — 400, 500, 600. Adding a weight means adding
 * a TTF to composeResources/font, not asking Compose to synthesise one.
 */
@Composable
internal fun anfasFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibmplexsans_regular, FontWeight.Normal),
    Font(Res.font.ibmplexsans_medium, FontWeight.Medium),
    Font(Res.font.ibmplexsans_semibold, FontWeight.SemiBold),
)

/**
 * The seven named roles from the export, verbatim. Letter spacing stays in `em` because
 * that is how Stitch expresses it — converting to sp would silently break at other sizes.
 *
 * [dataMono] is misleadingly named upstream: it resolves to IBM Plex Sans in all 23 screens,
 * not a mono face. Its job is digit alignment in tables and financial figures, so it carries
 * tabular figures instead of a different family.
 */
@Immutable
data class AnfasTextStyles(
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelCaps: TextStyle,
    val dataMono: TextStyle,
)

@Composable
internal fun anfasTextStyles(family: FontFamily): AnfasTextStyles = AnfasTextStyles(
    headlineLarge = TextStyle(
        fontFamily = family,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelCaps = TextStyle(
        fontFamily = family,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
    ),
    dataMono = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    ),
)

/**
 * M3's typography, so stock components pick up the family. Slots the export specifies are
 * exact; the rest keep M3's metrics and only swap the family — inventing sizes for slots the
 * design never defined would put values in the codebase that no screen backs up.
 */
@Composable
internal fun anfasTypography(family: FontFamily, styles: AnfasTextStyles): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = styles.headlineLarge,
        headlineMedium = styles.headlineMedium,
        headlineSmall = styles.headlineSmall,
        titleLarge = styles.headlineSmall,
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = styles.bodyLarge,
        bodyMedium = styles.bodyMedium,
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = styles.labelCaps,
    )
}

internal val LocalAnfasTextStyles = staticCompositionLocalOf<AnfasTextStyles> {
    error("AnfasTextStyles not provided — wrap the tree in AnfasTheme")
}
