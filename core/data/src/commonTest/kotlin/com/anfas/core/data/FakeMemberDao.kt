package com.anfas.core.data

import com.anfas.core.database.MemberDao
import com.anfas.core.database.MemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for Room. Query semantics mirror the DAO's SQL — case-insensitive
 * substring match on name or membership number, name-ordered — so a repository test that
 * passes here is not passing for the wrong reason.
 */
internal class FakeMemberDao(
    initial: List<MemberEntity> = emptyList(),
) : MemberDao {

    private val rows = MutableStateFlow(initial)

    /** Set to make the next read fail, so the error boundary can be exercised. */
    var failure: Throwable? = null

    /** Synchronous view of what has been written, for asserting on writes. */
    val current: List<MemberEntity> get() = rows.value

    override fun observeAll(): Flow<List<MemberEntity>> = rows.map { list ->
        failure?.let { throw it }
        list.sortedBy { it.fullName.lowercase() }
    }

    override fun observeNormalisedPhones(): Flow<List<String>> = rows.map { list ->
        failure?.let { throw it }
        list.mapNotNull { it.phoneNormalised }
    }

    override fun observeMatching(query: String): Flow<List<MemberEntity>> = rows.map { list ->
        failure?.let { throw it }
        list.filter {
            it.fullName.contains(query, ignoreCase = true) ||
                it.membershipNumber.contains(query, ignoreCase = true)
        }.sortedBy { it.fullName.lowercase() }
    }

    override fun observeById(id: String): Flow<MemberEntity?> = rows.map { list ->
        failure?.let { throw it }
        list.firstOrNull { it.id == id }
    }

    override suspend fun upsertAll(members: List<MemberEntity>) {
        failure?.let { throw it }
        rows.value = (rows.value.associateBy { it.id } + members.associateBy { it.id }).values.toList()
    }

    override suspend fun deleteById(id: String) {
        failure?.let { throw it }
        rows.value = rows.value.filterNot { it.id == id }
    }
}

internal fun memberEntity(
    id: String,
    name: String,
    number: String = "#$id",
    status: String = "ACTIVE",
    lastCheckInAtEpochMs: Long? = null,
    phone: String? = null,
) = MemberEntity(
    id = id,
    fullName = name,
    membershipNumber = number,
    phone = phone,
    phoneNormalised = phone?.let(com.anfas.core.model.IntakeValidator::normalisePhone)
        ?.takeIf { it.isNotEmpty() },
    status = status,
    lastCheckInAtEpochMs = lastCheckInAtEpochMs,
    avatarUrl = null,
)
