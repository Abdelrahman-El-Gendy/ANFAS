package com.anfas.feature.intakeocr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import com.anfas.core.designsystem.AnfasTableScroll
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeIssue
import com.anfas.core.model.IntakeRow
import com.anfas.core.ocr.ocrCapability
import com.anfas.core.ocr.rememberImageSource

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
 *  - **The source pane is pinned LTR.** A photograph is not a mirrored artifact, so the overlay
 *    boxes must not flip under an Arabic layout even though the rest of the screen does.
 */
@Composable
fun IntakeReviewScreen(component: IntakeReviewComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings
    // Registered unconditionally: rememberImageSource has to be called from composition on
    // Android, so it cannot sit behind the capability check that gates the buttons below.
    val imageSource = rememberImageSource(component::onImageCaptured)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(
            title = s.intake.title,
            subtitle = s.intake.subtitle,
        )

        state.notice?.let { notice ->
            NoticeBar(notice.render(s), component::onNoticeShown)
        }

        when (val content = state.content) {
            IntakeReviewContent.Loading -> Box(Modifier.fillMaxSize())

            is IntakeReviewContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.intake.loadFailedTitle,
                message = content.message,
            )

            IntakeReviewContent.NoBatches -> AnfasEmptyState(
                icon = AnfasIcons.DocumentScanner,
                title = s.intake.emptyTitle,
                // Desktop has neither a camera nor an OCR engine, so it says so instead of
                // offering buttons that cannot work. The UI reads the capability flag rather
                // than branching on platform.
                message = if (ocrCapability.isSupported) {
                    s.intake.emptyMessage
                } else {
                    s.intake.emptyMessageNoCapture
                },
                primaryAction = EmptyStateAction(
                    label = s.intake.newScan,
                    onClick = imageSource::captureFromCamera,
                    icon = AnfasIcons.DocumentScanner,
                ).takeIf { ocrCapability.canCapture },
                secondaryAction = EmptyStateAction(
                    label = s.intake.choosePhoto,
                    onClick = imageSource::pickFromLibrary,
                ).takeIf { ocrCapability.canPickImage },
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
    val s = strings
    AnfasCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = s.intake.sourceDocument,
                style = AnfasTheme.textStyles.headlineSmall,
                color = scheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AnfasIconButton(
                    icon = AnfasIcons.ZoomOut,
                    contentDescription = s.common.zoomOut,
                    onClick = component::onZoomOut,
                    enabled = state.zoom > IntakeReviewState.MIN_ZOOM,
                )
                AnfasIconButton(
                    icon = AnfasIcons.ZoomIn,
                    contentDescription = s.common.zoomIn,
                    onClick = component::onZoomIn,
                    enabled = state.zoom < IntakeReviewState.MAX_ZOOM,
                )
            }
        }
        AnfasTableDivider()
        // The whole source pane is pinned LTR. A photograph is not a mirrored artifact, and
        // Modifier.offset IS layout-direction aware -- it negates x under RTL -- so in Arabic
        // every overlay box would jump to the mirrored position and point staff at the wrong
        // place on the paper. Forcing the direction here also immunises any future overlay code
        // (Alignment, Arrangement, pan gestures) rather than patching one call site.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                contentAlignment = Alignment.Center,
            ) {
                // Boxes are positioned against the RENDERED IMAGE RECT, not the pane. Sizing them
                // off the pane is only correct when the photo's aspect ratio equals the pane's,
                // which it never does -- every box was previously offset by the letterboxing.
                val imageAspect = SHEET_ASPECT_RATIO
                Box(
                    modifier = Modifier
                        .aspectRatio(imageAspect)
                        .graphicsLayer(
                            scaleX = state.zoom,
                            scaleY = state.zoom,
                            translationX = state.panX,
                            translationY = state.panY,
                        ),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val imageWidth = maxWidth
                        val imageHeight = maxHeight

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, scheme.outlineVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = batch.sourceImageUri
                                    ?.let { s.intake.sourceImageNotRendered }
                                    ?: s.intake.noSourceImage,
                                style = AnfasTheme.textStyles.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }

                        batch.rows.forEach { row ->
                            val bounds = row.bounds ?: return@forEach
                            // A blocked row's box is red, so the eye can jump from the table
                            // straight to the place on the paper that needs re-reading.
                            val accent = if (row.isImportable) scheme.primary else scheme.error
                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = imageWidth * bounds.left,
                                        y = imageHeight * bounds.top,
                                    )
                                    .size(
                                        width = imageWidth * bounds.width,
                                        height = imageHeight * bounds.height,
                                    )
                                    .background(accent.copy(alpha = 0.10f))
                                    .border(2.dp, accent.copy(alpha = 0.60f)),
                            )
                        }
                    }
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
    val s = strings
    AnfasCard(modifier = modifier) {
        // Six columns do not fit a phone. Weights would divide the width evenly and clip every
        // cell to a few characters -- "Omar H", "Nov", "Mor" -- which is unreadable and, worse,
        // hides the misreads this screen exists to catch. So the table keeps a usable minimum
        // width and scrolls horizontally when the pane is narrower than that.
        Column(modifier = Modifier.fillMaxHeight()) {
            // Only the header and the rows scroll, and they share one scroll state so the
            // headings stay above their own columns. The footer is deliberately outside it —
            // it carries Discard and Import, and an action you have to go looking for
            // sideways is an action nobody finds.
            AnfasTableScroll(modifier = Modifier.weight(1f), fillHeight = true) {
                TableContent(batch, component, s)
            }
            TableFooter(state, component, scheme, s)
        }
    }
}

