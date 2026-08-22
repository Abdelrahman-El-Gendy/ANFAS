package com.anfas.feature.intakeocr

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.IntakeFieldKey
import com.anfas.core.data.IntakeRepository
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeRowId
import com.anfas.core.ocr.CameraAccess
import com.anfas.core.ocr.CameraPermissions
import com.anfas.core.ocr.CapturedImage
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reviewing a scanned sign-up sheet: correct what OCR misread, then import the rows that pass.
 *
 * The component always shows the **oldest sheet still under review** rather than taking a batch
 * id. Sheets are a queue of work, not addressable documents — staff photograph one and deal
 * with it — and picking the oldest means a forgotten sheet surfaces instead of being buried.
 *
 * Edits are written straight through to the repository rather than held locally. Validation is
 * relational (a corrected phone can un-block a *different* row), so the authoritative answer
 * has to come back from a revalidated read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntakeReviewComponent(
    componentContext: ComponentContext,
    private val repository: IntakeRepository,
    private val ingestion: IntakeIngestion,
    private val cameraPermissions: CameraPermissions,
    dispatchers: AppDispatchers,
    private val onImported: (imported: Int) -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("IntakeReview"))

    private val ui = MutableStateFlow(UiState())

    val state: StateFlow<IntakeReviewState> = combine(
        ui,
        repository.observeBatches().flatMapLatest { batchesResult ->
            when (batchesResult) {
                is AppResult.Failure ->
                    MutableStateFlow<AppResult<IntakeBatch?>>(
                        AppResult.Failure(batchesResult.error),
                    )

                is AppResult.Success -> {
                    val next = batchesResult.value
                        .filter { it.status == IntakeBatchStatus.REVIEWING }
                        .minByOrNull { it.capturedAt }
                    if (next == null) {
                        MutableStateFlow(AppResult.Success(null))
                    } else {
                        // Re-observe the chosen sheet: observeBatches() deliberately returns
                        // summaries without rows, and the review screen needs the rows.
                        repository.observeBatch(next.id).map { it }
                    }
                }
            }
        },
    ) { local, batchResult ->
        IntakeReviewState(
            content = batchResult.toContent(),
            zoom = local.zoom,
            panX = local.panX,
            panY = local.panY,
            isImporting = local.isImporting,
            isScanning = local.isScanning,
            cameraDenied = local.cameraDenied,
            outcome = local.outcome,
            notice = local.notice,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = IntakeReviewState(),
    )

    fun onFieldEdited(rowId: IntakeRowId, field: IntakeFieldKey, value: String) {
        scope.launch {
            when (val result = repository.editField(rowId, field, value)) {
                is AppResult.Failure -> ui.update {
                    it.copy(notice = IntakeNotice.Failed(result.error.message))
                }

                is AppResult.Success -> Unit // The revalidated read is the feedback.
            }
        }
    }

    fun onZoomIn() = ui.update { it.copy(zoom = it.zoom.stepZoom(ZOOM_STEP)) }

    fun onZoomOut() = ui.update { it.copy(zoom = it.zoom.stepZoom(1f / ZOOM_STEP)) }

    fun onPan(dx: Float, dy: Float) = ui.update {
        it.copy(panX = it.panX + dx, panY = it.panY + dy)
    }

    fun onResetView() = ui.update { it.copy(zoom = 1f, panX = 0f, panY = 0f) }

    fun onImport() {
        val batchId = state.value.batch?.id ?: return
        if (ui.value.isImporting) return
        ui.update { it.copy(isImporting = true, notice = null) }
        scope.launch { performImport(batchId) }
    }

    fun onDiscard() {
        val batchId = state.value.batch?.id ?: return
        scope.launch {
            when (val result = repository.discardBatch(batchId)) {
                is AppResult.Failure -> ui.update {
                    it.copy(notice = IntakeNotice.Failed(result.error.message))
                }

                is AppResult.Success -> ui.update { UiState(notice = IntakeNotice.Discarded) }
            }
        }
    }

    /**
     * Asks for camera access, then hands control back to the screen to launch the capture.
     *
     * The permission check lives here and the launcher lives in the screen because Android's
     * ActivityResultLauncher can only be registered in composition, while the check is a suspend
     * call. Passing [launch] in keeps the platform difference in one place instead of making
     * every caller branch.
     *
     * On Android this always proceeds — ACTION_IMAGE_CAPTURE needs no permission. On iOS a denial
     * is terminal until the user changes it in Settings, which is why it becomes sticky state
     * rather than a dismissible notice.
     */
    fun onCaptureRequested(launch: () -> Unit) {
        scope.launch {
            when (cameraPermissions.request()) {
                CameraAccess.Granted, CameraAccess.NotRequired -> {
                    ui.update { it.copy(cameraDenied = false) }
                    launch()
                }

                CameraAccess.Denied -> ui.update { it.copy(cameraDenied = true) }
            }
        }
    }

    /** Routes to the OS settings page, the only place the user can undo a denial. */
    fun onOpenSettings() = cameraPermissions.openSettings()

    /**
     * Called with whatever the platform image source produced. Capture itself lives in the
     * screen, because Android's ActivityResultLauncher can only be registered in composition.
     *
     * A cancelled capture never reaches here — the platform sources report nothing for a
     * cancel, since backing out of the camera is not an error to explain.
     */
    fun onImageCaptured(result: AppResult<CapturedImage>) {
        when (result) {
            is AppResult.Failure -> ui.update {
                it.copy(notice = IntakeNotice.Failed(result.error.message))
            }

            is AppResult.Success -> scope.launch {
                ui.update { it.copy(isScanning = true) }
                val notice = when (val outcome = ingestion.ingest(result.value)) {
                    is IngestionResult.Ingested -> IntakeNotice.Scanned(outcome.rows)
                    IngestionResult.NothingFound -> IntakeNotice.NothingFound
                    is IngestionResult.Failed -> IntakeNotice.Failed(outcome.error.message)
                }
                // The new batch arrives through observeBatches(), so the screen switches
                // itself; this only reports what happened.
                ui.update { it.copy(isScanning = false, notice = notice) }
            }
        }
    }

    fun onNoticeShown() = ui.update { it.copy(notice = null, outcome = null) }

    private suspend fun performImport(batchId: IntakeBatchId) {
        when (val result = repository.importBatch(batchId)) {
            is AppResult.Failure -> ui.update {
                it.copy(isImporting = false, notice = IntakeNotice.Failed(result.error.message))
            }

            is AppResult.Success -> {
                val outcome = result.value
                ui.update {
                    // Reset the view too: the next sheet is a different photograph, and
                    // inheriting the previous one's zoom and pan would be disorienting.
                    UiState(
                        outcome = outcome,
                        notice = IntakeNotice.Imported(outcome.imported, outcome.skipped),
                    )
                }
                onImported(outcome.imported)
            }
        }
    }

    private fun Float.stepZoom(factor: Float): Float =
        (this * factor).coerceIn(IntakeReviewState.MIN_ZOOM, IntakeReviewState.MAX_ZOOM)

    private data class UiState(
        val zoom: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f,
        val isImporting: Boolean = false,
        val isScanning: Boolean = false,
        val cameraDenied: Boolean = false,
        val outcome: com.anfas.core.data.ImportOutcome? = null,
        val notice: IntakeNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val ZOOM_STEP = 1.5f
    }
}

private fun AppResult<IntakeBatch?>.toContent(): IntakeReviewContent = when (this) {
    is AppResult.Failure -> IntakeReviewContent.Failed(error.message)

    is AppResult.Success ->
        value
            ?.let { IntakeReviewContent.Loaded(it) }
            ?: IntakeReviewContent.NoBatches
}
