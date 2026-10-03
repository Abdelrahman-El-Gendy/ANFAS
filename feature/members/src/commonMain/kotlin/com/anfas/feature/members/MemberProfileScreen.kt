package com.anfas.feature.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasCheckbox
import com.anfas.core.designsystem.AnfasDetailTopBar
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasProgressBar
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.designsystem.ProgressTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.LocalAppLanguage
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.formatMoney
import com.anfas.core.i18n.moneyStyle
import com.anfas.core.i18n.strings
import com.anfas.core.model.Member
import com.anfas.core.model.PlanTier
import com.anfas.core.model.SubscriptionTerm
import com.anfas.core.model.TermProgress

/**
 * One member's profile.
 *
 * Departures from the export's `member-profile`, all because the data does not exist rather than
 * because the design was ignored:
 *  - **No Sessions or Payments tabs.** Nothing records a check-in-log session or a payment line
 *    yet, so those two of the four tabs would be permanently empty. The subscription content is
 *    shown directly instead of behind a single-tab strip. **Therapy is real** — see below.
 *  - **No "Check-ins this month" and no "Recent activity".** There is no check-in log — the only
 *    thing stored is `Member.lastCheckInAt`, so that single fact is shown honestly and the
 *    14-item activity feed is not invented. Both return with the `live-checkin-log` screen.
 *  - **No "Member since".** `Member` has no created-at column. Adding one would be a Room
 *    migration that backfills a date nobody knows, which is worse than omitting the row.
 *  - **No "Send reminder" button.** The WhatsApp send job does not exist, so it would be dead.
 *
 * What is here is real: identity, status, the current term with its dates and a computed
 * remaining-time bar, Renew — which routes to the renewal sheet — and, for a session holding
 * `VIEW_THERAPY`, a Therapy button routing to `:feature:therapy`'s case file. Hidden rather than
 * disabled for everyone else, the same reasoning as Add Member: a greyed affordance only
 * advertises a capability the role does not have.
 */
