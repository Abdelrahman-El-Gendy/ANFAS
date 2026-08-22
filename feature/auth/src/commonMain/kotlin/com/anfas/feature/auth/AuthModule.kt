package com.anfas.feature.auth

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Lets :composeApp create a [SignInComponent] without seeing what it depends on. */
class SignInComponentFactory internal constructor(
    private val repository: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(componentContext: ComponentContext, onSignedIn: () -> Unit): SignInComponent =
        SignInComponent(
            componentContext = componentContext,
            repository = repository,
            dispatchers = dispatchers,
            onSignedIn = onSignedIn,
        )
}

/** Lets :composeApp create a [StaffListComponent] without seeing what it depends on. */
class StaffListComponentFactory internal constructor(
    private val repository: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(componentContext: ComponentContext): StaffListComponent = StaffListComponent(
        componentContext = componentContext,
        repository = repository,
        dispatchers = dispatchers,
    )
}

/** Factories only — components own scopes tied to their Decompose lifecycles. */
val AuthModule: Module = module {
    factory { SignInComponentFactory(repository = get(), dispatchers = get()) }
    factory { StaffListComponentFactory(repository = get(), dispatchers = get()) }
}
