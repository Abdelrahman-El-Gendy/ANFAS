package com.anfas.feature.intakeocr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.data.IntakeFieldKey
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIconButton
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasInlineEditField
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTableFooter
import com.anfas.core.designsystem.AnfasTableHeaderCell
import com.anfas.core.designsystem.AnfasTableHeaderRow
import com.anfas.core.designsystem.AnfasTableRow
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeIssue
import com.anfas.core.model.IntakeRow

/**
 * Reviewing a scanned sign-up sheet.
 *
 * Departures from `ocr-intake-review`, all documented rather than silent:
 *  - **No pan-tool button.** The export has a mode toggle; the source view is directly
 *    draggable and pinch-zoomable instead, which is fewer controls and works on touch.
 *  - **The split becomes stacked below 1024dp.** The export is desktop-only at 2560px; a 40/60
 *    split of a phone screen would make both halves useless.
 *  - **No "Merge" action on a duplicate.** The export offers it, but merging two member records
 *    is a destructive operation with no defined semantics anywhere in the design — which
 *    fields win, what happens to the other's history. Correcting the phone number is offered
 *    instead, which is reversible.
 *  - **No source image.** There is no capture path yet, so the pane renders its overlay over a
 *    placeholder. See the note on `IntakeRepository.createBatch`.
 */
@Composable
fun IntakeReviewScreen(component: IntakeReviewComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(
            title = "Intake",
            subtitle = "Review parsed data before import. Resolve warnings.",
        )

        state.notice?.let { notice ->
            NoticeBar(notice, component::onNoticeShown)
        }

        when (val content = state.content) {
            IntakeReviewContent.Loading -> Box(Modifier.fillMaxSize())

            is IntakeReviewContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = "Couldn't load the sheet",
                message = content.message,
            )

            IntakeReviewContent.NoBatches -> AnfasEmptyState(
                icon = AnfasIcons.DocumentScanner,
                title = "No scans yet",
                // The export offers a "New scan" button here. Capture is not implemented, and
                // a button that does nothing is worse than none — see the class comment.
                message = "Photograph a paper sign-up sheet to import members in bulk. " +
                    "Capture is not available yet.",
            )

            is IntakeReviewContent.Loaded -> ReviewBody(
                batch = content.batch,
                state = state,
                component = component,
            )
        }
    }
}

