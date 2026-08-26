package com.anfas.feature.dashboard

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.CheckInRepository
import com.anfas.core.data.MemberRepository
import com.anfas.core.data.ReminderRepository
import com.anfas.core.data.SubscriptionRepository
import com.anfas.core.model.ExpiringTerm
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipStatus
import com.anfas.core.model.RenewalQueue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Reads across three seams — members, subscriptions and reminders — and combines them.
 *
 * That is not a feature reaching into other features: all three are `:core:data` interfaces,
 * which is exactly what that module is for. `:feature:dashboard` has no dependency on
 * `:feature:members` or `:feature:subscriptions`.
 *
 * Combined into one state rather than three independent observers because the tiles have to
 * agree: a count of four expiring memberships beside a queue showing three is worse than a
 * dashboard that takes an extra frame to settle.
 */
class DashboardComponent(
    componentContext: ComponentContext,
    members: MemberRepository,
    subscriptions: SubscriptionRepository,
    reminders: ReminderRepository,
    checkIns: CheckInRepository,
    dispatchers: AppDispatchers,
    private val onMemberClicked: (MemberId) -> Unit,
    private val onOpenReminders: () -> Unit,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Dashboard"))

    // Resolved once: a dashboard session does not span midnight, and re-reading the date on
    // every emission would rebuild the day query on each keystroke elsewhere in the app.
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    val state: StateFlow<DashboardState> = combine(
        members.observeMembers(),
        subscriptions.observeCurrentTerms(),
        reminders.observeCounts(),
        checkIns.observeDaySummary(today),
    ) { membersResult, termsResult, countsResult, checkInResult ->
        // A failure in any one seam fails the whole screen. A dashboard showing three tiles and
        // silently omitting a fourth is worse than one saying it could not load: the missing
        // number reads as zero, and zero here means "nothing to chase".
        val firstError = listOf<AppResult<*>>(
            membersResult,
            termsResult,
            countsResult,
            checkInResult,
        )
            .filterIsInstance<AppResult.Failure>()
            .firstOrNull()
        if (firstError != null) {
            return@combine DashboardState(isLoading = false, error = firstError.error.message)
        }

        val allMembers = (membersResult as AppResult.Success).value
        val terms = (termsResult as AppResult.Success).value
        val counts = (countsResult as AppResult.Success).value
        val checkInSummary = (checkInResult as AppResult.Success).value

        val queue = RenewalQueue.needingAttention(terms, today)

        DashboardState(
            isLoading = false,
            activeMembers = allMembers.count { it.status == MembershipStatus.ACTIVE },
            totalMembers = allMembers.size,
            needingRenewal = queue.size,
            failedReminders = counts.failed,
            checkedInToday = checkInSummary.granted,
            turnedAwayToday = checkInSummary.denied,
            renewalQueue = queue.toRows(allMembers),
            error = null,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = DashboardState(),
    )

    fun onMemberSelected(id: MemberId) = onMemberClicked(id)

    /**
     * Both reminder tiles lead to the same screen -- the queue's own tabs are where you choose
     * between what is queued and what failed. Kept as two named methods rather than one because
     * the tiles differ in *when* they are offered, not in where they go.
     */
    fun onOpenRemindersClicked() = onOpenReminders()

    fun onFailedRemindersClicked() = onOpenReminders()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Drops terms whose member is gone. A queue row with no member cannot be rendered or acted on,
 * and a dangling term is a data problem to fix elsewhere rather than a row to show staff.
 */
private fun List<ExpiringTerm>.toRows(members: List<Member>): List<RenewalQueueRow> {
    val byId = members.associateBy { it.id }
    return mapNotNull { expiring ->
        byId[expiring.term.memberId]?.let { RenewalQueueRow(member = it, expiring = expiring) }
    }
}
