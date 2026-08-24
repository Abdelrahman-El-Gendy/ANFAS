package com.anfas.feature.announcements

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AnnouncementRepository
import com.anfas.core.data.AuthRepository
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

class AnnouncementsComponentFactory internal constructor(
    private val repository: AnnouncementRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(componentContext: ComponentContext): AnnouncementsComponent = AnnouncementsComponent(
        componentContext = componentContext,
        repository = repository,
        auth = auth,
        dispatchers = dispatchers,
    )
}

/**
 * Koin module for the announcements feature. Factories only — the component owns a coroutine
 * scope tied to its Decompose lifecycle and must never be a singleton.
 */
val AnnouncementsModule: Module = module {
    factory {
        AnnouncementsComponentFactory(repository = get(), auth = get(), dispatchers = get())
    }
}