@Composable
private fun ReviewBody(
    batch: IntakeBatch,
    state: IntakeReviewState,
    component: IntakeReviewComponent,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= AnfasBreakpoints.tabletMax
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SourceDocumentPane(
                    batch = batch,
                    state = state,
                    component = component,
                    modifier = Modifier.weight(SOURCE_PANE_WEIGHT).fillMaxHeight(),
                )
                ValidationPane(
                    batch = batch,
                    state = state,
                    component = component,
                    modifier = Modifier.weight(TABLE_PANE_WEIGHT).fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ValidationPane(
                    batch = batch,
                    state = state,
                    component = component,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/**
 * The photographed sheet with OCR boxes drawn over it.
 *
 * Boxes are positioned from normalised 0..1 bounds, so they stay correct at any zoom or pane
 * size without the layer knowing anything about the image's pixel dimensions.
 */
@Composable
private fun SourceDocumentPane(
    batch: IntakeBatch,
    state: IntakeReviewState,
    component: IntakeReviewComponent,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Source document",
                style = AnfasTheme.textStyles.headlineSmall,
                color = scheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnfasIconButton(
                    icon = AnfasIcons.ZoomOut,
                    contentDescription = "Zoom out",
                    onClick = component::onZoomOut,
                    enabled = state.zoom > IntakeReviewState.MIN_ZOOM,
                )
                AnfasIconButton(
                    icon = AnfasIcons.ZoomIn,
                    contentDescription = "Zoom in",
                    onClick = component::onZoomIn,
                    enabled = state.zoom < IntakeReviewState.MAX_ZOOM,
                )
            }
        }
        AnfasTableDivider()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.surfaceContainerLowest)
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        component.onPan(panChange.x, panChange.y)
                        if (zoomChange > 1f) {
                            component.onZoomIn()
                        } else if (zoomChange < 1f) {
                            component.onZoomOut()
                        }
                    }
                },
        ) {
            val paneWidth = maxWidth
            val paneHeight = maxHeight
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = state.zoom,
                        scaleY = state.zoom,
                        translationX = state.panX,
                        translationY = state.panY,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .border(1.dp, scheme.outlineVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = batch.sourceImageUri?.let { "Source image not rendered" }
                            ?: "No source image",
                        style = AnfasTheme.textStyles.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                batch.rows.forEach { row ->
                    val bounds = row.bounds ?: return@forEach
                    // A blocked row's box is red, so the eye can jump from the table straight
                    // to the place on the paper that needs re-reading.
                    val accent =
                        if (row.isImportable) scheme.primary else scheme.error
                    Box(
                        modifier = Modifier
                            .offset(x = paneWidth * bounds.left, y = paneHeight * bounds.top)
                            .size(
                                width = paneWidth * bounds.width,
                                height = paneHeight * bounds.height,
                            )
                            .background(accent.copy(alpha = 0.10f))
                            .border(2.dp, accent.copy(alpha = 0.60f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidationPane(
    batch: IntakeBatch,
    state: IntakeReviewState,
    component: IntakeReviewComponent,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = modifier) {
        AnfasTableHeaderRow {
            AnfasTableHeaderCell("#", Modifier.width(OrdinalWidth))
            AnfasTableHeaderCell("NAME", Modifier.weight(WEIGHT_NAME))
            AnfasTableHeaderCell("PHONE", Modifier.weight(WEIGHT_PHONE))
            AnfasTableHeaderCell("START", Modifier.weight(WEIGHT_DATE))
            AnfasTableHeaderCell("END", Modifier.weight(WEIGHT_DATE))
            AnfasTableHeaderCell("PLAN", Modifier.weight(WEIGHT_PLAN))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(items = batch.rows, key = { it.id.value }) { row ->
                IntakeRowCells(
                    row = row,
                    isLast = row == batch.rows.last(),
                    onEdit = { field, value -> component.onFieldEdited(row.id, field, value) },
                )
            }
        }
        AnfasTableFooter {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        append(state.readyCount)
                        append(" of ")
                        append(state.totalCount)
                        append(" rows ready for import")
                    },
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnfasSecondaryButton(text = "DISCARD", onClick = component::onDiscard)
                    AnfasPrimaryButton(
                        text = if (state.isImporting) {
                            "IMPORTING…"
                        } else {
                            "IMPORT ${state.readyCount}"
                        },
                        icon = AnfasIcons.Upload,
                        onClick = component::onImport,
                        enabled = state.canImport,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntakeRowCells(
    row: IntakeRow,
    isLast: Boolean,
    onEdit: (IntakeFieldKey, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AnfasTableRow(showDivider = !isLast) {
        Text(
            text = row.ordinal.toString(),
            style = AnfasTheme.textStyles.dataMono,
            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(OrdinalWidth),
        )
        AnfasInlineEditField(
            value = row.name.value,
            onValueChange = { onEdit(IntakeFieldKey.NAME, it) },
            needsReview = row.name.needsReview,
            error = row.errorFor(IntakeFieldKey.NAME),
            modifier = Modifier.weight(WEIGHT_NAME),
        )
        AnfasInlineEditField(
            value = row.phone.value,
            onValueChange = { onEdit(IntakeFieldKey.PHONE, it) },
            needsReview = row.phone.needsReview,
            error = row.errorFor(IntakeFieldKey.PHONE),
            modifier = Modifier.weight(WEIGHT_PHONE),
        )
        AnfasInlineEditField(
            value = row.startDate.value,
            onValueChange = { onEdit(IntakeFieldKey.START_DATE, it) },
            needsReview = row.startDate.needsReview,
            error = row.errorFor(IntakeFieldKey.START_DATE),
            modifier = Modifier.weight(WEIGHT_DATE),
        )
        AnfasInlineEditField(
            value = row.endDate.value,
            onValueChange = { onEdit(IntakeFieldKey.END_DATE, it) },
            needsReview = row.endDate.needsReview,
            error = row.errorFor(IntakeFieldKey.END_DATE),
            modifier = Modifier.weight(WEIGHT_DATE),
        )
        AnfasInlineEditField(
            value = row.plan.value,
            onValueChange = { onEdit(IntakeFieldKey.PLAN, it) },
            needsReview = row.plan.needsReview,
            error = row.errorFor(IntakeFieldKey.PLAN),
            modifier = Modifier.weight(WEIGHT_PLAN),
        )
    }
}

/**
 * Attributes an issue to the cell that can fix it, so the message appears where the correction
 * has to be typed rather than at the end of the row.
 */
private fun IntakeRow.errorFor(field: IntakeFieldKey): String? {
    val relevant = when (field) {
        IntakeFieldKey.NAME -> setOf(IntakeIssue.MISSING_NAME)

        IntakeFieldKey.PHONE -> setOf(
            IntakeIssue.MISSING_PHONE,
            IntakeIssue.DUPLICATE_PHONE,
            IntakeIssue.DUPLICATE_IN_BATCH,
        )

        IntakeFieldKey.START_DATE, IntakeFieldKey.END_DATE -> setOf(IntakeIssue.END_BEFORE_START)

        IntakeFieldKey.PLAN -> emptySet()
    }
    return issues.firstOrNull { it in relevant && it.isBlocking }?.label
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnfasTextAction(
            text = "Dismiss",
            onClick = onDismiss,
            emphasis = TextActionEmphasis.Muted,
        )
    }
}

private const val SOURCE_PANE_WEIGHT = 0.4f
private const val TABLE_PANE_WEIGHT = 0.6f
private const val WEIGHT_NAME = 2f
private const val WEIGHT_PHONE = 1.6f
private const val WEIGHT_DATE = 1.3f
private const val WEIGHT_PLAN = 1.2f
private val OrdinalWidth = 28.dp
