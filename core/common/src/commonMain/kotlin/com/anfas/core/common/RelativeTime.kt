package com.anfas.core.common

import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Relative timestamp labels shared by every staff worklist — the members directory's
 * "Last check-in" and the reminder queue's "Scheduled" column read the same way:
 * "Today 08:15", "Yesterday 17:30", "2 days ago", then an absolute date once it is a week old.
 *
 * This lives in :core:common rather than in a feature because two features need it and
 * features must never depend on each other.
 *
 * [now] and [zone] are parameters, not read from the environment, so callers are testable
 * without a globally installed clock and a test cannot pass merely because it runs in the
 * author's timezone.
 */
object RelativeTime {

    /**
     * @param separator between the day word and the time. The export uses ", " in the members
     *   table and " " in the reminder queue, so it is a parameter rather than a silent choice.
     * @param nullPlaceholder what to render when there is no timestamp at all — which is
     *   different from a timestamp of zero.
     */
    fun format(
        instant: Instant?,
        now: Instant,
        zone: TimeZone,
        separator: String = ", ",
        nullPlaceholder: String = "—",
    ): String {
        if (instant == null) return nullPlaceholder

        val then = instant.toLocalDateTime(zone)
        val daysAgo = now.toLocalDateTime(zone).date.toEpochDays() - then.date.toEpochDays()
        val time = "${then.hour.pad2()}:${then.minute.pad2()}"

        return when {
            // A future stamp means clock skew between the device and the server. Showing
            // "-1 days ago" would just look broken to staff.
            daysAgo <= 0L -> "Today$separator$time"

            daysAgo == 1L -> "Yesterday$separator$time"

            daysAgo < 7L -> "$daysAgo days ago"

            else -> "${then.date.month.shortName()} ${then.date.day}, ${then.date.year}"
        }
    }
}

private fun Int.pad2(): String = if (this < 10) "0$this" else toString()

/** Indexed by [Enum.ordinal]; the enum is declared January..December in order. */
private val MonthAbbreviations = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private fun Month.shortName(): String = MonthAbbreviations[ordinal]
