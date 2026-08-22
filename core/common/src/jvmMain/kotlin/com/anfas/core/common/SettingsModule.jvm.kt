package com.anfas.core.common

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

actual fun platformSettingsModule(): Module = module {
    single<Settings> {
        // java.util.prefs — note this means the packaged desktop app's jlink module list must
        // include java.prefs, or this throws only inside the app image.
        PreferencesSettings(Preferences.userRoot().node("com/anfas/app"))
    }
}
