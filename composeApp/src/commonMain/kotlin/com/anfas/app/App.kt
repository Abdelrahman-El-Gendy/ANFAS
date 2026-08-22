package com.anfas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.app.navigation.RootComponent
import com.anfas.app.navigation.topLevel
import com.anfas.core.designsystem.AnfasBottomNav
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasLanguageToggle
import com.anfas.core.designsystem.AnfasNavRail
import com.anfas.core.designsystem.AnfasScript
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.NavItem
import com.anfas.core.i18n.AppLanguage
import com.anfas.core.i18n.LanguageController
import com.anfas.core.i18n.ProvideLocalization
import com.anfas.core.i18n.strings
import com.anfas.feature.intakeocr.IntakeReviewScreen
import com.anfas.feature.members.MembersListScreen
import com.anfas.feature.subscriptions.ReminderQueueScreen
import com.anfas.feature.subscriptions.RenewalSheetScreen
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import org.koin.compose.koinInject

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
    val languageController: LanguageController = koinInject()
    val language by languageController.language.collectAsState()

    // Because `language` is snapshot state, switching it is an ordinary recomposition -- instant,
    // on all three platforms, with no restart. ProvideLocalization also sets LocalLayoutDirection,
    // so strings and mirroring can never disagree.
    ProvideLocalization(language) {
        AnfasTheme(script = language.toScript()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                val stack by root.stack.subscribeAsState()
                val active = stack.active.configuration.topLevel
                val s = strings
                val languages = AppLanguage.entries
                val toggle: @Composable () -> Unit = {
                    AnfasLanguageToggle(
                        options = languages.map { it.endonym },
                        selectedIndex = languages.indexOf(language),
                        onSelect = { languageController.select(languages[it]) },
                    )
                }
                val items = listOf(
                    NavItem(
                        label = s.members.title,
                        icon = AnfasIcons.Person,
                        selected = active == RootComponent.TopLevel.MEMBERS,
                        onClick = { root.onTopLevelSelected(RootComponent.TopLevel.MEMBERS) },
                    ),
                    NavItem(
                        label = s.reminders.title,
                        icon = AnfasIcons.Payments,
                        selected = active == RootComponent.TopLevel.REMINDERS,
                        onClick = { root.onTopLevelSelected(RootComponent.TopLevel.REMINDERS) },
                    ),
                    NavItem(
                        label = s.intake.title,
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
                                    title = s.common.appName,
                                    subtitle = s.common.appTagline,
                                    // Without this the toggle existed only on compact, so
                                    // language could not be changed at all on desktop.
                                    footer = toggle,
                                )
                            }
                            Host(root, Modifier.fillMaxSize())
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // The toggle sits above the content, not in the bottom bar. As a
                            // fourth bottom-nav slot it stole width from three real
                            // destinations and crowded the bar's end edge; a language switch
                            // is also not a navigation destination.
                            if (active != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = AnfasTheme.spacing.marginMobile,
                                            vertical = 8.dp,
                                        ),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    toggle()
                                }
                            }
                            Host(root, Modifier.fillMaxWidth().weight(1f))
                            if (active != null) AnfasBottomNav(items)
                        }
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

/**
 * The one place language and script meet. :core:designsystem deliberately knows nothing about
 * AppLanguage, and :core:i18n knows nothing about the type ramp, so the shell joins them.
 */
private fun AppLanguage.toScript(): AnfasScript = when (this) {
    AppLanguage.EN -> AnfasScript.Latin
    AppLanguage.AR -> AnfasScript.Arabic
}
