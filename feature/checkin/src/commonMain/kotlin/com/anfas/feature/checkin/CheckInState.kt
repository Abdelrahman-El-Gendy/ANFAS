package com.anfas.feature.checkin

import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.Member

/** What the search half of the screen is showing. */
sealed interface CheckInSearch {
    /** Nothing typed yet. The desk's resting state, so it says what to do rather than "empty". */
    data object Idle : CheckInSearch

    data class Results(val members: List<Member>) : CheckInSearch

    data class NoMatches(val query: String) : CheckInSearch
}

data class CheckInState(
    val query: String = "",
    val search: CheckInSearch = CheckInSearch.Idle,
    val summary: CheckInSummary = CheckInSummary(),
    val log: List<CheckIn> = emptyList(),
    /** The member currently being recorded, so their row can show progress. */
    val recordingId: String? = null,
    val notice: CheckInNotice? = null,
    val error: String? = null,
)

/**
 * Typed, and it carries the outcome rather than a rendered sentence — the screen decides how to
 * word "turned away because the membership expired", which is different copy in each language.
 */
sealed interface CheckInNotice {
    data class Recorded(val checkIn: CheckIn) : CheckInNotice

    data class Failed(val message: String) : CheckInNotice
}
