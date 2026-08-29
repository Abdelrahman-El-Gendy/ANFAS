package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.database.MemberDao
import com.anfas.core.database.SyncTables
import com.anfas.core.model.Member
import com.anfas.core.model.MemberId
import com.anfas.core.model.MembershipNumbers
import com.anfas.core.model.MembershipStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Room-backed [MemberRepository]. The local database is the source of truth the UI reads
 * from; remote sync writes *into* it rather than being read through, which is what keeps the
 * directory usable on the gym floor with no signal.
 *
 * There is no network path yet — `:core:network` exists but member sync is its own task. When
 * it lands it belongs here, writing through [upsert], and no feature code should change.
 *
 * Error handling comes from [runStorage] / [asAppResult] in StorageBoundary.kt, shared with
 * every other repository here so the boundary behaves identically across the module.
 */
internal class OfflineFirstMemberRepository(
    private val dao: MemberDao,
    private val newId: () -> String,
) : MemberRepository {

    override fun observeMembers(query: String): Flow<AppResult<List<Member>>> {
        val rows = if (query.isBlank()) dao.observeAll() else dao.observeMatching(query.trim())
        return rows.asAppResult("Could not load members") { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeMember(id: MemberId): Flow<AppResult<Member?>> = dao.observeById(id.value)
        .asAppResult("Could not load member ${id.value}") { it?.toDomain() }

    override suspend fun create(fullName: String, phone: String?): AppResult<Member> =
        runStorage("Could not add the member") {
            val existing = dao.observeAll().first().map { it.membershipNumber }
            val member = Member(
                id = MemberId(newId()),
                fullName = fullName.trim(),
                membershipNumber = MembershipNumbers.next(existing),
                phone = phone?.trim()?.takeIf { it.isNotEmpty() },
                // A member registered at the desk is active from that moment. Their subscription
                // is a separate act -- Renew on the profile -- because taking payment and taking
                // a name are different steps and staff do them at different times.
                status = MembershipStatus.ACTIVE,
                lastCheckInAt = null,
                avatarUrl = null,
            )
            dao.upsertAllTracked(
                members = listOf(member.toEntity()),
                changes = listOf(changeFor(SyncTables.MEMBERS, member.id.value)),
            )
            member
        }

    override suspend fun upsert(members: List<Member>): AppResult<Unit> =
        runStorage("Could not save members") {
            dao.upsertAllTracked(
                members = members.map { it.toEntity() },
                changes = changesFor(SyncTables.MEMBERS, members.map { it.id.value }),
            )
        }

    /**
     * The tombstones for the cascaded therapy rows are written by the DAO, inside the same
     * transaction and before the delete — see `MemberDao.deleteByIdTracked`. They cannot be
     * computed here: after `deleteById` returns, the cases and sessions SQLite removed are
     * unknowable.
     */
    override suspend fun delete(id: MemberId): AppResult<Unit> =
        runStorage("Could not delete member ${id.value}") {
            dao.deleteByIdTracked(id = id.value, nowEpochMs = capturedAt())
        }
}
