package com.anfas.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.app.navigation.RootComponent
import com.anfas.app.navigation.bottomBarSelection
import com.anfas.app.navigation.requiredPermission
import com.anfas.app.navigation.topLevel
import com.anfas.core.auth.Permission
import com.anfas.core.auth.Session
import com.anfas.core.auth.can
import com.anfas.core.designsystem.AnfasBottomNav
import com.anfas.core.designsystem.AnfasBreakpoints
import com.anfas.core.designsystem.AnfasEdgeDivider
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasIdentityRow
import com.anfas.core.designsystem.AnfasLanguageToggle
import com.anfas.core.designsystem.AnfasNavRail
import com.anfas.core.designsystem.AnfasOverflowMenu
import com.anfas.core.designsystem.AnfasScript
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.EmptyStateAction
import com.anfas.core.designsystem.MenuAction
import com.anfas.core.designsystem.NavItem
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.designsystem.initialsOf
import com.anfas.core.i18n.AppLanguage
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.LanguageController
import com.anfas.core.i18n.ProvideLocalization
import com.anfas.core.i18n.strings
import com.anfas.feature.auth.SignInScreen
import com.anfas.feature.auth.StaffListScreen
import com.anfas.feature.auth.label
import com.anfas.feature.checkin.CheckInScreen
import com.anfas.feature.dashboard.DashboardScreen
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
 * the design system picks between them. Only destinations that actually exist are offered — the
 * export's sidebar lists eight, but an entry that leads nowhere is worse than an absent one.
 *
 * Three kinds of thing, kept apart on purpose, because collapsing them is what produced a
 * six-slot bottom bar of ellipsised labels:
 *  - **Destinations** — [RootComponent.TopLevel], at most four, in the bar or the rail.
 *  - **Screen actions** — "scan a sheet" belongs to the directory, not to the bar.
 *  - **Account-level things** — language, staff management, sign out. These are not destinations,
 *    and on compact they live behind the top bar's overflow rather than competing with the
 *    screens used on every shift.
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
                val currentStaff by root.currentStaff.collectAsState()
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
                // Whoever is signed in, for the rail footer and the compact bar's avatar. Null
                // until the database read lands, and null while signed out -- both render as no
                // identity rather than as a placeholder person.
                val staffName = currentStaff?.displayName
                val staffRoles = currentStaff?.roles
                    ?.sortedBy { it.ordinal }
                    ?.joinToString(", ") { it.label(s) }
                    .orEmpty()
                // Account-level, not navigation. Staff management sits here rather than in the
                // bar because it is administration done occasionally by one person, and every
                // slot it took from the bar was taken from a screen used on every shift.
                // Permission-filtered, not disabled, for the same reason the tabs are: a greyed
                // entry advertises a capability the role does not have.
                val accountActions: List<MenuAction> = buildList {
                    if (session?.can(Permission.MANAGE_STAFF) == true) {
                        add(
                            MenuAction(
                                label = s.staff.title,
                                icon = AnfasIcons.Group,
                                onClick = root::onOpenStaff,
                            ),
                        )
                    }
                    add(MenuAction(label = s.auth.signOut, onClick = root::onSignOut))
                }
                // Built from TopLevel so the destination list, its permission and its label
                // cannot drift apart. Destinations the session cannot reach are removed, not
                // disabled: a greyed entry advertises a capability the role does not have, and
                // RBAC that leaks the shape of the app is only half a boundary.
                //
                // One builder, three lists, differing only by Placement -- so a new destination
                // cannot end up on the rail and missing from the bar by omission.
                val navItem: (RootComponent.TopLevel, RootComponent.TopLevel?) -> NavItem =
                    { destination, selection ->
                        NavItem(
                            label = when (destination) {
                                RootComponent.TopLevel.DASHBOARD -> s.dashboard.title
                                RootComponent.TopLevel.MEMBERS -> s.members.title
                                RootComponent.TopLevel.CHECK_IN -> s.checkIn.title
                                RootComponent.TopLevel.REMINDERS -> s.reminders.title
                                RootComponent.TopLevel.INTAKE -> s.intake.title
                                RootComponent.TopLevel.STAFF -> s.staff.title
                            },
                            icon = when (destination) {
                                RootComponent.TopLevel.DASHBOARD -> AnfasIcons.Schedule
                                RootComponent.TopLevel.MEMBERS -> AnfasIcons.Person
                                RootComponent.TopLevel.CHECK_IN -> AnfasIcons.CheckCircle
                                RootComponent.TopLevel.REMINDERS -> AnfasIcons.Payments
                                RootComponent.TopLevel.INTAKE -> AnfasIcons.DocumentScanner
                                RootComponent.TopLevel.STAFF -> AnfasIcons.Group
                            },
                            selected = selection == destination,
                            onClick = { root.onTopLevelSelected(destination) },
                        )
                    }
                val reachable: (RootComponent.Placement) -> List<RootComponent.TopLevel> =
                    { placement ->
                        RootComponent.TopLevel.entries.filter {
                            it.placement == placement && session?.can(it.permission) == true
                        }
                    }

                // The bar: Primary only, and intake folds onto Members so the tab you came from
                // stays lit.
                val items: List<NavItem> = reachable(RootComponent.Placement.Primary)
                    .map { navItem(it, active?.bottomBarSelection) }
                // The rail: Primary plus WideOnly, each lighting itself.
                val railItems: List<NavItem> =
                    (
                        reachable(RootComponent.Placement.Primary) +
                            reachable(RootComponent.Placement.WideOnly)
                        ).map { navItem(it, active) }
                // Below the rail's divider, with sign-out. Rendered as rows rather than as the
                // compact overflow because 256dp has room to spell them out.
                val secondaryRailItems: List<NavItem> =
                    reachable(RootComponent.Placement.Account).map { navItem(it, active) } +
                        NavItem(
                            label = s.auth.signOut,
                            icon = AnfasIcons.Logout,
                            selected = false,
                            onClick = root::onSignOut,
                        )

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
                                    // The rail carries every destination, the bar carries four.
                                    // That is what the export does, and the reason is width: a
                                    // 256dp list costs nothing per row, whereas the bar divides
                                    // a phone between its items. So intake is a rail
                                    // destination here and a members action on a phone.
                                    items = railItems,
                                    title = s.common.appName,
                                    subtitle = s.common.appTagline,
                                    secondaryItems = secondaryRailItems,
                                    // Without this the toggle existed only on compact, so
                                    // language could not be changed at all on desktop.
                                    // A 256dp rail has room to spell the account actions out,
                                    // so they are laid out rather than hidden behind an
                                    // overflow. The information architecture is the same as on
                                    // compact -- four destinations above, account below the
                                    // fold -- only the affordance differs, which is the whole
                                    // reason a rail and a bar are different components.
                                    footer = {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (staffName != null) {
                                                AnfasIdentityRow(
                                                    name = staffName,
                                                    subtitle = staffRoles,
                                                )
                                            }
                                            // The language switch stays a control rather than
                                            // becoming a rail row: it is a two-state toggle, and
                                            // a row that navigates nowhere among rows that do is
                                            // the confusion this footer group exists to avoid.
                                            Row(
                                                modifier = Modifier.padding(start = 12.dp),
                                            ) {
                                                toggle()
                                            }
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
                            // A top bar, not a floating pair of controls. Neither the language
                            // switch nor sign-out is a navigation destination, so neither
                            // belongs in the bottom bar -- as bottom-nav slots they stole width
                            // from the real destinations and crowded its end edge.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 56dp and a bottom edge, from the export's
                                    // `h-16 border-b border-white/10` header. Fixed height
                                    // rather than content height so the bar does not change
                                    // size between English and Arabic, and 56 rather than 64
                                    // because ours carries no avatar yet.
                                    .height(TOP_BAR_HEIGHT)
                                    .padding(
                                        start = AnfasTheme.spacing.marginMobile,
                                        end = 4.dp,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = s.common.appName,
                                    style = AnfasTheme.textStyles.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Weighted so the actions keep their size and the *name*
                                    // truncates -- the reverse pushes the overflow button off
                                    // the trailing edge, which is the fillMaxWidth-in-a-Row
                                    // defect this codebase has already been bitten by.
                                    modifier = Modifier.weight(1f),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Signed out the toggle stands alone, so someone can read
                                    // the login form in their own language before they have an
                                    // account. There is no account to manage yet.
                                    toggle()
                                    if (session != null) {
                                        AnfasOverflowMenu(
                                            actions = accountActions,
                                            contentDescription = s.common.moreOptions,
                                            initials = staffName?.let { initialsOf(it) },
                                        )
                                    }
                                }
                            }
                            AnfasEdgeDivider()
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
                is RootComponent.Child.Dashboard ->
                    DashboardScreen(component = child.component)

                is RootComponent.Child.MembersList ->
                    MembersListScreen(component = child.component)

                is RootComponent.Child.CheckIn ->
                    CheckInScreen(component = child.component)

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
    Permission.CHECK_IN_MEMBERS -> s.checkIn.title
    Permission.VIEW_THERAPY -> s.states.fieldStatus
    Permission.MANAGE_STAFF -> s.staff.title
}

/**
 * The compact top bar's height, from the export's `h-16` header. A constant rather than inline so
 * it cannot drift from the bottom bar's own touch-target floor.
 */
private val TOP_BAR_HEIGHT = 56.dp
