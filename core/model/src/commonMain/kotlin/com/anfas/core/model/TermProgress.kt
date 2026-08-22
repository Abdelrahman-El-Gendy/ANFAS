package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * How far through a subscription term a member is, on a given day.
 *
 * Pure and separate from [SubscriptionTerm] because it depends on *today*, which the model must
 * not reach for. The caller supplies the date, which is also what makes every boundary here
 * testable without freezing a clock.
 *
 * The design's `member-profile` renders this three ways at once — a "Time Remaining" percentage
 * ring, an "Expires in 2 days" pill and the start/end dates — so all three come from one
 * calculation rather than three call sites each doing their own arithmetic.
 */
data class TermProgress(
    /** Whole days from the term's start to its end. Zero for a single-day term. */
    val totalDays: Int,
    /** Days already served, clamped into `0..totalDays`. */
    val elapsedDays: Int,
    /**
     * Days left before the term ends, clamped at zero.
     *
     * Counted to the **end date inclusive**: a term ending today has one day left, not none. Staff
     * read this pill to decide whether to chase a renewal today, and a membership valid until
     * close of business should not read "expired".
     */
    val remainingDays: Int,
    val state: State,
) {
    /**
     * `0f..1f`. Zero when the term has not started, one when it has ended.
     *
     * A single-day term is reported as fully elapsed rather than dividing by zero — there is no
     * meaningful "half way" through one day, and 100% is the honest reading on the day itself.
     */
    val fraction: Float
        get() = when {
            state == State.NotStarted -> 0f
            totalDays <= 0 -> 1f
            else -> (elapsedDays.toFloat() / totalDays).coerceIn(0f, 1f)
        }

    /**
     * `0f..1f` of the term still to run — the inverse of [fraction].
     *
     * Exists as its own property because the design labels this bar "Time remaining", and a bar
     * labelled *remaining* that fills as time is *consumed* reads as empty on the day a member
     * pays. Getting that backwards is invisible in a unit test and obvious on a screen.
     */
    val remainingFraction: Float get() = 1f - fraction

    /** For the numeric badge beside the bar, so 0% and 100% are unambiguous. */
    val remainingPercent: Int get() = (remainingFraction * 100).toInt()

    enum class State {
        /** Bought in advance; the term begins on a future date. */
        NotStarted,
        Active,

        /** Active, but close enough to the end that staff should act. See [EXPIRING_SOON_DAYS]. */
        ExpiringSoon,
        Expired,
    }

    companion object {
        /**
         * A week. Long enough that a member who trains twice a week is told in person before their
         * membership lapses, short enough that the pill does not cry wolf for a month.
         */
        const val EXPIRING_SOON_DAYS: Int = 7

        fun of(term: SubscriptionTerm, today: LocalDate): TermProgress = of(
            startsOn = term.startsOn,
            endsOn = term.endsOn,
            today = today,
        )

        fun of(startsOn: LocalDate, endsOn: LocalDate, today: LocalDate): TermProgress {
            // A term whose dates are the wrong way round is corrupt data, not a crash: report it
            // as a zero-length term already served rather than producing negative days that would
            // render as "expires in -40 days".
            val totalDays = startsOn.daysUntil(endsOn).coerceAtLeast(0)
            val elapsedDays = startsOn.daysUntil(today).coerceIn(0, totalDays)
            val remainingDays = (today.daysUntil(endsOn) + 1).coerceAtLeast(0)

            val state = when {
                today < startsOn -> State.NotStarted
                today > endsOn -> State.Expired
                remainingDays <= EXPIRING_SOON_DAYS -> State.ExpiringSoon
                else -> State.Active
            }

            return TermProgress(
                totalDays = totalDays,
                elapsedDays = elapsedDays,
                remainingDays = remainingDays,
                state = state,
            )
        }
    }
}
