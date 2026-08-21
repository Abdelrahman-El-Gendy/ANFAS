package com.anfas.core.i18n

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Wiring for localisation. The controller is a `single` — the chosen language is app-wide state,
 * and a second instance would let two screens disagree.
 */
val i18nModule: Module = module {
    includes(platformI18nModule())
    single { LanguageController(settings = get(), deviceLanguageTag = deviceLanguageTag()) }
}

/**
 * Explicit per-platform Settings, following the same expect/actual shape as
 * :core:data's platformDatabaseModule rather than using multiplatform-settings-no-arg — that
 * artifact gets its Android Context by registering a hidden ContentProvider, and this keeps the
 * androidContext() requirement visible.
 */
internal expect fun platformI18nModule(): Module

internal expect fun deviceLanguageTag(): String?
