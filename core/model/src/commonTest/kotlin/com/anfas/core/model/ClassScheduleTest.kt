package com.anfas.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The grid arithmetic. Tested without a screen because a lane assigned wrongly does not look
 * wrong — it draws one class on top of another and the covered class disappears entirely.
 */
class ClassScheduleTest {

    private fun gymClass(
        id: String,
        start: String,
        minutes: Int,
        day: DayOfWeek = DayOfWeek.MONDAY,
        name: String = id,
    ) = GymClass(
        id = GymClassId(id),
        name = name,
        category = ClassCategory.GENERAL,
        room = "Studio A",
        capacity = 20,
        dayOfWeek = day,
        startsAt = LocalTime.parse(start),
        durationMinutes = minutes,
    )

    @Test
    fun `a class that does not overlap gets one full-width lane`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(gymClass("a", "08:00", 60), gymClass("b", "10:00", 60)),
            DayOfWeek.MONDAY,
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.lane == 0 && it.laneCount == 1 })
    }

    /** The design's Sunday 18:00: Power Lifting and Pilates side by side. */
    @Test
    fun `two classes at the same time get a lane each`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(gymClass("a", "18:00", 60), gymClass("b", "18:00", 60)),
            DayOfWeek.MONDAY,
        )
        assertEquals(setOf(0, 1), blocks.map { it.lane }.toSet())
        assertTrue(blocks.all { it.laneCount == 2 })
    }

    /**
     * The invariant that actually matters, asserted directly rather than via a lane count: two
     * classes that overlap must never share a lane, and everything in one overlap group must
     * agree on how many lanes the column is divided into.
     *
     * A chain is the case that catches a non-transitive grouping. A and C do not touch, so they
     * may share a lane — what must not happen is B ending up in a group that thinks it is one
     * lane wide while A thinks it is two, because then the column is divided inconsistently and
     * one block is drawn over another.
     */
    @Test
    fun `overlapping classes never share a lane`() {
        val chain = listOf(
            gymClass("a", "08:00", 60),
            gymClass("b", "08:30", 60),
            gymClass("c", "09:15", 60),
        )
        val blocks = ClassSchedule.layoutDay(chain, DayOfWeek.MONDAY)

        assertEquals(1, blocks.map { it.laneCount }.distinct().size, "inconsistent width: $blocks")
        blocks.forEach { first ->
            blocks.forEach { second ->
                if (first !== second && first.gymClass.overlaps(second.gymClass)) {
                    assertTrue(
                        first.lane != second.lane,
                        "${first.gymClass.id.value} and ${second.gymClass.id.value} overlap " +
                            "but share lane ${first.lane}",
                    )
                }
            }
        }
        // A ends before C begins, so reusing the lane is correct and keeps the column narrow.
        val byId = blocks.associateBy { it.gymClass.id.value }
        assertEquals(byId.getValue("a").lane, byId.getValue("c").lane)
        assertTrue(blocks.all { it.laneCount == 2 })
    }

    /** Three genuinely simultaneous classes do need three lanes. */
    @Test
    fun `three simultaneous classes get three lanes`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(
                gymClass("a", "18:00", 60),
                gymClass("b", "18:00", 60),
                gymClass("c", "18:15", 30),
            ),
            DayOfWeek.MONDAY,
        )
        assertTrue(blocks.all { it.laneCount == 3 }, "expected three lanes: $blocks")
        assertEquals(3, blocks.map { it.lane }.distinct().size)
    }

    /** A finished lane is reused rather than widening the whole group. */
    @Test
    fun `a lane is reused once its class has ended`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(
                gymClass("long", "08:00", 180),
                gymClass("short1", "08:00", 60),
                gymClass("short2", "09:30", 60),
            ),
            DayOfWeek.MONDAY,
        )
        assertTrue(blocks.all { it.laneCount == 2 }, "two lanes should suffice: $blocks")
        val byId = blocks.associateBy { it.gymClass.id.value }
        assertEquals(byId.getValue("short1").lane, byId.getValue("short2").lane)
    }

    /** Back-to-back is not an overlap: 09:00 ends exactly where 09:00 begins. */
    @Test
    fun `touching at the boundary is not an overlap`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(gymClass("a", "08:00", 60), gymClass("b", "09:00", 60)),
            DayOfWeek.MONDAY,
        )
        assertTrue(blocks.all { it.laneCount == 1 })
    }

    @Test
    fun `other days are excluded from a day's layout`() {
        val blocks = ClassSchedule.layoutDay(
            listOf(
                gymClass("mon", "08:00", 60, DayOfWeek.MONDAY),
                gymClass("tue", "08:00", 60, DayOfWeek.TUESDAY),
            ),
            DayOfWeek.MONDAY,
        )
        assertEquals(listOf("mon"), blocks.map { it.gymClass.id.value })
    }

    /** Same minute, so the order has to come from somewhere stable or the grid flickers. */
    @Test
    fun `equal start times are ordered by name then id`() {
        val ordered = ClassSchedule.onDay(
            listOf(
                gymClass("z", "08:00", 60, name = "Yoga"),
                gymClass("a", "08:00", 60, name = "HIIT"),
            ),
            DayOfWeek.MONDAY,
        )
        assertEquals(listOf("HIIT", "Yoga"), ordered.map { it.name })
    }

    @Test
    fun `the grid widens for an early class but never narrows`() {
        assertEquals(
            ClassSchedule.DEFAULT_START_HOUR,
            ClassSchedule.gridStartHour(listOf(gymClass("a", "09:00", 60))),
        )
        assertEquals(5, ClassSchedule.gridStartHour(listOf(gymClass("a", "05:30", 60))))
    }

    /** A class ending at 22:30 needs the 22:00 row drawn in full, so the grid reaches 23:00. */
    @Test
    fun `the grid end rounds up to the next whole hour`() {
        assertEquals(23, ClassSchedule.gridEndHour(listOf(gymClass("a", "21:30", 60))))
        assertEquals(
            ClassSchedule.DEFAULT_END_HOUR,
            ClassSchedule.gridEndHour(listOf(gymClass("a", "09:00", 60))),
        )
    }

    @Test
    fun `an empty timetable still has a grid`() {
        assertEquals(ClassSchedule.DEFAULT_START_HOUR, ClassSchedule.gridStartHour(emptyList()))
        assertEquals(ClassSchedule.DEFAULT_END_HOUR, ClassSchedule.gridEndHour(emptyList()))
        assertEquals(emptyList(), ClassSchedule.layoutDay(emptyList(), DayOfWeek.MONDAY))
    }

    @Test
    fun `the week can start on any day`() {
        assertEquals(
            listOf(
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            ClassSchedule.weekOrder(DayOfWeek.SATURDAY),
        )
        assertEquals(7, ClassSchedule.weekOrder(DayOfWeek.MONDAY).distinct().size)
    }

    /** A class in progress is still worth pointing someone at, so it counts as upcoming. */
    @Test
    fun `a class in progress counts as upcoming`() {
        val split = ClassSchedule.splitByProgress(
            listOf(gymClass("now", "09:00", 60)),
            DayOfWeek.MONDAY,
            nowMinuteOfDay = LocalTime.parse("09:30").toMinuteOfDay(),
        )
        assertEquals(listOf("now"), split.upcoming.map { it.id.value })
        assertTrue(split.finished.isEmpty())
    }

    @Test
    fun `a class that has ended is finished`() {
        val split = ClassSchedule.splitByProgress(
            listOf(gymClass("done", "09:00", 60), gymClass("later", "18:00", 60)),
            DayOfWeek.MONDAY,
            nowMinuteOfDay = LocalTime.parse("10:00").toMinuteOfDay(),
        )
        assertEquals(listOf("done"), split.finished.map { it.id.value })
        assertEquals(listOf("later"), split.upcoming.map { it.id.value })
    }

    /**
     * 23:30 plus ninety minutes is a typo, not an instruction to wrap past midnight. Wrapping
     * would give the class a negative length and sort it to the top of the grid.
     */
    @Test
    fun `a duration running past midnight is clamped inside the day`() {
        val late = gymClass("late", "23:30", 90)
        assertEquals(LocalTime(23, 59), late.endsAt)
        assertTrue(late.endMinute > late.startMinute)
    }

    @Test
    fun `overlaps is false across different days`() {
        val mon = gymClass("a", "08:00", 60, DayOfWeek.MONDAY)
        val tue = gymClass("b", "08:00", 60, DayOfWeek.TUESDAY)
        assertTrue(!mon.overlaps(tue))
        assertTrue(mon.overlaps(gymClass("c", "08:30", 60, DayOfWeek.MONDAY)))
    }
}
