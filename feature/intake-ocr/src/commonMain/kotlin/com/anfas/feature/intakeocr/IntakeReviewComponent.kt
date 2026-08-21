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
                is AppResult.Failure -> ui.update { it.copy(notice = result.error.message) }
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
                is AppResult.Failure -> ui.update { it.copy(notice = result.error.message) }
                is AppResult.Success -> ui.update { UiState(notice = "Sheet discarded.") }
            }
        }
    }

    fun onNoticeShown() = ui.update { it.copy(notice = null, outcome = null) }

    private suspend fun performImport(batchId: IntakeBatchId) {
        when (val result = repository.importBatch(batchId)) {
            is AppResult.Failure -> ui.update {
                it.copy(isImporting = false, notice = result.error.message)
            }

            is AppResult.Success -> {
                val outcome = result.value
                ui.update {
                    // Reset the view too: the next sheet is a different photograph, and
                    // inheriting the previous one's zoom and pan would be disorienting.
                    UiState(
                        outcome = outcome,
                        notice = importNotice(outcome.imported, outcome.skipped),
                    )
                }
                onImported(outcome.imported)
            }
        }
    }

    /**
     * Names the leftovers explicitly. "Imported 6" alone would let staff walk away believing
     * the sheet was fully processed when two people are still not registered.
     */
    private fun importNotice(imported: Int, skipped: Int): String = when {
        imported == 0 && skipped == 0 -> "Nothing on this sheet to import."
        imported == 0 -> "Nothing imported — all $skipped rows still need fixing."
        skipped == 0 && imported == 1 -> "1 member imported."
        skipped == 0 -> "$imported members imported."
        else -> "$imported imported; $skipped still need fixing."
    }

    private fun Float.stepZoom(factor: Float): Float =
        (this * factor).coerceIn(IntakeReviewState.MIN_ZOOM, IntakeReviewState.MAX_ZOOM)

    private data class UiState(
        val zoom: Float = 1f,
        val panX: Float = 0f,
        val panY: Float = 0f,
        val isImporting: Boolean = false,
        val outcome: com.anfas.core.data.ImportOutcome? = null,
        val notice: String? = null,
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
