package com.anfas.app.navigation

import com.anfas.core.auth.Permission
import com.anfas.core.auth.Role
import com.anfas.core.auth.Session
import com.anfas.core.auth.can
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each role actually sees and can reach.
 *
 * Asserted here rather than by screenshot because it is the *shape* of the app per role, and a
 * screenshot proves one role on one device. The shell filters destinations with exactly this
 * expression, so a role gaining a tab it should not have fails here.
 */
class NavigationPermissionTest {

    /** Everything a session can reach, wherever it is offered from. */
    private fun destinationsFor(vararg roles: Role): List<RootComponent.TopLevel> {
        val session = Session(userId = "s-1", roles = roles.toSet())
        return RootComponent.TopLevel.entries.filter { session.can(it.permission) }
    }

    /** Just the bottom bar — the list whose length is width-constrained. */
    private fun barFor(vararg roles: Role): List<RootComponent.TopLevel> {
        val session = Session(userId = "s-1", roles = roles.toSet())
        return RootComponent.TopLevel.entries.filter {
            it.placement == RootComponent.Placement.Primary && session.can(it.permission)
        }
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
                RootComponent.TopLevel.CLASSES,
                RootComponent.TopLevel.CHECK_IN,
                RootComponent.TopLevel.REMINDERS,
                RootComponent.TopLevel.INTAKE,
                RootComponent.TopLevel.ANNOUNCEMENTS,
                RootComponent.TopLevel.EQUIPMENT,
            ),
            destinationsFor(Role.Receptionist),
        )
    }

    /**
     * A coach sees who is in the room, what is on, can scan a sheet, and can see equipment
     * status. No reminders, no staff. The timetable and the equipment inventory are both
     * *visible* to every role that can sign in — a coach on the floor has to know what is on
     * next and whether a machine is broken — while changing either needs MANAGE_CLASSES or
     * MANAGE_EQUIPMENT, neither of which a coach holds.
     */
    @Test
    fun `a coach reaches the dashboard members classes check-in intake and equipment`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CLASSES,
                RootComponent.TopLevel.CHECK_IN,
                RootComponent.TopLevel.INTAKE,
                RootComponent.TopLevel.EQUIPMENT,
            ),
            destinationsFor(Role.Coach),
        )
        assertTrue(
            !Session("s-1", setOf(Role.Coach)).can(Permission.MANAGE_CLASSES),
            "a coach must not be able to move somebody else's class",
        )
        assertTrue(
            !Session("s-1", setOf(Role.Coach)).can(Permission.MANAGE_EQUIPMENT),
            "a coach must not be able to add or log equipment",
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
    fun `no role is offered more than four bottom-bar destinations`() {
        val bar = RootComponent.TopLevel.entries
            .count { it.placement == RootComponent.Placement.Primary }
        assertTrue(bar <= 4, "a bottom bar of $bar divides a phone too far")
        Role.entries.forEach { role ->
            assertTrue(
                barFor(role).size <= 4,
                "$role is offered ${barFor(role).size} tabs",
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
    fun `a therapist reaches members classes check-in and equipment`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CLASSES,
                RootComponent.TopLevel.CHECK_IN,
                RootComponent.TopLevel.EQUIPMENT,
            ),
            destinationsFor(Role.Therapist),
        )
    }

    /**
     * The rail carries every destination and the bar carries only Primary ones, so the two lists
     * must differ in exactly the documented way and no other. A destination added without a
     * Placement decision shows up here as a surprise in one list or the other.
     */

    /**
     * The export's own bottom bar is Dashboard / Members / Schedule / Check-in, and Subscriptions
     * appears only in its desktop sidebar. This pins that: the timetable is on the bar and the
     * reminder queue is not.
     */
    @Test
    fun `the bar matches the export - classes on it and reminders off it`() {
        assertEquals(
            listOf(
                RootComponent.TopLevel.DASHBOARD,
                RootComponent.TopLevel.MEMBERS,
                RootComponent.TopLevel.CLASSES,
                RootComponent.TopLevel.CHECK_IN,
            ),
            barFor(Role.Owner),
        )
        assertEquals(
            RootComponent.Placement.WideOnly,
            RootComponent.TopLevel.REMINDERS.placement,
        )
        // On a phone it is opened from the dashboard's renewal tile, so that is the tab that
        // stays lit while you are in it.
        assertEquals(
            RootComponent.TopLevel.DASHBOARD,
            RootComponent.TopLevel.REMINDERS.bottomBarSelection,
        )
    }

    @Test
    fun `intake is a rail destination and staff is neither`() {
        assertEquals(
            RootComponent.Placement.WideOnly,
            RootComponent.TopLevel.INTAKE.placement,
        )
        assertEquals(
            RootComponent.Placement.Account,
            RootComponent.TopLevel.STAFF.placement,
        )
        // On a phone intake has no slot, so the tab it was entered from stays lit; staff
        // management lights nothing, because you did not come from a tab.
        assertEquals(
            RootComponent.TopLevel.MEMBERS,
            RootComponent.TopLevel.INTAKE.bottomBarSelection,
        )
        assertNull(RootComponent.TopLevel.STAFF.bottomBarSelection)
    }

    /**
     * Signing in must land on a bar destination. Landing on intake or staff management would
     * open the app with nothing selected in the nav bar, and neither is "what needs doing".
     */
    @Test
    fun `every assignable role lands on a bar destination`() {
        (Role.entries - Role.Member).forEach { role ->
            val session = Session(userId = "s-1", roles = setOf(role))
            val landing = RootComponent.TopLevel.landingFor(session)
            assertEquals(
                RootComponent.Placement.Primary,
                landing?.placement,
                "$role lands on $landing",
            )
        }
        assertNull(
            RootComponent.TopLevel.landingFor(Session("s-1", setOf(Role.Member))),
            "Member grants nothing, so it must land nowhere rather than on a denied screen",
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
