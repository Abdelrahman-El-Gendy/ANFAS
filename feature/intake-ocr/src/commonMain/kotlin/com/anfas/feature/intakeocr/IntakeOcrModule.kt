package com.anfas.feature.intakeocr

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.IntakeRepository
import com.anfas.core.ocr.CameraPermissions
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /**
     * Runs capture housekeeping once at startup, mirroring the plan seed in `DataModule`:
     * `createdAtStart` so nothing has to ask for it, and on its own IO scope because Koin builds
     * singletons on whichever thread first resolves them — on Android, the main one.
     *
     * `appExceptionHandler` rather than a bare scope: housekeeping failing must never take the app
     * down with it. The worst outcome of a failure here is an orphaned file left on disk.
     */
    single(createdAtStart = true) {
        val housekeeping = IntakeHousekeeping(repository = get(), imageStore = get())
        val dispatchers = get<AppDispatchers>()
        CoroutineScope(
            dispatchers.io + SupervisorJob() + appExceptionHandler("IntakeHousekeeping"),
        ).also { scope -> scope.launch { housekeeping.run() } }
    }
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
