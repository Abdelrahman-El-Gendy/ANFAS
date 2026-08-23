package com.anfas.feature.dashboard

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.strings

/**
 * The reception dashboard: what needs doing at the desk right now.
 *
 * Departures from the export's `reception-dashboard`, every one because the number does not exist
 * rather than because the design was ignored. A dashboard is the screen where an invented figure
 * is most likely to be believed and acted on, so nothing here is fabricated:
 *  - **No percentage deltas** ("2.4% ▲"). Comparing against last week needs history the app does
 *    not keep — there are no snapshots, only current state — and a trend arrow pointing at
 *    nothing is worse than no arrow.
 *  - **No SEND action on a queue row.** The WhatsApp send job does not exist. The row opens the
 *    member instead, which is where a renewal is actually taken.
 *
 * `staff-dashboard` is deliberately not built at all: its capacity load, equipment issues,
 * cleanliness timer and upcoming classes are backed by nothing, and four fabricated figures is
 * not a screen worth shipping.
 */
@Composable
fun DashboardScreen(component: DashboardComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(title = s.dashboard.title, subtitle = s.dashboard.subtitle)

        when {
            state.isLoading -> Unit

            state.error != null -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.dashboard.loadFailedTitle,
                message = state.error.orEmpty(),
            )

            else -> {
                Tiles(state, component, s)

                if (state.allClear) {
                    AnfasEmptyState(
                        icon = AnfasIcons.CheckCircle,
                        title = s.dashboard.allClearTitle,
                        message = s.dashboard.allClearMessage,
                    )
                } else {
                    RenewalQueueCard(state, component, s)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(state: DashboardState, component: DashboardComponent, s: AppStrings) {
    // FlowRow so three tiles sit in a row on a tablet and wrap to one column on a phone, rather
    // than being squeezed to a width where the figure itself truncates.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Tile(
            label = s.dashboard.activeMembers,
            value = state.activeMembers.toString(),
            hint = s.dashboard.ofTotal(state.totalMembers),
            tone = ChipTone.Neutral,
        )
        Tile(
            label = s.checkIn.totalToday,
            value = state.checkedInToday.toString(),
            hint = if (state.turnedAwayToday > 0) {
                s.dashboard.turnedAwayHint(state.turnedAwayToday)
            } else {
                s.checkIn.logTitle
            },
            tone = ChipTone.Neutral,
        )
        Tile(
            label = s.dashboard.needingRenewal,
            value = state.needingRenewal.toString(),
            hint = if (state.needingRenewal == 0) {
                s.dashboard.nothingToChase
            } else {
                s.dashboard.needingRenewalHint
            },
            // Amber only when there is something to act on. A permanently-coloured tile stops
            // being read after a week.
            tone = if (state.needingRenewal > 0) ChipTone.Critical else ChipTone.Positive,
        )
        Tile(
            label = s.dashboard.failedReminders,
            value = state.failedReminders.toString(),
            hint = if (state.failedReminders == 0) {
                s.dashboard.nothingToChase
            } else {
                s.dashboard.failedRemindersHint
            },
            tone = if (state.failedReminders > 0) ChipTone.Critical else ChipTone.Positive,
            onClick = component::onFailedRemindersClicked.takeIf { state.failedReminders > 0 },
        )
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    hint: String,
    tone: ChipTone,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(
        modifier = Modifier
            .widthIn(min = TILE_MIN_WIDTH)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )
            Text(
                // dataMonoLtr: a count is Latin digits whatever the layout direction.
                text = value,
                style = AnfasTheme.textStyles.headlineLarge,
                color = if (tone == ChipTone.Critical) scheme.error else scheme.onSurface,
            )
            Text(
                text = hint,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenewalQueueCard(state: DashboardState, component: DashboardComponent, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = s.dashboard.renewalQueueTitle,
            style = AnfasTheme.textStyles.labelCaps,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        AnfasTableDivider()

        if (state.renewalQueue.isEmpty()) {
            Text(
                text = s.dashboard.renewalQueueEmpty,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@AnfasCard
        }

        // Bounded height: this card sits inside a verticalScroll, so a LazyColumn needs a
        // ceiling or it measures with infinite height and crashes.
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = QUEUE_MAX_HEIGHT)) {
            items(items = state.renewalQueue, key = { it.member.id.value }) { row ->
                QueueRow(row = row, onClick = { component.onMemberSelected(row.member.id) }, s = s)
                AnfasTableDivider()
            }
        }
    }
}

@Composable
private fun QueueRow(row: RenewalQueueRow, onClick: () -> Unit, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.member.fullName,
                style = AnfasTheme.textStyles.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Localised date, so no forced LTR — it contains a translated month name.
                text = s.formatLong(row.expiring.term.endsOn),
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        AnfasStatusChip(
            label = if (row.expiring.hasExpired) {
                s.dashboard.expired
            } else {
                s.dashboard.inDays(row.expiring.progress.remainingDays - 1)
            },
            tone = if (row.expiring.hasExpired) ChipTone.Critical else ChipTone.Neutral,
        )
    }
}

/** Below this a tile's figure starts truncating, so they wrap to one column instead. */
private val TILE_MIN_WIDTH = 150.dp

/** Enough to show the work without the queue pushing the tiles off the top of the screen. */
private val QUEUE_MAX_HEIGHT = 420.dp
