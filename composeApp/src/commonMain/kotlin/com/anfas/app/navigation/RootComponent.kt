package com.anfas.app.navigation

import com.anfas.core.auth.Permission
import com.anfas.core.auth.Session
import com.anfas.core.auth.can
import com.anfas.core.common.AppDispatchers
import com.anfas.core.common.appExceptionHandler
import com.anfas.core.data.AuthRepository
import com.anfas.core.model.MemberId
import com.anfas.feature.auth.SignInComponent
import com.anfas.feature.auth.SignInComponentFactory
import com.anfas.feature.auth.StaffListComponent
import com.anfas.feature.auth.StaffListComponentFactory
import com.anfas.feature.checkin.CheckInComponent
import com.anfas.feature.checkin.CheckInComponentFactory
import com.anfas.feature.dashboard.DashboardComponent
import com.anfas.feature.dashboard.DashboardComponentFactory
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
    private val memberProfileFactory: MemberProfileComponentFactory by inject()
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
                    val landing = TopLevel.entries.firstOrNull { current.can(it.permission) }
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
        val landing = TopLevel.entries.firstOrNull { session.value?.can(it.permission) == true }
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

    fun onSignOut() {
        scope.launch { authRepository.signOut() }
    }

    fun onTopLevelSelected(destination: TopLevel) {
        navigation.replaceAll(
            when (destination) {
                TopLevel.DASHBOARD -> Config.Dashboard
                TopLevel.MEMBERS -> Config.MembersList
                TopLevel.CHECK_IN -> Config.CheckIn
                TopLevel.REMINDERS -> Config.ReminderQueue
                TopLevel.INTAKE -> Config.IntakeReview
                TopLevel.STAFF -> Config.StaffList
            },
        )
    }

    private fun createChild(config: Config, context: ComponentContext): Child = when (config) {
        Config.Dashboard -> Child.Dashboard(
            dashboardFactory.create(
                componentContext = context,
                onMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
                onOpenReminders = { onTopLevelSelected(TopLevel.REMINDERS) },
            ),
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
                // Intake is a top-level destination, so this replaces rather than pushes —
                // otherwise "scan a sheet" from the members empty state leaves a members
                // screen underneath that back would return to mid-scan.
                onScanSheetClicked = { onTopLevelSelected(TopLevel.INTAKE) },
            ),
        )

        is Config.MemberProfile -> Child.MemberProfile(
            memberProfileFactory.create(
                componentContext = context,
                memberId = MemberId(config.memberId),
                // Pushed on top of the profile, so back returns to the member rather than to
                // the directory — the profile is where you check the result of a renewal.
                onRenewClicked = { id -> navigation.push(Config.Renewal(id.value)) },
                onBackClicked = { navigation.pop() },
            ),
        )

        Config.ReminderQueue -> Child.ReminderQueue(
            reminderQueueFactory.create(
                componentContext = context,
                // The profile, not the renewal sheet: a failed reminder is a question about the
                // member ("is this number right, is the plan still live"), and renewal is one
                // tap further on from there.
                onOpenMemberClicked = { id -> navigation.push(Config.MemberProfile(id.value)) },
            ),
        )

        Config.IntakeReview -> Child.IntakeReview(
            intakeReviewFactory.create(
                componentContext = context,
                // Imported members land in the directory, so that is where to look next.
                onImported = { navigation.replaceAll(Config.MembersList) },
            ),
        )

        Config.StaffList -> Child.StaffList(
            staffListFactory.create(componentContext = context),
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
        @SerialName("renewal")
        data class Renewal(val memberId: String) : Config
    }

    /** Instantiated components, one per [Config]. */
    sealed interface Child {
        data class Dashboard(val component: DashboardComponent) : Child

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
     */
    enum class TopLevel(val permission: Permission) {
        // First, so signing in lands on "what needs doing" rather than a directory. Gated on
        // VIEW_MEMBERS because every tile is derived from member and subscription data — a role
        // that cannot see members has nothing to put on it.
        DASHBOARD(Permission.VIEW_MEMBERS),
        MEMBERS(Permission.VIEW_MEMBERS),
        CHECK_IN(Permission.CHECK_IN_MEMBERS),
        REMINDERS(Permission.VIEW_REMINDERS),
        INTAKE(Permission.SCAN_INTAKE),
        STAFF(Permission.MANAGE_STAFF),
    }
}

/** Which nav entry should read as active for a given route, or null for detail screens. */
internal val RootComponent.Config.topLevel: RootComponent.TopLevel?
    get() = when (this) {
        RootComponent.Config.Dashboard -> RootComponent.TopLevel.DASHBOARD

        RootComponent.Config.MembersList -> RootComponent.TopLevel.MEMBERS

        RootComponent.Config.CheckIn -> RootComponent.TopLevel.CHECK_IN

        RootComponent.Config.ReminderQueue -> RootComponent.TopLevel.REMINDERS

        RootComponent.Config.IntakeReview -> RootComponent.TopLevel.INTAKE

        RootComponent.Config.StaffList -> RootComponent.TopLevel.STAFF

        // Detail routes keep the *parent* tab lit rather than clearing the bar. The profile is
        // reached from the directory and the renewal sheet from the profile, so Members staying
        // highlighted tells you where back will take you.
        is RootComponent.Config.MemberProfile -> RootComponent.TopLevel.MEMBERS

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
        is RootComponent.Config.MemberProfile -> Permission.VIEW_MEMBERS
        RootComponent.Config.ReminderQueue -> Permission.VIEW_REMINDERS
        RootComponent.Config.IntakeReview -> Permission.SCAN_INTAKE
        is RootComponent.Config.Renewal -> Permission.MANAGE_SUBSCRIPTIONS
        RootComponent.Config.StaffList -> Permission.MANAGE_STAFF
    }
