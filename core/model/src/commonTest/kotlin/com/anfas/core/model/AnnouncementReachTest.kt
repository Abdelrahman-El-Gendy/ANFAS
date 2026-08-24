package com.anfas.core.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementReachTest {

    private val today = LocalDate.parse("2026-08-15")

    private fun member(id: String, status: MembershipStatus) = Member(
        id = MemberId(id),
        fullName = id,
        membershipNumber = id,
        phone = null,
        status = status,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    private fun term(memberId: String, endsOn: String) = SubscriptionTerm(
        id = SubscriptionId("t-$memberId"),
        memberId = MemberId(memberId),
        planId = PlanId("p-1"),
        tier = PlanTier.MONTHLY,
        startsOn = LocalDate.parse("2026-01-01"),
        endsOn = LocalDate.parse(endsOn),
        paymentMethod = PaymentMethod.CASH,
        paid = Money.of(100),
    )

    @Test
    fun `all members includes every status`() {
        val members = listOf(
            member("a", MembershipStatus.ACTIVE),
            member("b", MembershipStatus.EXPIRED),
            member("c", MembershipStatus.SUSPENDED),
        )
        assertEquals(
            3,
            AnnouncementReach.count(AnnouncementAudience.ALL_MEMBERS, members, emptyMap(), today),
        )
    }

    @Test
    fun `active only excludes every other status`() {
        val members = listOf(
            member("a", MembershipStatus.ACTIVE),
            member("b", MembershipStatus.EXPIRED),
            member("c", MembershipStatus.SUSPENDED),
            member("d", MembershipStatus.PAUSED),
        )
        val matched = AnnouncementReach.matching(
            AnnouncementAudience.ACTIVE_ONLY,
            members,
            emptyMap(),
            today,
        )
        assertEquals(listOf(MemberId("a")), matched.map { it.id })
    }

    @Test
    fun `expiring this month matches a term ending later in the same month`() {
        val members = listOf(member("a", MembershipStatus.ACTIVE))
        val terms = mapOf(MemberId("a") to term("a", "2026-08-31"))
        assertEquals(
            1,
            AnnouncementReach.count(
                AnnouncementAudience.EXPIRING_THIS_MONTH,
                members,
                terms,
                today,
            ),
        )
    }

    @Test
    fun `expiring this month excludes a term that already lapsed earlier this month`() {
        val members = listOf(member("a", MembershipStatus.EXPIRED))
        val terms = mapOf(MemberId("a") to term("a", "2026-08-01"))
        assertEquals(
            0,
            AnnouncementReach.count(
                AnnouncementAudience.EXPIRING_THIS_MONTH,
                members,
                terms,
                today,
            ),
        )
    }

    @Test
    fun `expiring this month excludes a term ending next month`() {
        val members = listOf(member("a", MembershipStatus.ACTIVE))
        val terms = mapOf(MemberId("a") to term("a", "2026-09-01"))
        assertEquals(
            0,
            AnnouncementReach.count(
                AnnouncementAudience.EXPIRING_THIS_MONTH,
                members,
                terms,
                today,
            ),
        )
    }

    @Test
    fun `expiring this month excludes a member with no term at all`() {
        val members = listOf(member("a", MembershipStatus.ACTIVE))
        assertEquals(
            0,
            AnnouncementReach.count(
                AnnouncementAudience.EXPIRING_THIS_MONTH,
                members,
                emptyMap(),
                today,
            ),
        )
    }

    @Test
    fun `expiring this month includes today itself`() {
        val members = listOf(member("a", MembershipStatus.ACTIVE))
        val terms = mapOf(MemberId("a") to term("a", today.toString()))
        assertEquals(
            1,
            AnnouncementReach.count(
                AnnouncementAudience.EXPIRING_THIS_MONTH,
                members,
                terms,
                today,
            ),
        )
    }
}
