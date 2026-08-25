package com.anfas.feature.intakeocr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.anfas.core.data.IntakeFieldKey
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasDetailTopBar
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIconButton
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasInlineEditField
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTableFooter
import com.anfas.core.designsystem.AnfasTableHeaderCell
import com.anfas.core.designsystem.AnfasTableHeaderRow
import com.anfas.core.designsystem.AnfasTableMinWidth
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
 *  - **The split becomes stacked below 1024dp**, source pane above the table, same 40/60 ratio.
 *    The export is desktop-only at 2560px; side-by-side on a phone would make both halves
 *    useless, but dropping the photo altogether is worse — a phone is where a sheet is actually
 *    photographed, and the pane is zoomable and pannable so a small one is still usable.
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

    Column(modifier = modifier.fillMaxSize()) {
        // Intake is pushed from the directory rather than being a nav-bar destination, so it
        // owns its way back. Outside the padded body: a top bar's divider spans the screen.
        AnfasDetailTopBar(
            title = s.intake.title,
            onBack = component::onClose,
            backContentDescription = s.common.back,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // weight(1f) so the review table below is bounded and scrolls. Without it the
                // table takes its intrinsic height and the footer is pushed off-screen.
                .weight(1f)
                .padding(horizontal = AnfasTheme.spacing.marginMobile)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = s.intake.subtitle,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                // Camera denied outranks the empty state. The empty state's primary action is
                // "New scan", and offering it while the OS is refusing is how you get someone
                // tapping a button that silently does nothing.
                IntakeReviewContent.NoBatches if state.cameraDenied -> AnfasEmptyState(
                    icon = AnfasIcons.Warning,
                    title = s.intake.cameraDeniedTitle,
                    message = s.intake.cameraDeniedMessage,
                    primaryAction = EmptyStateAction(
                        label = s.intake.cameraDeniedAction,
                        onClick = component::onOpenSettings,
                    ),
                    // Still offered: the library needs no permission, so a denial does not have to
                    // be a dead end.
                    secondaryAction = EmptyStateAction(
                        label = s.intake.choosePhoto,
                        onClick = imageSource::pickFromLibrary,
                    ).takeIf { ocrCapability.canPickImage },
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
                        // Through the component, so camera access is checked first.
                        onClick = { component.onCaptureRequested(imageSource::captureFromCamera) },
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
            // The same 40/60 split, stacked. The source pane used to be dropped entirely here,
            // which left the photo unreachable on the very device that took it -- a phone is
            // where a sheet actually gets photographed. A 40%-height pane is small, but it is
            // pinch-zoomable and pannable, so it works as "glance at the paper to check a
            // garbled name", which is the whole reason the pane exists.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SourceDocumentPane(
                    batch = batch,
                    state = state,
                    component = component,
                    modifier = Modifier.fillMaxWidth().weight(SOURCE_PANE_WEIGHT),
                )
                ValidationPane(
                    batch = batch,
                    state = state,
                    component = component,
                    modifier = Modifier.fillMaxWidth().weight(TABLE_PANE_WEIGHT),
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

                        val sourceImageUri = batch.sourceImageUri
                        if (sourceImageUri == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, scheme.outlineVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = s.intake.noSourceImage,
                                    style = AnfasTheme.textStyles.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // FillBounds, not Fit: the overlay boxes below are positioned against
                            // this exact imageWidth/imageHeight rect, computed from the fixed
                            // SHEET_ASPECT_RATIO pane rather than the photo's own true aspect
                            // ratio (never stored per batch). Letterboxing with Fit would put the
                            // rendered photo and its overlay boxes at different scales.
                            SubcomposeAsyncImage(
                                model = sourceImageUri,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, scheme.outlineVariant),
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = s.intake.sourceImageNotRendered,
                                            style = AnfasTheme.textStyles.bodyMedium,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                },
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
            // A six-column table needs 640dp. Below that AnfasTableScroll made Start, End and
            // Plan reachable only by scrolling sideways, so on a phone they read as missing —
            // and their inline editors, which have always been there, were unreachable with
            // them. Narrow screens get one card per row instead, with every field labelled and
            // editable in place.
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                if (maxWidth < AnfasTableMinWidth) {
                    RowCardList(batch, component, s, Modifier.fillMaxSize())
                } else {
                    AnfasTableScroll(modifier = Modifier.fillMaxSize(), fillHeight = true) {
                        TableContent(batch, component, s)
                    }
                }
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

/**
 * One card per scanned row, for a phone.
 *
 * Every field is labelled and inline-editable, which is the point: this screen exists so a human
 * can correct what OCR misread, and a cell nobody can see is a cell nobody can fix.
 */
@Composable
private fun RowCardList(
    batch: IntakeBatch,
    component: IntakeReviewComponent,
    s: AppStrings,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = batch.rows, key = { it.id.value }) { row ->
            IntakeRowCard(
                row = row,
                onEdit = { field, value -> component.onFieldEdited(row.id, field, value) },
                s = s,
            )
        }
    }
}

@Composable
private fun IntakeRowCard(
    row: IntakeRow,
    onEdit: (IntakeFieldKey, String) -> Unit,
    s: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                // The printed row number, so staff can find this line on the paper in their hand.
                text = s.intake.columnOrdinal + " " + row.ordinal.toString(),
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
            CardField(s.intake.columnName, row, IntakeFieldKey.NAME, onEdit, s)
            CardField(s.intake.columnPhone, row, IntakeFieldKey.PHONE, onEdit, s)
            // Start and End together on one line: they are read as a pair, and a wrong end date
            // is usually only obvious next to its start.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    CardField(s.intake.columnStart, row, IntakeFieldKey.START_DATE, onEdit, s)
                }
                Box(Modifier.weight(1f)) {
                    CardField(s.intake.columnEnd, row, IntakeFieldKey.END_DATE, onEdit, s)
                }
            }
            CardField(s.intake.columnPlan, row, IntakeFieldKey.PLAN, onEdit, s)
        }
    }
}

@Composable
private fun CardField(
    label: String,
    row: IntakeRow,
    key: IntakeFieldKey,
    onEdit: (IntakeFieldKey, String) -> Unit,
    s: AppStrings,
) {
    val field = row.fieldFor(key)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.labelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnfasInlineEditField(
            value = field.value,
            onValueChange = { onEdit(key, it) },
            needsReview = field.needsReview,
            error = row.errorFor(key)?.label(s),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Maps a key back to its cell, so the card can be built from a list rather than by hand. */
private fun IntakeRow.fieldFor(key: IntakeFieldKey) = when (key) {
    IntakeFieldKey.NAME -> name
    IntakeFieldKey.PHONE -> phone
    IntakeFieldKey.START_DATE -> startDate
    IntakeFieldKey.END_DATE -> endDate
    IntakeFieldKey.PLAN -> plan
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
