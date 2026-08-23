package com.anfas.core.data

import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.PasswordHash
import com.anfas.core.auth.PasswordHasher
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SessionStore
import com.anfas.core.auth.SignInResult
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    @Test
    fun `a fresh install reports no accounts rather than invalid credentials`() = runTest {
        val repository = repository()

        // The distinction matters: NoAccounts sends the shell to first-run setup, whereas
        // InvalidCredentials would show a login nobody on earth could pass.
        assertEquals(
            SignInResult.NoAccounts,
            repository.signIn("anyone", "anything").valueOrFail(),
        )
        assertEquals(false, repository.hasAnyAccount().valueOrFail())
    }

    @Test
    fun `the first owner can be created and is signed in immediately`() = runTest {
        val store = FakeSessionStore()
        val repository = repository(sessionStore = store)

        val outcome = repository
            .createFirstOwner("Fahd", "correct-horse", "Fahd Owner")
            .valueOrFail()

        val created = assertIs<CreateAccountOutcome.Created>(outcome)
        assertEquals(setOf(Role.Owner), created.session.roles)
        assertEquals(created.session, store.current())
    }

    @Test
    fun `a created owner can sign in with a normalised username`() = runTest {
        val repository = repository()
        repository.createFirstOwner("Fahd", "correct-horse", "Fahd").valueOrFail()

        // Typed at a busy reception desk: different case, stray whitespace, same account.
        val result = repository.signIn("  FAHD ", "correct-horse").valueOrFail()

        assertIs<SignInResult.Success>(result)
    }

    @Test
    fun `a wrong password is rejected and does not create a session`() = runTest {
        val store = FakeSessionStore()
        val repository = repository(sessionStore = store)
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        store.clear()

        val result = repository.signIn("fahd", "wrong-horse").valueOrFail()

        assertEquals(SignInResult.InvalidCredentials, result)
        assertNull(store.current())
    }

    /**
     * An unknown username and a wrong password must be indistinguishable, or anyone holding the
     * device can enumerate who works at the gym.
     */
    @Test
    fun `an unknown username reports the same result as a wrong password`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()

        assertEquals(
            repository.signIn("fahd", "wrong").valueOrFail(),
            repository.signIn("nobody", "wrong").valueOrFail(),
        )
    }

    /**
     * And it must also *cost* the same. Returning before hashing would make a missing account
     * measurably faster, which is the same leak by a different channel — so the repository hashes
     * against a decoy. Asserted by counting verifications, not by timing, which would be flaky.
     */
    @Test
    fun `an unknown username still performs a password verification`() = runTest {
        val hasher = CountingHasher()
        val repository = repository(hasher = hasher)
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        hasher.reset()

        repository.signIn("nobody-here", "whatever").valueOrFail()

        assertEquals(1, hasher.verifications)
    }

    @Test
    fun `a disabled account is refused even with the right password`() = runTest {
        val dao = FakeStaffDao()
        val repository = repository(dao = dao)
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        dao.setEnabled(false)

        assertEquals(
            SignInResult.AccountDisabled,
            repository.signIn("fahd", "correct-horse").valueOrFail(),
        )
    }

    @Test
    fun `credential problems are reported per field instead of thrown`() = runTest {
        val outcome = repository()
            .createFirstOwner("ab", "short", "  ")
            .valueOrFail()

        val rejected = assertIs<CreateAccountOutcome.Rejected>(outcome)
        assertEquals(
            setOf(
                CredentialProblem.UsernameTooShort,
                CredentialProblem.PasswordTooShort,
                CredentialProblem.DisplayNameBlank,
            ),
            rejected.problems,
        )
    }

    /** Otherwise anyone reaching the setup screen could mint an Owner on a device in use. */
    @Test
    fun `a second first-owner attempt is refused`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()

        assertEquals(
            CreateAccountOutcome.AlreadyInitialised,
            repository.createFirstOwner("someone", "another-pass", "Someone").valueOrFail(),
        )
    }

    @Test
    fun `signing out clears the session`() = runTest {
        val store = FakeSessionStore()
        val repository = repository(sessionStore = store)
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()

        repository.signOut().valueOrFail()

        assertNull(store.current())
        assertTrue(repository.hasAnyAccount().valueOrFail())
    }

    // --- staff management -------------------------------------------------------------------

    @Test
    fun `an owner can create staff with the roles they choose`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()

        val outcome = repository
            .createStaff("mona", "another-pass", "Mona", setOf(Role.Receptionist))
            .valueOrFail()

        assertIs<CreateAccountOutcome.Created>(outcome)
        assertTrue(repository.signIn("mona", "another-pass").valueOrFail() is SignInResult.Success)
    }

    /**
     * The owner is established once, at first run. A second owner is a support problem — who
     * removes whom — with no product need, so the role is narrowed rather than rejected.
     */
    @Test
    fun `creating staff cannot grant the owner role`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        repository.createStaff("mona", "another-pass", "Mona", setOf(Role.Owner, Role.Coach))
            .valueOrFail()

        val mona = repository.observeStaff().first().valueOrFail().first { it.username == "mona" }

        assertFalse(Role.Owner in mona.roles)
        assertEquals(setOf(Role.Coach), mona.roles)
    }

    @Test
    fun `creating staff with no usable role falls back to receptionist`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        repository.createStaff("mona", "another-pass", "Mona", setOf(Role.Owner)).valueOrFail()

        val mona = repository.observeStaff().first().valueOrFail().first { it.username == "mona" }

        assertEquals(setOf(Role.Receptionist), mona.roles)
    }

    @Test
    fun `a duplicate username is refused`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()

        val outcome = repository
            .createStaff("FAHD", "another-pass", "Someone", setOf(Role.Coach))
            .valueOrFail()

        val rejected = assertIs<CreateAccountOutcome.Rejected>(outcome)
        assertTrue(CredentialProblem.UsernameTaken in rejected.problems)
    }

    /**
     * The rule that keeps a device recoverable. There is no server, so an owner who disables
     * themselves would leave a device nobody can administer and no way back except wiping the
     * app and losing the membership.
     */
    @Test
    fun `the last administrator cannot disable themselves`() = runTest {
        val repository = repository()
        val created = repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        val ownerId = assertIs<CreateAccountOutcome.Created>(created).session.userId

        assertEquals(
            StaffChangeOutcome.WouldLockOutDevice,
            repository.setStaffEnabled(ownerId, enabled = false).valueOrFail(),
        )
    }

    @Test
    fun `a non-administrator can be disabled and then cannot sign in`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        repository.createStaff("mona", "another-pass", "Mona", setOf(Role.Coach)).valueOrFail()
        val mona = repository.observeStaff().first().valueOrFail().first { it.username == "mona" }

        assertEquals(
            StaffChangeOutcome.Changed,
            repository.setStaffEnabled(mona.id, enabled = false).valueOrFail(),
        )
        assertEquals(
            SignInResult.AccountDisabled,
            repository.signIn("mona", "another-pass").valueOrFail(),
        )
    }

    @Test
    fun `an owner can reset a password and the old one stops working`() = runTest {
        val repository = repository()
        repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        repository.createStaff("mona", "another-pass", "Mona", setOf(Role.Coach)).valueOrFail()
        val mona = repository.observeStaff().first().valueOrFail().first { it.username == "mona" }

        repository.resetStaffPassword(mona.id, "brand-new-pass").valueOrFail()

        assertTrue(
            repository.signIn("mona", "brand-new-pass").valueOrFail() is SignInResult.Success,
        )
        assertEquals(
            SignInResult.InvalidCredentials,
            repository.signIn("mona", "another-pass").valueOrFail(),
        )
    }

    /** Only the password rule applies — the full validator would call the account's own name taken. */
    @Test
    fun `a reset only checks the password rule`() = runTest {
        val repository = repository()
        val created = repository.createFirstOwner("fahd", "correct-horse", "Fahd").valueOrFail()
        val ownerId = assertIs<CreateAccountOutcome.Created>(created).session.userId

        assertEquals(
            StaffChangeOutcome.Rejected(setOf(CredentialProblem.PasswordTooShort)),
            repository.resetStaffPassword(ownerId, "short").valueOrFail(),
        )
        assertEquals(
            StaffChangeOutcome.Changed,
            repository.resetStaffPassword(ownerId, "long-enough-pass").valueOrFail(),
        )
    }

    @Test
    fun `changing a missing account reports not found`() = runTest {
        val repository = repository()

        assertEquals(
            StaffChangeOutcome.NotFound,
            repository.setStaffEnabled("nope", enabled = false).valueOrFail(),
        )
    }

    private fun repository(
        dao: StaffDao = FakeStaffDao(),
        sessionStore: SessionStore = FakeSessionStore(),
        hasher: PasswordHasher = FakeHasher(),
    ): AuthRepository = OfflineFirstAuthRepository(
        dao = dao,
        sessionStore = sessionStore,
        hasher = hasher,
        dispatchers = UnconfinedDispatchers,
    )
}

