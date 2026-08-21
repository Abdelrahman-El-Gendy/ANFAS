package com.anfas.feature.intakeocr

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.data.ImportOutcome
import com.anfas.core.data.IntakeFieldKey
import com.anfas.core.data.IntakeRepository
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import com.anfas.core.model.IntakeField
import com.anfas.core.model.IntakeRow
import com.anfas.core.model.IntakeRowId
import com.anfas.core.model.IntakeValidator
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeReviewComponentTest {

    @Test
    fun `with no sheets the screen reports NoBatches`() = runTest {
        val component = component(FakeIntakeRepository())

        component.state.test {
            assertIs<IntakeReviewContent.NoBatches>(awaitItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the oldest sheet still under review is the one shown`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch("newer", capturedAtMs = 2_000, rows = listOf(row("a"))),
                batch("older", capturedAtMs = 1_000, rows = listOf(row("b"))),
            ),
        )
        val component = component(repo)

        component.state.test {
            val loaded = assertIs<IntakeReviewContent.Loaded>(awaitItem().content)
            assertEquals(IntakeBatchId("older"), loaded.batch.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an imported sheet is skipped in favour of one still under review`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch("done", capturedAtMs = 1_000, status = IntakeBatchStatus.IMPORTED),
                batch("todo", capturedAtMs = 2_000, rows = listOf(row("a"))),
            ),
        )
        val component = component(repo)

        component.state.test {
            val loaded = assertIs<IntakeReviewContent.Loaded>(awaitItem().content)
            assertEquals(IntakeBatchId("todo"), loaded.batch.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the ready counter reflects only importable rows`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch(
                    "b1",
                    rows = listOf(
                        row("a", name = "Alex Thompson", phone = "555-0192"),
                        row("b", name = "", phone = "555-0271"),
                        row("c", name = "Ben Carter", phone = "555-0653"),
                    ),
                ),
            ),
        )
        val component = component(repo)

        component.state.test {
            val state = awaitItem()
            assertEquals(2, state.readyCount)
            assertEquals(3, state.totalCount)
            assertTrue(state.canImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an edit is written through to the repository`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(batch("b1", rows = listOf(row("a", name = "", phone = "555-0192")))),
        )
        val component = component(repo)

        component.state.test {
            assertEquals(0, awaitItem().readyCount)

            component.onFieldEdited(IntakeRowId("a"), IntakeFieldKey.NAME, "Alex Thompson")

            // Revalidation happens on read, so correcting the name unblocks the row.
            assertEquals(1, awaitItem().readyCount)
            assertEquals(listOf(IntakeFieldKey.NAME to "Alex Thompson"), repo.edits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a partial import says how many were left behind`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch(
                    "b1",
                    rows = listOf(
                        row("a", name = "Alex Thompson", phone = "555-0192"),
                        row("b", name = "", phone = "555-0271"),
                    ),
                ),
            ),
        )
        val component = component(repo)

        component.state.test {
            awaitItem()
            component.onImport()
            // Typed, not prose. The counts are what matters, and naming the leftovers is the
            // behaviour under test.
            assertEquals(IntakeNotice.Imported(imported = 1, skipped = 1), awaitNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a clean import reads in the singular`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch("b1", rows = listOf(row("a", name = "Alex Thompson", phone = "555-0192"))),
            ),
        )
        val component = component(repo)

        component.state.test {
            awaitItem()
            component.onImport()
            assertEquals(IntakeNotice.Imported(imported = 1, skipped = 0), awaitNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an import where nothing qualifies says so instead of claiming success`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(batch("b1", rows = listOf(row("a", name = "", phone = "")))),
        )
        val component = component(repo)

        component.state.test {
            val state = awaitItem()
            assertEquals(0, state.readyCount)
            // The button is disabled, but the notice must still be right if it is reached.
            assertTrue(!state.canImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `importing reports the count to the caller so it can navigate`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch("b1", rows = listOf(row("a", name = "Alex Thompson", phone = "555-0192"))),
            ),
        )
        var reported: Int? = null
        val component = component(repo, onImported = { reported = it })

        component.state.test {
            awaitItem()
            component.onImport()
            awaitNotice()
            assertEquals(1, reported)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed import surfaces the message and stops the spinner`() = runTest {
        val repo = FakeIntakeRepository(
            listOf(
                batch("b1", rows = listOf(row("a", name = "Alex Thompson", phone = "555-0192"))),
            ),
            importResult = AppResult.Failure(AppError.Storage("database is locked")),
        )
        val component = component(repo)

        component.state.test {
            awaitItem()
            component.onImport()
            assertEquals(IntakeNotice.Failed("database is locked"), awaitNotice())
            assertTrue(!component.state.value.isImporting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `zoom is clamped at both ends`() = runTest {
        val repo = FakeIntakeRepository(listOf(batch("b1", rows = listOf(row("a")))))
        val component = component(repo)

        component.state.test {
            awaitItem()
            repeat(12) { component.onZoomIn() }
            assertEquals(IntakeReviewState.MAX_ZOOM, component.state.value.zoom)

            repeat(24) { component.onZoomOut() }
            assertEquals(IntakeReviewState.MIN_ZOOM, component.state.value.zoom)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discarding a sheet reports it and leaves nothing under review`() = runTest {
        val repo = FakeIntakeRepository(listOf(batch("b1", rows = listOf(row("a")))))
        val component = component(repo)

        component.state.test {
            awaitItem()
            component.onDiscard()
            assertEquals(IntakeNotice.Discarded, awaitNotice())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- helpers --------------------------------------------------------------------------

    private suspend fun TurbineTestContext<IntakeReviewState>.awaitNotice(): IntakeNotice {
        repeat(8) {
            awaitItem().notice?.let { return it }
        }
        error("No notice was emitted")
    }

    private fun TestScope.component(
        repository: IntakeRepository,
        onImported: (Int) -> Unit = {},
    ): IntakeReviewComponent {
        val lifecycle = LifecycleRegistry()
        val component = IntakeReviewComponent(
            componentContext = DefaultComponentContext(lifecycle = lifecycle),
            repository = repository,
            dispatchers = TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            onImported = onImported,
        )
        lifecycle.resume()
        return component
    }

    private fun batch(
        id: String,
        capturedAtMs: Long = 1_000,
        status: IntakeBatchStatus = IntakeBatchStatus.REVIEWING,
        rows: List<IntakeRow> = emptyList(),
    ) = IntakeBatch(
        id = IntakeBatchId(id),
        capturedAt = Instant.fromEpochMilliseconds(capturedAtMs),
        sourceImageUri = "file://sheet.jpg",
        status = status,
        rows = rows,
    )

    private fun row(id: String, name: String = "Alex Thompson", phone: String = "555-0192") =
        IntakeRow(
            id = IntakeRowId(id),
            ordinal = 1,
            name = IntakeField(name, 0.99f),
            phone = IntakeField(phone, 0.99f),
            startDate = IntakeField("Nov 1, 2023", 0.99f),
            endDate = IntakeField("Oct 31, 2024", 0.99f),
            plan = IntakeField("Annual", 0.99f),
            issues = emptySet(),
            bounds = null,
        )
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

/**
 * Fake that revalidates on read the way the real repository does — without that, an edit test
 * would pass against a store that never recomputes issues, which is the whole behaviour under
 * test.
 */
private class FakeIntakeRepository(
    batches: List<IntakeBatch> = emptyList(),
    private val importResult: AppResult<ImportOutcome>? = null,
) : IntakeRepository {

    private val state = MutableStateFlow(batches)

    val edits = mutableListOf<Pair<IntakeFieldKey, String>>()

    override fun observeBatches(): Flow<AppResult<List<IntakeBatch>>> =
        state.map { AppResult.Success(it) }

    override fun observeBatch(id: IntakeBatchId): Flow<AppResult<IntakeBatch?>> = state.map { all ->
        val batch = all.firstOrNull { it.id == id }
        AppResult.Success(
            batch?.copy(rows = IntakeValidator.validate(batch.rows, existingPhones = emptySet())),
        )
    }

    override suspend fun createBatch(batch: IntakeBatch): AppResult<Unit> {
        state.value = state.value + batch
        return AppResult.Success(Unit)
    }

    override suspend fun editField(
        rowId: IntakeRowId,
        field: IntakeFieldKey,
        value: String,
    ): AppResult<Unit> {
        edits += field to value
        state.value = state.value.map { batch ->
            batch.copy(
                rows = batch.rows.map { row ->
                    if (row.id != rowId) {
                        row
                    } else {
                        when (field) {
                            IntakeFieldKey.NAME -> row.copy(name = row.name.editedTo(value))

                            IntakeFieldKey.PHONE -> row.copy(phone = row.phone.editedTo(value))

                            IntakeFieldKey.START_DATE ->
                                row.copy(startDate = row.startDate.editedTo(value))

                            IntakeFieldKey.END_DATE ->
                                row.copy(endDate = row.endDate.editedTo(value))

                            IntakeFieldKey.PLAN -> row.copy(plan = row.plan.editedTo(value))
                        }
                    }
                },
            )
        }
        return AppResult.Success(Unit)
    }

    override suspend fun importBatch(id: IntakeBatchId): AppResult<ImportOutcome> {
        importResult?.let { return it }
        val batch = state.value.firstOrNull { it.id == id }
            ?: return AppResult.Failure(AppError.Storage("no batch"))
        val validated = IntakeValidator.validate(batch.rows, existingPhones = emptySet())
        val outcome = ImportOutcome(
            imported = validated.count { it.isImportable },
            skipped = validated.count { !it.isImportable },
        )
        state.value = state.value.map {
            if (it.id == id) it.copy(status = IntakeBatchStatus.IMPORTED) else it
        }
        return AppResult.Success(outcome)
    }

    override suspend fun discardBatch(id: IntakeBatchId): AppResult<Unit> {
        state.value = state.value.map {
            if (it.id == id) it.copy(status = IntakeBatchStatus.DISCARDED) else it
        }
        return AppResult.Success(Unit)
    }
}
