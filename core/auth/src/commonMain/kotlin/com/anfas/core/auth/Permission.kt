package com.anfas.core.auth

/**
 * What a signed-in member of staff is allowed to do.
 *
 * Permissions, not role checks, at every call site. `if (session.isOwner)` scattered through the
 * app means adding a role requires finding every one of those checks; `session.can(ManageStaff)`
 * means adding a role is one line in [Role.permissions].
 *
 * Deliberately coarse and deliberately limited to what the app actually does today. A permission
 * with no enforcement point is a claim about security that nothing checks, which is worse than an
 * absent one.
 */
enum class Permission {
    /** See the member directory and any member's profile. */
    VIEW_MEMBERS,

    /** Create a member by hand, or edit one. */
    EDIT_MEMBERS,

    /** Sell or renew a subscription. Money changes hands, so it is not the default. */
    MANAGE_SUBSCRIPTIONS,

    VIEW_REMINDERS,

    /** Re-queue a failed reminder, which sends a WhatsApp message on the gym's account. */
    RETRY_REMINDERS,

    /** Photograph a sign-up sheet and review what OCR read. */
    SCAN_INTAKE,

    /**
     * Turn reviewed intake rows into real members. Separate from [SCAN_INTAKE] on purpose: the
     * design's workflow is that anyone at the desk can scan, and someone accountable imports.
     */
    IMPORT_INTAKE,

    /**
     * Let a member into the gym, and see today's entries.
     *
     * Everyone who works a shift needs this — a coach on the floor turns people away as often as
     * the desk does — so it is the most widely granted permission here.
     */
    CHECK_IN_MEMBERS,

    /** Read and write therapy case files — clinical notes, so the narrowest grant here. */
    VIEW_THERAPY,

    /** Create, disable and reset other staff accounts. Owner only. */
    MANAGE_STAFF,

    /**
     * Add, edit and remove classes on the weekly timetable.
     *
     * Separate from viewing it, which needs no permission beyond signing in: a coach on the floor
     * has to know what is on and where, and every role that can sign in is working a shift. What
     * a coach must not do is move somebody else's class.
     */
    MANAGE_CLASSES,

    /**
     * Write and publish gym-wide announcements.
     *
     * One permission, not a view/manage split: an announcement is authored content for members
     * to eventually see, not operational information the way the class timetable is — there is
     * no equivalent need for a coach mid-shift to read draft copy nobody has published yet.
     */
    MANAGE_ANNOUNCEMENTS,
}

/**
 * The permissions each role holds.
 *
 * Sourced from the roles the export itself names and the screens it puts them on. Where the design
 * is silent the narrower reading wins — a role that turns out to need more can be widened after
 * someone asks, whereas a role that could already do too much has already done it.
 */
val Role.permissions: Set<Permission>
    get() = when (this) {
        // Everything, including the ability to grant everything.
        Role.Owner -> Permission.entries.toSet()

        // Runs the gym day to day but cannot mint accounts. Staff management stays with the owner
        // so that an admin cannot quietly promote themselves.
        Role.Admin -> Permission.entries.toSet() - Permission.MANAGE_STAFF

        // The reception desk: the whole member and intake workflow, and money, because taking a
        // renewal payment at the desk is the job. No therapy — those are clinical notes.
        Role.Receptionist -> setOf(
            Permission.VIEW_MEMBERS,
            Permission.EDIT_MEMBERS,
            Permission.MANAGE_SUBSCRIPTIONS,
            Permission.VIEW_REMINDERS,
            Permission.RETRY_REMINDERS,
            Permission.SCAN_INTAKE,
            Permission.IMPORT_INTAKE,
            Permission.CHECK_IN_MEMBERS,
            Permission.MANAGE_CLASSES,
            Permission.MANAGE_ANNOUNCEMENTS,
        )

        // Needs to know who is in the room and whether their membership is live. Not payments,
        // not therapy notes, and not importing — a coach scanning a sheet is fine, a coach
        // registering members is not their job.
        Role.Coach -> setOf(
            Permission.VIEW_MEMBERS,
            Permission.SCAN_INTAKE,
            Permission.CHECK_IN_MEMBERS,
        )

        // Clinical work plus enough member context to do it.
        Role.Therapist -> setOf(
            Permission.VIEW_MEMBERS,
            Permission.VIEW_THERAPY,
            Permission.CHECK_IN_MEMBERS,
        )

        // Members do not sign in to this app. The role exists so a future member-facing surface
        // has somewhere to hang, and grants nothing until it does.
        Role.Member -> emptySet()
    }

/** The union across every role held, so a coach who is also a therapist gets both. */
val Session.permissions: Set<Permission>
    get() = roles.flatMapTo(mutableSetOf()) { it.permissions }

fun Session.can(permission: Permission): Boolean = permission in permissions

fun Session.canAll(vararg required: Permission): Boolean = required.all { can(it) }