/**
 * Reversible stand-in for PBKDF2. Real hashing at 210,000 iterations per assertion would make
 * this suite slow enough to tempt someone into lowering the production cost; the derivation itself
 * is verified against a known vector in :core:auth.
 */
private open class FakeHasher : PasswordHasher {
    override fun hash(password: String) = PasswordHash(
        algorithm = PasswordHash.PBKDF2_SHA256,
        iterations = 1,
        salt = byteArrayOf(1),
        hash = password.encodeToByteArray(),
    )

    override fun verify(password: String, against: PasswordHash): Boolean =
        password.encodeToByteArray().contentEquals(against.hash)
}

private class CountingHasher : FakeHasher() {
    var verifications = 0
        private set

    override fun verify(password: String, against: PasswordHash): Boolean {
        verifications++
        return super.verify(password, against)
    }

    fun reset() {
        verifications = 0
    }
}

private class FakeSessionStore : SessionStore {
    private val state = MutableStateFlow<Session?>(null)

    override fun observe(): Flow<Session?> = state.asStateFlow()

    override suspend fun current(): Session? = state.value

    override suspend fun save(session: Session) {
        state.value = session
    }

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeStaffDao : StaffDao {
    private val rows = MutableStateFlow<List<StaffEntity>>(emptyList())

    fun setEnabled(enabled: Boolean) {
        rows.value = rows.value.map { it.copy(isEnabled = enabled) }
    }

    override suspend fun findByUsername(username: String): StaffEntity? =
        rows.value.firstOrNull { it.username == username }

    override suspend fun findById(id: String): StaffEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun count(): Int = rows.value.size

    override suspend fun allUsernames(): List<String> = rows.value.map { it.username }

    override fun observeAll(): Flow<List<StaffEntity>> = rows.map { it }

    override suspend fun upsert(staff: StaffEntity) {
        rows.value = rows.value.filterNot { it.id == staff.id } + staff
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}
