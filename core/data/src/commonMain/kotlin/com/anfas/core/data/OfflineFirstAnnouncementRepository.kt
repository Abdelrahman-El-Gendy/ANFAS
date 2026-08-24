package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.database.AnnouncementDao
import com.anfas.core.database.AnnouncementEntity
import com.anfas.core.database.MemberDao
import com.anfas.core.database.StaffDao
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.model.Announcement
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementId
import com.anfas.core.model.AnnouncementReach
import com.anfas.core.model.AnnouncementStatus
import com.anfas.core.model.MemberId
import com.anfas.core.model.minuteOfDayToTime
import com.anfas.core.model.toMinuteOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineFirstAnnouncementRepository(
    private val announcements: AnnouncementDao,
    private val members: MemberDao,
    private val subscriptions: SubscriptionDao,
    private val staff: StaffDao,
    private val dispatchers: AppDispatchers,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : AnnouncementRepository {

    override fun observeAll(): Flow<AppResult<List<AnnouncementDetail>>> =
        combine(announcements.observeAll(), staff.observeAll()) { rows, staffRows ->
            val names = staffRows.associate { it.id to it.displayName }
            rows.map { row -> AnnouncementDetail(row.toDomain(), names[row.createdByStaffId]) }
        }.asAppResult("Could not load announcements") { it }

    override fun observeReach(audience: AnnouncementAudience): Flow<AppResult<Int>> =
        combine(members.observeAll(), subscriptions.observeAllCurrent()) { memberRows, termRows ->
            val today = Clock.System.now().toLocalDateTime(zone).date
            val currentTerms = termRows.associate { MemberId(it.memberId) to it.toDomain() }
            AnnouncementReach.count(audience, memberRows.map { it.toDomain() }, currentTerms, today)
        }.asAppResult("Could not compute audience") { it }

    override suspend fun createDraft(
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
        createdByStaffId: String?,
        createdAt: Instant,
    ): AppResult<SaveAnnouncementOutcome> = withContext(dispatchers.io) {
        runStorage("Could not save the announcement") {
            val problems = validate(title, body)
            if (problems.isNotEmpty()) return@runStorage SaveAnnouncementOutcome.Invalid(problems)

            val id = AnnouncementId(Uuid.random().toString())
            announcements.upsert(
                AnnouncementEntity(
                    id = id.value,
                    title = title.trim(),
                    body = body.trim(),
                    audience = audience.name,
                    eventDateEpochDay = eventDate?.toEpochDays(),
                    eventMinuteOfDay = eventTime?.toMinuteOfDay(),
                    status = AnnouncementStatus.DRAFT.name,
                    createdByStaffId = createdByStaffId,
                    createdAtEpochMs = createdAt.toEpochMilliseconds(),
                    publishedAtEpochMs = null,
                    recipientCountAtPublish = null,
                ),
            )
            SaveAnnouncementOutcome.Saved(id)
        }
    }

    override suspend fun updateDraft(
        id: AnnouncementId,
        title: String,
        body: String,
        audience: AnnouncementAudience,
        eventDate: LocalDate?,
        eventTime: LocalTime?,
    ): AppResult<SaveAnnouncementOutcome> = withContext(dispatchers.io) {
        runStorage("Could not save the announcement") {
            val problems = validate(title, body)
            if (problems.isNotEmpty()) return@runStorage SaveAnnouncementOutcome.Invalid(problems)

            val existing = announcements.findById(id.value)
                ?: return@runStorage SaveAnnouncementOutcome.Invalid(emptySet())
            // Audience is frozen once published -- see the interface KDoc -- but title, body and
            // the event card may still be corrected regardless of status.
            val stillDraft = existing.status == AnnouncementStatus.DRAFT.name
            announcements.upsert(
                existing.copy(
                    title = title.trim(),
                    body = body.trim(),
                    audience = if (stillDraft) audience.name else existing.audience,
                    eventDateEpochDay = eventDate?.toEpochDays(),
                    eventMinuteOfDay = eventTime?.toMinuteOfDay(),
                ),
            )
            SaveAnnouncementOutcome.Saved(id)
        }
    }

    override suspend fun publish(id: AnnouncementId, publishedAt: Instant): AppResult<Unit> =
        withContext(dispatchers.io) {
            runStorage("Could not publish the announcement") {
                val existing = announcements.findById(id.value) ?: return@runStorage Unit
                val audience = AnnouncementAudience.entries
                    .firstOrNull { it.name == existing.audience }
                    ?: AnnouncementAudience.ALL_MEMBERS
                val today = publishedAt.toLocalDateTime(zone).date
                val memberRows = members.observeAll().first()
                val termRows = subscriptions.observeAllCurrent().first()
                val currentTerms = termRows.associate { MemberId(it.memberId) to it.toDomain() }
                val count = AnnouncementReach.count(
                    audience,
                    memberRows.map { it.toDomain() },
                    currentTerms,
                    today,
                )

                announcements.upsert(
                    existing.copy(
                        status = AnnouncementStatus.PUBLISHED.name,
                        publishedAtEpochMs = publishedAt.toEpochMilliseconds(),
                        recipientCountAtPublish = count,
                    ),
                )
            }
        }

    override suspend fun deleteDraft(id: AnnouncementId): AppResult<Unit> =
        withContext(dispatchers.io) {
            runStorage("Could not delete the announcement") {
                val existing = announcements.findById(id.value) ?: return@runStorage Unit
                if (existing.status == AnnouncementStatus.DRAFT.name) {
                    announcements.delete(id.value)
                }
            }
        }

    private fun validate(title: String, body: String): Set<AnnouncementProblem> = buildSet {
        if (title.isBlank()) add(AnnouncementProblem.TITLE_BLANK)
        if (body.isBlank()) add(AnnouncementProblem.BODY_BLANK)
    }
}

private fun AnnouncementEntity.toDomain() = Announcement(
    id = AnnouncementId(id),
    title = title,
    body = body,
    audience = AnnouncementAudience.entries.firstOrNull { it.name == audience }
        ?: AnnouncementAudience.ALL_MEMBERS,
    eventDate = eventDateEpochDay?.let { LocalDate.fromEpochDays(it) },
    eventTime = eventMinuteOfDay?.let { minuteOfDayToTime(it) },
    status = AnnouncementStatus.entries.firstOrNull { it.name == status }
        ?: AnnouncementStatus.DRAFT,
    createdByStaffId = createdByStaffId,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    publishedAt = publishedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    recipientCountAtPublish = recipientCountAtPublish,
)
