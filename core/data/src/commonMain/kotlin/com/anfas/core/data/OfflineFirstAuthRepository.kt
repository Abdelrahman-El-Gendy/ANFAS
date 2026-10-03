package com.anfas.core.data

import com.anfas.core.auth.CredentialRules
import com.anfas.core.auth.PasswordHash
import com.anfas.core.auth.PasswordHasher
import com.anfas.core.auth.Permission
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SessionStore
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.auth.normaliseUsername
import com.anfas.core.auth.permissions
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineFirstAuthRepository(
    private val dao: StaffDao,
    private val sessionStore: SessionStore,
    private val hasher: PasswordHasher,
    private val dispatchers: AppDispatchers,
) : AuthRepository {

    private val log = logger("Auth")

    /**
     * The stored session, **re-derived from the `staff` row it names** rather than trusted as
     * written.
     *
     * `Settings` and the database do not survive together. The Room file is deliberately excluded
     * from cloud backup and device transfer because the whole domain is PII, while
     * SharedPreferences / NSUserDefaults are not — so a restored or transferred install arrives
     * holding a session id for a staff row that does not exist. Reading the store alone put such
     * an install straight onto the dashboard as an authenticated nobody. Observed on the iOS
     * simulator after wiping app data: the session outlived the database.
     *
     * Three states collapse to "not signed in", and each is a real one:
     *  - **no row** — the transfer/restore case above, and the only way a row disappears at all,
     *    since staff are disabled rather than deleted so history stays attributable;
     *  - **row disabled** — an Owner revoking an account has to take effect on the device that
     *    account is signed in on, not at its next sign-in. `SignInResult.AccountDisabled` already
     *    refuses the front door; this closes the window someone is already through.
     *  - **roles taken from the row, never from storage** — the stored copy is a snapshot from
     *    whenever the person signed in, so a demoted Owner kept every Owner permission until they
     *    happened to sign out. That is the part of this worth calling a privilege bug rather than
     *    a restore bug.
     *
     * The stale keys are **not** cleared here, deliberately. Writing to storage from inside a cold
     * flow would fire per collector, and a single unreadable read would then sign someone out
     * permanently instead of transiently. Nothing is leaked by leaving them: the value is an id and
     * role names, never a secret, and both signing in and signing out overwrite them.
     *
     * No new UI is needed for any of this. An install with no database has no staff rows either, so
     * `SignInComponent` offers first-run setup — which is exactly the truth. The disabled and
     * demoted cases land on the sign-in form, which is also the truth. This is why the export's
     * `session-expired` screen still has no caller.
     */
    override fun observeSession(): Flow<Session?> = sessionStore.observe().flatMapLatest { stored ->
        // flatMapLatest for the same reason observeCurrentStaff uses it: a sign-out must stop
        // observing the row rather than keep evaluating the previous person's.
        if (stored == null) {
            flowOf(null)
        } else {
            dao.observeById(stored.userId).map { row ->
                val roles = row?.rolesSet().orEmpty()
                when {
                    row == null || !row.isEnabled -> null

                    // Same rule SettingsSessionStore applies to its own stored roles: a
                    // session that can do nothing is worse than no session, because the shell
                    // draws the whole app around a user who is then denied every screen.
                    // Reachable if a row's roles string stops parsing -- a renamed Role entry.
                    roles.isEmpty() -> null

                    else -> Session(userId = stored.userId, roles = roles)
                }
            }
        }
    }

    override fun observeCurrentStaff(): Flow<StaffAccount?> =
        sessionStore.observe().flatMapLatest { session ->
            // flatMapLatest, not combine: a sign-out must stop observing the row rather than
            // keep emitting the previous person's name against a null session.
            if (session ==
                null
            ) {
                flowOf(null)
            } else {
                dao.observeById(session.userId).map { it?.toDomain() }
            }
        }

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

    override suspend fun createStaff(
        username: String,
        password: String,
        displayName: String,
        roles: Set<Role>,
    ): AppResult<CreateAccountOutcome> = withContext(dispatchers.io) {
        runStorage("Could not create the account") {
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
                // Owner is stripped rather than rejected: the caller's UI does not offer it, so
                // its presence would be a programming error, and silently narrowing is safer
                // than creating a second owner because a check was missed somewhere.
                roles = (roles - Role.Owner).ifEmpty { setOf(Role.Receptionist) },
                passwordHash = hasher.hash(password),
                createdAt = Clock.System.now(),
            )
            dao.upsert(account.toEntity())
            // No username, no roles: a shared reception device's log must not say who was added.
            log.i("Staff account created")
            CreateAccountOutcome.Created(Session(account.id, account.roles))
        }
    }

    override suspend fun setStaffEnabled(
        id: String,
        enabled: Boolean,
    ): AppResult<StaffChangeOutcome> = withContext(dispatchers.io) {
        runStorage("Could not change the account") {
            val row = dao.findById(id) ?: return@runStorage StaffChangeOutcome.NotFound

            if (!enabled && wouldLoseLastAdministrator(row)) {
                return@runStorage StaffChangeOutcome.WouldLockOutDevice
            }

            dao.upsert(row.copy(isEnabled = enabled))
            StaffChangeOutcome.Changed
        }
    }

    override suspend fun resetStaffPassword(
        id: String,
        newPassword: String,
    ): AppResult<StaffChangeOutcome> = withContext(dispatchers.io) {
        runStorage("Could not reset the password") {
            val row = dao.findById(id) ?: return@runStorage StaffChangeOutcome.NotFound

            // Only the password rule applies: the username and display name are unchanged, and
            // running the full validator would report the account's own username as taken.
            val problems = CredentialRules.validatePassword(newPassword)
            if (problems.isNotEmpty()) return@runStorage StaffChangeOutcome.Rejected(problems)

            val hash = hasher.hash(newPassword)
            dao.upsert(
                row.copy(
                    passwordAlgorithm = hash.algorithm,
                    passwordIterations = hash.iterations,
                    passwordSalt = hash.salt,
                    passwordHash = hash.hash,
                ),
            )
            log.i("Staff password reset")
            StaffChangeOutcome.Changed
        }
    }

    /**
     * True when [candidate] is the only enabled account that can still administer the device.
     *
     * Without this an owner can disable themselves and leave a device nobody can administer —
     * and there is no server to recover from, so the only way back would be wiping the app and
     * losing the membership.
     */
    private suspend fun wouldLoseLastAdministrator(candidate: StaffEntity): Boolean {
        if (Permission.MANAGE_STAFF !in candidate.rolesSet().flatMap { it.permissions }) {
            return false
        }
        val others = dao.observeAll().first().filter { it.id != candidate.id && it.isEnabled }
        return others.none { row ->
            Permission.MANAGE_STAFF in row.rolesSet().flatMap { it.permissions }
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
