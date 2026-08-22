package com.anfas.core.data

import com.anfas.core.auth.CredentialRules
import com.anfas.core.auth.PasswordHash
import com.anfas.core.auth.PasswordHasher
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SessionStore
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.auth.normaliseUsername
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class OfflineFirstAuthRepository(
    private val dao: StaffDao,
    private val sessionStore: SessionStore,
    private val hasher: PasswordHasher,
    private val dispatchers: AppDispatchers,
) : AuthRepository {

    private val log = logger("Auth")

    override fun observeSession(): Flow<Session?> = sessionStore.observe()

    override suspend fun hasAnyAccount(): AppResult<Boolean> = withContext(dispatchers.io) {
        runStorage("Could not read staff accounts") { dao.count() > 0 }
    }

    override suspend fun signIn(username: String, password: String): AppResult<SignInResult> =
        withContext(dispatchers.io) {
            runStorage("Could not sign in") {
                if (dao.count() == 0) return@runStorage SignInResult.NoAccounts

                val row = dao.findByUsername(normaliseUsername(username))

                // Hash even when the username is unknown. Returning immediately would make a
                // missing account measurably faster than a wrong password, which is how an
                // attacker enumerates usernames from a device they have picked up.
                val stored = row?.toPasswordHash() ?: decoyHash
                val matches = hasher.verify(password, stored)

                when {
                    row == null || !matches -> SignInResult.InvalidCredentials

                    !row.isEnabled -> SignInResult.AccountDisabled

                    else -> {
                        val session = Session(userId = row.id, roles = row.rolesSet())
                        sessionStore.save(session)
                        // No username, no password, no roles: a log line naming who signed in on
                        // a shared reception device is exactly what must not be on disk.
                        log.i("Staff signed in")
                        SignInResult.Success(session)
                    }
                }
            }
        }

    override suspend fun signOut(): AppResult<Unit> = withContext(dispatchers.io) {
        runStorage("Could not sign out") { sessionStore.clear() }
    }

    override suspend fun createFirstOwner(
        username: String,
        password: String,
        displayName: String,
    ): AppResult<CreateAccountOutcome> = withContext(dispatchers.io) {
        runStorage("Could not create the account") {
            // Re-checked here rather than trusted from the caller: the setup screen could be
            // reached again on a device that has since been set up on another tab or window.
            if (dao.count() > 0) return@runStorage CreateAccountOutcome.AlreadyInitialised

            val problems = CredentialRules.validate(
                username = username,
                password = password,
                displayName = displayName,
                existingUsernames = dao.allUsernames().toSet(),
            )
            if (problems.isNotEmpty()) return@runStorage CreateAccountOutcome.Rejected(problems)

            val account = StaffAccount(
                id = Uuid.random().toString(),
                username = normaliseUsername(username),
                displayName = displayName.trim(),
                // The first account is always the Owner. Someone has to be able to create the
                // others, and a device with no Owner cannot add staff at all.
                roles = setOf(Role.Owner),
                passwordHash = hasher.hash(password),
                createdAt = Clock.System.now(),
            )
            dao.upsert(account.toEntity())

            val session = Session(userId = account.id, roles = account.roles)
            sessionStore.save(session)
            log.i("First owner account created")
            CreateAccountOutcome.Created(session)
        }
    }

    override fun observeStaff(): Flow<AppResult<List<StaffAccount>>> =
        dao.observeAll().asAppResult("Could not load staff") { rows ->
            rows.map { it.toDomain() }
        }

    /**
     * A throwaway hash used only to spend the same time as a real verification when the username
     * does not exist. Built once at construction so the cost of *creating* it is not itself a
     * timing signal, and never stored anywhere.
     */
    private val decoyHash: PasswordHash by lazy { hasher.hash(Uuid.random().toString()) }
}

private fun StaffEntity.toPasswordHash() = PasswordHash(
    algorithm = passwordAlgorithm,
    iterations = passwordIterations,
    salt = passwordSalt,
    hash = passwordHash,
)

private fun StaffEntity.rolesSet(): Set<Role> = roles.split(',')
    .mapNotNull { name -> Role.entries.firstOrNull { it.name == name.trim() } }
    .toSet()

private fun StaffEntity.toDomain() = StaffAccount(
    id = id,
    username = username,
    displayName = displayName,
    roles = rolesSet(),
    passwordHash = toPasswordHash(),
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    isEnabled = isEnabled,
)

private fun StaffAccount.toEntity() = StaffEntity(
    id = id,
    username = username,
    displayName = displayName,
    roles = roles.joinToString(",") { it.name },
    passwordAlgorithm = passwordHash.algorithm,
    passwordIterations = passwordHash.iterations,
    passwordSalt = passwordHash.salt,
    passwordHash = passwordHash.hash,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
    isEnabled = isEnabled,
)
