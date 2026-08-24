package com.anfas.feature.equipment

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasBanner
import com.anfas.core.designsystem.AnfasCard
import com.anfas.core.designsystem.AnfasChoiceChip
import com.anfas.core.designsystem.AnfasEmptyState
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.AnfasPrimaryButton
import com.anfas.core.designsystem.AnfasScreenHeader
import com.anfas.core.designsystem.AnfasSearchField
import com.anfas.core.designsystem.AnfasShapes
import com.anfas.core.designsystem.AnfasStatusChip
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.designsystem.BannerTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.i18n.strings
import com.anfas.core.model.Equipment
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone

/**
 * The gym floor's equipment inventory — the export's `equipment-detail`, its own fourth desktop
 * rail item with no mobile counterpart at all (see `RootComponent.TopLevel.EQUIPMENT`'s KDoc).
 *
 * A grid-plus-drawer screen the same shape as the export draws it, but the drawer's footer keeps
 * only what real data backs: "Mark out of order" and "Log maintenance" write real rows. Nothing
 * here fabricates a number the way the parked reception-dashboard tiles would have.
 */
@Composable
fun EquipmentScreen(component: EquipmentComponent, modifier: Modifier = Modifier) {
    val state by component.state.collectAsState()
    val s = strings

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AnfasTheme.spacing.marginDesktop)
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnfasScreenHeader(
            title = s.equipment.title,
            subtitle = s.equipment.subtitle,
            actions = if (state.mayManage) {
                {
                    AnfasPrimaryButton(
                        text = s.equipment.addEquipment,
                        icon = AnfasIcons.Add,
                        onClick = component::onNewEquipment,
                    )
                }
            } else {
                null
            },
        )

        state.notice?.let { notice ->
            val isFailure = notice is EquipmentNotice.Failed
            AnfasBanner(
                message = notice.render(s),
                icon = if (isFailure) AnfasIcons.ErrorOutline else AnfasIcons.Check,
                tone = if (isFailure) BannerTone.Critical else BannerTone.Informational,
                dismissLabel = s.common.dismiss,
                onDismiss = component::onNoticeShown,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnfasChoiceChip(
                label = s.equipment.filterAll,
                selected = state.statusFilter == null,
                onClick = { component.onStatusFilterChanged(null) },
            )
            EquipmentStatus.entries.forEach { status ->
                AnfasChoiceChip(
                    label = status.label(s),
                    selected = state.statusFilter == status,
                    onClick = { component.onStatusFilterChanged(status) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnfasChoiceChip(
                    label = s.equipment.zoneAll,
                    selected = state.zoneFilter == null,
                    onClick = { component.onZoneFilterChanged(null) },
                    icon = AnfasIcons.LocationOn,
                )
                EquipmentZone.entries.forEach { zone ->
                    AnfasChoiceChip(
                        label = zone.label(s),
                        selected = state.zoneFilter == zone,
                        onClick = { component.onZoneFilterChanged(zone) },
                        icon = AnfasIcons.LocationOn,
                    )
                }
            }
            AnfasSearchField(
                value = state.searchQuery,
                onValueChange = component::onSearchQueryChanged,
                placeholder = s.equipment.searchPlaceholder,
                clearContentDescription = s.common.clearSearch,
                modifier = Modifier.width(240.dp),
            )
        }

        when (val content = state.content) {
            EquipmentContent.Loading -> Box(Modifier.fillMaxSize())

            is EquipmentContent.Failed -> AnfasEmptyState(
                icon = AnfasIcons.ErrorOutline,
                title = s.equipment.loadFailedTitle,
                message = content.message,
            )

            EquipmentContent.Empty -> AnfasEmptyState(
                icon = AnfasIcons.Build,
                title = s.equipment.emptyTitle,
                message = s.equipment.emptyMessage,
            )

            is EquipmentContent.Loaded -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(state.visibleEquipment, key = { it.id.value }) { equipment ->
                    EquipmentCard(
                        equipment = equipment,
                        onClick = { component.onEquipmentSelected(equipment.id) },
                        s = s,
                    )
                }
            }
        }
    }

    state.detail?.let { detail ->
        EquipmentDetailDrawer(
            detailState = detail,
            mayManage = state.mayManage,
            component = component,
            s = s,
        )
    }
    state.addForm?.let { form ->
        AddEquipmentDialog(form = form, component = component, s = s)
    }
}

@Composable
private fun EquipmentCard(equipment: Equipment, onClick: () -> Unit, s: AppStrings) {
    val scheme = MaterialTheme.colorScheme
    AnfasCard(
        modifier = Modifier.fillMaxWidth().clip(AnfasShapes.base).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp)
                .background(scheme.surfaceContainerHigh),
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                AnfasStatusChip(
                    label = equipment.status.label(s),
                    tone = equipment.status.tone(),
                )
            }
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = equipment.name,
                style = AnfasTheme.textStyles.headlineSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = equipment.assetTag,
                style = AnfasTheme.textStyles.dataMono,
                color = scheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = AnfasIcons.LocationOn,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.height(16.dp),
                )
                Text(
                    text = equipment.zone.label(s),
                    style = AnfasTheme.textStyles.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun EquipmentNotice.render(s: AppStrings): String = when (this) {
    EquipmentNotice.EquipmentSaved -> s.equipment.equipmentSaved
    EquipmentNotice.MaintenanceLogged -> s.equipment.maintenanceLogged
    EquipmentNotice.MarkedOutOfOrder -> s.equipment.markedOutOfOrder
    is EquipmentNotice.Failed -> message
}
