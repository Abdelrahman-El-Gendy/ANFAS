package com.anfas.app.navigation

import com.anfas.core.auth.Permission
import com.anfas.core.auth.Session
import com.anfas.core.auth.StaffAccount
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.model.MemberId
import com.anfas.feature.announcements.AnnouncementsComponent
import com.anfas.feature.announcements.AnnouncementsComponentFactory
import com.anfas.feature.auth.SignInComponent
import com.anfas.feature.auth.SignInComponentFactory
import com.anfas.feature.auth.StaffListComponent
import com.anfas.feature.auth.StaffListComponentFactory
import com.anfas.feature.checkin.CheckInComponent
import com.anfas.feature.checkin.CheckInComponentFactory
import com.anfas.feature.classes.ClassesComponent
import com.anfas.feature.classes.ClassesComponentFactory
import com.anfas.feature.dashboard.DashboardComponent
import com.anfas.feature.dashboard.DashboardComponentFactory
import com.anfas.feature.equipment.EquipmentComponent
import com.anfas.feature.equipment.EquipmentComponentFactory
import com.anfas.feature.intakeocr.IntakeReviewComponent
import com.anfas.feature.intakeocr.IntakeReviewComponentFactory
import com.anfas.feature.members.MemberProfileComponent
import com.anfas.feature.members.MemberProfileComponentFactory
import com.anfas.feature.members.MembersListComponent
import com.anfas.feature.members.MembersListComponentFactory
import com.anfas.feature.subscriptions.ReminderQueueComponent
import com.anfas.feature.subscriptions.ReminderQueueComponentFactory
import com.anfas.feature.subscriptions.RenewalSheetComponent
import com.anfas.feature.subscriptions.RenewalSheetComponentFactory
import com.anfas.feature.therapy.TherapyComponent
import com.anfas.feature.therapy.TherapyComponentFactory
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Decompose navigation root.
 *
 * Two kinds of destination, handled differently on purpose:
 *  - [TopLevel] entries are what the nav rail/bottom bar switches between, so selecting one
 *    `replaceAll`s rather than pushing. Otherwise tapping between tabs would build an
 *    unbounded back stack.
 *  - detail destinations like [Config.Renewal] are pushed, so back pops them.
 *
 * Feature component factories are resolved from Koin here rather than passed in, so the three
 * platform launchers keep constructing `RootComponent(context)` and the iOS Swift side stays
 * untouched. This is the one place a service-locator lookup is acceptable — it is app-shell
 * glue. Features themselves take constructor dependencies and are testable without Koin.
 */
