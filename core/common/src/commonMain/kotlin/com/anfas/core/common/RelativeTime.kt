package com.anfas.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * How long ago a timestamp was, as *structure* rather than text.
 *
 * This used to return a formatted English string ("Today, 08:15", "2 days ago"), which made every
 * staff worklist untranslatable and baked a month-name array into a module that has no business
 * knowing about language. Rendering now happens in `:core:i18n`; this module only classifies.
 *
 * The tests are better for it too — they assert on cases instead of on prose.
 */
sealed interface RelativeStamp {
    data class Today(val hour: Int, val minute: Int) : RelativeStamp
    data class Yesterday(val hour: Int, val minute: Int) : RelativeStamp
    data class DaysAgo(val days: Int) : RelativeStamp
    data class On(val date: LocalDate) : RelativeStamp

    /** No timestamp at all, which is different from a timestamp of zero. */
    data object None : RelativeStamp
}

object RelativeTime {

    /**
     * [now] and [zone] are parameters, not read from the environment, so callers are testable
     * without a globally installed clock and a test cannot pass merely because it happens to run
     * in the author's timezone.
     */
    fun classify(instant: Instant?, now: Instant, zone: TimeZone): RelativeStamp {
        if (instant == null) return RelativeStamp.None

        val then = instant.toLocalDateTime(zone)
        val daysAgo = now.toLocalDateTime(zone).date.toEpochDays() - then.date.toEpochDays()

        return when {
            // A future stamp means clock skew between device and server. "-1 days ago" would just
            // look broken to staff.
            daysAgo <= 0L -> RelativeStamp.Today(then.hour, then.minute)

            daysAgo == 1L -> RelativeStamp.Yesterday(then.hour, then.minute)

            daysAgo < 7L -> RelativeStamp.DaysAgo(daysAgo.toInt())

            else -> RelativeStamp.On(then.date)
        }
    }
}
