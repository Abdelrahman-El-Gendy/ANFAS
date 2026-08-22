package com.anfas.core.i18n

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring for localisation. The controller is a `single` — the chosen language is app-wide state,
 * and a second instance would let two screens disagree.
 */
val i18nModule: Module = module {
    // Settings comes from :core:common's platformSettingsModule, registered once for the app —
    // :core:data needs the same store for the session. See that module's KDoc.
    single { LanguageController(settings = get(), deviceLanguageTag = deviceLanguageTag()) }
}

internal expect fun deviceLanguageTag(): String?
