package com.anfas.feature.announcements

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the announcements feature. Intentionally empty — UI, components and use cases
 * are separate tasks. This exists so the DI wiring point already has a home.
 */
val AnnouncementsModule: Module = module {
}
