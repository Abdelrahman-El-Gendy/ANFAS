package com.anfas.feature.dashboard

import com.anfas.core.model.ExpiringTerm
import com.anfas.core.model.Member

/**
 * The reception dashboard.
 *
 * Every number here is derived from something the app actually stores. What the export also shows
 * and this does not is listed in the screen's KDoc — a dashboard is the one screen where an
 * invented figure is most likely to be believed and acted on.
 */
data class DashboardState(
    val isLoading: Boolean = true,
    val activeMembers: Int = 0,
    val totalMembers: Int = 0,
    /** Memberships expired or expiring within a week. The renewal work queue's size. */
    val needingRenewal: Int = 0,
    val failedReminders: Int = 0,
    /** Granted entries today. Real now that check-in records them. */
    val checkedInToday: Int = 0,
    /** Attempts refused today — expired, suspended or paused memberships. */
    val turnedAwayToday: Int = 0,
    /** Soonest to expire first, already-lapsed ones ahead of those merely expiring. */
    val renewalQueue: List<RenewalQueueRow> = emptyList(),
    val error: String? = null,
) {
    /** Nothing to chase and nothing failing: worth saying so rather than showing empty tables. */
    val allClear: Boolean
        get() = !isLoading && error == null && needingRenewal == 0 && failedReminders == 0
}

/**
 * A queue row pairs the term with the member it belongs to, resolved here rather than in the UI
 * so the screen never has to look a member up mid-render.
 */
data class RenewalQueueRow(val member: Member, val expiring: ExpiringTerm)
