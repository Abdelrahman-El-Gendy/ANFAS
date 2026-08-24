package com.anfas.feature.equipment

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.EquipmentRepository
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

class EquipmentComponentFactory internal constructor(
    private val repository: EquipmentRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(componentContext: ComponentContext): EquipmentComponent = EquipmentComponent(
        componentContext = componentContext,
        repository = repository,
        auth = auth,
        dispatchers = dispatchers,
    )
}

/**
 * Koin module for the equipment feature. Factories only — the component owns a coroutine scope
 * tied to its Decompose lifecycle and must never be a singleton.
 */
val EquipmentModule: Module = module {
    factory {
        EquipmentComponentFactory(repository = get(), auth = get(), dispatchers = get())
    }
}
