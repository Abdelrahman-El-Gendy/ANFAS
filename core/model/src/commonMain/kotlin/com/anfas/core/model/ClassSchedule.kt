package com.anfas.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Minutes since midnight. */
fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

/** Inverse of [toMinuteOfDay]. Callers are responsible for keeping the value inside a day. */
fun minuteOfDayToTime(minuteOfDay: Int): LocalTime =
    LocalTime(hour = minuteOfDay / 60, minute = minuteOfDay % 60)

internal const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * Where a class block sits on the week grid, and how wide.
 *
 * [lane] and [laneCount] are what let two classes at the same hour sit side by side rather than
 * on top of each other — the design shows exactly that on Sunday at 18:00 (Power Lifting and
 * Pilates). Computed here rather than in the composable because it is arithmetic on intervals,
 * which is testable without a screen, and because getting it wrong hides a class entirely.
 */
data class ClassBlock(
    val gymClass: GymClass,
    /** 0-based column within its overlap group. */
    val lane: Int,
    /** How many lanes that group needs. 1 when nothing overlaps. */
    val laneCount: Int,
)

/**
 * The timetable, arranged for the two designed screens.
 *
 * Pure: takes the classes and the day, returns positions. Everything the grid needs to draw is
 * decided here, so the composable only multiplies by a row height.
 */
object ClassSchedule {

    /**
     * The grid's vertical extent — the design's axis runs 06:00 to 22:00 inclusive, seventeen
     * hour rows.
     *
     * Kept as a floor and a ceiling rather than a fixed window, because a gym that opens at 05:00
     * would otherwise have its first class drawn above the top of the grid and silently clipped.
     * [gridStartHour] widens to fit the earliest class, never narrows.
     */
    const val DEFAULT_START_HOUR: Int = 6
    const val DEFAULT_END_HOUR: Int = 22

    fun gridStartHour(classes: List<GymClass>): Int {
        val earliest = classes.minOfOrNull { it.startMinute / 60 } ?: DEFAULT_START_HOUR
        return minOf(DEFAULT_START_HOUR, earliest)
    }

    /**
     * Exclusive, and rounded **up** to the next whole hour: a class ending at 22:30 needs the
     * 22:00 row to be fully drawn, so the grid must reach 23:00.
     */
    fun gridEndHour(classes: List<GymClass>): Int {
        val latest = classes.maxOfOrNull { (it.endMinute + 59) / 60 } ?: DEFAULT_END_HOUR
        return maxOf(DEFAULT_END_HOUR, latest)
    }

    /** Monday-first, matching `DayOfWeek`'s own ordering rather than the export's Saturday-first
     * grid — the week's first day is a locale question, and [weekOrder] answers it. */
    fun weekOrder(firstDay: DayOfWeek): List<DayOfWeek> {
        val all = DayOfWeek.entries
        val start = all.indexOf(firstDay)
        return List(all.size) { all[(start + it) % all.size] }
    }

    /** One day's classes, earliest first, with a stable tie-break so the order never flickers. */
    fun onDay(classes: List<GymClass>, day: DayOfWeek): List<GymClass> =
        classes.filter { it.dayOfWeek == day }
            .sortedWith(compareBy({ it.startMinute }, { it.name }, { it.id.value }))

    /**
     * Lays out one day's column.
     *
     * The invariant: two classes that overlap never share a lane, and everything in one overlap
     * group agrees on [ClassBlock.laneCount] so the column is divided consistently. A group that
     * disagreed with itself would divide the same column two ways and draw one block over
     * another.
     *
     * Grouping is transitive — a chain A–B–C is one group even though A and C do not touch —
     * because B's width has to be decided against everything it competes with. Within a group
     * lanes are then assigned greedily to the first free one, so C *can* reuse A's lane once A
     * has finished rather than forcing the column three ways for no reason.
     */
    fun layoutDay(classes: List<GymClass>, day: DayOfWeek): List<ClassBlock> {
        val ordered = onDay(classes, day)
        if (ordered.isEmpty()) return emptyList()

        val blocks = mutableListOf<ClassBlock>()
        var group = mutableListOf<GymClass>()
        var groupEnd = -1

        fun flush() {
            if (group.isEmpty()) return
            // Lane end times, so a lane can be reused once its previous class has finished.
            val laneEnds = mutableListOf<Int>()
            val assigned = group.map { item ->
                val lane = laneEnds.indexOfFirst { it <= item.startMinute }
                    .takeIf { it >= 0 }
                    ?: laneEnds.size.also { laneEnds.add(0) }
                laneEnds[lane] = item.endMinute
                item to lane
            }
            val laneCount = laneEnds.size
            assigned.forEach { (item, lane) ->
                blocks += ClassBlock(gymClass = item, lane = lane, laneCount = laneCount)
            }
            group = mutableListOf()
            groupEnd = -1
        }

        ordered.forEach { item ->
            // A new group starts where nothing in the current one is still running.
            if (group.isNotEmpty() && item.startMinute >= groupEnd) flush()
            group.add(item)
            groupEnd = maxOf(groupEnd, item.endMinute)
        }
        flush()
        return blocks
    }

    /**
     * Which classes are still to come today, and which have finished, for the mobile screen.
     *
     * [nowMinuteOfDay] is passed in rather than read from a clock so this stays pure and the
     * "already finished" boundary is testable. A class in progress counts as **upcoming**: the
     * desk still needs to point somebody at it.
     */
    fun splitByProgress(classes: List<GymClass>, day: DayOfWeek, nowMinuteOfDay: Int): DaySplit {
        val ordered = onDay(classes, day)
        return DaySplit(
            upcoming = ordered.filter { it.endMinute > nowMinuteOfDay },
            finished = ordered.filter { it.endMinute <= nowMinuteOfDay },
        )
    }
}

data class DaySplit(val upcoming: List<GymClass>, val finished: List<GymClass>) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && finished.isEmpty()
}
