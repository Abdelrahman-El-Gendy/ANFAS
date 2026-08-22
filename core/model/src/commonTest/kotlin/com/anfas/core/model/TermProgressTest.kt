package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TermProgressTest {

    private val start = LocalDate(2024, 1, 1)
    private val end = LocalDate(2024, 12, 31)

    private fun at(date: LocalDate) = TermProgress.of(start, end, date)

    @Test
    fun `mid term reports elapsed and remaining that add up`() {
        val progress = at(LocalDate(2024, 7, 1))

        assertEquals(365, progress.totalDays)
        assertEquals(182, progress.elapsedDays)
        assertEquals(184, progress.remainingDays)
        assertEquals(TermProgress.State.Active, progress.state)
    }

    @Test
    fun `a term that has not begun reports zero progress`() {
        val progress = at(LocalDate(2023, 12, 25))

        assertEquals(TermProgress.State.NotStarted, progress.state)
        assertEquals(0, progress.elapsedDays)
        assertEquals(0f, progress.fraction)
    }

    /**
     * The boundary staff care about most: a membership valid until close of business today must
     * not read "expired", so the final day counts as one day remaining rather than zero.
     */
    @Test
    fun `the last day of a term still has one day remaining`() {
        val progress = at(end)

        assertEquals(1, progress.remainingDays)
        assertEquals(TermProgress.State.ExpiringSoon, progress.state)
    }

    @Test
    fun `the day after the end date is expired`() {
        val progress = at(LocalDate(2025, 1, 1))

        assertEquals(TermProgress.State.Expired, progress.state)
        assertEquals(0, progress.remainingDays)
        assertEquals(1f, progress.fraction)
    }

    @Test
    fun `expiring soon starts a week out`() {
        assertEquals(TermProgress.State.ExpiringSoon, at(LocalDate(2024, 12, 25)).state)
        assertEquals(TermProgress.State.Active, at(LocalDate(2024, 12, 24)).state)
    }

    /** No division by zero, and 100% is the honest reading on the one day the term covers. */
    @Test
    fun `a single day term is fully elapsed rather than undefined`() {
        val day = LocalDate(2024, 5, 5)
        val progress = TermProgress.of(day, day, day)

        assertEquals(0, progress.totalDays)
        assertEquals(1f, progress.fraction)
        assertEquals(1, progress.remainingDays)
    }

    /**
     * Corrupt data, not a crash. Reversed dates must not produce "expires in -40 days" — the row
     * reads as already served instead.
     */
    @Test
    fun `a term whose dates are reversed is reported as served`() {
        val progress = TermProgress.of(
            startsOn = LocalDate(2024, 6, 1),
            endsOn = LocalDate(2024, 5, 1),
            today = LocalDate(2024, 7, 1),
        )

        assertEquals(0, progress.totalDays)
        assertEquals(0, progress.remainingDays)
        assertEquals(TermProgress.State.Expired, progress.state)
    }

    /**
     * The bar is labelled "Time remaining", so a term that starts today must read as full. This
     * caught a real inversion: the bar was fed [TermProgress.fraction] and showed empty on the
     * day a member paid.
     */
    @Test
    fun `remaining is the inverse of elapsed`() {
        val fresh = at(start)
        assertEquals(1f, fresh.remainingFraction)
        assertEquals(100, fresh.remainingPercent)

        val done = at(LocalDate(2025, 6, 1))
        assertEquals(0f, done.remainingFraction)
        assertEquals(0, done.remainingPercent)
    }
}
