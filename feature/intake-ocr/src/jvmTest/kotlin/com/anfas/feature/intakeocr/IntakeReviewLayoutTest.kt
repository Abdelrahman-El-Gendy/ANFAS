package com.anfas.feature.intakeocr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.SignInResult
import com.anfas.core.auth.StaffAccount
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.CreateAccountOutcome
import com.anfas.core.data.StaffChangeOutcome
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.EnglishStrings
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeField
import com.anfas.core.model.IntakeRow
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.OcrBounds
import com.anfas.core.ocr.CameraAccess
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The photographed sheet must be reachable on a phone.
 *
 * Below `AnfasBreakpoints.tabletMax` this screen used to render **only** the validation table and
 * drop the source pane entirely — so the photo was unreachable on the very device that takes it,
 * and the image-rendering feature shipped one commit earlier was invisible on Android and iPhone.
 * The screen's own KDoc claimed the split "becomes stacked", and the narrow branch was a `Column`
 * with `spacedBy(16.dp)` wrapping a single child: an unfinished implementation of that intent.
 *
 * `./gradlew check` was green throughout. Only looking at a screen found it, which is why this
 * asserts presence at real phone widths rather than trusting the branch to keep both panes.
 */
@OptIn(ExperimentalTestApi::class)
class IntakeReviewLayoutTest {

    /** Both panes carry a heading, which is what makes their presence assertable. */
    private val sourcePaneHeading = EnglishStrings.intake.sourceDocument

    @Test
    fun `the source pane is present at phone widths`() {
        PHONE_WIDTHS.forEach { width ->
            runComposeUiTest {
                setReviewScreen(widthDp = width)
                onNodeWithText(sourcePaneHeading)
                    .assertExists(
                        "no \"$sourcePaneHeading\" pane at ${width}dp — the photographed sheet " +
                            "is unreachable on a phone, which is where it is taken",
                    )
            }
        }
    }

    /** Present in the tree is not the same as on screen: a zero-height pane would still exist. */
    @Test
    fun `the source pane is actually displayed at the narrowest phone width`() {
        runComposeUiTest {
            setReviewScreen(widthDp = PHONE_WIDTHS.min())
            onNodeWithText(sourcePaneHeading).assertIsDisplayed()
        }
    }

    /** The wide branch was never broken; pinned so a fix to one branch cannot break the other. */
    @Test
    fun `the source pane survives at desktop width`() {
        runComposeUiTest {
            setReviewScreen(widthDp = DESKTOP_WIDTH)
            onNodeWithText(sourcePaneHeading).assertIsDisplayed()
        }
    }

    private fun ComposeUiTest.setReviewScreen(widthDp: Int) {
        val component = reviewComponent()
        setContent {
            AnfasTheme {
                // requiredSize, NOT size: `size` is still clamped by the incoming constraints
                // of the test surface, so a width wider than the default window was silently
                // squeezed back under the breakpoint -- which made the desktop case quietly
                // exercise the narrow branch and pass for the wrong reason.
                Box(
                    Modifier.requiredSize(
                        width = widthDp.dp,
                        height = TALL_ENOUGH_TO_LAY_OUT.dp,
                    ),
                ) {
                    IntakeReviewScreen(component)
                }
            }
        }
    }

    private fun reviewComponent(): IntakeReviewComponent {
        val lifecycle = LifecycleRegistry()
        val repository = FakeIntakeRepository(listOf(oneReviewableBatch()))
        val component = IntakeReviewComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = repository,
            ingestion = IntakeIngestion(
                recogniser = FakeTextRecogniser(),
                repository = repository,
                imageStore = RecordingImageStore(),
            ),
            cameraPermissions = FakeCameraPermissions(CameraAccess.Granted),
            auth = FakeIntakeAuth(setOf(Role.Owner)),
            dispatchers = ImmediateDispatchers,
            onImported = {},
            onCloseClicked = {},
        )
        lifecycle.resume()
        return component
    }

    /**
     * One batch, one clean row, and a `sourceImageUri` — the pane renders its heading either way,
     * but a batch with an image is the realistic case and exercises the Coil branch too.
     */
    private fun oneReviewableBatch() = IntakeBatch(
        id = IntakeBatchId("b-1"),
        capturedAt = Instant.fromEpochSeconds(1_700_000_000),
        sourceImageUri = "file:///nonexistent/sheet.png",
        status = IntakeBatchStatus.REVIEWING,
        rows = listOf(
            IntakeRow(
                id = IntakeRowId("r-1"),
                ordinal = 0,
                name = IntakeField("Youssef Kamal", 0.94f),
                phone = IntakeField("+20 101 234 5678", 0.91f),
                startDate = IntakeField("1 Sep 2026", 0.9f),
                endDate = IntakeField("1 Oct 2026", 0.9f),
                plan = IntakeField("Monthly", 0.93f),
                issues = emptySet(),
                bounds = OcrBounds(left = 0.05f, top = 0.3f, right = 0.95f, bottom = 0.35f),
            ),
        ),
    )

    private companion object {
        /** Content widths after each screen's 16dp page margin: small phone, iPhone 17, Pixel. */
        val PHONE_WIDTHS = listOf(300, 370, 395)

        /** Comfortably past the breakpoint, so the wide branch is the one under test. */
        val DESKTOP_WIDTH = AnfasBreakpoints.tabletMax.value.toInt() + 300

        /** Tall enough that a stacked 40/60 split has room to lay both panes out. */
        const val TALL_ENOUGH_TO_LAY_OUT = 900
    }
}

// --- fakes ---------------------------------------------------------------------------------

private object ImmediateDispatchers : AppDispatchers {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

private class FakeIntakeAuth(private val roles: Set<Role>) : AuthRepository {
    override fun observeSession(): Flow<Session?> =
        MutableStateFlow(Session(userId = "s-1", roles = roles))

    override fun observeCurrentStaff(): Flow<StaffAccount?> = MutableStateFlow(null)
    override suspend fun hasAnyAccount(): AppResult<Boolean> = AppResult.Success(true)

    override suspend fun signIn(username: String, password: String): AppResult<SignInResult> =
        AppResult.Success(SignInResult.InvalidCredentials)

    override suspend fun signOut(): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun createFirstOwner(
        username: String,
        password: String,
        displayName: String,
    ): AppResult<CreateAccountOutcome> = AppResult.Success(CreateAccountOutcome.AlreadyInitialised)

    override fun observeStaff(): Flow<AppResult<List<StaffAccount>>> =
        MutableStateFlow(AppResult.Success(emptyList()))

    override suspend fun createStaff(
        username: String,
        password: String,
        displayName: String,
        roles: Set<Role>,
    ): AppResult<CreateAccountOutcome> = AppResult.Success(CreateAccountOutcome.AlreadyInitialised)

    override suspend fun setStaffEnabled(
        id: String,
        enabled: Boolean,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.Changed)

    override suspend fun resetStaffPassword(
        id: String,
        newPassword: String,
    ): AppResult<StaffChangeOutcome> = AppResult.Success(StaffChangeOutcome.Changed)
}
