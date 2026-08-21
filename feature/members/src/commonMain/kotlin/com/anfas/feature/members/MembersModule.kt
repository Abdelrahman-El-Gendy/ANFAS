package com.anfas.feature.members

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module for the members feature. Only factories are exported — components are per-screen
 * and own a coroutine scope tied to their lifecycle, so they must never be singletons.
 */
val MembersModule: Module = module {
    factory { MembersListComponentFactory(repository = get(), dispatchers = get()) }
}
