package com.anfas.feature.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anfas.core.data.EquipmentDetail
import com.anfas.core.designsystem.AnfasDialog
import com.anfas.core.designsystem.AnfasSecondaryButton
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTextAction
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.TextActionEmphasis
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.LocalAppLanguage
import com.anfas.core.i18n.MoneyStyle
import com.anfas.core.i18n.formatLong
import com.anfas.core.i18n.formatMoney
import com.anfas.core.i18n.moneyStyle
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.MaintenanceLogEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A modal rather than the export's slide-in side panel — the same list-plus-`AnfasDialog` shape
 * every other feature in this app uses for a single record's detail (Classes, Therapy,
 * Announcements). A dedicated sliding-drawer component for this one screen would be a new
 * primitive for no reuse.
 */
@Composable
internal fun EquipmentDetailDrawer(
    detailState: EquipmentDetailState,
    mayManage: Boolean,
    component: EquipmentComponent,
    s: AppStrings,
) {
    val detail = detailState.detail
    AnfasDialog(
        title = detail?.equipment?.name.orEmpty(),
        onDismissRequest = component::onDetailDismissed,
        closeContentDescription = s.common.close,
        footer = if (detail != null && mayManage) {
            {
                AnfasTextAction(
                    text = s.equipment.markOutOfOrder,
                    onClick = component::onMarkOutOfOrder,
                    emphasis = TextActionEmphasis.Muted,
                    enabled = detail.equipment.status != EquipmentStatus.OUT_OF_ORDER,
                )
                Spacer(Modifier.weight(1f))
                AnfasSecondaryButton(
                    text = s.equipment.logMaintenance,
                    onClick = component::onRequestLogMaintenance,
                )
            }
        } else {
            null
        },
    ) {
        if (detail == null) {
            Text(text = "…", style = AnfasTheme.textStyles.bodyMedium)
        } else {
            EquipmentDetailContent(detail = detail, s = s)
        }
    }

    detailState.logForm?.let { form ->
        LogMaintenanceDialog(form = form, component = component, s = s)
    }
}

@Composable
private fun EquipmentDetailContent(detail: EquipmentDetail, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    val zone = TimeZone.currentSystemDefault()
    val money = LocalAppLanguage.current.moneyStyle()
    val equipment = detail.equipment

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AnfasStatusChip(label = equipment.status.label(s), tone = equipment.status.tone())

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(s.equipment.specificationsTitle)
            SpecRow(s.equipment.specZone, equipment.zone.label(s))
            equipment.manufacturer?.let { SpecRow(s.equipment.specManufacturer, it) }
            equipment.serialNumber?.let { SpecRow(s.equipment.specSerial, it) }
            equipment.purchasedOn?.let { SpecRow(s.equipment.specPurchased, s.formatLong(it)) }
            equipment.warrantyUntil?.let {
                SpecRow(s.equipment.specWarrantyUntil, s.formatLong(it))
            }
            SpecRow(
                s.equipment.specLastService,
                detail.lastServiceOn
                    ?.let { s.formatLong(it.toLocalDateTime(zone).date) }
                    ?: s.equipment.lastServiceNotRecorded,
            )
        }

        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.10f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(s.equipment.maintenanceLogTitle)
            if (detail.log.isEmpty()) {
                Text(
                    text = s.equipment.noLogEntriesYet,
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                detail.log.forEach { entry ->
                    MaintenanceLogRow(entry = entry, s = s, zone = zone, money = money)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AnfasTheme.textStyles.labelCaps,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MaintenanceLogRow(
    entry: MaintenanceLogEntry,
    s: AppStrings,
    zone: TimeZone,
    money: MoneyStyle,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = s.formatLong(entry.occurredAt.toLocalDateTime(zone).date),
            style = AnfasTheme.textStyles.dataMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.summary,
            style = AnfasTheme.textStyles.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (entry.details.isNotBlank()) {
            Text(
                text = entry.details,
                style = AnfasTheme.textStyles.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.partsUsed?.let {
            Text(
                text = s.equipment.partsUsedPrefix(it),
                style = AnfasTheme.textStyles.labelCaps,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val who = entry.technician?.let { s.equipment.technicianPrefix(it) }
                ?: entry.reportedByStaffName?.let { s.equipment.reportedByPrefix(it) }
            who?.let {
                Text(
                    text = it,
                    style = AnfasTheme.textStyles.labelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entry.cost?.let {
                Text(
                    text = s.equipment.costLabel(formatMoney(it, money)),
                    style = AnfasTheme.textStyles.labelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
