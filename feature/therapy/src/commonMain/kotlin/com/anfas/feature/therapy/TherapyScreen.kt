package com.anfas.feature.therapy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.data.TherapyCaseDetail
import com.anfas.core.designsystem.AnfasAvatar
import com.anfas.core.designsystem.AnfasBanner
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasDetailTopBar
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.BannerTone
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.Tone
import com.anfas.core.designsystem.initialsOf
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.asLtrIsolate
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.strings
import com.anfas.core.model.CaseStatus
import com.anfas.core.model.Member
import com.anfas.core.model.PainScoreTrend
import com.anfas.core.model.TherapySession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One member's therapy record — the export's `therapy-case-file`.
 *
 * Departures from the export, all because the data or a system it depends on does not exist:
 *  - **No "Next Appointment" card.** There is no booking system — `:feature:classes` schedules
 *    recurring weekly slots, not per-patient appointments, and check-in is walk-in. Inventing a
 *    date here would promise a booking nothing keeps.
 *  - **No "Files" card.** Nothing in this app stores general attachments; `:core:ocr`'s image
 *    capture is narrow and single-purpose (a sign-up sheet), not a document library.
 *  - **No range-of-motion figure** ("Shoulder Flexion 115° → 158°"). That is condition-specific
 *    and would need a general named-metric system — see `TherapyProgress`'s KDoc. Pain score is
 *    the one measurement every case can report the same way, so it is the one shown.
 *
 * There is also no roster to browse: the screen is reached from a member's own profile, exactly
 * as Renewal is, because the export never drew a caseload list — see `TherapyRepository`'s KDoc.
 */
