package com.anfas.feature.intakeocr

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.IntakeRepository
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Lets :composeApp construct the review component without seeing its dependencies. Same pattern
 * as the other features — the router supplies the context and the navigation callbacks only.
 */
class IntakeReviewComponentFactory internal constructor(
    private val intake: IntakeRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onImported: (imported: Int) -> Unit,
    ): IntakeReviewComponent = IntakeReviewComponent(
        componentContext = componentContext,
        repository = intake,
        dispatchers = dispatchers,
        onImported = onImported,
    )
}

/**
 * Koin module for the intake-ocr feature. Factories only — the component owns a coroutine scope
 * tied to its Decompose lifecycle and must never be a singleton.
 */
val IntakeOcrModule: Module = module {
    factory { IntakeReviewComponentFactory(intake = get(), dispatchers = get()) }
}
