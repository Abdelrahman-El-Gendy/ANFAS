package com.anfas.feature.subscriptions

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the subscriptions feature. Intentionally empty — UI, components and use cases
 * are separate tasks. This exists so the DI wiring point already has a home.
 */
val SubscriptionsModule: Module = module {
}