@Composable
private fun ColumnScope.TableContent(
    batch: IntakeBatch,
    component: IntakeReviewComponent,
    s: AppStrings,
) {
    AnfasTableHeaderRow {
        AnfasTableHeaderCell(s.intake.columnOrdinal, Modifier.width(OrdinalWidth))
        AnfasTableHeaderCell(s.intake.columnName, Modifier.weight(WEIGHT_NAME))
        AnfasTableHeaderCell(s.intake.columnPhone, Modifier.weight(WEIGHT_PHONE))
        AnfasTableHeaderCell(s.intake.columnStart, Modifier.weight(WEIGHT_DATE))
        AnfasTableHeaderCell(s.intake.columnEnd, Modifier.weight(WEIGHT_DATE))
        AnfasTableHeaderCell(s.intake.columnPlan, Modifier.weight(WEIGHT_PLAN))
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableFooter(
    state: IntakeReviewState,
    component: IntakeReviewComponent,
    scheme: ColorScheme,
    s: AppStrings,
) {
    AnfasTableFooter {
        // FlowRow so a phone stacks the count above the actions instead of squeezing the label
        // into a four-line column beside them.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = s.intake.rowsReady(state.readyCount, state.totalCount),
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
                // weight() is what keeps the buttons whole. Unweighted, this label claimed
                // its full intrinsic width and the import button was measured at whatever
                // was left -- on a phone that collapsed it to a featureless amber sliver.
                // Weighted children are measured last, so the buttons now get their
                // intrinsic size and the label takes the remainder.
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnfasSecondaryButton(text = s.common.discard, onClick = component::onDiscard)
                AnfasPrimaryButton(
                    text = if (state.isImporting) {
                        s.intake.importing
                    } else {
                        s.intake.importCount(state.readyCount)
                    },
                    icon = AnfasIcons.Upload,
                    onClick = component::onImport,
                    enabled = state.canImport,
                )
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
    val s = strings
    AnfasTableRow(showDivider = !isLast) {
        Text(
            text = row.ordinal.toString(),
            style = AnfasTheme.textStyles.dataMonoLtr,
            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(OrdinalWidth),
        )
        AnfasInlineEditField(
            value = row.name.value,
            onValueChange = { onEdit(IntakeFieldKey.NAME, it) },
            needsReview = row.name.needsReview,
            error = row.errorFor(IntakeFieldKey.NAME)?.label(s),
            modifier = Modifier.weight(WEIGHT_NAME),
        )
        AnfasInlineEditField(
            value = row.phone.value,
            onValueChange = { onEdit(IntakeFieldKey.PHONE, it) },
            needsReview = row.phone.needsReview,
            error = row.errorFor(IntakeFieldKey.PHONE)?.label(s),
            modifier = Modifier.weight(WEIGHT_PHONE),
        )
        AnfasInlineEditField(
            value = row.startDate.value,
            onValueChange = { onEdit(IntakeFieldKey.START_DATE, it) },
            needsReview = row.startDate.needsReview,
            error = row.errorFor(IntakeFieldKey.START_DATE)?.label(s),
            modifier = Modifier.weight(WEIGHT_DATE),
        )
        AnfasInlineEditField(
            value = row.endDate.value,
            onValueChange = { onEdit(IntakeFieldKey.END_DATE, it) },
            needsReview = row.endDate.needsReview,
            error = row.errorFor(IntakeFieldKey.END_DATE)?.label(s),
            modifier = Modifier.weight(WEIGHT_DATE),
        )
        AnfasInlineEditField(
            value = row.plan.value,
            onValueChange = { onEdit(IntakeFieldKey.PLAN, it) },
            needsReview = row.plan.needsReview,
            error = row.errorFor(IntakeFieldKey.PLAN)?.label(s),
            modifier = Modifier.weight(WEIGHT_PLAN),
        )
    }
}

/**
 * Attributes an issue to the cell that can fix it, so the message appears where the correction
 * has to be typed rather than at the end of the row.
 */
private fun IntakeRow.errorFor(field: IntakeFieldKey): IntakeIssue? {
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
    return issues.firstOrNull { it in relevant && it.isBlocking }
}

/** Blocking issue -> message, shown in the cell that can fix it. */
private fun IntakeIssue.label(s: AppStrings): String = when (this) {
    IntakeIssue.MISSING_NAME -> s.intake.issueMissingName
    IntakeIssue.MISSING_PHONE -> s.intake.issueMissingPhone
    IntakeIssue.DUPLICATE_PHONE -> s.intake.issueDuplicate
    IntakeIssue.DUPLICATE_IN_BATCH -> s.intake.issueDuplicateInSheet
    IntakeIssue.END_BEFORE_START -> s.intake.issueEndBeforeStart
    IntakeIssue.UNREADABLE_DATE -> s.intake.issueUnreadableDate
    IntakeIssue.UNKNOWN_PLAN -> s.intake.issueUnknownPlan
    IntakeIssue.LOW_CONFIDENCE -> s.intake.issueLowConfidence
}

/** Typed notice -> sentence. The component deliberately does not do this itself. */
private fun IntakeNotice.render(s: AppStrings): String = when (this) {
    is IntakeNotice.Imported -> when {
        imported == 0 && skipped == 0 -> s.intake.importedNothingToDo
        imported == 0 -> s.intake.importedNoneAllBlocked(skipped)
        skipped == 0 -> s.intake.importedAll(imported)
        else -> s.intake.importedPartial(imported, skipped)
    }

    IntakeNotice.Discarded -> s.intake.sheetDiscarded

    is IntakeNotice.Failed -> message

    is IntakeNotice.Scanned -> s.intake.scannedRows(rows)

    IntakeNotice.NothingFound -> s.intake.scanFoundNothing
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    val s = strings
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
            text = s.common.dismiss,
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

/**
 * Placeholder aspect ratio for the source pane until real capture lands and the image's own
 * dimensions are known. A4 in portrait, which is what a gym sign-up sheet is.
 */
private const val SHEET_ASPECT_RATIO = 210f / 297f
