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

    /**
     * Staff management is no longer a destination at all — it moved to the account menu — so an
     * admin now sees exactly what an owner sees.
     */
    @Test
    fun `an admin reaches every destination`() {
        assertEquals(RootComponent.TopLevel.entries, destinationsFor(Role.Admin))
    }

    @Test
    fun `a receptionist runs the desk`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CHECK_IN,
                RootComponent.TopLevel.REMINDERS,
            ),
            destinationsFor(Role.Receptionist),
        )
    }

    /** A coach sees who is in the room and whose membership is live. No reminders. */
    @Test
    fun `a coach reaches the dashboard members and check-in`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CHECK_IN,
            ),
            destinationsFor(Role.Coach),
        )
    }

    /**
     * The bar divides a phone's width equally between destinations, so the count is a hard
     * constraint rather than a preference: Material caps a bottom bar at five and iOS at
     * five-plus-More. There were six, which in Arabic on a narrow phone ellipsised every label.
     *
     * Asserted against the enum rather than the rendered bar because the enum is what the shell
     * maps over — a seventh destination fails here, at the point the decision is actually made.
     */
    @Test
    fun `no role is offered more than four destinations`() {
        assertTrue(
            RootComponent.TopLevel.entries.size <= 4,
            "a bottom bar of ${RootComponent.TopLevel.entries.size} divides a phone too far",
        )
        Role.entries.forEach { role ->
            assertTrue(
                destinationsFor(role).size <= 4,
                "$role is offered ${destinationsFor(role).size} tabs",
            )
        }
    }

    /**
     * Intake and staff management were destinations and are now reached from a screen and from
     * the account menu respectively. They are still real routes with their own permissions —
     * this is what stops "reduce the bar" from quietly meaning "delete two features".
     */
    @Test
    fun `intake and staff remain reachable routes with their own permissions`() {
        assertEquals(
            Permission.SCAN_INTAKE,
            RootComponent.Config.IntakeReview.requiredPermission,
        )
        assertEquals(
            Permission.MANAGE_STAFF,
            RootComponent.Config.StaffList.requiredPermission,
        )

        // The entry points, in the roles that hold them: a coach scans, only an owner manages
        // staff. Losing either grant is how a moved entry point becomes an unreachable screen.
        val coach = Session(userId = "s-1", roles = setOf(Role.Coach))
        assertTrue(coach.can(Permission.SCAN_INTAKE), "a coach must still reach intake")
        assertTrue(!coach.can(Permission.MANAGE_STAFF))

        val admin = Session(userId = "s-2", roles = setOf(Role.Admin))
        assertTrue(!admin.can(Permission.MANAGE_STAFF), "staff management stays with the owner")
    }

    /**
     * Intake is entered from the directory, so a role that can scan must also be able to see the
     * screen the button is on. Nothing enforces that except this: SCAN_INTAKE without
     * VIEW_MEMBERS would be a permission with no way to exercise it.
     */
    @Test
    fun `every role that can scan can reach the screen the action lives on`() {
        Role.entries
            .filter { Session(userId = "s-1", roles = setOf(it)).can(Permission.SCAN_INTAKE) }
            .forEach { role ->
                assertTrue(
                    Session(userId = "s-1", roles = setOf(role)).can(Permission.VIEW_MEMBERS),
                    "$role can scan but cannot open the directory the action lives on",
                )
            }
    }

    @Test
    fun `a therapist reaches members and check-in`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CHECK_IN,
            ),
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
