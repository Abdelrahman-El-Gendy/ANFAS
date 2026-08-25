package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatePickerBoundaryTest {

    @Test
    fun `null round-trips as null`() {
        assertNull(DatePickerBoundary.toEpochMillis(null))
        assertNull(DatePickerBoundary.toLocalDate(null))
    }

    @Test
    fun `a date round-trips unchanged`() {
        listOf("2026-09-01", "2026-01-01", "2026-12-31", "1970-01-01").forEach { iso ->
            val date = LocalDate.parse(iso)
            val millis = DatePickerBoundary.toEpochMillis(date)
            assertEquals(date, DatePickerBoundary.toLocalDate(millis), iso)
        }
    }

    /** The picker hands back UTC midnight, so that exact value must map to the same day. */
    @Test
    fun `utc midnight maps to its own day`() {
        // 2026-09-01T00:00:00Z
        val millis = LocalDate.parse("2026-09-01").toEpochDays() * 86_400_000L
        assertEquals(LocalDate.parse("2026-09-01"), DatePickerBoundary.toLocalDate(millis))
    }

    /**
     * Truncating division would put any pre-epoch selection a day late. Not a date this app
     * expects, but the helper is shared and the next caller should not inherit the trap.
     */
    @Test
    fun `a pre-epoch date does not drift`() {
        val date = LocalDate.parse("1969-07-20")
        assertEquals(date, DatePickerBoundary.toLocalDate(DatePickerBoundary.toEpochMillis(date)))
    }
}
