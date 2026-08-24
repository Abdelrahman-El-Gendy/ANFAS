package com.anfas.feature.announcements

import com.anfas.core.auth.Permission
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AnnouncementDetail
import com.anfas.core.data.AnnouncementProblem
import com.anfas.core.data.AnnouncementRepository
import com.anfas.core.data.AuthRepository
import com.anfas.core.data.SaveAnnouncementOutcome
import com.anfas.core.model.AnnouncementId
import com.anfas.core.model.IntakeValidator
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlin.time.Clock

/**
 * Compose, edit, publish and delete gym-wide bulletins.
 *
 * One component for both the list and the form, the same shape as `ClassesComponent`: the two
 * always appear together on this screen, and splitting them would mean two places the same
 * audience-reach figure could disagree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementsComponent(
    componentContext: ComponentContext,
    private val repository: AnnouncementRepository,
    private val auth: AuthRepository,
    private val dispatchers: AppDispatchers,
    private val clock: Clock = Clock.System,
) : ComponentContext by componentContext {

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Announcements"))

    private val ui = MutableStateFlow(UiState())

    /**
     * Read synchronously by [onSubmitForm]/[onPublishConfirmed] to attribute a new draft to
     * whoever is signed in — the router already enforces `MANAGE_ANNOUNCEMENTS` before this
     * screen composes, so this is attribution, not a permission check.
     */
    private val currentStaffId: StateFlow<String?> = auth.observeSession()
        .map { it?.userId }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    val state: StateFlow<AnnouncementsState> = combine(
        ui,
        auth.observeSession(),
        repository.observeAll(),
        // Whichever of the two the audience picker is currently expressed through -- the
        // open form, or the confirm dialog it was moved into by onRequestPublish -- so the
        // "Reaches N members" figure never goes stale mid-flow.
        ui.flatMapLatest { local ->
            val audience = local.form?.audience ?: local.publishConfirm?.audience
            if (audience == null) flowOf(null) else repository.observeReach(audience)
        },
    ) { local, session, announcementsResult, reachResult ->
        AnnouncementsState(
            content = when (announcementsResult) {
                is AppResult.Failure ->
                    AnnouncementsContent.Failed(announcementsResult.error.message)

                is AppResult.Success -> announcementsResult.value.toContent()
            },
            form = local.form,
            liveReach = (reachResult as? AppResult.Success)?.value,
            publishConfirm = local.publishConfirm,
            deleteConfirmId = local.deleteConfirmId,
            notice = local.notice,
            mayManage = session?.can(Permission.MANAGE_ANNOUNCEMENTS) == true,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AnnouncementsState(),
    )

    fun onNoticeShown() = ui.update { it.copy(notice = null) }

    fun onNewAnnouncement() = ui.update {
        it.copy(form = AnnouncementForm(), notice = null)
    }

    fun onEditAnnouncement(id: AnnouncementId) {
        val detail = detailFor(id) ?: return
        val announcement = detail.announcement
        ui.update {
            it.copy(
                form = AnnouncementForm(
                    editing = id.value,
                    wasPublished = !announcement.isDraft,
                    title = announcement.title,
                    body = announcement.body,
                    audience = announcement.audience,
                    eventDateText = announcement.eventDate?.toString().orEmpty(),
                    eventHour = announcement.eventTime?.hour,
                    eventMinute = announcement.eventTime?.minute ?: 0,
                ),
                notice = null,
            )
        }
    }

    fun onFormDismissed() = ui.update { it.copy(form = null) }

    fun onFormChanged(transform: (AnnouncementForm) -> AnnouncementForm) = ui.update { local ->
        local.copy(form = local.form?.let { transform(it).copy(problems = emptySet()) })
    }

    fun onSubmitForm() {
        val form = state.value.form ?: return
        if (!form.canSubmit) return

        val trimmedDate = form.eventDateText.trim()
        val eventDate = if (trimmedDate.isEmpty()) null else IntakeValidator.parseDate(trimmedDate)
        if (trimmedDate.isNotEmpty() && eventDate == null) {
            ui.update {
                it.copy(
                    form = it.form?.copy(
                        problems = setOf(AnnouncementProblem.EVENT_DATE_UNREADABLE),
                    ),
                )
            }
            return
        }
        val eventTime = eventDate?.let {
            form.eventHour?.let { h -> LocalTime(h, form.eventMinute) }
        }

        ui.update { it.copy(form = form.copy(isSubmitting = true)) }

        scope.launch {
            val editingId = form.editing?.let { AnnouncementId(it) }
            val result = if (editingId != null) {
                repository.updateDraft(
                    id = editingId,
                    title = form.title,
                    body = form.body,
                    audience = form.audience,
                    eventDate = eventDate,
                    eventTime = eventTime,
                )
            } else {
                repository.createDraft(
                    title = form.title,
                    body = form.body,
                    audience = form.audience,
                    eventDate = eventDate,
                    eventTime = eventTime,
                    createdByStaffId = currentStaffId.value,
                    createdAt = clock.now(),
                )
            }

            when (result) {
                is AppResult.Failure -> ui.update {
                    it.copy(
                        form = it.form?.copy(isSubmitting = false),
                        notice = AnnouncementsNotice.Failed(result.error.message),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    is SaveAnnouncementOutcome.Invalid -> ui.update {
                        it.copy(
                            form = it.form?.copy(isSubmitting = false, problems = outcome.problems),
                        )
                    }

                    is SaveAnnouncementOutcome.Saved -> ui.update {
                        it.copy(form = null, notice = AnnouncementsNotice.DraftSaved)
                    }
                }
            }
        }
    }

    /** Opens a confirmation rather than publishing immediately — see `AnnouncementForm.editing`
     * on why there is no "unpublish" to undo a mis-tap. */
    fun onRequestPublish() {
        val form = state.value.form ?: return
        // There is no unpublish, so a form already published must not be able to fire this a
        // second time and silently overwrite the frozen recipient count and publishedAt. The
        // dialog already withholds the button for this case; this is the same rule enforced
        // where it cannot be bypassed by a component method being callable from anywhere.
        if (form.wasPublished) return
        ui.update { it.copy(form = null, publishConfirm = form) }
    }

    fun onPublishDismissed() = ui.update {
        it.copy(publishConfirm = null, form = it.publishConfirm)
    }

    fun onPublishConfirmed() {
        val form = state.value.publishConfirm ?: return
        val editingId = form.editing?.let { AnnouncementId(it) }
        if (editingId == null) {
            // A brand-new draft cannot be published before it has ever been saved -- save it
            // first, in the same shape it was confirmed in, then publish the row that creates.
            scope.launch {
                val created = repository.createDraft(
                    title = form.title,
                    body = form.body,
                    audience = form.audience,
                    eventDate = form.eventDateText.trim().takeIf { it.isNotEmpty() }
                        ?.let(IntakeValidator::parseDate),
                    eventTime = form.eventHour?.let { LocalTime(it, form.eventMinute) },
                    createdByStaffId = currentStaffId.value,
                    createdAt = clock.now(),
                )
                val id =
                    ((created as? AppResult.Success)?.value as? SaveAnnouncementOutcome.Saved)?.id
                ui.update { it.copy(publishConfirm = null) }
                if (id != null) publishNow(id)
            }
        } else {
            ui.update { it.copy(publishConfirm = null) }
            publishNow(editingId)
        }
    }

    private fun publishNow(id: AnnouncementId) {
        scope.launch {
            when (val result = repository.publish(id, clock.now())) {
                is AppResult.Failure ->
                    ui.update { it.copy(notice = AnnouncementsNotice.Failed(result.error.message)) }

                is AppResult.Success ->
                    ui.update { it.copy(notice = AnnouncementsNotice.Published) }
            }
        }
    }

    fun onRequestDelete(id: AnnouncementId) = ui.update { it.copy(deleteConfirmId = id.value) }

    fun onDeleteDismissed() = ui.update { it.copy(deleteConfirmId = null) }

    fun onDeleteConfirmed() {
        val id = state.value.deleteConfirmId?.let { AnnouncementId(it) } ?: return
        ui.update { it.copy(deleteConfirmId = null) }
        scope.launch {
            when (val result = repository.deleteDraft(id)) {
                is AppResult.Failure ->
                    ui.update { it.copy(notice = AnnouncementsNotice.Failed(result.error.message)) }

                is AppResult.Success ->
                    ui.update { it.copy(notice = AnnouncementsNotice.Deleted) }
            }
        }
    }

    private fun detailFor(id: AnnouncementId): AnnouncementDetail? =
        (state.value.content as? AnnouncementsContent.Loaded)
            ?.announcements
            ?.firstOrNull { it.announcement.id == id }

    private fun List<AnnouncementDetail>.toContent(): AnnouncementsContent =
        if (isEmpty()) AnnouncementsContent.Empty else AnnouncementsContent.Loaded(this)

    private data class UiState(
        val form: AnnouncementForm? = null,
        val publishConfirm: AnnouncementForm? = null,
        val deleteConfirmId: String? = null,
        val notice: AnnouncementsNotice? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
