package com.anfas.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Geometry, not appearance — the same reason [AnfasScreenHeaderTest] exists.
 *
 * Every dialog in this app is a form, and its footer holds the only way out: Save and Cancel. The
 * body was never actually scrollable despite the component's KDoc saying it was, so a form taller
 * than the window pushed its own footer past the bottom edge and the dialog became impossible to
 * either submit or dismiss. On a phone with the keyboard open that is not a tall form — it is a
 * normal one.
 *
 * Asserted as "the footer stays inside the dialog", not as a pixel position, because the failure
 * is categorical: either the actions are reachable or they are not.
 *
 * Two tests, doing different jobs. The first fails without the fix -- verified by reverting it --
 * and is the bug detector. The second passes either way and is a guard against over-correcting:
 * `weight(1f)` without `fill = false` would fix the footer and stretch every small confirm dialog
 * to full height instead. A third test asserting the body's content merely *exists* was written
 * and then deleted: it passed in both states, so it discriminated nothing.
 */
@OptIn(ExperimentalTestApi::class)
class AnfasDialogTest {

    private val footerLabel = "Save"
    private val firstFieldLabel = "First field"

    @Test
    fun `the footer stays reachable when the body is taller than the dialog`() {
        runComposeUiTest {
            setDialog(bodyHeight = 2_000.dp, windowHeight = 640.dp)

            val dialog = onNodeWithText(DIALOG_TITLE).getBoundsInRoot()
            val footer = onNodeWithText(footerLabel).getBoundsInRoot()

            assertTrue(
                footer.bottom <= 640.dp,
                "the footer's bottom is at ${footer.bottom}, past the 640dp window — a body that " +
                    "does not scroll pushes Save and Cancel off the screen and the dialog cannot " +
                    "be submitted or dismissed",
            )
            assertTrue(
                footer.top > dialog.top,
                "the footer (${footer.top}) is not below the title (${dialog.top})",
            )
        }
    }

    /**
     * The other half of `weight(1f, fill = false)`: a short dialog must still wrap its content
     * rather than stretch to the full window height, or every small confirm dialog becomes a
     * full-height sheet.
     */
    @Test
    fun `a short dialog wraps its content instead of filling the window`() {
        runComposeUiTest {
            setDialog(bodyHeight = 80.dp, windowHeight = 800.dp)

            val footer = onNodeWithText(footerLabel).getBoundsInRoot()

            assertTrue(
                footer.bottom < 400.dp,
                "a dialog with an 80dp body put its footer at ${footer.bottom}, so the body " +
                    "stretched to fill the window instead of wrapping",
            )
        }
    }

    private fun ComposeUiTest.setDialog(
        bodyHeight: androidx.compose.ui.unit.Dp,
        windowHeight: androidx.compose.ui.unit.Dp,
    ) {
        setContent {
            AnfasTheme {
                // requiredSize, not size: the incoming constraints of the test surface would
                // otherwise clamp the height under test and the overflow case would silently
                // exercise the short one. Same trap as IntakeReviewLayoutTest's desktop case.
                Box(modifier = Modifier.requiredSize(width = 420.dp, height = windowHeight)) {
                    AnfasDialogBodyUnderTest(bodyHeight = bodyHeight, footerLabel = footerLabel)
                }
            }
        }
    }

    private companion object {
        const val DIALOG_TITLE = "Add equipment"
    }

    @androidx.compose.runtime.Composable
    private fun AnfasDialogBodyUnderTest(
        bodyHeight: androidx.compose.ui.unit.Dp,
        footerLabel: String,
    ) {
        AnfasDialogPanel(
            title = DIALOG_TITLE,
            closeContentDescription = "Close",
            onDismissRequest = {},
            footer = { Text(footerLabel) },
        ) {
            Text(firstFieldLabel)
            Box(modifier = Modifier.height(bodyHeight))
        }
    }
}
