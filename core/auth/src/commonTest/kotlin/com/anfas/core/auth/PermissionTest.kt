package com.anfas.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionTest {

    private fun session(vararg roles: Role) = Session(userId = "s-1", roles = roles.toSet())

    @Test
    fun `an owner can do everything`() {
        val owner = session(Role.Owner)

        Permission.entries.forEach { permission ->
            assertTrue(owner.can(permission), "owner should hold $permission")
        }
    }

    /**
     * The one thing separating Admin from Owner. Staff management stays with the owner so an
     * admin cannot quietly promote themselves — if this ever passes, that boundary is gone.
     */
    @Test
    fun `an admin can do everything except manage staff`() {
        val admin = session(Role.Admin)

        assertFalse(admin.can(Permission.MANAGE_STAFF))
        assertTrue(admin.can(Permission.MANAGE_SUBSCRIPTIONS))
        assertEquals(
            Permission.entries.size - 1,
            admin.permissions.size,
        )
    }

    @Test
    fun `a receptionist runs the desk but not therapy or staff`() {
        val desk = session(Role.Receptionist)

        assertTrue(
            desk.can(Permission.MANAGE_SUBSCRIPTIONS),
            "taking payment at the desk is the job",
        )
        assertTrue(desk.can(Permission.IMPORT_INTAKE))
        assertFalse(desk.can(Permission.VIEW_THERAPY), "clinical notes are not desk work")
        assertFalse(desk.can(Permission.MANAGE_STAFF))
    }

    @Test
    fun `a coach can see members and scan but not take money`() {
        val coach = session(Role.Coach)

        assertTrue(coach.can(Permission.VIEW_MEMBERS))
        assertTrue(coach.can(Permission.SCAN_INTAKE))
        assertFalse(coach.can(Permission.IMPORT_INTAKE))
        assertFalse(coach.can(Permission.MANAGE_SUBSCRIPTIONS))
        assertFalse(coach.can(Permission.EDIT_MEMBERS))
    }

    @Test
    fun `a therapist gets clinical access and member context only`() {
        val therapist = session(Role.Therapist)

        assertEquals(
            setOf(Permission.VIEW_MEMBERS, Permission.VIEW_THERAPY),
            therapist.permissions,
        )
    }

    /** The role exists for a future surface and must not silently grant anything meanwhile. */
    @Test
    fun `the member role grants nothing`() {
        assertEquals(emptySet(), session(Role.Member).permissions)
    }

    @Test
    fun `multiple roles union rather than override`() {
        val both = session(Role.Coach, Role.Therapist)

        assertTrue(both.can(Permission.VIEW_THERAPY), "from Therapist")
        assertTrue(both.can(Permission.SCAN_INTAKE), "from Coach")
        assertFalse(both.can(Permission.MANAGE_STAFF), "neither role grants it")
    }

    @Test
    fun `canAll requires every permission`() {
        val desk = session(Role.Receptionist)

        assertTrue(desk.canAll(Permission.VIEW_MEMBERS, Permission.IMPORT_INTAKE))
        assertFalse(desk.canAll(Permission.VIEW_MEMBERS, Permission.MANAGE_STAFF))
    }

    /**
     * A session with no roles — which SettingsSessionStore refuses to restore — can do nothing.
     * Asserted so a future change cannot make "no roles" mean "unrestricted".
     */
    @Test
    fun `a session with no roles can do nothing`() {
        val empty = Session(userId = "s-1", roles = emptySet())

        Permission.entries.forEach { assertFalse(empty.can(it), "should not hold $it") }
    }
}
