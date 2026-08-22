package com.anfas.feature.auth

import app.cash.turbine.test
import com.anfas.core.auth.CredentialProblem
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInComponentTest {

    /**
     * The state that would otherwise brick the app: a fresh install, or an existing one that has
     * just migrated to schema v6, has no staff rows. Showing it a login form would mean nobody
     * could ever get in.
     */
    @Test
    fun `no accounts opens first-run setup rather than the login form`() = runTest {
        val component = component(FakeAuthRepository(hasAccounts = false))

        component.state.test {
            assertEquals(SignInMode.FirstRun, awaitUntilNotChecking())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `existing accounts open the login form`() = runTest {
        val component = component(FakeAuthRepository(hasAccounts = true))

        component.state.test {
            assertEquals(SignInMode.SignIn, awaitUntilNotChecking())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a successful sign-in reports it once`() = runTest {
        var signedIn = 0
        val component = component(
            repository = FakeAuthRepository(
                hasAccounts = true,
                signInResult = SignInResult.Success(Session("s-1", setOf(Role.Owner))),
            ),
            onSignedIn = { signedIn++ },
        )
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("correct-horse")

        component.onSubmit()

        assertEquals(1, signedIn)
    }

    /**
     * The password must not survive a failure. A wrong value left in the field invites hammering
     * the same one, and a retained secret sits in a state object for the life of the process.
     */
    @Test
    fun `a failed sign-in clears the password and shows one message`() = runTest {
        val component = component(
            FakeAuthRepository(hasAccounts = true, signInResult = SignInResult.InvalidCredentials),
        )
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("wrong")

        component.onSubmit()

        val state = component.state.value
        assertEquals(SignInError.InvalidCredentials, state.error)
        assertEquals("", state.password)
        assertFalse(state.isSubmitting)
        // The username is kept: retyping it after every mistake is what makes a login hostile.
        assertEquals("fahd", state.username)
    }

    @Test
    fun `typing clears a previous failure`() = runTest {
        val component = component(
            FakeAuthRepository(hasAccounts = true, signInResult = SignInResult.InvalidCredentials),
        )
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("wrong")
        component.onSubmit()

        component.onPasswordChanged("r")

        assertEquals(null, component.state.value.error)
    }

    @Test
    fun `a disabled account reports its own message`() = runTest {
        val component = component(
            FakeAuthRepository(hasAccounts = true, signInResult = SignInResult.AccountDisabled),
        )
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("correct-horse")

        component.onSubmit()

        assertEquals(SignInError.AccountDisabled, component.state.value.error)
    }

    @Test
    fun `creation problems land on their fields`() = runTest {
        val component = component(
            FakeAuthRepository(
                hasAccounts = false,
                createOutcome = CreateAccountOutcome.Rejected(
                    setOf(CredentialProblem.PasswordTooShort),
                ),
            ),
        )
        component.state.test {
            awaitUntilNotChecking()
            cancelAndIgnoreRemainingEvents()
        }
        component.onDisplayNameChanged("Fahd")
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("short")

        component.onSubmit()

        assertTrue(component.state.value.problem(CredentialProblem.PasswordTooShort))
        assertFalse(component.state.value.problem(CredentialProblem.UsernameTaken))
    }

    /** Set up on another device since this screen opened: fall back rather than dead-ending. */
    @Test
    fun `an already-initialised device falls back to signing in`() = runTest {
        val component = component(
            FakeAuthRepository(
                hasAccounts = false,
                createOutcome = CreateAccountOutcome.AlreadyInitialised,
            ),
        )
        component.state.test {
            awaitUntilNotChecking()
            cancelAndIgnoreRemainingEvents()
        }
        component.onDisplayNameChanged("Fahd")
        component.onUsernameChanged("fahd")
        component.onPasswordChanged("correct-horse")

        component.onSubmit()

        assertEquals(SignInMode.SignIn, component.state.value.mode)
    }

    @Test
    fun `submitting is refused while a field is empty`() = runTest {
        val component = component(FakeAuthRepository(hasAccounts = true))
        component.state.test {
            awaitUntilNotChecking()
            cancelAndIgnoreRemainingEvents()
        }

        component.onUsernameChanged("fahd")

        assertFalse(component.state.value.canSubmit)
        component.onPasswordChanged("x")
        assertTrue(component.state.value.canSubmit)
    }

    /**
     * Regression: the mode was decided once in init. The shell creates this component eagerly,
     * before any account exists, so it latched FirstRun and still said "Set up this device" after
     * an owner had been created and signed out again — with the previous person's name and
     * username still in the form, on a shared reception device.
     */
    @Test
    fun `signing out re-evaluates the mode and clears the form`() = runTest {
        val sessions = MutableStateFlow<Session?>(null)
        val repository = FakeAuthRepository(hasAccounts = false, sessions = sessions)
        val component = component(repository)
        component.state.test { awaitUntilNotChecking(); cancelAndIgnoreRemainingEvents() }
        component.onDisplayNameChanged("Fahd")
        component.onUsernameChanged("fahd")

        // An account now exists, and the session ends.
        repository.hasAccountsNow = true
        sessions.value = Session("s-1", setOf(Role.Owner))
        sessions.value = null

        val state = component.state.value
        assertEquals(SignInMode.SignIn, state.mode)
        assertEquals("", state.username)
        assertEquals("", state.displayName)
    }

    private suspend fun app.cash.turbine.TurbineTestContext<SignInState>.awaitUntilNotChecking():
        SignInMode {
        repeat(EMISSION_ALLOWANCE) {
            val mode = awaitItem().mode
            if (mode != SignInMode.Checking) return mode
        }
        error("still Checking after $EMISSION_ALLOWANCE emissions")
    }

    private fun TestScope.component(
        repository: AuthRepository,
        onSignedIn: () -> Unit = {},
    ): SignInComponent {
        val lifecycle = LifecycleRegistry()
        val component = SignInComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = repository,
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onSignedIn = onSignedIn,
        )
        lifecycle.resume()
        return component
    }

    private companion object {
        const val EMISSION_ALLOWANCE = 5
    }
}

private class FakeAuthRepository(
    hasAccounts: Boolean,
    private val sessions: MutableStateFlow<Session?> = MutableStateFlow(null),
    private val signInResult: SignInResult = SignInResult.InvalidCredentials,
    private val createOutcome: CreateAccountOutcome =
        CreateAccountOutcome.Created(Session("s-1", setOf(Role.Owner))),
    private val failWith: AppError? = null,
) : AuthRepository {

    /** Mutable so a test can make an account appear between emissions. */
    var hasAccountsNow: Boolean = hasAccounts

    override fun observeSession(): Flow<Session?> = sessions

    override suspend fun hasAnyAccount(): AppResult<Boolean> =
        failWith?.let { AppResult.Failure(it) } ?: AppResult.Success(hasAccountsNow)

    override suspend fun signIn(username: String, password: String): AppResult<SignInResult> =
        failWith?.let { AppResult.Failure(it) } ?: AppResult.Success(signInResult)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun createFirstOwner(
        username: String,
        password: String,
        displayName: String,
    ): AppResult<CreateAccountOutcome> =
        failWith?.let { AppResult.Failure(it) } ?: AppResult.Success(createOutcome)

    override fun observeStaff(): Flow<AppResult<List<StaffAccount>>> =
        flowOf(AppResult.Success(emptyList()))
}
private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
