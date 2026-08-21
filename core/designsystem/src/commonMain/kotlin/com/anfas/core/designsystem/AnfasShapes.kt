package com.anfas.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The export defines exactly four radii — 4dp, 8dp, 12dp and a pill — so that is all there is.
 *
 * 12dp is the base: cards, inputs, buttons and tags all use it. 4dp is deliberate on
 * checkboxes and radios, which stay square-ish rather than circular to match IBM Plex's
 * technical feel. `large`/`extraLarge` intentionally also resolve to 12dp; the design has no
 * larger radius, and inventing one would let components drift off-spec.
 */
internal val anfasShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

/** Named for the roles the design actually talks about, so call sites read like the spec. */
object AnfasShapes {
    /** Cards, inputs, buttons, tags. */
    val base = RoundedCornerShape(12.dp)
    /** Checkboxes and radios. */
    val selection = RoundedCornerShape(4.dp)
    /** Status chips only — pill shape distinguishes them from actionable buttons. */
    val chip = RoundedCornerShape(percent = 50)
}
