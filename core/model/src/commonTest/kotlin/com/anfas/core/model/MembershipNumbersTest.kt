package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MembershipNumbersTest {

    @Test
    fun `the first member gets the starting number`() {
        assertEquals("#10000", MembershipNumbers.next(emptyList()))
    }

    @Test
    fun `the next number is one past the highest issued`() {
        assertEquals(
            "#10004",
            MembershipNumbers.next(listOf("#10000", "#10003", "#10001")),
        )
    }

    /** A hand-typed value during a migration must not stop the gym registering anybody. */
    @Test
    fun `malformed numbers are ignored rather than fatal`() {
        assertEquals(
            "#10008",
            MembershipNumbers.next(listOf("legacy", "", "#10007", "n/a")),
        )
    }

    @Test
    fun `numbers with and without the prefix compare equal`() {
        assertEquals("#10006", MembershipNumbers.next(listOf("10005")))
        assertEquals(10_005, MembershipNumbers.parse("#10005"))
        assertEquals(10_005, MembershipNumbers.parse("10005"))
        assertNull(MembershipNumbers.parse("none"))
    }

    /**
     * The bug this guards: a bulk import once handed every row the same number, and Room's upsert
     * collapsed eight members into one. Allocating the whole run in one pass makes that
     * impossible.
     */
    @Test
    fun `a run is consecutive and never repeats`() {
        val run = MembershipNumbers.nextRun(listOf("#10000"), count = 4)

        assertEquals(listOf("#10001", "#10002", "#10003", "#10004"), run)
        assertEquals(run.size, run.distinct().size)
    }

    @Test
    fun `an empty run is allowed`() {
        assertEquals(emptyList(), MembershipNumbers.nextRun(listOf("#10000"), count = 0))
    }

    /**
     * Legacy or hand-typed numbers below the start must not drag the sequence down. With one
     * member at "#1", the next must still be the first real number rather than "#2".
     */
    @Test
    fun `numbers below the start do not lower the sequence`() {
        assertEquals("#10000", MembershipNumbers.next(listOf("#1", "#7", "#500")))
        assertEquals(
            listOf("#10000", "#10001"),
            MembershipNumbers.nextRun(listOf("#3"), count = 2),
        )
    }

    @Test
    fun `numbers above the start continue normally`() {
        assertEquals("#88393", MembershipNumbers.next(listOf("#1", "#88392")))
    }
}
