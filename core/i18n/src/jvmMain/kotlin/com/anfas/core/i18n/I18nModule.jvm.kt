package com.anfas.core.i18n

import com.russhwolf.settings.PreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale
import java.util.prefs.Preferences

internal actual fun platformI18nModule(): Module = module {
    single<com.russhwolf.settings.Settings> {
        // java.util.prefs -- note this means the packaged desktop app's jlink module list must
        // include java.prefs, or this throws only inside the app image.
        PreferencesSettings(Preferences.userRoot().node("com/anfas/app"))
    }
}

internal actual fun deviceLanguageTag(): String? = Locale.getDefault().language