class RootComponent(componentContext: ComponentContext) :
    ComponentContext by componentContext,
    KoinComponent {

    private val authRepository: AuthRepository by inject()
    private val dispatchers: AppDispatchers by inject()
    private val signInFactory: SignInComponentFactory by inject()
    private val staffListFactory: StaffListComponentFactory by inject()
    private val dashboardFactory: DashboardComponentFactory by inject()
    private val membersListFactory: MembersListComponentFactory by inject()
    private val checkInFactory: CheckInComponentFactory by inject()
    private val classesFactory: ClassesComponentFactory by inject()
    private val memberProfileFactory: MemberProfileComponentFactory by inject()
    private val therapyFactory: TherapyComponentFactory by inject()
    private val announcementsFactory: AnnouncementsComponentFactory by inject()
    private val equipmentFactory: EquipmentComponentFactory by inject()
    private val reminderQueueFactory: ReminderQueueComponentFactory by inject()
    private val renewalSheetFactory: RenewalSheetComponentFactory by inject()
    private val intakeReviewFactory: IntakeReviewComponentFactory by inject()

    private val scope =
        coroutineScope(dispatchers.main + SupervisorJob() + appExceptionHandler("Root"))

    /**
     * Null means nobody is signed in, which the shell renders as [signIn] instead of the app.
     *
     * Gating in the shell rather than adding a SignIn route to the stack is deliberate: an auth
     * screen inside the navigation stack can be reached with the back button after signing in,
     * and a signed-out app would still hold a back stack of screens it must not show.
     */
    val session: StateFlow<Session?> = authRepository.observeSession()
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /**
     * The signed-in person's own account, for the chrome that names them.
     *
     * Separate from [session] rather than folded into it: the session is what the app is *gated*
     * on and must be available synchronously from persisted state, whereas this is a database
     * read that arrives a moment later. Merging them would delay the gate behind a query.
     */
    val currentStaff: StateFlow<StaffAccount?> = authRepository.observeCurrentStaff()
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /**
     * Created eagerly with its own child context rather than lazily inside the stack, so it keeps
     * its typed-but-unsubmitted state across a configuration change while the user is filling it
     * in — the same reason every other component here is lifecycle-scoped.
     */
    val signIn: SignInComponent = signInFactory.create(
        componentContext = childContext(key = "signIn"),
        // Nothing to navigate: `session` emits and the shell swaps the subtree.
        onSignedIn = {},
    )

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.MembersList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    init {
        // Reset to the first tab whenever the session ends, so signing back in — possibly as a
        // different member of staff — does not resume on the previous person's screen.
        scope.launch {
            session.collect { current ->
                if (current == null) {
                    navigation.replaceAll(Config.MembersList)
                } else {
                    // Land on the first destination this session can reach. A therapist has no
                    // reminders and a coach cannot import, so assuming Members is only right
                    // because every role that can sign in holds VIEW_MEMBERS — assert that by
                    // falling back explicitly rather than by luck.
                    val landing = TopLevel.landingFor(current)
                    if (landing != null) onTopLevelSelected(landing)
                }
            }
        }
    }

    /**
     * Back out of a route this session may not see.
     *
     * Pops if there is anywhere to pop to, otherwise replaces with the first destination the
     * session *can* reach — a permission-denied screen with a dead "go back" is a trap.
     */
    fun onPermissionDeniedDismissed() {
        val landing = session.value?.let { TopLevel.landingFor(it) }
        if (stack.value.backStack.isNotEmpty()) {
            navigation.pop()
        } else if (landing != null) {
            onTopLevelSelected(landing)
        } else {
            // No reachable destination at all, which means this account can do nothing. Signing
            // out is the only honest exit.
            onSignOut()
        }
    }

    /**
     * Leave a screen that has its own back affordance.
     *
     * Pops when there is something to pop, and otherwise goes to [fallback]. Both cases really
     * happen for the same screen: intake is **pushed** from the directory on a phone, where back
     * must return there, and **selected** from the rail on desktop, where the stack holds one
     * entry and `pop()` would silently do nothing — a dead back button.
     */
    private fun popOrGoTo(fallback: TopLevel) {
        if (stack.value.backStack.isNotEmpty()) navigation.pop() else onTopLevelSelected(fallback)
    }

    fun onSignOut() {
        scope.launch { authRepository.signOut() }
    }

    fun onTopLevelSelected(destination: TopLevel) {
        navigation.replaceAll(
            when (destination) {
                TopLevel.DASHBOARD -> Config.Dashboard
                TopLevel.MEMBERS -> Config.MembersList
                TopLevel.CLASSES -> Config.Classes
                TopLevel.CHECK_IN -> Config.CheckIn
                TopLevel.REMINDERS -> Config.ReminderQueue
                TopLevel.INTAKE -> Config.IntakeReview
                TopLevel.ANNOUNCEMENTS -> Config.Announcements
                TopLevel.EQUIPMENT -> Config.Equipment
                TopLevel.STAFF -> Config.StaffList
            },
        )
    }

    /**
     * Staff management, from the account menu rather than the nav bar.
     *
     * Pushed, not [navigation.replaceAll]: it is administration reached from wherever you were,
     * and back returning you there is the whole reason it is not a tab.
     */
    fun onOpenStaff() {
        navigation.push(Config.StaffList)
    }

    private fun createChild(config: Config, context: ComponentContext): Child = when (config) {
        Config.Dashboard -> Child.Dashboard(
            dashboardFactory.create(
                componentContext = context,
                onMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
                // Pushed, not selected: on a phone Reminders has no tab, so back must return
                // to the dashboard tile it was opened from.
                onOpenReminders = { navigation.push(Config.ReminderQueue) },
            ),
        )

        Config.Classes -> Child.Classes(
            classesFactory.create(componentContext = context),
        )

        Config.Announcements -> Child.Announcements(
            announcementsFactory.create(componentContext = context),
        )

        Config.Equipment -> Child.Equipment(
            equipmentFactory.create(componentContext = context),
        )

        Config.CheckIn -> Child.CheckIn(
            checkInFactory.create(
                componentContext = context,
                onMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
            ),
        )

        Config.MembersList -> Child.MembersList(
            membersListFactory.create(
                componentContext = context,
                onMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
                onAddMemberClicked = {},
                // Pushed, because scanning is a task whose product is members rather than a
                // place you visit: back lands on the directory the new rows just joined. Safe
                // to leave mid-scan too — the batch lives in `intake_batches`, so the component
                // picks the same sheet back up rather than losing it.
                onScanSheetClicked = { navigation.push(Config.IntakeReview) },
            ),
        )

        is Config.MemberProfile -> Child.MemberProfile(
            memberProfileFactory.create(
                componentContext = context,
                memberId = MemberId(config.memberId),
                // Pushed on top of the profile, so back returns to the member rather than to
                // the directory — the profile is where you check the result of a renewal.
                onRenewClicked = { id -> navigation.push(Config.Renewal(id.value)) },
                onTherapyClicked = { id -> navigation.push(Config.TherapyCase(id.value)) },
                onBackClicked = { navigation.pop() },
            ),
        )

        is Config.TherapyCase -> Child.TherapyCase(
            therapyFactory.create(
                componentContext = context,
                memberId = MemberId(config.memberId),
                onBackClicked = { navigation.pop() },
            ),
        )

        Config.ReminderQueue -> Child.ReminderQueue(
            reminderQueueFactory.create(
                componentContext = context,
                // Reached from the dashboard tile on a phone and from the rail on desktop, so it
                // needs popOrGoTo for the same reason intake does.
                onCloseClicked = { popOrGoTo(TopLevel.DASHBOARD) },
                // The profile, not the renewal sheet: a failed reminder is a question about the
                // member ("is this number right, is the plan still live"), and renewal is one
                // tap further on from there.
                onOpenMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
            ),
        )

        Config.IntakeReview -> Child.IntakeReview(
            intakeReviewFactory.create(
                componentContext = context,
                // The directory either way: on a phone that is the screen underneath, and on
                // desktop it is where the imported members have just appeared. Not a bare pop(),
                // because from the rail there is nothing to pop and back would be dead.
                onImported = { popOrGoTo(TopLevel.MEMBERS) },
                onCloseClicked = { popOrGoTo(TopLevel.MEMBERS) },
            ),
        )

        Config.StaffList -> Child.StaffList(
            staffListFactory.create(
                componentContext = context,
                // Reached from the account group on both form factors, so there is no one screen
                // it sits under — the dashboard is where "done here" goes.
                onBackClicked = { popOrGoTo(TopLevel.DASHBOARD) },
            ),
        )

        is Config.Renewal -> Child.Renewal(
            renewalSheetFactory.create(
                componentContext = context,
                memberId = MemberId(config.memberId),
                onRenewed = { navigation.pop() },
                onCancelled = { navigation.pop() },
            ),
        )
    }

    /** Route definitions. One entry per destination. */

    /**
     * Every variant carries an explicit [SerialName], and that is a correctness requirement
     * rather than tidiness.
     *
     * Decompose serialises the whole back stack into Essenty's StateKeeper on every state save,
     * and a @Serializable sealed hierarchy writes a class discriminator that defaults to the
     * fully-qualified class name. `decompose-android.aar` ships no ProGuard rules of its own, and
     * kotlinx.serialization's rules deliberately allow these classes to be obfuscated — so R8 is
     * free to rename them. Within one build the writer and the reader agree, which is why this
     * tests perfectly clean. It breaks when state saved by one build is read by a build where R8
     * chose different names: a crash on cold resume after an app update, in production only.
     *
     * A stable string decouples the persisted format from whatever R8 does to the class names.
     * **Add a @SerialName to every new Config variant**, and never change an existing one — the
     * old value may be sitting in a saved state on someone's phone.
     */
    @Serializable
    sealed interface Config {
        @Serializable
        @SerialName("dashboard")
        data object Dashboard : Config

        @Serializable
        @SerialName("members-list")
        data object MembersList : Config

        @Serializable
        @SerialName("check-in")
        data object CheckIn : Config

        @Serializable
        @SerialName("classes")
        data object Classes : Config

        @Serializable
        @SerialName("announcements")
        data object Announcements : Config

        @Serializable
        @SerialName("equipment")
        data object Equipment : Config

        @Serializable
        @SerialName("reminder-queue")
        data object ReminderQueue : Config

        @Serializable
        @SerialName("intake-review")
        data object IntakeReview : Config

        /**
         * Carries the raw id string rather than [MemberId]: configs are serialized to restore
         * state, and a value class adds nothing here beyond a custom serializer.
         */
        @Serializable
        @SerialName("staff-list")
        data object StaffList : Config

        @Serializable
        @SerialName("member-profile")
        data class MemberProfile(val memberId: String) : Config

        @Serializable
        @SerialName("therapy-case")
        data class TherapyCase(val memberId: String) : Config

        @Serializable
        @SerialName("renewal")
        data class Renewal(val memberId: String) : Config
    }

    /** Instantiated components, one per [Config]. */
    sealed interface Child {
        data class Dashboard(val component: DashboardComponent) : Child

        data class Classes(val component: ClassesComponent) : Child

        data class Announcements(val component: AnnouncementsComponent) : Child

        data class Equipment(val component: EquipmentComponent) : Child

        data class TherapyCase(val component: TherapyComponent) : Child

        data class MembersList(val component: MembersListComponent) : Child
        data class CheckIn(val component: CheckInComponent) : Child
        data class MemberProfile(val component: MemberProfileComponent) : Child
        data class StaffList(val component: StaffListComponent) : Child
        data class ReminderQueue(val component: ReminderQueueComponent) : Child
        data class IntakeReview(val component: IntakeReviewComponent) : Child
        data class Renewal(val component: RenewalSheetComponent) : Child
    }

    /**
     * The destinations the nav rail/bottom bar offers, each with the permission it needs.
     *
     * Carrying the permission here rather than checking roles at the call site is what makes
     * adding a role a one-line change in Role.permissions instead of a hunt through the shell.
     *
     * **Four, and deliberately not more.** The bottom bar divides a phone's width equally between
     * these, so each entry added shrinks every other one; Material caps a bottom bar at five and
     * iOS at five-plus-More for the same reason. There were six here, which on a narrow phone in
     * Arabic left every label ellipsised — a bar of six unreadable stubs, where the whole job of
     * the labels is to tell you which is which.
     *
     * Cutting it is not a matter of dropping the two least important screens, but of asking which
     * of the six are *places*:
     *  - **Intake** is a task, not a place. You scan when a stack of paper arrives, and what it
     *    produces is members — so it is an action on the directory, and back returns to the rows
     *    it just created. The design already said this: `members-empty` offers "scan a sheet".
     *  - **Staff** is administration, and belongs with the other account-level things (language,
     *    sign out) rather than beside the screens used on every shift.
     *
     * Both remain real, permission-guarded routes; only their entry point moved. Nothing was
     * removed from the app, and neither is nested inside a second menu.
     *
     * The count is already role-dependent — a coach sees three, a receptionist four — so this is
     * a ceiling rather than a fixed set.
     */
    enum class TopLevel(val permission: Permission, val placement: Placement) {
        // First, so signing in lands on "what needs doing" rather than a directory. Gated on
        // VIEW_MEMBERS because every tile is derived from member and subscription data — a role
        // that cannot see members has nothing to put on it.
        DASHBOARD(Permission.VIEW_MEMBERS, Placement.Primary),
        MEMBERS(Permission.VIEW_MEMBERS, Placement.Primary),

        /**
         * The export's own fourth bar item, and the reason it is here rather than Reminders:
         * `class-schedule` and `weekly-class-schedule` put the timetable in the mobile bar and
         * Subscriptions only in the desktop sidebar. Viewing needs no permission beyond signing
         * in — everyone working a shift needs to know what is on — so this is gated on
         * VIEW_MEMBERS like the dashboard, and *managing* is MANAGE_CLASSES inside the screen.
         */
        CLASSES(Permission.VIEW_MEMBERS, Placement.Primary),
        CHECK_IN(Permission.CHECK_IN_MEMBERS, Placement.Primary),

        /**
         * Off the bar, per the export: chasing renewals is desk work, and the phone's four slots
         * belong to the floor. Still one tap away on a phone — the dashboard's "needs renewal"
         * tile opens it, which is where you look for it anyway.
         */
        REMINDERS(Permission.VIEW_REMINDERS, Placement.WideOnly),
        INTAKE(Permission.SCAN_INTAKE, Placement.WideOnly),

        /**
         * Rail only, with no mobile equivalent at all — not even a mobile screen reached from a
         * parent, the way Intake and Reminders are. The export itself never designed a mobile
         * `create-announcement`: it drew this as a desktop-only screen. [Placement.DesktopOnly]
         * says that plainly rather than overloading [Placement.WideOnly]'s meaning, which
         * documents a real "reached from its parent screen" mobile path this destination does
         * not have.
         */
        ANNOUNCEMENTS(Permission.MANAGE_ANNOUNCEMENTS, Placement.DesktopOnly),

        /**
         * Rail only, and also with no mobile equivalent at all — `equipment-detail` is `D`
         * (desktop) in the export's own screen inventory, the same as `create-announcement`.
         * Gated on VIEW_MEMBERS, not a new equipment-specific permission: viewing needs no
         * permission beyond signing in, the same reasoning as [CLASSES] — a coach on the floor
         * needs to know a treadmill is broken. Adding, logging and status changes are
         * MANAGE_EQUIPMENT, enforced inside the screen.
         */
        EQUIPMENT(Permission.VIEW_MEMBERS, Placement.DesktopOnly),
        STAFF(Permission.MANAGE_STAFF, Placement.Account),
        ;

        companion object {
            /**
             * Where a session lands on sign-in, and where a permission-denied screen backs out
             * to. Restricted to [Placement.Primary] deliberately: opening the app on the intake
             * queue, or on staff management, is not "what needs doing" — and on a phone neither
             * has a tab, so the nav bar would show nothing selected on the very first screen.
             */
            fun landingFor(session: Session): TopLevel? = entries.firstOrNull {
                it.placement == Placement.Primary &&
                    session.can(it.permission)
            }
        }
    }

    /**
     * Where a destination is offered. This is the one place the two form factors legitimately
     * disagree, and the reason is width: a 256dp rail costs nothing per row, whereas the bottom
     * bar divides a phone equally between its items.
     */
    enum class Placement {
        /** Bottom bar and rail. At most four of these — see the note on [TopLevel]. */
        Primary,

        /**
         * Rail only. On a phone it is reached from the screen it belongs to — intake is entered
         * from the directory, because what it produces is members.
         */
        WideOnly,

        /**
         * Never a destination row: the rail's footer group, and the compact overflow. Not a place
         * you work, so it does not compete with the screens used on every shift.
         */
        Account,

        /**
         * Rail only, and — unlike [WideOnly] — reachable *nowhere* on a phone, because the export
         * never designed a mobile screen for it at all. `Announcements` and `Equipment` are its
         * users today; a future feature designed for desktop only belongs here too rather than
         * being forced into [WideOnly] with an invented mobile entry point.
         */
        DesktopOnly,
    }
}

