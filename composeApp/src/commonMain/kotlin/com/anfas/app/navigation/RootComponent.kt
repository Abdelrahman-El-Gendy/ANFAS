package com.anfas.app.navigation

import com.anfas.core.model.MemberId
import com.anfas.feature.intakeocr.IntakeReviewComponent
import com.anfas.feature.intakeocr.IntakeReviewComponentFactory
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
class RootComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext, KoinComponent {

    private val membersListFactory: MembersListComponentFactory by inject()
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

    private fun createChild(config: Config, context: ComponentContext): Child =
        when (config) {
            Config.MembersList -> Child.MembersList(
                membersListFactory.create(
                    componentContext = context,
                    // The member profile screen exists in the design but not yet in the app,
                    // so tapping a row opens the renewal sheet — the primary thing staff do
                    // with a member. When the profile lands, this becomes the profile route
                    // and renewal moves behind the row's overflow menu.
                    onMemberClicked = { id -> navigation.push(Config.Renewal(id.value)) },
                    onAddMemberClicked = {},
                    onScanSheetClicked = {},
                ),
            )

            Config.ReminderQueue -> Child.ReminderQueue(
                reminderQueueFactory.create(
                    componentContext = context,
                    onOpenMemberClicked = { id -> navigation.push(Config.Renewal(id.value)) },
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
    @Serializable
    sealed interface Config {
        @Serializable
        data object MembersList : Config

        @Serializable
        data object ReminderQueue : Config

        @Serializable
        data object IntakeReview : Config

        /**
         * Carries the raw id string rather than [MemberId]: configs are serialized to restore
         * state, and a value class adds nothing here beyond a custom serializer.
         */
        @Serializable
        data class Renewal(val memberId: String) : Config
    }

    /** Instantiated components, one per [Config]. */
    sealed interface Child {
        data class MembersList(val component: MembersListComponent) : Child
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
        is RootComponent.Config.Renewal -> null
    }
