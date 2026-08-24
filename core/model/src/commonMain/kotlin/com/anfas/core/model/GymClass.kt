package com.anfas.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One class in the gym's weekly timetable.
 *
 * A **recurring weekly slot**, not a dated occurrence, because that is what a gym timetable is
 * and what both designed screens show: `weekly-class-schedule` repeats HIIT across four days of
 * one week with no notion of which week, and `class-schedule` asks only "what is on today".
 * Storing dated instances would mean generating rows forward forever and deciding how far.
 *
 * The cost is stated rather than hidden: there is **no way to cancel a single date** or to say
 * "no classes on the public holiday". That needs an exceptions table, and it should be added when
 * someone asks for it rather than guessed at now.
 *
 * [instructorStaffId] references a `staff` row and is resolved at read time rather than copied.
 * The opposite choice from [CheckIn], and deliberately so: a check-in is a historical fact that
 * must survive a rename, whereas a timetable is forward-looking — renaming a coach should change
 * next week's schedule, not leave the old name on it.
 */
data class GymClass(
    val id: GymClassId,
    val name: String,
    val category: ClassCategory,
    /** Free text: the design shows "Studio A", "Weight Room", "Zen Studio". */
    val room: String,
    /**
     * How many people fit. The **limit**, not an occupancy — see [ClassOccupancy] for why the
     * design's "14/20" is not rendered.
     */
    val capacity: Int,
    /** Null for a slot whose coach is not decided yet, which is a real state on a timetable. */
    val instructorStaffId: String? = null,
    val dayOfWeek: DayOfWeek,
    val startsAt: LocalTime,
    val durationMinutes: Int,
) {
    /**
     * Exclusive end. Computed rather than stored so the two can never disagree, and clamped to
     * the end of the day: a class cannot run past midnight, and one entered as 23:30 + 90min is a
     * typo rather than an instruction to wrap around to 01:00 and sort itself first.
     */
    val endsAt: LocalTime
        get() {
            val end = startsAt.toMinuteOfDay() + durationMinutes
            return minuteOfDayToTime(minOf(end, MINUTES_PER_DAY - 1))
        }

    val startMinute: Int get() = startsAt.toMinuteOfDay()

    /** Exclusive, and always greater than [startMinute] — see the clamping note on [endsAt]. */
    val endMinute: Int get() = maxOf(startMinute + 1, endsAt.toMinuteOfDay())

    fun overlaps(other: GymClass): Boolean =
        dayOfWeek == other.dayOfWeek && startMinute < other.endMinute &&
            other.startMinute < endMinute

    companion object {
        /**
         * The shortest and longest a class may be. A zero-length class would be invisible on the
         * grid, and a 24-hour one would paint over every other block.
         */
        const val MIN_DURATION_MINUTES: Int = 5
        const val MAX_DURATION_MINUTES: Int = 8 * 60

        /** Below one nobody can attend; the upper bound is a typo guard, not a gym rule. */
        const val MIN_CAPACITY: Int = 1
        const val MAX_CAPACITY: Int = 500
    }
}

/**
 * The three groupings the design's own legend names — General, Women's Only, Recovery — and no
 * others. The mobile row renders this as the class's subtitle ("Conditioning" under "HIIT
 * Foundation") and both screens colour the block by it.
 *
 * Not free text, because the colour has to come from somewhere and a palette keyed on arbitrary
 * strings ends up assigning by hash. Adding a fourth means choosing a fourth colour deliberately.
 */
enum class ClassCategory {
    /** The default. Amber, the app's primary. */
    GENERAL,

    /**
     * Women's-only programming, which in this gym is a real scheduling constraint rather than a
     * label. Rose in the export's prose palette.
     */
    WOMENS_ONLY,

    /** Mobility, yoga, ice bath. Sage in the export's prose palette. */
    RECOVERY,
}

/**
 * Why the design's "14/20" and "Waitlist: 3" are **not** rendered anywhere.
 *
 * Both need a booking system, and there is none: members do not sign in to this app at all
 * (`Role.Member` grants nothing), so nothing in the product can know that fourteen of twenty
 * places are taken. The numbers on the export are illustration.
 *
 * Showing a capacity *bar* filled from an invented numerator would be the worst version of this,
 * because a staff member would read it and decide whether to let a walk-in join. So the schedule
 * shows the limit and says "20 places" — which is true, and is the number the desk actually needs
 * when someone asks if there is room.
 *
 * This object exists to make the omission findable rather than to hold behaviour: when bookings
 * arrive, this is the KDoc that explains what to build.
 */
object ClassOccupancy