@Composable
fun TherapyScreen(component: TherapyComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(modifier = modifier.fillMaxSize()) {
        AnfasDetailTopBar(
            title = s.therapy.title,
            onBack = component::onBack,
            backContentDescription = s.common.back,
        )
        AnfasBanner(
            message = s.therapy.restrictedBanner,
            icon = AnfasIcons.Lock,
            tone = BannerTone.Restricted,
        )

        when (val content = state.content) {
            TherapyContent.Loading -> Box(Modifier.fillMaxSize())

            is TherapyContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.therapy.loadFailedTitle,
                message = content.message,
                modifier = Modifier.fillMaxSize(),
            )

            TherapyContent.MemberMissing -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.members.profileNotFoundTitle,
                message = s.members.profileNotFoundMessage,
                modifier = Modifier.fillMaxSize(),
            )

            is TherapyContent.Loaded -> Body(
                member = content.member,
                state = state,
                component = component,
                s = s,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    state.caseForm?.let { form -> CaseFormDialog(form = form, component = component, s = s) }
    state.sessionForm?.let { form -> SessionFormDialog(form = form, component = component, s = s) }
    if (state.closeConfirmVisible) {
        CloseCaseConfirmDialog(component = component, s = s)
    }
}

@Composable
private fun Body(
    member: Member,
    state: TherapyState,
    component: TherapyComponent,
    s: AppStrings,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(member = member, state = state, s = s)

        state.notice?.let { notice ->
            val isFailure = notice is TherapyNotice.Failed
            AnfasBanner(
                message = notice.render(s),
                icon = if (isFailure) AnfasIcons.ErrorOutline else AnfasIcons.Check,
                tone = if (isFailure) BannerTone.Critical else BannerTone.Informational,
                dismissLabel = s.common.dismiss,
                onDismiss = component::onNoticeShown,
            )
        }

        if (detail == null) {
            AnfasEmptyState(
                icon = AnfasIcons.Group,
                title = s.therapy.emptyTitle,
                message = s.therapy.emptyMessage,
                tone = Tone.Invitation,
                primaryAction = EmptyStateAction(
                    label = s.therapy.openCase,
                    onClick = component::onOpenCaseForm,
                    icon = AnfasIcons.Add,
                ),
            )
            return@Column
        }

        detail.case.contraindications?.let { text -> ContraindicationsCard(text = text, s = s) }

        IntakeCard(detail = detail, s = s)

        SessionsCard(state = state, component = component, s = s)

        state.progress?.let { trend -> ProgressCard(trend = trend, s = s) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnfasSecondaryButton(
                text = s.therapy.editCase,
                onClick = component::onEditCaseForm,
                modifier = Modifier.weight(1f),
            )
            if (state.canCloseCase) {
                AnfasSecondaryButton(
                    text = s.therapy.closeCase,
                    onClick = component::onCloseCaseRequested,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.canOpenNewCase) {
            // Only reachable once the case above is closed -- see TherapyState.canOpenNewCase.
            AnfasPrimaryButton(
                text = s.therapy.openCase,
                onClick = component::onOpenCaseForm,
                icon = AnfasIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Header(member: Member, state: TherapyState, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    val detail = state.detail
    Row(verticalAlignment = Alignment.Top) {
        AnfasAvatar(initials = initialsOf(member.fullName), size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.fullName,
                    style = AnfasTheme.textStyles.headlineSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                detail?.let {
                    Spacer(Modifier.width(8.dp))
                    val active = it.case.status == CaseStatus.ACTIVE
                    AnfasStatusChip(
                        label = if (active) s.therapy.statusActive else s.therapy.statusClosed,
                        tone = if (active) ChipTone.Recovery else ChipTone.Neutral,
                    )
                }
            }
            if (detail != null) {
                Text(
                    text = detail.case.condition,
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = buildList {
                        add(s.therapy.caseOpenedOn(s.formatLong(detail.case.openedOn)))
                        detail.therapistName(detail.case.therapistStaffId)?.let {
                            add(s.therapy.therapistPrefix(it))
                        }
                        detail.case.referredBy?.let { add(s.therapy.referredByPrefix(it)) }
                    }.joinToString(" · "),
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (detail.case.status == CaseStatus.CLOSED) {
                    Text(
                        text = s.therapy.closedCaseMessage,
                        style = AnfasTheme.textStyles.labelCaps,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContraindicationsCard(text: String, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(scheme.error.copy(alpha = 0.05f))
            .border(1.dp, scheme.error.copy(alpha = 0.30f), AnfasShapes.base)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = AnfasIcons.Warning,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = s.therapy.contraindicationsTitle,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.error,
            )
        }
        Text(text = text, style = AnfasTheme.textStyles.bodyMedium, color = scheme.onSurface)
    }
}

@Composable
private fun IntakeCard(detail: TherapyCaseDetail, s: AppStrings) {
    val hasOnset = detail.case.onset.isNotBlank()
    val hasMechanism = detail.case.mechanism.isNotBlank()
    if (!hasOnset && !hasMechanism) return

    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = s.therapy.intakeTitle,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
            if (hasOnset) IntakeRow(label = s.therapy.fieldOnset, value = detail.case.onset)
            if (hasMechanism) {
                IntakeRow(label = s.therapy.fieldMechanism, value = detail.case.mechanism)
            }
        }
    }
}

@Composable
private fun IntakeRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = AnfasTheme.textStyles.labelCaps, color = scheme.onSurfaceVariant)
        Text(text = value, style = AnfasTheme.textStyles.bodyMedium, color = scheme.onSurface)
    }
}

@Composable
private fun SessionsCard(state: TherapyState, component: TherapyComponent, s: AppStrings) {
    val detail = state.detail ?: return
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.therapy.sessionsTitle,
                    style = AnfasTheme.textStyles.labelCaps,
                    color = scheme.onSurfaceVariant,
                )
                if (state.canLogSession) {
                    AnfasTextAction(text = s.therapy.logSession, onClick = component::onLogSession)
                }
            }
            if (detail.sessions.isEmpty()) {
                Text(
                    text = s.therapy.noSessionsYet,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                detail.sessions.forEachIndexed { index, session ->
                    SessionRow(
                        session = session,
                        therapistName = detail.therapistName(session.therapistStaffId),
                        s = s,
                    )
                    if (index != detail.sessions.lastIndex) AnfasTableDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: TherapySession, therapistName: String?, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    val zone = TimeZone.currentSystemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = s.formatLong(session.at.toLocalDateTime(zone).date),
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = (therapistName ?: s.therapy.unassignedTherapist) + " · " +
                    s.therapy.durationMinutes(session.durationMinutes),
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
        }
        if (session.treatmentTypes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                session.treatmentTypes.forEach { type -> TreatmentChip(text = type.label(s)) }
            }
        }
        if (session.notes.isNotBlank()) {
            Text(
                text = session.notes,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        session.painScore?.let {
            Text(
                text = "${s.therapy.painScoreLabel}: ${it.toString().asLtrIsolate()}/10",
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TreatmentChip(text: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = AnfasTheme.textStyles.labelCaps, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun ProgressCard(trend: PainScoreTrend, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = s.therapy.progressTitle,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.therapy.painScoreLabel,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurface,
                )
                Text(
                    text = s.therapy.painScoreTrend(trend.first, trend.latest).asLtrIsolate(),
                    style = AnfasTheme.textStyles.dataMono,
                    color = if (trend.isImproving) scheme.primary else scheme.onSurface,
                )
            }
            PainScoreSparkline(scores = trend.scores)
        }
    }
}

/**
 * A minimal line chart of real recorded pain scores, not a fabricated visual — the export draws
 * one, and every point plotted here is a number a therapist actually entered.
 */
@Composable
private fun PainScoreSparkline(scores: List<Int>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
        if (scores.size < 2) return@Canvas
        val maxScore = 10f
        val stepX = size.width / (scores.size - 1)
        val points = scores.mapIndexed { index, score ->
            Offset(x = index * stepX, y = size.height * (1f - score / maxScore))
        }
        for (i in 0 until points.lastIndex) {
            drawLine(color = color, start = points[i], end = points[i + 1], strokeWidth = 4f)
        }
    }
}

internal fun TherapyNotice.render(s: AppStrings): String = when (this) {
    is TherapyNotice.CaseOpened -> s.therapy.caseOpened(condition)
    TherapyNotice.CaseClosed -> s.therapy.caseClosed
    TherapyNotice.SessionLogged -> s.therapy.sessionLogged
    TherapyNotice.AlreadyOpen -> s.therapy.alreadyOpenMessage
    is TherapyNotice.Failed -> message
}
