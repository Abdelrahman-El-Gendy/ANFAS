package com.anfas.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import anfas.core.designsystem.generated.resources.Res
import anfas.core.designsystem.generated.resources.ibmplexsans_medium
import anfas.core.designsystem.generated.resources.ibmplexsans_regular
import anfas.core.designsystem.generated.resources.ibmplexsans_semibold
import anfas.core.designsystem.generated.resources.ibmplexsansarabic_medium
import anfas.core.designsystem.generated.resources.ibmplexsansarabic_regular
import anfas.core.designsystem.generated.resources.ibmplexsansarabic_semibold
import org.jetbrains.compose.resources.Font

/**
 * Which script the type ramp targets.
 *
 * Declared here rather than taking a language from :core:i18n, so the design system stays free of
 * any dependency on localisation or the domain. The app shell maps its language onto this.
 */
enum class AnfasScript { Latin, Arabic }

/**
 * The family for a script. Three static weights each — 400, 500, 600, the three the ramp uses.
 *
 * This MUST be a branch, not a merged family: `FontFamily` selects by weight and style, not by
 * glyph coverage, so listing Latin and Arabic faces together would not make Arabic text pick the
 * Arabic face. The bundled IBM Plex Sans has **no Arabic glyphs at all**, so without this Arabic
 * renders as tofu on Android and falls back to an arbitrary system face elsewhere — three
 * platforms that look like three different apps.
 */
@Composable
internal fun anfasFontFamily(script: AnfasScript): FontFamily = when (script) {
    AnfasScript.Latin -> FontFamily(
        Font(Res.font.ibmplexsans_regular, FontWeight.Normal),
        Font(Res.font.ibmplexsans_medium, FontWeight.Medium),
        Font(Res.font.ibmplexsans_semibold, FontWeight.SemiBold),
    )

    AnfasScript.Arabic -> FontFamily(
        Font(Res.font.ibmplexsansarabic_regular, FontWeight.Normal),
        Font(Res.font.ibmplexsansarabic_medium, FontWeight.Medium),
        Font(Res.font.ibmplexsansarabic_semibold, FontWeight.SemiBold),
    )
}

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
    val dataMonoLtr: TextStyle,
)

/**
 * Three adjustments for Arabic, each fixing something that would otherwise ship visibly broken:
 *
 *  - **letter spacing is zeroed.** Arabic is cursive, so the export's -0.02em/+0.08em tracking
 *    breaks the joins between letters and visually shatters words.
 *  - **line height gains ~10%.** Arabic ascenders, descenders and any harakat overflow a
 *    Latin-tuned lineHeight; the table's 20sp dataMono would clip.
 *  - **labelCaps grows 1sp and loses its tracking.** Arabic has no letter case, so a role built
 *    for 11sp uppercase Latin is simply unreadable; the strings are sentence-case Arabic.
 */
@Composable
internal fun anfasTextStyles(
    family: FontFamily,
    script: AnfasScript = AnfasScript.Latin,
): AnfasTextStyles {
    val arabic = script == AnfasScript.Arabic
    fun tracking(latin: TextUnit): TextUnit = if (arabic) 0.em else latin
    fun leading(latin: TextUnit): TextUnit = if (arabic) (latin.value * 1.1f).sp else latin

    return anfasTextStylesFor(family, arabic, ::tracking, ::leading)
}

@Composable
private fun anfasTextStylesFor(
    family: FontFamily,
    arabic: Boolean,
    tracking: (TextUnit) -> TextUnit,
    leading: (TextUnit) -> TextUnit,
): AnfasTextStyles = AnfasTextStyles(
    headlineLarge = TextStyle(
        fontFamily = family,
        fontSize = 32.sp,
        lineHeight = leading(40.sp),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking((-0.02).em),
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = leading(32.sp),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking((-0.01).em),
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontSize = 20.sp,
        lineHeight = leading(28.sp),
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = leading(24.sp),
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = leading(20.sp),
        fontWeight = FontWeight.Normal,
    ),
    labelCaps = TextStyle(
        fontFamily = family,
        fontSize = if (arabic) 12.sp else 11.sp,
        lineHeight = leading(16.sp),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = tracking(0.08.em),
    ),
    dataMono = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = leading(20.sp),
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    ),
    // Same metrics as dataMono but pinned LTR. Phone numbers, money and dates begin or end with
    // direction-neutral characters, so inside an RTL paragraph the "+", the "−" and the currency
    // code render at the wrong end. This is the fix for that.
    dataMonoLtr = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = leading(20.sp),
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
        textDirection = TextDirection.Ltr,
    ),
)

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
