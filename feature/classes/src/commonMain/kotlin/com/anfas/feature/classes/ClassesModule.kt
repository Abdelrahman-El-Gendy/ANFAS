package com.anfas.feature.classes

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.ClassRepository
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.uuid.Uuid

class ClassesComponentFactory internal constructor(
    private val repository: ClassRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(componentContext: ComponentContext): ClassesComponent = ClassesComponent(
        componentContext = componentContext,
        repository = repository,
        auth = auth,
        dispatchers = dispatchers,
        newId = { Uuid.random().toString() },
    )
}

/**
 * Koin module for the classes feature. Factories only — the component owns a coroutine scope
 * tied to its Decompose lifecycle and must never be a singleton.
 */
val ClassesModule: Module = module {
    factory { ClassesComponentFactory(repository = get(), auth = get(), dispatchers = get()) }
}
