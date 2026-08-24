package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class TherapyProgressTest {

    private fun session(id: String, at: Long, painScore: Int? = null) = TherapySession(
        id = TherapySessionId(id),
        caseId = TherapyCaseId("c-1"),
        therapistStaffId = null,
        at = Instant.fromEpochSeconds(at),
        durationMinutes = 45,
        treatmentTypes = emptySet(),
        notes = "",
        painScore = painScore,
    )

    @Test
    fun `no sessions produce no trend`() {
        assertNull(TherapyProgress.painScoreTrend(emptyList()))
    }

    @Test
    fun `a single scored session is not a trend`() {
        val sessions = listOf(session("a", 100, painScore = 7))
        assertNull(TherapyProgress.painScoreTrend(sessions))
    }

    @Test
    fun `sessions with no pain score at all produce no trend`() {
        val sessions = listOf(session("a", 100), session("b", 200))
        assertNull(TherapyProgress.painScoreTrend(sessions))
    }

    /** A session with no score is skipped rather than treated as zero, which would fake relief. */
    @Test
    fun `an unscored session in the middle is skipped not treated as zero`() {
        val sessions = listOf(
            session("a", 100, painScore = 8),
            session("b", 200, painScore = null),
            session("c", 300, painScore = 4),
        )
        val trend = TherapyProgress.painScoreTrend(sessions)
        assertEquals(8, trend?.first)
        assertEquals(4, trend?.latest)
        assertEquals(listOf(8, 4), trend?.scores)
    }

    @Test
    fun `the trend follows chronological order not insertion order`() {
        val sessions = listOf(
            session("later", 300, painScore = 3),
            session("earlier", 100, painScore = 7),
        )
        val trend = TherapyProgress.painScoreTrend(sessions)
        assertEquals(7, trend?.first)
        assertEquals(3, trend?.latest)
    }

    @Test
    fun `a falling score reads as improving`() {
        val trend = TherapyProgress.painScoreTrend(
            listOf(session("a", 100, painScore = 7), session("b", 200, painScore = 3)),
        )
        assertTrue(trend!!.isImproving)
    }

    @Test
    fun `a rising or equal score does not read as improving`() {
        assertTrue(
            !TherapyProgress.painScoreTrend(
                listOf(session("a", 100, painScore = 3), session("b", 200, painScore = 5)),
            )!!.isImproving,
        )
        assertTrue(
            !TherapyProgress.painScoreTrend(
                listOf(session("a", 100, painScore = 4), session("b", 200, painScore = 4)),
            )!!.isImproving,
        )
    }
}
