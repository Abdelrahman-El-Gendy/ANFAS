package com.anfas.feature.intakeocr

import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.data.IntakeRepository
import com.anfas.core.ocr.IntakeImageStore

/**
 * Reconciles the captures on disk with the batches that reference them, once at startup.
 *
 * `IntakeImageStore.purgeExcept` had no production caller from the day it was written — it is
 * documented as a "startup safety net" and there was no startup that used it, so an image whose
 * batch vanished without cleanup stayed on disk forever. This is that caller.
 *
 * It lives in `:feature:intake-ocr` rather than in `:core:data` or `:core:ocr` because it needs
 * both sides — the batches table and the platform file store — and `:core:ocr` is declared only by
 * this module. Same placement reasoning as [IntakeIngestion], which sequences the same two seams.
 *
 * **The order is the invariant.** Adoption moves files into the current directory *before* they
 * are named by any row, so a purge running in between would delete exactly the sheets adoption
 * exists to rescue. Hence sequential steps in one function rather than two independent startup
 * jobs, and hence [IntakeHousekeepingTest] pinning the ordering rather than only the outcomes.
 */
class IntakeHousekeeping(
    private val repository: IntakeRepository,
    private val imageStore: IntakeImageStore,
) {
    private val log = logger("IntakeHousekeeping")

    suspend fun run() {
        adoptRelocatedCaptures()
        purgeOrphanedCaptures()
    }

    /**
     * Repoints rows at captures that have moved on disk.
     *
     * The store reports only the moves that actually happened, so a row is never repointed at a
     * file that is not there. A failed repoint is logged and skipped rather than aborting the
     * rest: one unreadable row must not strand every other adopted sheet at a path nothing names,
     * which would then be purged by the very next step.
     */
    private suspend fun adoptRelocatedCaptures() {
        val moved = imageStore.adoptLegacyCaptures()
        if (moved.isEmpty()) return

        var repointed = 0
        moved.forEach { (from, to) ->
            when (val result = repository.relocateSourceImage(from = from, to = to)) {
                is AppResult.Success -> repointed++

                is AppResult.Failure ->
                    log.w("Could not repoint a relocated capture: ${result.error}")
            }
        }
        log.i("Repointed $repointed of ${moved.size} relocated captures")
    }

    /**
     * Deletes captures no batch references any more.
     *
     * A failed read is a **hard stop**, not a warning that proceeds: an empty keep-set and "the
     * database could not be read" are indistinguishable to `purgeExcept`, and treating the second
     * as the first would delete every sheet awaiting review. The same asymmetry the design note
     * gives for `purgeExcept` under sync — an under-reported keep-set is destructive, while
     * skipping a purge costs only disk.
     */
    private suspend fun purgeOrphanedCaptures() {
        when (val uris = repository.sourceImageUris()) {
            is AppResult.Failure ->
                log.w("Skipped the capture purge; could not read the batches: ${uris.error}")

            is AppResult.Success -> imageStore.purgeExcept(uris.value.toSet())
        }
    }
}
