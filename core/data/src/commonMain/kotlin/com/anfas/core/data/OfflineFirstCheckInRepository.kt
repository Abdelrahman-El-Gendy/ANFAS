package com.anfas.core.data

import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.AppResult
import com.anfas.core.common.logger
import com.anfas.core.database.CheckInDao
import com.anfas.core.database.CheckInEntity
import com.anfas.core.database.MemberDao
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.model.CheckIn
import com.anfas.core.model.CheckInId
import com.anfas.core.model.CheckInOutcome
import com.anfas.core.model.CheckInPolicy
import com.anfas.core.model.CheckInSummary
import com.anfas.core.model.MemberId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal class OfflineFirstCheckInRepository(
    private val dao: CheckInDao,
    private val memberDao: MemberDao,
    private val subscriptionDao: SubscriptionDao,
    private val dispatchers: AppDispatchers,
    private val zone: TimeZone,
    private val newId: () -> String,
    private val now: () -> Instant = { Clock.System.now() },
) : CheckInRepository {

    private val log = logger("CheckIn")

    override suspend fun recordAttempt(memberId: MemberId, today: LocalDate): AppResult<CheckIn> =
        withContext(dispatchers.io) {
            runStorage("Could not record the check-in") {
                val member = memberDao.observeById(memberId.value).first()?.toDomain()
                    ?: throw IllegalStateException("No member ${memberId.value}")
                val term = subscriptionDao.observeCurrent(memberId.value).first()?.toDomain()

                val outcome = CheckInPolicy.decide(member = member, term = term, today = today)
                val checkIn = CheckIn(
                    id = CheckInId(newId()),
                    memberId = member.id,
                    // Copied, not joined: the log must still name whoever walked in even if the
                    // member is later renamed or removed.
                    memberName = member.fullName,
                    membershipNumber = member.membershipNumber,
                    at = now(),
                    outcome = outcome,
                )
                dao.insert(checkIn.toEntity())

                // Only a granted entry updates the member's last-seen stamp. A refused attempt is
                // not a visit, and showing it as one would make the directory claim an expired
                // member trained today.
                if (outcome.grantsEntry) {
                    memberDao.upsertAll(listOf(member.copy(lastCheckInAt = checkIn.at).toEntity()))
                }

                // No name and no number: the log is on disk, but a shared reception device's log file
                // must not record who entered.
                log.i("Check-in recorded: $outcome")
                checkIn
            }
        }

    override fun observeDay(date: LocalDate): Flow<AppResult<List<CheckIn>>> {
        val (from, until) = date.bounds()
        return dao.observeBetween(from, until)
            .asAppResult("Could not load check-ins") { rows -> rows.map { it.toDomain() } }
    }

    override fun observeDaySummary(date: LocalDate): Flow<AppResult<CheckInSummary>> {
        val (from, until) = date.bounds()
        return dao.observeBetween(from, until).asAppResult("Could not load check-ins") { rows ->
            CheckInSummary.of(rows.map { it.toDomain() }) { instant ->
                instant.toLocalDateTime(zone).hour
            }
        }
    }

    override fun observeMonthlyCount(memberId: MemberId, month: LocalDate): Flow<AppResult<Int>> {
        val firstOfMonth = LocalDate(month.year, month.month, 1)
        val from = firstOfMonth.atStartOfDayIn(zone).toEpochMilliseconds()
        val until = firstOfMonth.plus(1, kotlinx.datetime.DateTimeUnit.MONTH)
            .atStartOfDayIn(zone)
            .toEpochMilliseconds()
        return dao.observeGrantedCount(memberId.value, from, until)
            .asAppResult("Could not count check-ins") { it }
    }

    /**
     * The day's half-open bounds in the device's zone.
     *
     * Computed here rather than stored as a date column because "today" depends on the zone, and
     * a gym near a boundary — or a device that travels — must not have yesterday's evening
     * entries reappear in today's log.
     */
    private fun LocalDate.bounds(): Pair<Long, Long> {
        val start = atStartOfDayIn(zone).toEpochMilliseconds()
        val end = plus(1, kotlinx.datetime.DateTimeUnit.DAY).atStartOfDayIn(zone)
            .toEpochMilliseconds()
        return start to end
    }
}

private fun CheckIn.toEntity() = CheckInEntity(
    id = id.value,
    memberId = memberId?.value,
    memberName = memberName,
    membershipNumber = membershipNumber,
    atEpochMs = at.toEpochMilliseconds(),
    outcome = outcome.name,
)

private fun CheckInEntity.toDomain() = CheckIn(
    id = CheckInId(id),
    memberId = memberId?.let(::MemberId),
    memberName = memberName,
    membershipNumber = membershipNumber,
    at = Instant.fromEpochMilliseconds(atEpochMs),
    // An unrecognised value means a newer build wrote an outcome this one does not know. Reading
    // it as a denial is the safe direction: it never claims someone was let in.
    outcome = CheckInOutcome.entries.firstOrNull { it.name == outcome }
        ?: CheckInOutcome.NO_MEMBERSHIP,
)
