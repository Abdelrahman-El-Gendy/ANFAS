package com.anfas.feature.intakeocr

import com.anfas.core.common.AppDispatchers
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.IntakeRepository
import com.anfas.core.ocr.CameraPermissions
import com.arkivanov.decompose.ComponentContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Lets :composeApp construct the review component without seeing its dependencies. Same pattern
 * as the other features — the router supplies the context and the navigation callbacks only.
 */
class IntakeReviewComponentFactory internal constructor(
    private val intake: IntakeRepository,
    private val ingestion: IntakeIngestion,
    private val cameraPermissions: CameraPermissions,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
) {
    fun create(
        componentContext: ComponentContext,
        onImported: (imported: Int) -> Unit,
        onCloseClicked: () -> Unit,
    ): IntakeReviewComponent = IntakeReviewComponent(
        componentContext = componentContext,
        repository = intake,
        ingestion = ingestion,
        cameraPermissions = cameraPermissions,
        auth = auth,
        dispatchers = dispatchers,
        onImported = onImported,
        onCloseClicked = onCloseClicked,
    )
}

/**
 * Koin module for the intake-ocr feature. Factories only — the component owns a coroutine scope
 * tied to its Decompose lifecycle and must never be a singleton.
 */
val IntakeOcrModule: Module = module {
    // Stateless, so a single is fine — unlike the component, which owns a lifecycle scope.
    single { IntakeIngestion(recogniser = get(), repository = get(), imageStore = get()) }
    factory {
        IntakeReviewComponentFactory(
            intake = get(),
            ingestion = get(),
            cameraPermissions = get(),
            auth = get(),
            dispatchers = get(),
        )
    }
}
