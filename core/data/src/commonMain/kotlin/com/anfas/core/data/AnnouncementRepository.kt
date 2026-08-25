package com.anfas.core.data

import com.anfas.core.common.AppResult
import com.anfas.core.model.Announcement
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/**
 * Staff-authored bulletins. See `Announcement`'s KDoc: this creates and tracks them, and does
 * not deliver anything to anyone — there is no member-facing surface, no push service, and the
 * WhatsApp send job is separately parked.
 */
interface AnnouncementRepository {

    fun observeAll(): Flow<AppResult<List<AnnouncementDetail>>>

    /** The live "Reaches N members" figure while composing, recomputed as membership changes. */
    fun observeReach(audience: AnnouncementAudience): Flow<AppResult<Int>>

    suspend fun createDraft(
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
        createdByStaffId: String?,
        createdAt: Instant,
    ): AppResult<SaveAnnouncementOutcome>

    /** Refused once the announcement is published — see `Announcement`'s KDoc on why audience
     * is frozen at that point. Title, body and event details may still be edited (a typo fix). */
    suspend fun updateDraft(
        id: AnnouncementId,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
    ): AppResult<SaveAnnouncementOutcome>

    /**
     * One-way. Stamps [Announcement.publishedAt] and freezes [Announcement.recipientCountAtPublish]
     * from the audience as it stands *right now* — there is no "unpublish".
     */
    suspend fun publish(id: AnnouncementId, publishedAt: Instant): AppResult<Unit>

    /** Refused for a published announcement — that history is never deleted, matching every
     * other append-only record in this app (check-ins, therapy sessions). */
    suspend fun deleteDraft(id: AnnouncementId): AppResult<Unit>
}

/** An announcement with its author's name resolved, the same shape as `TherapyCaseDetail`. */
data class AnnouncementDetail(val announcement: Announcement, val createdByName: String?)

sealed interface SaveAnnouncementOutcome {
    data class Saved(val id: AnnouncementId) : SaveAnnouncementOutcome
    data class Invalid(val problems: Set<AnnouncementProblem>) : SaveAnnouncementOutcome
}

enum class AnnouncementProblem {
    TITLE_BLANK,
    BODY_BLANK,
}
