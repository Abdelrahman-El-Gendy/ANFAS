package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import kotlinx.coroutines.flow.Flow

/**
 * The only way a feature reaches member data.
 *
 * Reads emit [AppResult] rather than a bare list because the repository is the error
 * boundary: storage failures are converted here, so no feature ever sees a Room exception.
 *
 * There is deliberately no `isEmpty()`: the two nothing-states the design distinguishes —
 * `members-list-empty` ("No members yet") versus `search-no-results` ("No members match
 * \"khaled\"") — are told apart by whether the *query* was blank, so a separate count query
 * would be dead weight.
 */
interface MemberRepository {

    /** Blank [query] means the whole directory. */
    fun observeMembers(query: String = ""): Flow<AppResult<List<Member>>>

    fun observeMember(id: MemberId): Flow<AppResult<Member?>>

    suspend fun upsert(members: List<Member>): AppResult<Unit>

    suspend fun delete(id: MemberId): AppResult<Unit>
}
