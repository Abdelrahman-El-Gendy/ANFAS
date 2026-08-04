package com.anfas.feature.members

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the members feature. Intentionally empty — UI, components and use cases
 * are separate tasks. This exists so the DI wiring point already has a home.
 */
val MembersModule: Module = module {
}
