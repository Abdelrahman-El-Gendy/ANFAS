package com.anfas.core.common

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A fixed zone and a fixed "now" — these must not depend on where the test runs.
 *
 * Asserts on [RelativeStamp] cases rather than on rendered prose, which is what made this
 * classifier worth extracting: the rules are testable independently of language.
 */
class RelativeTimeTest {

    private val zone = TimeZone.UTC
    private val now = at(2026, 8, 21, 12, 0)

    @Test
    fun `no timestamp classifies as None`() {
        assertEquals(RelativeStamp.None, classify(null))
    }

    @Test
    fun `same calendar day is Today and carries the time`() {
        assertEquals(RelativeStamp.Today(8, 15), classify(at(2026, 8, 21, 8, 15)))
    }

    @Test
    fun `previous calendar day is Yesterday even when less than 24 hours ago`() {
        // 23 hours before now, but a different calendar date — the rule follows the date.
        assertEquals(RelativeStamp.Yesterday(13, 0), classify(at(2026, 8, 20, 13, 0)))
    }

    @Test
    fun `two to six days ago counts days`() {
        assertEquals(RelativeStamp.DaysAgo(2), classify(at(2026, 8, 19, 9, 0)))
        assertEquals(RelativeStamp.DaysAgo(6), classify(at(2026, 8, 15, 9, 0)))
    }

    @Test
    fun `a week or older becomes an absolute date`() {
        assertEquals(RelativeStamp.On(LocalDate(2026, 8, 14)), classify(at(2026, 8, 14, 9, 0)))
        assertEquals(RelativeStamp.On(LocalDate(2023, 10, 12)), classify(at(2023, 10, 12, 9, 0)))
    }

    @Test
    fun `a future timestamp from clock skew is Today rather than negative days`() {
        assertEquals(RelativeStamp.Today(15, 0), classify(at(2026, 8, 22, 15, 0)))
    }

    private fun classify(instant: kotlin.time.Instant?) = RelativeTime.classify(instant, now, zone)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime(y, m, d, h, min).toInstant(zone)
}
