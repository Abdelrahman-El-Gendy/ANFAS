package com.anfas.app.navigation

import com.anfas.core.auth.Permission
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.can
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What each role actually sees and can reach.
 *
 * Asserted here rather than by screenshot because it is the *shape* of the app per role, and a
 * screenshot proves one role on one device. The shell filters destinations with exactly this
 * expression, so a role gaining a tab it should not have fails here.
 */
class NavigationPermissionTest {

    private fun destinationsFor(vararg roles: Role): List<RootComponent.TopLevel> {
        val session = Session(userId = "s-1", roles = roles.toSet())
        return RootComponent.TopLevel.entries.filter { session.can(it.permission) }
    }

    @Test
    fun `an owner reaches every destination`() {
        assertEquals(RootComponent.TopLevel.entries, destinationsFor(Role.Owner))
    }

    /** Staff management is the only thing an admin cannot reach. */
    @Test
    fun `an admin reaches everything except staff`() {
        assertEquals(
            RootComponent.TopLevel.entries - RootComponent.TopLevel.STAFF,
            destinationsFor(Role.Admin),
        )
    }

    @Test
    fun `a receptionist runs the desk without staff management`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.REMINDERS,
                RootComponent.TopLevel.INTAKE,
            ),
            destinationsFor(Role.Receptionist),
        )
    }

    /** A coach sees who is in the room and can scan a sheet. No reminders, no staff. */
    @Test
    fun `a coach reaches members and intake only`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.INTAKE,
            ),
            destinationsFor(Role.Coach),
        )
    }

    @Test
    fun `a therapist reaches members only`() {
        assertEquals(
            listOf(RootComponent.TopLevel.DASHBOARD, RootComponent.TopLevel.MEMBERS),
            destinationsFor(Role.Therapist),
        )
    }

    /**
     * The route guard, not the nav bar. Every route must name a permission, and the money route
     * must not be reachable by a role that cannot take payment — this is what stops a coach
     * landing on the renewal sheet via a restored back stack.
     */
    @Test
    fun `the renewal route requires permission to take payment`() {
        assertEquals(
            Permission.MANAGE_SUBSCRIPTIONS,
            RootComponent.Config.Renewal(memberId = "m-1").requiredPermission,
        )

        val coach = Session(userId = "s-1", roles = setOf(Role.Coach))
        assertTrue(!coach.can(RootComponent.Config.Renewal("m-1").requiredPermission))
    }

    @Test
    fun `the staff route requires staff management`() {
        assertEquals(
            Permission.MANAGE_STAFF,
            RootComponent.Config.StaffList.requiredPermission,
        )
    }

    /**
     * Every role that can sign in must reach *something*, or the app opens on a
     * permission-denied screen it can never leave. Member is excluded: it grants nothing by
     * design and no account is created with it.
     */
    @Test
    fun `every assignable role has somewhere to land`() {
        (Role.entries - Role.Member).forEach { role ->
            assertTrue(
                destinationsFor(role).isNotEmpty(),
                "$role would sign in with nowhere to go",
            )
        }
    }
}
