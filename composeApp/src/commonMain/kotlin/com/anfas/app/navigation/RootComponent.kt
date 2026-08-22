package com.anfas.app.navigation

import com.anfas.core.model.MemberId
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
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
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

    private val membersListFactory: MembersListComponentFactory by inject()
    private val memberProfileFactory: MemberProfileComponentFactory by inject()
    private val reminderQueueFactory: ReminderQueueComponentFactory by inject()
    private val renewalSheetFactory: RenewalSheetComponentFactory by inject()
    private val intakeReviewFactory: IntakeReviewComponentFactory by inject()

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.MembersList,
        handleBackButton = true,
        childFactory = ::createChild,
    )

    fun onTopLevelSelected(destination: TopLevel) {
        navigation.replaceAll(
            when (destination) {
                TopLevel.MEMBERS -> Config.MembersList
                TopLevel.REMINDERS -> Config.ReminderQueue
                TopLevel.INTAKE -> Config.IntakeReview
            },
        )
    }

    private fun createChild(config: Config, context: ComponentContext): Child = when (config) {
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
        @SerialName("members-list")
        data object MembersList : Config

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
        @SerialName("member-profile")
        data class MemberProfile(val memberId: String) : Config

        @Serializable
        @SerialName("renewal")
        data class Renewal(val memberId: String) : Config
    }

    /** Instantiated components, one per [Config]. */
    sealed interface Child {
        data class MembersList(val component: MembersListComponent) : Child
        data class MemberProfile(val component: MemberProfileComponent) : Child
        data class ReminderQueue(val component: ReminderQueueComponent) : Child
        data class IntakeReview(val component: IntakeReviewComponent) : Child
        data class Renewal(val component: RenewalSheetComponent) : Child
    }

    /** The destinations the nav rail/bottom bar offers. */
    enum class TopLevel { MEMBERS, REMINDERS, INTAKE }
}

/** Which nav entry should read as active for a given route, or null for detail screens. */
internal val RootComponent.Config.topLevel: RootComponent.TopLevel?
    get() = when (this) {
        RootComponent.Config.MembersList -> RootComponent.TopLevel.MEMBERS

        RootComponent.Config.ReminderQueue -> RootComponent.TopLevel.REMINDERS

        RootComponent.Config.IntakeReview -> RootComponent.TopLevel.INTAKE

        // Detail routes keep the *parent* tab lit rather than clearing the bar. The profile is
        // reached from the directory and the renewal sheet from the profile, so Members staying
        // highlighted tells you where back will take you.
        is RootComponent.Config.MemberProfile -> RootComponent.TopLevel.MEMBERS

        is RootComponent.Config.Renewal -> null
    }
