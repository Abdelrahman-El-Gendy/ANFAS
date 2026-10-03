package com.anfas.feature.intakeocr

import com.anfas.core.common.AppError
import com.anfas.core.common.AppResult
import com.anfas.core.model.IntakeBatch
import com.anfas.core.model.IntakeBatchId
import com.anfas.core.model.IntakeBatchStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `purgeExcept` deletes files. Every test here exists because a wrong answer costs a sheet
 * somebody photographed and has not reviewed yet, and the two ways to get it wrong are opposite:
 * purging too eagerly, or repointing rows at files that were never moved.
 */
class IntakeHousekeepingTest {

    @Test
    fun `a capture no batch references is purged and a referenced one is kept`() = runTest {
        val repository = FakeIntakeRepository(listOf(batch("b1", uri = LIVE)))
        val store = RecordingImageStore()

        IntakeHousekeeping(repository, store).run()

        val kept = store.purgedKeeping.single()
        assertEquals(setOf(LIVE), kept)
    }

    /**
     * The ordering invariant, and the reason this class sequences its two steps rather than
     * registering them as independent startup jobs.
     *
     * Adoption moves a file into the current directory before any row names it. If the purge ran
     * first it would build its keep-set from the *old* path, find the newly-moved file unaccounted
     * for, and delete it — so the step written to rescue those sheets would be the step that
     * destroys them.
     *
     * Asserted on the keep-set's *contents*, not on the call order alone: `events` would still
     * read `[adopt, purge]` if the repoint silently failed, and the keep-set is what actually
     * decides whether the file survives.
     */
    @Test
    fun `a relocated capture is repointed before the purge decides what to keep`() = runTest {
        val repository = FakeIntakeRepository(listOf(batch("b1", uri = LEGACY)))
        val store = RecordingImageStore(legacyCaptures = mapOf(LEGACY to ADOPTED))

        IntakeHousekeeping(repository, store).run()

        assertEquals(listOf("adopt", "purge"), store.events)
        assertEquals(listOf(LEGACY to ADOPTED), repository.relocations)

        val kept = store.purgedKeeping.single()
        assertContains(kept, ADOPTED)
        assertTrue(
            LEGACY !in kept,
            "the keep-set still names the pre-move path, so the purge ran against stale rows " +
                "and the adopted sheet would be deleted",
        )
    }

    /**
     * One unreadable row must not strand the rest. Everything adopted in the same pass is sitting
     * at a path nothing names yet, so aborting the loop would hand the very next step a keep-set
     * missing those files.
     */
    @Test
    fun `a failed repoint does not stop the remaining ones from being attempted`() = runTest {
        val repository = FakeIntakeRepository(
            batches = listOf(batch("b1", uri = LEGACY), batch("b2", uri = "file://old/two.jpg")),
            relocateResult = AppResult.Failure(AppError.Storage("locked")),
        )
        val store = RecordingImageStore(
            legacyCaptures = mapOf(LEGACY to ADOPTED, "file://old/two.jpg" to "file://new/two.jpg"),
        )

        IntakeHousekeeping(repository, store).run()

        assertEquals(2, repository.relocations.size, "the loop aborted on the first failure")
    }

    /**
     * The destructive asymmetry, pinned. An empty keep-set and "the batches could not be read"
     * are indistinguishable to `purgeExcept`, and reading the second as the first deletes every
     * sheet awaiting review. Skipping a purge costs disk; getting this wrong costs the data.
     */
    @Test
    fun `an unreadable batch list skips the purge rather than deleting everything`() = runTest {
        val repository = FakeIntakeRepository(
            batches = listOf(batch("b1", uri = LIVE)),
            urisResult = AppResult.Failure(AppError.Storage("database is locked")),
        )
        val store = RecordingImageStore()

        IntakeHousekeeping(repository, store).run()

        assertTrue(
            store.purgedKeeping.isEmpty(),
            "a purge ran on an unreadable keep-set, which deletes every unreviewed sheet",
        )
    }

    @Test
    fun `nothing to adopt means no rows are touched and the purge still runs`() = runTest {
        val repository = FakeIntakeRepository(listOf(batch("b1", uri = LIVE)))
        val store = RecordingImageStore()

        IntakeHousekeeping(repository, store).run()

        assertTrue(repository.relocations.isEmpty())
        assertEquals(listOf("adopt", "purge"), store.events)
    }

    private fun batch(id: String, uri: String) = IntakeBatch(
        id = IntakeBatchId(id),
        capturedAt = Instant.fromEpochMilliseconds(1_000),
        sourceImageUri = uri,
        status = IntakeBatchStatus.REVIEWING,
        rows = emptyList(),
    )

    private companion object {
        const val LIVE = "file://new/live.jpg"
        const val LEGACY = "file://old/sheet.jpg"
        const val ADOPTED = "file://new/sheet.jpg"
    }
}
