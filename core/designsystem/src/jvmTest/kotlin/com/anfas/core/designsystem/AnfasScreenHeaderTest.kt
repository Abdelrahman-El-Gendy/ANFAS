package com.anfas.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Geometry, not appearance.
 *
 * This exists because a green `./gradlew check` said nothing about the header running its title
 * straight into the action button on an iPhone. A test cannot tell you a screen looks wrong, but
 * it can tell you that two things which must not touch are touching.
 *
 * **The assertion is a minimum *readable* gap, not merely "no overlap", and that distinction is
 * the whole test.** Measured against the unfixed component, the real iPhone case left a 1dp gap —
 * technically not overlapping, visually flush — so a first draft asserting `gap > 0` passed
 * against the bug. Narrower widths were a true 0dp. With the gutter every width here yields at
 * least 16dp.
 */
@OptIn(ExperimentalTestApi::class)
class AnfasScreenHeaderTest {

    private val longTitle = "Today's schedule"
    private val actionLabel = "Add class"

    @Test
    fun `the title keeps a readable gap from the action at every phone width`() {
        PHONE_CONTENT_WIDTHS.forEach { width ->
            runComposeUiTest {
                setHeader(width)

                val title = onNodeWithText(longTitle).getBoundsInRoot()
                val action = onNodeWithText(actionLabel).getBoundsInRoot()
                val gap = action.left - title.right

                assertTrue(
                    gap.value >= MIN_VISUAL_GAP.value - TOLERANCE,
                    "at $width content width the title ends $gap from the action, under the " +
                        "$MIN_VISUAL_GAP minimum. Unfixed this was 0dp at the narrow widths and " +
                        "1dp at 370dp (the iPhone 17 case), which reads as clipped text.",
                )
            }
        }
    }

    /**
     * The gutter must come out of the title's share rather than push the action off the edge —
     * which is why it is padding on the actions, and so part of their measured width, instead of
     * an `Arrangement` gap.
     */
    @Test
    fun `the action stays inside the header at every phone width`() {
        PHONE_CONTENT_WIDTHS.forEach { width ->
            runComposeUiTest {
                setHeader(width)

                val action = onNodeWithText(actionLabel).getBoundsInRoot()
                assertTrue(
                    action.right.value <= width.value + TOLERANCE,
                    "at $width the action's right edge ${action.right} spills past the header",
                )
                assertTrue(
                    action.left.value >= -TOLERANCE,
                    "at $width the action's left edge ${action.left} is off-screen",
                )
            }
        }
    }

    /**
     * With no actions there is nothing to clear, so the gutter must not be reserved against
     * nothing: the title keeps the full width and stays on one line at a size where it wraps as
     * soon as a button competes with it.
     */
    @Test
    fun `a header with no actions keeps the title on one line`() = runComposeUiTest {
        setContent {
            AnfasTheme {
                Box(Modifier.width(IPHONE_CONTENT_WIDTH)) {
                    AnfasScreenHeader(title = longTitle, subtitle = SUBTITLE)
                }
            }
        }

        val title = onNodeWithText(longTitle).getBoundsInRoot()
        val titleHeight = title.bottom - title.top
        assertTrue(
            titleHeight.value < TWO_LINE_HEIGHT_FLOOR.value,
            "the title wrapped to $titleHeight with no action competing for width",
        )
    }

    private fun ComposeUiTest.setHeader(width: Dp) {
        setContent {
            AnfasTheme {
                Box(Modifier.width(width)) {
                    AnfasScreenHeader(
                        title = longTitle,
                        subtitle = SUBTITLE,
                        actions = {
                            AnfasPrimaryButton(
                                text = actionLabel,
                                icon = AnfasIcons.Add,
                                onClick = {},
                            )
                        },
                    )
                }
            }
        }
    }

    private companion object {
        const val SUBTITLE = "The weekly timetable."

        /**
         * The width the header is actually handed: device width minus the 16dp page margin every
         * screen applies — **not** the device width. Getting that wrong is how the first draft of
         * this test passed against the unfixed component: at a full 402dp the title fitted with
         * 33dp to spare, and the collision only appears at the real 370dp.
         */
        val IPHONE_CONTENT_WIDTH = 370.dp

        /**
         * 370dp is the iPhone 17 (402 - 32) and 395dp the Pixel 9 Pro (~427 - 32). The narrower
         * two stand in for small phones and split-screen, where the collision was a true 0dp.
         */
        val PHONE_CONTENT_WIDTHS = listOf(300.dp, 340.dp, IPHONE_CONTENT_WIDTH, 395.dp)

        /** Below this, a gap reads as text running into a button rather than as spacing. */
        val MIN_VISUAL_GAP = 12.dp

        /** One line of `headlineLarge` measures 42dp here and two measure 81dp, so anything under
         * this is single-line. */
        val TWO_LINE_HEIGHT_FLOOR = 60.dp

        /** Sub-pixel rounding in layout, not a real overflow allowance. */
        const val TOLERANCE = 0.5f
    }
}
