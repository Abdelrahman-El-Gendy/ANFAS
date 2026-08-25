package com.anfas.feature.announcements

import com.anfas.core.data.AnnouncementDetail
import com.anfas.core.data.AnnouncementProblem
import com.anfas.core.model.AnnouncementAudience
import kotlinx.datetime.LocalDate

sealed interface AnnouncementsContent {
    data object Loading : AnnouncementsContent
    data class Loaded(val announcements: List<AnnouncementDetail>) : AnnouncementsContent
    data object Empty : AnnouncementsContent
    data class Failed(val message: String) : AnnouncementsContent
}

data class AnnouncementsState(
    val content: AnnouncementsContent = AnnouncementsContent.Loading,
    val form: AnnouncementForm? = null,
    /** The live "Reaches N members" figure for [AnnouncementForm.audience] as it stands right
     * now, recomputed as the picker changes and as membership data changes underneath it. */
    val liveReach: Int? = null,
    val publishConfirm: AnnouncementForm? = null,
    val deleteConfirmId: String? = null,
    val notice: AnnouncementsNotice? = null,
    /** Whether this session holds `Permission.MANAGE_ANNOUNCEMENTS`. */
    val mayManage: Boolean = false,
)

/**
 * The compose/edit form. [editing] carries the id being edited, and — for a *published*
 * announcement — [wasPublished] so the screen can withhold the audience picker: see
 * `AnnouncementRepository.updateDraft`'s KDoc on why audience freezes at publish time.
 */
data class AnnouncementForm(
    val editing: String? = null,
    val wasPublished: Boolean = false,
    val title: String = "",
    val body: String = "",
    val audience: AnnouncementAudience = AnnouncementAudience.ALL_MEMBERS,
    /** Chosen from `AnfasDateField`'s calendar, so it cannot be unreadable. Null means no event,
     * which is what hides the event-time chips. */
    val eventDate: LocalDate? = null,
    val eventHour: Int? = null,
    val eventMinute: Int = 0,
    val isSubmitting: Boolean = false,
    val problems: Set<AnnouncementProblem> = emptySet(),
) {
    val canSubmit: Boolean get() = !isSubmitting && title.isNotBlank() && body.isNotBlank()
}

sealed interface AnnouncementsNotice {
    data object DraftSaved : AnnouncementsNotice
    data object Published : AnnouncementsNotice
    data object Deleted : AnnouncementsNotice
    data class Failed(val message: String) : AnnouncementsNotice
}
