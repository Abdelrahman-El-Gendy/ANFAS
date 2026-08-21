package com.anfas.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anfas.app.navigation.RootComponent
import com.anfas.app.navigation.topLevel
import com.anfas.core.designsystem.AnfasBottomNav
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasNavRail
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.NavItem
import com.anfas.feature.intakeocr.IntakeReviewScreen
import com.anfas.feature.members.MembersListScreen
import com.anfas.feature.subscriptions.ReminderQueueScreen
import com.anfas.feature.subscriptions.RenewalSheetScreen
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import androidx.compose.runtime.getValue

/**
 * App shell: theme, the navigation host, and the top-level nav chrome.
 *
 * The design uses a fixed rail on desktop and a bottom bar on mobile, so the breakpoint from
 * the design system picks between them. Only the two destinations that actually exist are
 * offered — the export's sidebar lists eight, but an entry that leads nowhere is worse than
 * an absent one.
 *
 * Detail routes (the renewal sheet) hide the nav chrome: they are pushed, and back pops them.
 */
@Composable
fun App(root: RootComponent) {
    AnfasTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val stack by root.stack.subscribeAsState()
            val active = stack.active.configuration.topLevel
            val items = listOf(
                NavItem(
                    label = "Members",
                    icon = AnfasIcons.Person,
                    selected = active == RootComponent.TopLevel.MEMBERS,
                    onClick = { root.onTopLevelSelected(RootComponent.TopLevel.MEMBERS) },
                ),
                NavItem(
                    label = "Reminders",
                    icon = AnfasIcons.Payments,
                    selected = active == RootComponent.TopLevel.REMINDERS,
                    onClick = { root.onTopLevelSelected(RootComponent.TopLevel.REMINDERS) },
                ),
                NavItem(
                    label = "Intake",
                    icon = AnfasIcons.DocumentScanner,
                    selected = active == RootComponent.TopLevel.INTAKE,
                    onClick = { root.onTopLevelSelected(RootComponent.TopLevel.INTAKE) },
                ),
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize().safeContentPadding()) {
                val wide = maxWidth >= AnfasBreakpoints.tabletMax
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (active != null) {
                            AnfasNavRail(
                                items = items,
                                title = "ANFAS",
                                subtitle = "GYM MANAGEMENT",
                            )
                        }
                        Host(root, Modifier.fillMaxSize())
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Host(root, Modifier.fillMaxWidth().weight(1f))
                        if (active != null) AnfasBottomNav(items)
                    }
                }
            }
        }
    }
}

@Composable
private fun Host(root: RootComponent, modifier: Modifier) {
    Box(modifier = modifier) {
        Children(stack = root.stack, modifier = Modifier.fillMaxSize()) { created ->
            when (val child = created.instance) {
                is RootComponent.Child.MembersList ->
                    MembersListScreen(component = child.component)

                is RootComponent.Child.ReminderQueue ->
                    ReminderQueueScreen(component = child.component)

                is RootComponent.Child.IntakeReview ->
                    IntakeReviewScreen(component = child.component)

                is RootComponent.Child.Renewal ->
                    RenewalSheetScreen(component = child.component)
            }
        }
    }
}