/** Which nav entry should read as active for a given route, or null for detail screens. */
internal val RootComponent.Config.topLevel: RootComponent.TopLevel?
    get() = when (this) {
        RootComponent.Config.Dashboard -> RootComponent.TopLevel.DASHBOARD

        RootComponent.Config.MembersList -> RootComponent.TopLevel.MEMBERS

        RootComponent.Config.CheckIn -> RootComponent.TopLevel.CHECK_IN

        RootComponent.Config.Classes -> RootComponent.TopLevel.CLASSES

        RootComponent.Config.Announcements -> RootComponent.TopLevel.ANNOUNCEMENTS

        RootComponent.Config.Equipment -> RootComponent.TopLevel.EQUIPMENT

        RootComponent.Config.ReminderQueue -> RootComponent.TopLevel.REMINDERS

        RootComponent.Config.IntakeReview -> RootComponent.TopLevel.INTAKE

        RootComponent.Config.StaffList -> RootComponent.TopLevel.STAFF

        // Detail routes keep the *parent* destination lit rather than clearing the chrome. The
        // profile is reached from the directory and the renewal sheet from the profile, so
        // Members staying highlighted tells you where back will take you.
        is RootComponent.Config.MemberProfile -> RootComponent.TopLevel.MEMBERS

        is RootComponent.Config.TherapyCase -> RootComponent.TopLevel.MEMBERS

        is RootComponent.Config.Renewal -> null
    }

