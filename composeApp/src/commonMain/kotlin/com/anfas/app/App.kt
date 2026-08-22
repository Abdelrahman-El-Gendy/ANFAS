package com.anfas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.app.navigation.RootComponent
import com.anfas.app.navigation.requiredPermission
import com.anfas.app.navigation.topLevel
import com.anfas.core.auth.Permission
import com.anfas.core.auth.Session
import com.anfas.core.auth.can
import com.anfas.core.designsystem.AnfasBottomNav
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasLanguageToggle
import com.anfas.core.designsystem.AnfasNavRail
import com.anfas.core.designsystem.AnfasScript
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.NavItem
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.i18n.AppLanguage
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.LanguageController
import com.anfas.core.i18n.ProvideLocalization
import com.anfas.core.i18n.strings
import com.anfas.feature.auth.SignInScreen
import com.anfas.feature.auth.StaffListScreen
import com.anfas.feature.intakeocr.IntakeReviewScreen
import com.anfas.feature.members.MemberProfileScreen
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
                val session by root.session.collectAsState()
                val stack by root.stack.subscribeAsState()
                // Nav chrome is hidden while signed out: there is nothing to navigate to, and a
                // bottom bar over a login form invites tapping into screens that do not exist yet
                // for this user.
                val active = stack.active.configuration.topLevel.takeIf { session != null }
                val s = strings
                val languages = AppLanguage.entries
                val toggle: @Composable () -> Unit = {
                    AnfasLanguageToggle(
                        options = languages.map { it.endonym },
                        selectedIndex = languages.indexOf(language),
                        onSelect = { languageController.select(languages[it]) },
                    )
                }
                val signOut: @Composable () -> Unit = {
                    AnfasTextAction(
                        text = s.auth.signOut,
                        onClick = root::onSignOut,
                        emphasis = TextActionEmphasis.Muted,
                    )
                }
                // Built from TopLevel so the destination list, its permission and its label
                // cannot drift apart. Destinations the session cannot reach are removed, not
                // disabled: a greyed tab advertises a capability the role does not have, and
                // RBAC that leaks the shape of the app is only half a boundary.
                val items: List<NavItem> = RootComponent.TopLevel.entries
                    .filter { destination -> session?.can(destination.permission) == true }
                    .map { destination ->
                        NavItem(
                            label = when (destination) {
                                RootComponent.TopLevel.MEMBERS -> s.members.title
                                RootComponent.TopLevel.REMINDERS -> s.reminders.title
                                RootComponent.TopLevel.INTAKE -> s.intake.title
                                RootComponent.TopLevel.STAFF -> s.staff.title
                            },
                            icon = when (destination) {
                                RootComponent.TopLevel.MEMBERS -> AnfasIcons.Person
                                RootComponent.TopLevel.REMINDERS -> AnfasIcons.Payments
                                RootComponent.TopLevel.INTAKE -> AnfasIcons.DocumentScanner
                                RootComponent.TopLevel.STAFF -> AnfasIcons.Group
                            },
                            selected = active == destination,
                            onClick = { root.onTopLevelSelected(destination) },
                        )
                    }

                // Insets are applied per-region, not wholesale. safeContentPadding() on the
                // whole shell also inset the bottom navigation bar, leaving it hovering above a
                // strip of empty background; the bar now consumes that inset itself so its
                // surface reaches the screen edge. Everything else still clears the notch.
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                            ),
                        ),
                ) {
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
                                    footer = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            toggle()
                                            signOut()
                                        }
                                    },
                                )
                            }
                            if (session == null) {
                                SignInScreen(root.signIn, Modifier.fillMaxSize())
                            } else {
                                Host(root, session!!, Modifier.fillMaxSize())
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // The toggle sits above the content, not in the bottom bar. As a
                            // fourth bottom-nav slot it stole width from three real
                            // destinations and crowded the bar's end edge; a language switch
                            // is also not a navigation destination.
                            if (session == null) {
                                // Signed out: the toggle alone, so someone can read the login
                                // form in their own language before they have an account.
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
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = AnfasTheme.spacing.marginMobile,
                                            vertical = 8.dp,
                                        ),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    signOut()
                                    Spacer(Modifier.width(8.dp))
                                    toggle()
                                }
                            }
                            if (session == null) {
                                SignInScreen(
                                    component = root.signIn,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                            } else {
                                Host(root, session!!, Modifier.fillMaxWidth().weight(1f))
                                AnfasBottomNav(items)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [session] is passed in so every child can be checked against it. Hiding an unreachable
 * destination from the nav bar is the primary defence; this is the second, for a route that is
 * reached anyway — a back stack restored after process death, or a destination pushed by code
 * that forgot to check.
 */
@Composable
private fun Host(root: RootComponent, session: Session, modifier: Modifier) {
    val s = strings
    Box(modifier = modifier) {
        Children(stack = root.stack, modifier = Modifier.fillMaxSize()) { created ->
            val required = created.configuration.requiredPermission
            if (!session.can(required)) {
                // The design's permission-denied screen. Its copy is about a staff *role*, which
                // is exactly what this is — unlike the camera denial in intake, which reuses none
                // of it.
                AnfasEmptyState(
                    icon = AnfasIcons.Warning,
                    title = s.states.permissionDeniedTitle(required.areaLabel(s)),
                    message = s.states.permissionDeniedMessage,
                    primaryAction = EmptyStateAction(
                        label = s.states.permissionDeniedAction,
                        onClick = root::onPermissionDeniedDismissed,
                    ),
                )
                return@Children
            }
            when (val child = created.instance) {
                is RootComponent.Child.MembersList ->
                    MembersListScreen(component = child.component)

                is RootComponent.Child.MemberProfile ->
                    MemberProfileScreen(component = child.component)

                is RootComponent.Child.StaffList ->
                    StaffListScreen(component = child.component)

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

/**
 * The human name for the area a permission guards, for the permission-denied message.
 *
 * Reuses the destinations' own titles where there is one, so the sentence a coach sees names the
 * thing they tapped rather than an internal enum.
 */
private fun Permission.areaLabel(s: AppStrings): String = when (this) {
    Permission.VIEW_MEMBERS, Permission.EDIT_MEMBERS -> s.members.title
    Permission.MANAGE_SUBSCRIPTIONS -> s.renewal.selectDuration
    Permission.VIEW_REMINDERS, Permission.RETRY_REMINDERS -> s.reminders.title
    Permission.SCAN_INTAKE, Permission.IMPORT_INTAKE -> s.intake.title
    Permission.VIEW_THERAPY -> s.states.fieldStatus
    Permission.MANAGE_STAFF -> s.staff.title
}
