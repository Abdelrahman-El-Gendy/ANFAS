package com.anfas.feature.equipment

import androidx.compose.ui.graphics.vector.ImageVector
import com.anfas.core.designsystem.AnfasIcons
import com.anfas.core.designsystem.ChipTone
import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.EquipmentStatus
import com.anfas.core.model.EquipmentZone

internal fun EquipmentStatus.label(s: AppStrings): String = when (this) {
    EquipmentStatus.OPERATIONAL -> s.equipment.statusOperational
    EquipmentStatus.NEEDS_SERVICE -> s.equipment.statusNeedsService
    EquipmentStatus.OUT_OF_ORDER -> s.equipment.statusOutOfOrder
    EquipmentStatus.ON_ORDER -> s.equipment.statusOnOrder
}

internal fun EquipmentStatus.tone(): ChipTone = when (this) {
    EquipmentStatus.OPERATIONAL -> ChipTone.Positive
    EquipmentStatus.NEEDS_SERVICE -> ChipTone.Warning
    EquipmentStatus.OUT_OF_ORDER -> ChipTone.Critical
    EquipmentStatus.ON_ORDER -> ChipTone.Neutral
}

internal fun EquipmentStatus.icon(): ImageVector = when (this) {
    EquipmentStatus.OPERATIONAL -> AnfasIcons.CheckCircle
    EquipmentStatus.NEEDS_SERVICE -> AnfasIcons.Build
    EquipmentStatus.OUT_OF_ORDER -> AnfasIcons.Cancel
    EquipmentStatus.ON_ORDER -> AnfasIcons.LocalShipping
}

internal fun EquipmentZone.label(s: AppStrings): String = when (this) {
    EquipmentZone.CARDIO_FLOOR -> s.equipment.zoneCardioFloor
    EquipmentZone.WEIGHT_ROOM -> s.equipment.zoneWeightRoom
    EquipmentZone.RECOVERY -> s.equipment.zoneRecovery
}
