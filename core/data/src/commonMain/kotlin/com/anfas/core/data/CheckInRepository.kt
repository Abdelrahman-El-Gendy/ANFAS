package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.MemberId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The entry log.
 *
 * Append-only by design: a check-in is a historical fact, so there is no update and no delete.
 * Recording an attempt also decides its outcome — the caller does not get to say whether entry
 * was granted, because a UI that could would eventually let an expired member in by passing the
 * wrong flag.
 */
interface CheckInRepository {

    /**
     * Records an attempt for [memberId] and returns what was decided.
     *
     * The outcome comes from `CheckInPolicy`, evaluated here against the member's current status
     * and term. Refused attempts are recorded too: the moment someone was turned away is what
     * staff get asked about later.
     */
    suspend fun recordAttempt(memberId: MemberId, today: LocalDate): AppResult<CheckIn>

    /** Everything logged on [date] in the device's zone, newest first. */
    fun observeDay(date: LocalDate): Flow<AppResult<List<CheckIn>>>

    /** Today's counts and peak hour, for the log header and the dashboard tile. */
    fun observeDaySummary(date: LocalDate): Flow<AppResult<CheckInSummary>>

    /** Granted entries for one member within a month, for their profile. */
    fun observeMonthlyCount(memberId: MemberId, month: LocalDate): Flow<AppResult<Int>>
}
