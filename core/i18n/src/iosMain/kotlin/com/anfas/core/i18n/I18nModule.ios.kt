package com.anfas.core.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * Reads the device's preferred language only to pick an initial default. The in-app toggle does
 * NOT write here: changing NSUserDefaults' AppleLanguages would only take effect on the next
 * launch, which is exactly what the typed string table avoids.
 */
internal actual fun deviceLanguageTag(): String? =
    (NSLocale.preferredLanguages.firstOrNull() as? String)
