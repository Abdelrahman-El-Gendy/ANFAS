package com.anfas.core.i18n

import com.russhwolf.settings.NSUserDefaultsSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

internal actual fun platformI18nModule(): Module = module {
    single<com.russhwolf.settings.Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
}

/**
 * Reads the device's preferred language only to pick an initial default. The in-app toggle does
 * NOT write here: changing NSUserDefaults' AppleLanguages would only take effect on the next
 * launch, which is exactly what the typed string table avoids.
 */
internal actual fun deviceLanguageTag(): String? =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
