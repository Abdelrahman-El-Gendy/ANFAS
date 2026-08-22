package com.anfas.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasCheckbox
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasSelectableRow
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTableDivider
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.LocalAppLanguage
import com.anfas.core.i18n.MoneyStyle
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.formatMoney
import com.anfas.core.i18n.formatMoneyNegated
import com.anfas.core.i18n.moneyStyle
import com.anfas.core.i18n.strings
import com.anfas.core.model.PaymentMethod
import com.anfas.core.model.RenewalQuote
import com.anfas.core.model.RenewalStart
import com.anfas.core.model.SubscriptionPlan
import kotlinx.datetime.LocalDate

/**
 * The renewal sheet: pick a duration, when it starts and how it is paid, then confirm.
 *
 * One thing the export shows that is not built: a "Discount −140 EGP" line with no control
 * anywhere to set it. The arithmetic is implemented and tested in
 * [com.anfas.core.model.RenewalCalculator], and the summary renders the line when a discount
 * is present — but there is no UI to enter one, because the design never says where it comes
 * from. Wire it to whatever decides it (promo code, staff override) when that exists.
 */
@Composable
fun RenewalSheetScreen(component: RenewalSheetComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val s = strings
    val money = LocalAppLanguage.current.moneyStyle()

    // fillMaxSize, not fillMaxWidth. Height matters here: `weight` divides the *bounded* height
    // of its parent, so with a wrap-content root the weighted child had nothing to divide and
    // fell back to its own intrinsic height. verticalScroll then measured its content unbounded,
    // the column grew taller than the window, and the scroll never engaged -- so in landscape
    // the plan list was simply cut off at the top with no way to reach it. Bounding the root is
    // what makes the scroll real.
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                // fill = true, so the footer is pinned below the scroll area rather than
                // floating directly under content that may be taller than the screen.
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AnfasTheme.spacing.marginMobile)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column {
                Text(
                    text = state.memberName,
                    style = AnfasTheme.textStyles.headlineSmall,
                    color = scheme.onSurface,
                )
                Text(
                    text = state.currentTermEndsOn
                        ?.let { s.renewal.currentPlanEnds(s.formatLong(it)) }
                        ?: s.renewal.noActivePlan,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.error,
                )
            }

            Section(s.renewal.selectDuration) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.plans.forEach { plan ->
                        PlanRow(
                            plan = plan,
                            selected = plan.id == state.selectedPlanId,
                            onClick = { component.onPlanSelected(plan.id) },
                        )
                    }
                }
            }

            Section(s.renewal.startDate) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnfasChoiceChip(
                        label = s.renewal.startToday,
                        selected = state.start == RenewalStart.TODAY,
                        onClick = { component.onStartSelected(RenewalStart.TODAY) },
                    )
                    AnfasChoiceChip(
                        label = s.renewal.startWhenCurrentEnds,
                        selected = state.start == RenewalStart.WHEN_CURRENT_ENDS,
                        onClick = { component.onStartSelected(RenewalStart.WHEN_CURRENT_ENDS) },
                    )
                }
            }

            Section(s.renewal.paymentMethod) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        AnfasChoiceChip(
                            label = method.label(s),
                            icon = method.icon,
                            selected = state.paymentMethod == method,
                            onClick = { component.onPaymentMethodSelected(method) },
                        )
                    }
                }
            }

            state.quote?.let { QuoteSummary(it) }
        }

        AnfasTableDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainer)
                .padding(AnfasTheme.spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnfasCheckbox(
                    checked = state.sendWhatsAppConfirmation,
                    onCheckedChange = component::onSendWhatsAppChanged,
                )
                Text(
                    text = s.renewal.sendWhatsAppConfirmation,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnfasSecondaryButton(
                    text = s.common.cancel,
                    onClick = component::onCancel,
                    modifier = Modifier.weight(1f),
                )
                AnfasPrimaryButton(
                    text = if (state.isConfirming) s.renewal.confirming else s.renewal.confirm,
                    onClick = component::onConfirm,
                    enabled = state.canConfirm,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = AnfasTheme.textStyles.labelCaps,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun PlanRow(plan: SubscriptionPlan, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = strings
    val money = LocalAppLanguage.current.moneyStyle()
    AnfasSelectableRow(selected = selected, onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = plan.tier.label(s),
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                )
                plan.savingsPercent?.let { percent ->
                    AnfasStatusChip(
                        label = s.renewal.savePercent(percent),
                        tone = ChipTone.Positive,
                    )
                }
            }
            Text(
                text = plan.perks,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatMoney(plan.price, money),
            style = AnfasTheme.textStyles.dataMonoLtr,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun QuoteSummary(quote: RenewalQuote) {
    val scheme = MaterialTheme.colorScheme
    val s = strings
    val money = LocalAppLanguage.current.moneyStyle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AnfasShapes.base)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, AnfasShapes.base)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryLine(
            label = s.renewal.planLine(quote.plan.tier.label(s)),
            value = formatMoney(quote.plan.price, money),
        )
        if (!quote.discount.isZero) {
            SummaryLine(
                label = s.renewal.discount,
                value = formatMoneyNegated(quote.discount, money),
                valueColor = scheme.secondary,
            )
        }
        AnfasTableDivider(Modifier.padding(vertical = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = s.common.total,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = formatMoney(quote.total, money),
                style = AnfasTheme.textStyles.headlineMedium.copy(
                    textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                ),
                color = scheme.onSurface,
            )
        }
        Text(
            text = s.renewal.newEndDate(s.formatLong(quote.endsOn)),
            style = AnfasTheme.textStyles.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // LTR: "−140 EGP" and "1,650 EGP" begin/end with direction-neutral characters, so in an
        // RTL paragraph the minus sign and the currency code render at the wrong end.
        Text(text = value, style = AnfasTheme.textStyles.dataMonoLtr, color = valueColor)
    }
}

private fun com.anfas.core.model.PlanTier.label(s: AppStrings): String = when (this) {
    com.anfas.core.model.PlanTier.MONTHLY -> s.renewal.tierMonthly
    com.anfas.core.model.PlanTier.QUARTERLY -> s.renewal.tierQuarterly
    com.anfas.core.model.PlanTier.ANNUAL -> s.renewal.tierAnnual
}

private fun PaymentMethod.label(s: AppStrings): String = when (this) {
    PaymentMethod.CASH -> s.renewal.paymentCash
    PaymentMethod.CARD -> s.renewal.paymentCard
    PaymentMethod.INSTAPAY -> s.renewal.paymentInstapay
    PaymentMethod.VODAFONE_CASH -> s.renewal.paymentVodafoneCash
}

private val PaymentMethod.icon
    get() = when (this) {
        PaymentMethod.CASH -> AnfasIcons.Payments
        PaymentMethod.CARD -> AnfasIcons.CreditCard
        PaymentMethod.INSTAPAY -> AnfasIcons.Send
        PaymentMethod.VODAFONE_CASH -> AnfasIcons.Smartphone
    }
