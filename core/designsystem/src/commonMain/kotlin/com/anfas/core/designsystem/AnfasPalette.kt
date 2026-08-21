package com.anfas.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Raw palette extracted from the Stitch export. GENERATED — see design/stitch/TOKENS.md.
 * Do not hand-edit and do not add a colour here that no screen uses; re-derive from the
 * export instead. Nothing outside this file should contain a hex literal.
 */
internal object AnfasPalette {
    val Background = Color(0xFF141311) // 22/23 screens — see TOKENS.md
    val Error = Color(0xFFFFB4AB) // 22/23 screens — see TOKENS.md
    val ErrorContainer = Color(0xFF93000A)
    val InverseOnSurface = Color(0xFF32302E)
    val InversePrimary = Color(0xFF835400)
    val InverseSurface = Color(0xFFE6E2DE)
    val OnBackground = Color(0xFFE6E2DE)
    val OnError = Color(0xFF690005)
    val OnErrorContainer = Color(0xFFFFDAD6)
    val OnPrimary = Color(0xFF462B00)
    val OnPrimaryContainer = Color(0xFF5F3C00)
    val OnPrimaryFixed = Color(0xFF2A1800)
    val OnPrimaryFixedVariant = Color(0xFF643F00)
    val OnSecondary = Color(0xFF213528)
    val OnSecondaryContainer = Color(0xFFA7BEAD)
    val OnSecondaryFixed = Color(0xFF0C1F14)
    val OnSecondaryFixedVariant = Color(0xFF374B3E)
    val OnSurface = Color(0xFFE6E2DE)
    val OnSurfaceVariant = Color(0xFFD6C4B0)
    val OnTertiary = Color(0xFF4E232E)
    val OnTertiaryContainer = Color(0xFF653541)
    val OnTertiaryFixed = Color(0xFF340E1A)
    val OnTertiaryFixedVariant = Color(0xFF683844)
    val Outline = Color(0xFF9E8E7C)
    val OutlineVariant = Color(0xFF514536)
    val Primary = Color(0xFFFFC16C)
    val PrimaryContainer = Color(0xFFE8A33D)
    val PrimaryFixed = Color(0xFFFFDDB5)
    val PrimaryFixedDim = Color(0xFFFFB956)
    val Secondary = Color(0xFFB5CCBA)
    val SecondaryContainer = Color(0xFF394E40)
    val SecondaryFixed = Color(0xFFD1E8D6)
    val SecondaryFixedDim = Color(0xFFB5CCBA)
    val Surface = Color(0xFF141311)
    val SurfaceBright = Color(0xFF3B3936)
    val SurfaceContainer = Color(0xFF211F1D)
    val SurfaceContainerHigh = Color(0xFF2B2A27)
    val SurfaceContainerHighest = Color(0xFF363432)
    val SurfaceContainerLow = Color(0xFF1D1B19)
    val SurfaceContainerLowest = Color(0xFF0F0E0C)
    val SurfaceDim = Color(0xFF141311)
    val SurfaceTint = Color(0xFFFFB956)
    val SurfaceVariant = Color(0xFF363432)
    val Tertiary = Color(0xFFFDBBC8)
    val TertiaryContainer = Color(0xFFDFA0AD)
    val TertiaryFixed = Color(0xFFFFD9E0)
    val TertiaryFixedDim = Color(0xFFF7B5C3)

    // Brand accents. Not Tailwind tokens — these appear as raw hex in the markup,
    // and design.md's prose is their only specification. See TOKENS.md.
    val BrandOffWhite = Color(0xFFF5F1EA) // borders @10%, table rules @5%, hover @2%
    val BrandSage = Color(0xFF7A9080) // recovery / therapy / wellness
    val BrandRose = Color(0xFFB87D8A) // group classes / women's programming
    val BrandCharcoal = Color(0xFF1C1A17) // card surface in the prose palette
}
