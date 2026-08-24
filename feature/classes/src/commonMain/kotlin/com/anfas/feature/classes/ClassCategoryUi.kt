package com.anfas.feature.classes

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.AppStrings
import com.anfas.core.model.ClassCategory

/**
 * The colour and label for each category — the design's own legend, mapped onto tokens that
 * already exist.
 *
 * `design.md`'s prose palette assigns sage to recovery/wellness and rose to group classes and
 * women's programming, so those two are not choices made here; the third falls to the app's
 * primary amber. Nothing invents a hex.
 */
@Composable
@ReadOnlyComposable
internal fun ClassCategory.accent(): Color = when (this) {
    ClassCategory.GENERAL -> MaterialTheme.colorScheme.primary
    ClassCategory.WOMENS_ONLY -> AnfasTheme.colors.rose
    ClassCategory.RECOVERY -> AnfasTheme.colors.sage
}

internal fun ClassCategory.label(s: AppStrings): String = when (this) {
    ClassCategory.GENERAL -> s.classes.categoryGeneral
    ClassCategory.WOMENS_ONLY -> s.classes.categoryWomensOnly
    ClassCategory.RECOVERY -> s.classes.categoryRecovery
}
