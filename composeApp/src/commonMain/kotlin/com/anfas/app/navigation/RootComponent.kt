package com.anfas.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

/**
 * Decompose navigation root. Deliberately routeless: [Config] has no entries yet, so
 * [childSlot] always resolves to no active child.
 *
 * Feature routes are a separate task. What is established here is the shape they plug
 * into — a serializable Config hierarchy and a single navigation owner — so features never
 * grow their own ad-hoc navigation.
 */
class RootComponent(
    componentContext: ComponentContext,
) : ComponentContext by componentContext {

    private val navigation = SlotNavigation<Config>()

    val child: Value<ChildSlot<Config, Child>> = childSlot(
        source = navigation,
        serializer = Config.serializer(),
        // No initial configuration: nothing is routed to yet.
        initialConfiguration = { null },
        handleBackButton = true,
        childFactory = ::createChild,
    )

    /**
     * [Config] is an empty sealed hierarchy, so this is unreachable today. Once routes are
     * added, replace the body with an exhaustive `when (config)` — the compiler will then
     * require a Child for every Config.
     */
    private fun createChild(config: Config, context: ComponentContext): Child =
        error("No routes are defined yet")

    /** Route definitions. Empty by design — add one entry per destination. */
    @Serializable
    sealed interface Config

    /** Instantiated components, one per [Config]. */
    sealed interface Child
}
