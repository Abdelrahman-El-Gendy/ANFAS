package com.anfas.feature.checkin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasSearchField
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings
import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInOutcome
import com.anfas.core.model.Member

/**
 * The reception desk.
 *
 * Departures from the export's `live-checkin-log`, each because the figure does not exist:
 *  - **No "Current Capacity".** Knowing who is *inside* needs check-out, and there is none — the
 *    app records entries, not occupancy. A percentage derived from entries alone would climb all
 *    day and read as a full gym by closing time.
 *  - **No Successful/Denied tabs.** With one day's entries on screen the outcome chip already
 *    separates them, and a filter over a list this short is a control that earns nothing.
 *  - **No "Load More".** One local day is the whole query; history belongs on a member's profile.
 *  - **No barcode or tag scanning.** There is no hardware integration, so entry is a name lookup.
 *    "Unknown ID" cannot happen here, which is why [CheckIn.memberId] is nullable but never null
 *    today — the column is ready for a scanner that reports an unrecognised tag.
 */
@Composable
fun CheckInScreen(component: CheckInComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(title = s.checkIn.title, subtitle = s.checkIn.subtitle)

        Summary(state, s)

        AnfasSearchField(
            value = state.query,
            onValueChange = component::onQueryChanged,
            onClear = component::onClearSearch,
            placeholder = s.checkIn.searchPlaceholder,
            clearContentDescription = s.common.clearSearch,
            modifier = Modifier.fillMaxWidth(),
        )

        state.notice?.let { notice ->
            Notice(notice = notice, onDismiss = component::onNoticeShown, s = s)
        }

        when (val search = state.search) {
            CheckInSearch.Idle -> Unit

            is CheckInSearch.NoMatches -> Text(
                text = s.checkIn.noMatches(search.query),
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is CheckInSearch.Results -> SearchResults(search.members, state, component, s)
        }

        if (state.error != null) {
            AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.checkIn.loadFailedTitle,
                message = state.error.orEmpty(),
            )
        } else {
            // weight(1f), so the log claims whatever is left below the summary and the search
            // rather than being squeezed to a sliver. Without it the LazyColumn was the last
            // child of a non-scrolling Column and got about twelve pixels — one clipped row.
            TodaysLog(state, s, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(state: CheckInState, s: AppStrings) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Figure(s.checkIn.totalToday, state.summary.granted.toString(), critical = false)
        Figure(
            label = s.checkIn.deniedToday,
            value = state.summary.denied.toString(),
            // Coloured only when it is non-zero: a permanently red tile stops being read.
            critical = state.summary.denied > 0,
        )
        Figure(
            label = s.checkIn.peakHour,
            value = state.summary.peakHour?.let { s.checkIn.hourLabel(it) }
                ?: s.checkIn.noPeakYet,
            critical = false,
        )
    }
}

@Composable
private fun Figure(label: String, value: String, critical: Boolean) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.widthIn(min = FIGURE_MIN_WIDTH)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = AnfasTheme.textStyles.headlineSmall,
                color = if (critical) scheme.error else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchResults(
    found: List<Member>,
    state: CheckInState,
    component: CheckInComponent,
    s: AppStrings,
) {
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        // Bounded: the results sit above the log, and an unbounded list would push it off screen.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = RESULTS_MAX_HEIGHT)) {
            items(items = found, key = { it.id.value }) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { component.onMemberSelected(member.id) },
                    ) {
                        Text(
                            text = member.fullName,
                            style = AnfasTheme.textStyles.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = s.members.idPrefix(member.membershipNumber),
                            style = AnfasTheme.textStyles.dataMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnfasPrimaryButton(
                        text = if (state.recordingId == member.id.value) {
                            s.checkIn.recording
                        } else {
                            s.checkIn.action
                        },
                        onClick = { component.onCheckIn(member.id) },
                        // Disabled for every row while one is recording, so a double tap cannot
                        // log two people at once.
                        enabled = state.recordingId == null,
                    )
                }
                AnfasTableDivider()
            }
        }
    }
}

@Composable
private fun TodaysLog(state: CheckInState, s: AppStrings, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = modifier) {
        Text(
            text = s.checkIn.logTitle,
            style = AnfasTheme.textStyles.labelCaps,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        AnfasTableDivider()

        if (state.log.isEmpty()) {
            Text(
                text = s.checkIn.logEmpty,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@AnfasCard
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(items = state.log, key = { it.id.value }) { entry ->
                LogRow(entry, s)
                AnfasTableDivider()
            }
        }
    }
}

@Composable
private fun LogRow(entry: CheckIn, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.memberName,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Latin-only, so an LTR run keeps the number intact under an Arabic layout.
                text = entry.membershipNumber,
                style = AnfasTheme.textStyles.dataMonoLtr,
                color = scheme.onSurfaceVariant,
            )
        }
        AnfasStatusChip(
            label = entry.outcome.label(s),
            tone = if (entry.wasGranted) ChipTone.Positive else ChipTone.Critical,
        )
    }
}

@Composable
private fun Notice(notice: CheckInNotice, onDismiss: () -> Unit, s: AppStrings) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when (notice) {
                is CheckInNotice.Recorded -> if (notice.checkIn.wasGranted) {
                    s.checkIn.grantedNotice(notice.checkIn.memberName)
                } else {
                    // Names the reason, because what the desk does next depends on it.
                    s.checkIn.deniedNotice(notice.checkIn.memberName) + " " +
                        notice.checkIn.outcome.label(s)
                }

                is CheckInNotice.Failed -> notice.message
            },
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AnfasTextAction(text = s.common.dismiss, onClick = onDismiss)
    }
}

/** Each denial names what the desk does next, not merely that it failed. */
private fun CheckInOutcome.label(s: AppStrings): String = when (this) {
    CheckInOutcome.GRANTED -> s.checkIn.granted
    CheckInOutcome.EXPIRED -> s.checkIn.outcomeExpired
    CheckInOutcome.SUSPENDED -> s.checkIn.outcomeSuspended
    CheckInOutcome.PAUSED -> s.checkIn.outcomePaused
    CheckInOutcome.NO_MEMBERSHIP -> s.checkIn.outcomeNoMembership
}

private val FIGURE_MIN_WIDTH = 130.dp
private val RESULTS_MAX_HEIGHT = 260.dp
