package com.anfas.core.common

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fixed zone and a fixed "now" — these must not depend on where the test runs.
 */
class RelativeTimeTest {

    private val zone = TimeZone.UTC
    private val now = at(2026, 8, 21, 12, 0)

    @Test
    fun `never checked in renders as a dash`() {
        assertEquals("—", RelativeTime.format(null, now, zone))
    }

    @Test
    fun `same day shows the time in 24 hour form`() {
        assertEquals("Today, 08:15", RelativeTime.format(at(2026, 8, 21, 8, 15), now, zone))
    }

    @Test
    fun `single digit minutes are zero padded`() {
        assertEquals("Today, 08:05", RelativeTime.format(at(2026, 8, 21, 8, 5), now, zone))
    }

    @Test
    fun `previous calendar day is Yesterday even when less than 24 hours ago`() {
        // 23 hours before `now`, but a different calendar date — the label follows the date.
        assertEquals("Yesterday, 13:00", RelativeTime.format(at(2026, 8, 20, 13, 0), now, zone))
    }

    @Test
    fun `two to six days ago counts days`() {
        assertEquals("2 days ago", RelativeTime.format(at(2026, 8, 19, 9, 0), now, zone))
        assertEquals("6 days ago", RelativeTime.format(at(2026, 8, 15, 9, 0), now, zone))
    }

    @Test
    fun `a week or older switches to an absolute date`() {
        assertEquals("Aug 14, 2026", RelativeTime.format(at(2026, 8, 14, 9, 0), now, zone))
        assertEquals("Oct 12, 2023", RelativeTime.format(at(2023, 10, 12, 9, 0), now, zone))
    }

    @Test
    fun `a future timestamp from clock skew is shown as today rather than negative days`() {
        assertEquals("Today, 15:00", RelativeTime.format(at(2026, 8, 22, 15, 0), now, zone))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime(y, m, d, h, min).toInstant(zone)
}
