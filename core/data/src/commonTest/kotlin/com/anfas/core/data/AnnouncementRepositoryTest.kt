package com.anfas.core.data

import app.cash.turbine.test
import com.anfas.core.database.AnnouncementDao
import com.anfas.core.database.AnnouncementEntity
import com.anfas.core.database.StaffDao
import com.anfas.core.database.StaffEntity
import com.anfas.core.database.SubscriptionDao
import com.anfas.core.database.SubscriptionEntity
import com.anfas.core.database.SubscriptionPlanEntity
import com.anfas.core.database.SyncOutboxEntity
import com.anfas.core.database.SyncTables
import com.anfas.core.database.SyncTombstoneEntity
import com.anfas.core.model.AnnouncementAudience
import com.anfas.core.model.AnnouncementId
import com.anfas.core.model.AnnouncementStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AnnouncementRepositoryTest {

    private val createdAt = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun `a draft is saved and read back with the given fields`() = runTest {
        val repository = repository()

        val outcome = repository.createDraft(
            title = "  New squat racks  ",
            body = "  Great news team!  ",
            audience = AnnouncementAudience.ACTIVE_ONLY,
            eventDate = LocalDate.parse("2026-08-16"),
            eventTime = LocalTime(18, 0),
            createdByStaffId = "s-1",
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id

        repository.observeAll().test {
            val detail = awaitItem().valueOrFail().single()
            assertEquals(id, detail.announcement.id)
            assertEquals("New squat racks", detail.announcement.title)
            assertEquals("Great news team!", detail.announcement.body)
            assertEquals(AnnouncementStatus.DRAFT, detail.announcement.status)
            assertEquals(AnnouncementAudience.ACTIVE_ONLY, detail.announcement.audience)
            assertEquals(LocalDate.parse("2026-08-16"), detail.announcement.eventDate)
            assertEquals(LocalTime(18, 0), detail.announcement.eventTime)
            assertNull(detail.announcement.publishedAt)
            assertNull(detail.announcement.recipientCountAtPublish)
        }
    }

    @Test
    fun `the author is named from the staff table`() = runTest {
        val staff = FakeAnnouncementStaff(mapOf("s-1" to "Ahmed Owner"))
        val repository = repository(staff = staff)
        repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = "s-1",
            createdAt = createdAt,
        ).valueOrFail()

        repository.observeAll().test {
            assertEquals("Ahmed Owner", awaitItem().valueOrFail().single().createdByName)
        }
    }

    @Test
    fun `a blank title or body is refused and nothing is written`() = runTest {
        val repository = repository()

        val outcome = repository.createDraft(
            title = "   ",
            body = "   ",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()

        assertEquals(
            setOf(AnnouncementProblem.TITLE_BLANK, AnnouncementProblem.BODY_BLANK),
            assertIs<SaveAnnouncementOutcome.Invalid>(outcome).problems,
        )
        repository.observeAll().test {
            assertTrue(awaitItem().valueOrFail().isEmpty())
        }
    }

    @Test
    fun `publishing stamps publishedAt and freezes a real recipient count`() = runTest {
        val members = FakeMemberDao(
            listOf(
                memberEntity("m-1", "Active One", status = "ACTIVE"),
                memberEntity("m-2", "Active Two", status = "ACTIVE"),
                memberEntity("m-3", "Expired One", status = "EXPIRED"),
            ),
        )
        val repository = repository(members = members)
        val outcome = repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ACTIVE_ONLY,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id
        val publishedAt = Instant.fromEpochSeconds(1_800_000_000)

        repository.publish(id, publishedAt).valueOrFail()

        repository.observeAll().test {
            val announcement = awaitItem().valueOrFail().single().announcement
            assertEquals(AnnouncementStatus.PUBLISHED, announcement.status)
            assertEquals(publishedAt, announcement.publishedAt)
            assertEquals(2, announcement.recipientCountAtPublish)
        }
    }

    /** The frozen count must not silently recompute if membership changes after publishing. */
    @Test
    fun `the recipient count does not change after a member is added later`() = runTest {
        val members = FakeMemberDao(listOf(memberEntity("m-1", "One", status = "ACTIVE")))
        val repository = repository(members = members)
        val outcome = repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id
        repository.publish(id, Instant.fromEpochSeconds(1_800_000_000)).valueOrFail()

        members.upsertAll(listOf(memberEntity("m-2", "Two", status = "ACTIVE")))

        repository.observeAll().test {
            assertEquals(1, awaitItem().valueOrFail().single().announcement.recipientCountAtPublish)
        }
    }

    @Test
    fun `editing a draft changes its fields`() = runTest {
        val repository = repository()
        val outcome = repository.createDraft(
            title = "Old title",
            body = "Old body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id

        repository.updateDraft(
            id = id,
            title = "New title",
            body = "New body",
            audience = AnnouncementAudience.ACTIVE_ONLY,
            eventDate = null,
            eventTime = null,
        ).valueOrFail()

        repository.observeAll().test {
            val announcement = awaitItem().valueOrFail().single().announcement
            assertEquals("New title", announcement.title)
            assertEquals(AnnouncementAudience.ACTIVE_ONLY, announcement.audience)
        }
    }

    /** Audience is frozen at publish time; editing afterwards must not silently change it. */
    @Test
    fun `editing a published announcement does not change its audience`() = runTest {
        val repository = repository()
        val outcome = repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id
        repository.publish(id, Instant.fromEpochSeconds(1_800_000_000)).valueOrFail()

        repository.updateDraft(
            id = id,
            title = "Fixed a typo",
            body = "Body",
            audience = AnnouncementAudience.ACTIVE_ONLY,
            eventDate = null,
            eventTime = null,
        ).valueOrFail()

        repository.observeAll().test {
            val announcement = awaitItem().valueOrFail().single().announcement
            assertEquals("Fixed a typo", announcement.title)
            assertEquals(AnnouncementAudience.ALL_MEMBERS, announcement.audience)
        }
    }

    @Test
    fun `a draft may be deleted`() = runTest {
        val repository = repository()
        val outcome = repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id

        repository.deleteDraft(id).valueOrFail()

        repository.observeAll().test {
            assertTrue(awaitItem().valueOrFail().isEmpty())
        }
    }

    /** History is never deleted, matching every other append-only record in this app. */
    @Test
    fun `a published announcement cannot be deleted`() = runTest {
        val repository = repository()
        val outcome = repository.createDraft(
            title = "Title",
            body = "Body",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = createdAt,
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(outcome).id
        repository.publish(id, Instant.fromEpochSeconds(1_800_000_000)).valueOrFail()

        repository.deleteDraft(id).valueOrFail()

        repository.observeAll().test {
            assertEquals(1, awaitItem().valueOrFail().size)
        }
    }

    @Test
    fun `observeReach reports a live count for the given audience`() = runTest {
        val members = FakeMemberDao(
            listOf(
                memberEntity("m-1", "One", status = "ACTIVE"),
                memberEntity("m-2", "Two", status = "EXPIRED"),
            ),
        )
        val repository = repository(members = members)

        repository.observeReach(AnnouncementAudience.ACTIVE_ONLY).test {
            assertEquals(1, awaitItem().valueOrFail())
        }
    }

    /** Every write path here files an outbox entry. See SyncOutboxTest for why that matters. */
    @Test
    fun `each announcement write files an outbox entry`() = runTest {
        val dao = FakeAnnouncementDao()
        val repository = OfflineFirstAnnouncementRepository(
            announcements = dao,
            members = FakeMemberDao(),
            subscriptions = FakeAnnouncementSubscriptions(emptyList()),
            staff = FakeAnnouncementStaff(emptyMap()),
            dispatchers = UnconfinedDispatchers,
        )

        val saved = repository.createDraft(
            title = "Racks",
            body = "News",
            audience = AnnouncementAudience.ALL_MEMBERS,
            eventDate = null,
            eventTime = null,
            createdByStaffId = null,
            createdAt = Instant.fromEpochMilliseconds(0),
        ).valueOrFail()
        val id = assertIs<SaveAnnouncementOutcome.Saved>(saved).id
        assertEquals(listOf(id.value), dao.sync.upserts(SyncTables.ANNOUNCEMENTS))

        repository.updateDraft(
            id,
            "Racks",
            "More news",
            AnnouncementAudience.ALL_MEMBERS,
            null,
            null,
        )
            .valueOrFail()
        repository.publish(id, Instant.fromEpochMilliseconds(1)).valueOrFail()
        assertEquals(3, dao.sync.upserts(SyncTables.ANNOUNCEMENTS).size)

        // A published announcement is not deletable, so the delete path needs a fresh draft.
        val draft = assertIs<SaveAnnouncementOutcome.Saved>(
            repository.createDraft(
                title = "Temp",
                body = "Temp",
                audience = AnnouncementAudience.ALL_MEMBERS,
                eventDate = null,
                eventTime = null,
                createdByStaffId = null,
                createdAt = Instant.fromEpochMilliseconds(2),
            ).valueOrFail(),
        ).id
        repository.deleteDraft(draft).valueOrFail()
        assertEquals(listOf(draft.value), dao.sync.deletes(SyncTables.ANNOUNCEMENTS))
        assertEquals(listOf(draft.value), dao.sync.tombstoned(SyncTables.ANNOUNCEMENTS))
    }

    private fun repository(
        members: FakeMemberDao = FakeMemberDao(),
        subscriptions: SubscriptionDao = FakeAnnouncementSubscriptions(emptyList()),
        staff: StaffDao = FakeAnnouncementStaff(emptyMap()),
    ): AnnouncementRepository = OfflineFirstAnnouncementRepository(
        announcements = FakeAnnouncementDao(),
        members = members,
        subscriptions = subscriptions,
        staff = staff,
        dispatchers = UnconfinedDispatchers,
    )
}

internal class FakeAnnouncementDao : AnnouncementDao {
    private val rows = MutableStateFlow<List<AnnouncementEntity>>(emptyList())

    override fun observeAll(): Flow<List<AnnouncementEntity>> =
        rows.map { list -> list.sortedByDescending { it.createdAtEpochMs } }

    override suspend fun findById(id: String): AnnouncementEntity? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(announcement: AnnouncementEntity) {
        rows.value = rows.value.filterNot { it.id == announcement.id } + announcement
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    // --- sync bookkeeping. The tracked writes are default methods on the DAO, so implementing
    // these two gives this fake the production sequencing rather than a re-implementation of it.
    val sync = OutboxRecorder()

    override suspend fun recordChange(entry: SyncOutboxEntity) = sync.record(entry)

    override suspend fun recordTombstones(entries: List<SyncTombstoneEntity>) = sync.record(entries)
}

internal class FakeAnnouncementSubscriptions(terms: List<SubscriptionEntity>) : SubscriptionDao {
    private val rows = MutableStateFlow(terms)

    override fun observePlans(): Flow<List<SubscriptionPlanEntity>> = MutableStateFlow(emptyList())
    override suspend fun upsertPlans(plans: List<SubscriptionPlanEntity>) = Unit

    override fun observeCurrent(memberId: String): Flow<SubscriptionEntity?> = rows.map { list ->
        list.filter { it.memberId == memberId }.maxByOrNull { it.endsOnEpochDay }
    }

    override fun observeAllCurrent(): Flow<List<SubscriptionEntity>> = rows

    override suspend fun upsert(subscription: SubscriptionEntity) {
        rows.value = rows.value.filterNot { it.id == subscription.id } + subscription
    }

    // --- sync bookkeeping
    val sync = OutboxRecorder()

    override suspend fun recordChange(entry: SyncOutboxEntity) = sync.record(entry)

    override suspend fun insertPlansIfAbsent(plans: List<SubscriptionPlanEntity>) {
        val known = planRowsForSeed.map { it.id }.toSet()
        planRowsForSeed += plans.filterNot { it.id in known }
    }

    override suspend fun planIds(): List<String> = planRowsForSeed.map { it.id }

    /** Plan rows as the seed sees them. Separate from whatever the fake models for reads. */
    val planRowsForSeed = mutableListOf<SubscriptionPlanEntity>()
}

internal class FakeAnnouncementStaff(initial: Map<String, String>) : StaffDao {
    private val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<StaffEntity>> = rows.map { map ->
        map.map { (id, name) -> staffEntity(id, name) }
    }

    override suspend fun findByUsername(username: String): StaffEntity? = null
    override suspend fun findById(id: String): StaffEntity? =
        rows.value[id]?.let { staffEntity(id, it) }

    override fun observeById(id: String): Flow<StaffEntity?> =
        rows.map { map -> map[id]?.let { staffEntity(id, it) } }

    override suspend fun count(): Int = rows.value.size
    override suspend fun allUsernames(): List<String> = rows.value.keys.toList()
    override suspend fun upsert(staff: StaffEntity) = Unit
    override suspend fun delete(id: String) = Unit

    private fun staffEntity(id: String, name: String) = StaffEntity(
        id = id,
        username = name.lowercase().replace(' ', '.'),
        displayName = name,
        roles = "Owner",
        passwordAlgorithm = "PBKDF2WithHmacSHA256",
        passwordIterations = 1,
        passwordSalt = ByteArray(1),
        passwordHash = ByteArray(1),
        createdAtEpochMs = 0L,
        isEnabled = true,
    )
}
