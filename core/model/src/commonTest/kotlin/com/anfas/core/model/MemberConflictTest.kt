package com.anfas.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberConflictTest {

    private fun member(
        name: String = "Omar Hassan",
        number: String = "#10000",
        phone: String? = "01001234567",
        status: MembershipStatus = MembershipStatus.ACTIVE,
    ) = Member(
        id = MemberId("m-1"),
        fullName = name,
        membershipNumber = number,
        phone = phone,
        status = status,
        lastCheckInAt = null,
        avatarUrl = null,
    )

    @Test
    fun `identical records need no resolution`() {
        val conflict = MemberConflict.of(member(), member())

        assertFalse(conflict.needsResolution)
        assertEquals(emptyList(), conflict.differing)
    }

    @Test
    fun `a differing phone number is flagged and the others are not`() {
        val conflict = MemberConflict.of(member(), member(phone = "01119876543"))

        assertTrue(conflict.needsResolution)
        assertEquals(listOf(ConflictField.PHONE), conflict.differing.map { it.field })
    }

    @Test
    fun `a differing status is flagged`() {
        val conflict = MemberConflict.of(
            member(status = MembershipStatus.ACTIVE),
            member(status = MembershipStatus.SUSPENDED),
        )

        assertEquals(listOf(ConflictField.STATUS), conflict.differing.map { it.field })
    }

    /**
     * The case that would otherwise put an unanswerable question in front of staff: OCR intake
     * can store an empty string where another device stored null.
     */
    @Test
    fun `null and blank are the same value`() {
        val conflict = MemberConflict.of(member(phone = null), member(phone = "   "))

        assertFalse(conflict.needsResolution)
    }

    /**
     * A check-in is append-only, so the later timestamp is simply the truth — asking a human to
     * arbitrate it would be noise, and a resolution screen full of noise gets clicked through.
     */
    @Test
    fun `a newer check-in is not a conflict`() {
        val conflict = MemberConflict.of(
            member().copy(lastCheckInAt = kotlin.time.Instant.fromEpochMilliseconds(1_000)),
            member().copy(lastCheckInAt = kotlin.time.Instant.fromEpochMilliseconds(9_000)),
        )

        assertFalse(conflict.needsResolution)
    }

    @Test
    fun `every arbitrated field is reported even when it agrees`() {
        val conflict = MemberConflict.of(member(), member(name = "Omar H"))

        // The screen renders the whole record, marking only what differs.
        assertEquals(ConflictField.entries.size, conflict.fields.size)
        assertEquals(1, conflict.differing.size)
    }
}
