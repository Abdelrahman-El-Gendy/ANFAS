package com.anfas.feature.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import com.anfas.core.common.MoneyFormat
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

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
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
                    text = state.currentTermEndsOn?.let { "Current plan ends ${it.formatLong()}" }
                        ?: "No active plan",
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

            Section("SELECT DURATION") {
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

            Section("START DATE") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnfasChoiceChip(
                        label = "Start today",
                        selected = state.start == RenewalStart.TODAY,
                        onClick = { component.onStartSelected(RenewalStart.TODAY) },
                    )
                    AnfasChoiceChip(
                        label = "Start when current ends",
                        selected = state.start == RenewalStart.WHEN_CURRENT_ENDS,
                        onClick = { component.onStartSelected(RenewalStart.WHEN_CURRENT_ENDS) },
                    )
                }
            }

            Section("PAYMENT METHOD") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentMethod.entries.forEach { method ->
                        AnfasChoiceChip(
                            label = method.label,
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
                    text = "Send WhatsApp confirmation",
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnfasSecondaryButton(
                    text = "CANCEL",
                    onClick = component::onCancel,
                    modifier = Modifier.weight(1f),
                )
                AnfasPrimaryButton(
                    text = if (state.isConfirming) "CONFIRMING…" else "CONFIRM RENEWAL",
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
    AnfasSelectableRow(selected = selected, onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = plan.tier.label,
                    style = AnfasTheme.textStyles.bodyLarge,
                    color = scheme.onSurface,
                )
                plan.savingsPercent?.let { percent ->
                    AnfasStatusChip(label = "Save $percent%", tone = ChipTone.Positive)
                }
            }
            Text(
                text = plan.perks,
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormat.format(plan.price),
            style = AnfasTheme.textStyles.bodyLarge,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun QuoteSummary(quote: RenewalQuote) {
    val scheme = MaterialTheme.colorScheme
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
            label = "${quote.plan.tier.label} plan",
            value = MoneyFormat.format(quote.plan.price),
        )
        if (!quote.discount.isZero) {
            SummaryLine(
                label = "Discount",
                value = MoneyFormat.formatNegated(quote.discount),
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
                text = "Total",
                style = AnfasTheme.textStyles.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormat.format(quote.total),
                style = AnfasTheme.textStyles.headlineMedium,
                color = scheme.onSurface,
            )
        }
        Text(
            text = "New end date: ${quote.endsOn.formatLong()}",
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
        Text(text = value, style = AnfasTheme.textStyles.bodyMedium, color = valueColor)
    }
}

private val com.anfas.core.model.PlanTier.label: String
    get() = when (this) {
        com.anfas.core.model.PlanTier.MONTHLY -> "Monthly"
        com.anfas.core.model.PlanTier.QUARTERLY -> "Quarterly"
        com.anfas.core.model.PlanTier.ANNUAL -> "Annual"
    }

private val PaymentMethod.label: String
    get() = when (this) {
        PaymentMethod.CASH -> "Cash"
        PaymentMethod.CARD -> "Card"
        PaymentMethod.INSTAPAY -> "InstaPay"
        PaymentMethod.VODAFONE_CASH -> "Vodafone Cash"
    }

private val PaymentMethod.icon
    get() = when (this) {
        PaymentMethod.CASH -> AnfasIcons.Payments
        PaymentMethod.CARD -> AnfasIcons.CreditCard
        PaymentMethod.INSTAPAY -> AnfasIcons.Send
        PaymentMethod.VODAFONE_CASH -> AnfasIcons.Smartphone
    }

/** "12 Aug 2026" — the export's date style on this screen. */
private fun LocalDate.formatLong(): String {
    val months = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    return "$day ${months[month.ordinal]} $year"
}