@Composable
fun MemberProfileScreen(component: MemberProfileComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(modifier = modifier.fillMaxSize()) {
        // Shared with the other two pushed screens (intake, staff) rather than reimplemented
        // per screen -- the back affordance must sit in the same place on all of them.
        AnfasDetailTopBar(
            title = s.members.profileTitle,
            onBack = component::onBack,
            backContentDescription = s.common.back,
        )

        when (val content = state.content) {
            MemberProfileContent.Loading -> Spacer(Modifier.fillMaxSize())

            MemberProfileContent.Missing -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.members.profileNotFoundTitle,
                message = s.members.profileNotFoundMessage,
            )

            is MemberProfileContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.members.loadFailedTitle,
                message = content.message,
            )

            is MemberProfileContent.Loaded -> ProfileBody(
                content = content,
                mayViewTherapy = state.mayViewTherapy,
                mayEdit = state.mayEdit,
                onRenew = component::onRenew,
                onTherapy = component::onTherapy,
                onWhatsAppConsentChanged = component::onWhatsAppConsentChanged,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileBody(
    content: MemberProfileContent.Loaded,
    mayViewTherapy: Boolean,
    mayEdit: Boolean,
    onRenew: () -> Unit,
    onTherapy: () -> Unit,
    onWhatsAppConsentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings
    Column(
        // Scrollable, and bounded by the caller's weight(1f). Without both, a short landscape
        // window clips the membership card and the Renew button with no way to reach them.
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AnfasTheme.spacing.marginMobile)
            .padding(top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Identity(content.member, content.progress)

        MembershipCard(term = content.term, progress = content.progress, s = s)

        WhatsAppConsent(
            consented = content.member.whatsappOptIn,
            enabled = mayEdit,
            onChange = onWhatsAppConsentChanged,
            s = s,
        )

        AnfasPrimaryButton(
            text = s.members.profileRenew,
            onClick = onRenew,
            icon = AnfasIcons.Payments,
            modifier = Modifier.fillMaxWidth(),
        )

        // Hidden, not disabled, for a role that cannot see clinical records -- a coach with no
        // VIEW_THERAPY should not even learn the button exists.
        if (mayViewTherapy) {
            AnfasSecondaryButton(
                text = s.therapy.title,
                onClick = onTherapy,
                icon = AnfasIcons.Group,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * WhatsApp consent.
 *
 * Shown to everyone who can open the profile but only editable with `EDIT_MEMBERS`, which is the
 * opposite call from the Therapy button above: whether a member may be messaged is a fact about
 * them that anyone reading the profile should be able to see, whereas clinical records are not.
 *
 * The hint is not decoration. Consent has to be *asked for* -- Meta has no API to tell us whether
 * someone opted in -- so the person tapping this is asserting they asked.
 */
@Composable
private fun WhatsAppConsent(
    consented: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    s: AppStrings,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnfasCheckbox(
            checked = consented,
            // Null rather than a no-op lambda, so the control reads as unavailable to
            // accessibility services instead of merely ignoring taps.
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).padding(top = 10.dp),
        ) {
            Text(
                text = s.members.profileWhatsAppConsent,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = s.members.profileWhatsAppConsentHint,
                // No bodySmall in this design system; the variant colour carries the hierarchy.
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Identity(member: Member, progress: TermProgress?) {
    val scheme = MaterialTheme.colorScheme
    val s = strings
    Row(verticalAlignment = Alignment.CenterVertically) {
        MemberAvatar(member = member, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = member.fullName,
                style = AnfasTheme.textStyles.headlineSmall,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Not dataMonoLtr: the Arabic label would be reordered along with the number.
                // The *number* is wrapped in an LTR isolate inside the string table instead.
                text = s.members.idPrefix(member.membershipNumber),
                style = AnfasTheme.textStyles.dataMono,
                color = scheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AnfasIcons.Smartphone,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // Phone numbers stay LTR even in an Arabic layout: the leading "+" is
                    // direction-neutral and would otherwise render at the wrong end.
                    text = member.phone ?: s.members.profileNoPhone,
                    style = if (member.phone != null) {
                        AnfasTheme.textStyles.dataMonoLtr
                    } else {
                        AnfasTheme.textStyles.bodyMedium
                    },
                    color = scheme.onSurfaceVariant,
                )
            }
            progress?.let { ExpiryChip(it, s) }
        }
    }
}

/** The export's pill above the membership card. Derived from the term, not from member.status. */
@Composable
private fun ExpiryChip(progress: TermProgress, s: AppStrings) {
    val (label, tone) = when (progress.state) {
        TermProgress.State.Expired -> s.members.profileExpired to ChipTone.Critical

        TermProgress.State.ExpiringSoon ->
            s.members.profileExpiresInDays(progress.remainingDays) to ChipTone.Critical

        TermProgress.State.NotStarted -> null to ChipTone.Neutral

        TermProgress.State.Active ->
            s.members.profileExpiresInDays(progress.remainingDays) to ChipTone.Neutral
    }
    label?.let {
        Row(modifier = Modifier.padding(top = 4.dp)) {
            AnfasStatusChip(label = it, tone = tone)
        }
    }
}

@Composable
private fun MembershipCard(term: SubscriptionTerm?, progress: TermProgress?, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    val money = LocalAppLanguage.current.moneyStyle()

    AnfasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = s.members.profileCurrentMembership,
                style = AnfasTheme.textStyles.labelCaps,
                color = scheme.onSurfaceVariant,
            )

            if (term == null || progress == null) {
                // Not an error state. A member imported from a paper sheet has no term until
                // someone renews, and Renew below is exactly the action to offer.
                Text(
                    text = s.members.profileNoActivePlan,
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                )
                Text(
                    text = s.members.profileNoActivePlanMessage,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                return@Column
            }

            DetailRow(s.members.profilePlan, term.tier.label(s))
            // mono = false for dates and money. Both are *localised* strings that contain Arabic
            // words -- a translated month name, the "ج.م" currency abbreviation -- and forcing
            // TextDirection.Ltr on them reorders those runs: "22 أغسطس 2026" rendered as
            // "22 2026 أغسطس". dataMonoLtr is for Latin-only data (phone numbers, ids).
            DetailRow(s.members.profileStartDate, s.formatLong(term.startsOn))
            DetailRow(s.members.profileEndDate, s.formatLong(term.endsOn))
            DetailRow(s.members.profilePaid, formatMoney(term.paid, money))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = s.members.profileTimeRemaining,
                        style = AnfasTheme.textStyles.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        // The export shows a number beside the bar, and it earns its place: at
                        // full or empty the bar alone is ambiguous about which it is.
                        text = s.members.profilePercent(progress.remainingPercent),
                        style = AnfasTheme.textStyles.dataMonoLtr,
                        color = scheme.onSurface,
                    )
                }
                AnfasProgressBar(
                    // remainingFraction, not fraction. The label says "remaining", so the bar
                    // starts full and drains; feeding it elapsed time made a freshly-paid
                    // membership render as an empty bar.
                    fraction = progress.remainingFraction,
                    tone = when (progress.state) {
                        TermProgress.State.Expired,
                        TermProgress.State.ExpiringSoon,
                        -> ProgressTone.Critical

                        else -> ProgressTone.Normal
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            // Dates and money keep an LTR run so their separators and currency code do not
            // migrate to the wrong end inside an Arabic paragraph.
            text = value,
            style = if (mono) {
                AnfasTheme.textStyles.dataMonoLtr
            } else {
                AnfasTheme.textStyles.bodyMedium
            },
            color = scheme.onSurface,
        )
    }
}

/**
 * Reads the tier names out of the renewal section of the string table rather than duplicating
 * them under `members`. A plan tier is the same word wherever it appears, and a second copy would
 * be a translation that drifts. The *function* is local because a feature may never depend on
 * another feature — :feature:subscriptions has its own identical one.
 */
private fun PlanTier.label(s: AppStrings): String = when (this) {
    PlanTier.MONTHLY -> s.renewal.tierMonthly
    PlanTier.QUARTERLY -> s.renewal.tierQuarterly
    PlanTier.ANNUAL -> s.renewal.tierAnnual
}
