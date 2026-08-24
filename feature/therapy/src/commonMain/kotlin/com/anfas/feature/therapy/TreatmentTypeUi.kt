package com.anfas.feature.therapy

import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.TreatmentType

internal fun TreatmentType.label(s: AppStrings): String = when (this) {
    TreatmentType.MANUAL_THERAPY -> s.therapy.treatmentManualTherapy
    TreatmentType.EXERCISE -> s.therapy.treatmentExercise
    TreatmentType.DRY_NEEDLING -> s.therapy.treatmentDryNeedling
    TreatmentType.ULTRASOUND -> s.therapy.treatmentUltrasound
}
