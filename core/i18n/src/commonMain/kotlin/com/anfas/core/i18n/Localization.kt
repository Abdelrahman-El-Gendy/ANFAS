package com.anfas.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Strings for the active language. Defaults to English so a preview or a test that forgets to
 * wrap still renders instead of throwing.
 */
val LocalStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.EN }

/**
 * Provides strings and layout direction together, from one value.
 *
 * Because [language] arrives as snapshot state, switching it is an ordinary recomposition —
 * instantaneous, on all three platforms, with no restart and no platform API. That is the entire
 * payoff of a typed string table over Compose Resources, whose locale cannot be overridden at all
 * in 1.11.1 and which on iOS would require relaunching the app.
 */
@Composable
fun ProvideLocalization(language: AppLanguage, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalStrings provides language.strings(),
        LocalLayoutDirection provides
            if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

fun AppLanguage.strings(): AppStrings = when (this) {
    AppLanguage.EN -> EnglishStrings
    AppLanguage.AR -> ArabicStrings
}

/** Shorthand so screens read `strings.members.title`. */
val strings: AppStrings
    @Composable @ReadOnlyComposable
    get() = LocalStrings.current
