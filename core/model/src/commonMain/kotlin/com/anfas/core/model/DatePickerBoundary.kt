package com.anfas.core.model

import kotlinx.datetime.LocalDate

/**
 * The boundary between `AnfasDateField` and this app's date type.
 *
 * The Material 3 picker stores its selection as **UTC start-of-day epoch milliseconds** and
 * `:core:designsystem` deliberately has no `kotlinx.datetime` dependency, so the conversion has to
 * happen somewhere shared — here, rather than copied into every screen with a date on it. Two
 * copies of this arithmetic is how one screen ends up a day off in a timezone behind UTC.
 *
 * **Always UTC, never the device zone.** A picker selection is a calendar date a human pointed at,
 * not an instant: converting it through `TimeZone.currentSystemDefault()` would turn
 * "1 September" into 31 August for anyone west of Greenwich. Same reasoning as
 * `IntakeValidator.parseDate` returning a bare `LocalDate`.
 */
object DatePickerBoundary {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun toEpochMillis(date: LocalDate?): Long? = date?.let { it.toEpochDays() * MILLIS_PER_DAY }

    fun toLocalDate(epochMillis: Long?): LocalDate? = epochMillis?.let {
        // floorDiv, not `/`: integer division truncates toward zero, so any pre-1970 selection
        // would land a day late. Dates before 1970 are not expected here, but a conversion that
        // is only correct for recent dates is a trap for whoever reuses this next.
        LocalDate.fromEpochDays(it.floorDiv(MILLIS_PER_DAY))
    }
}