/**
 * The permission a route requires, checked by the shell before the screen is composed.
 *
 * Hiding an unreachable destination from the nav bar is the primary defence; this is the second
 * one, for a route that is reached anyway — a back stack restored after process death, or a
 * destination pushed by code that forgot to check. Without it a coach could land on the renewal
 * sheet and take a payment.
 *
 * Exhaustive on purpose: adding a Config without deciding its permission will not compile.
 */
internal val RootComponent.Config.requiredPermission: Permission
    get() = when (this) {
        RootComponent.Config.Dashboard -> Permission.VIEW_MEMBERS

        RootComponent.Config.MembersList -> Permission.VIEW_MEMBERS

        RootComponent.Config.CheckIn -> Permission.CHECK_IN_MEMBERS

        // Viewing the timetable, not managing it. MANAGE_CLASSES is enforced inside the screen,
        // because add/edit and view live on one route -- the same split as scan/import.
        RootComponent.Config.Classes -> Permission.VIEW_MEMBERS

        RootComponent.Config.Announcements -> Permission.MANAGE_ANNOUNCEMENTS

        // Viewing the inventory, not managing it -- MANAGE_EQUIPMENT is enforced inside the
        // screen, the same split as Classes.
        RootComponent.Config.Equipment -> Permission.VIEW_MEMBERS

        is RootComponent.Config.MemberProfile -> Permission.VIEW_MEMBERS

        is RootComponent.Config.TherapyCase -> Permission.VIEW_THERAPY

        RootComponent.Config.ReminderQueue -> Permission.VIEW_REMINDERS

        RootComponent.Config.IntakeReview -> Permission.SCAN_INTAKE

        is RootComponent.Config.Renewal -> Permission.MANAGE_SUBSCRIPTIONS

        RootComponent.Config.StaffList -> Permission.MANAGE_STAFF
    }

/**
 * Which **bottom bar** entry lights up, which is not always the destination you are on.
 *
 * The bar holds only [RootComponent.Placement.Primary] entries, so the two that are not in it
 * have to resolve to something: intake folds onto Members, because on a phone that is where it
 * was entered from and where back returns to, and staff management lights nothing at all —
 * highlighting a tab would point at a screen you did not come from.
 *
 * The rail needs none of this: every destination has its own row there, so it lights itself.
 */
internal val RootComponent.TopLevel.bottomBarSelection: RootComponent.TopLevel?
    get() = when (this) {
        RootComponent.TopLevel.INTAKE -> RootComponent.TopLevel.MEMBERS
        RootComponent.TopLevel.REMINDERS -> RootComponent.TopLevel.DASHBOARD
        RootComponent.TopLevel.STAFF -> null
        else -> this
    }
