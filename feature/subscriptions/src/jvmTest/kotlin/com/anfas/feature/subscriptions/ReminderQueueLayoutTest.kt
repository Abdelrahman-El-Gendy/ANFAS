package com.anfas.feature.subscriptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.anfas.core.designsystem.AnfasTableMinWidth
import com.anfas.core.designsystem.AnfasTheme
import com.anfas.core.i18n.EnglishStrings
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Six columns do not fit on a phone.
 *
 * Below `AnfasTableMinWidth` the queue rendered its desktop table anyway, squeezing headers down to
 * "MEM…"/"TEM…" and wrapping the status chip onto two lines — three in Arabic, where the labels
 * are longer. Nobody had seen it: the queue was always empty until the scheduler landed. Exactly
 * the kind of thing a geometry test catches and a green `check` does not.
 *
 * Asserted on the *header labels*, because they are what only the table renders: a card labels its
 * fields but never prints a MEMBER column heading. So their presence is a proxy for "the table
 * branch was chosen", and it is checked in both directions.
 */
@OptIn(ExperimentalTestApi::class)
class ReminderQueueLayoutTest {

    private val s = EnglishStrings
    private val memberName = "Omar Khaled"

    @Test
    fun `a phone gets cards rather than the squeezed table`() {
        runComposeUiTest {
            setQueue(widthDp = PHONE_CONTENT_WIDTH)

            // The reminder is still shown -- so this is not passing because nothing rendered.
            onNodeWithText(memberName).assertIsDisplayed()

            assertTrue(
                onAllNodesWithText(s.reminders.columnMember).fetchSemanticsNodes().isEmpty(),
                "the table's column headings rendered at ${PHONE_CONTENT_WIDTH}dp, so the " +
                    "six-column layout is being squeezed onto a phone again",
            )
        }
    }

    @Test
    fun `a wide window still gets the table`() {
        runComposeUiTest {
            setQueue(widthDp = DESKTOP_CONTENT_WIDTH)

            onNodeWithText(memberName).assertIsDisplayed()
            onNodeWithText(s.reminders.columnMember).assertIsDisplayed()
        }
    }

    /**
     * A window *exactly* at the table's minimum still gets cards — and that is correct.
     *
     * The threshold is compared against the width the table is actually handed, which is the window
     * minus the screen's own horizontal padding, so a 640dp window offers the table something under
     * 640dp. Written as a passing assertion rather than left as a surprise because this is the trap
     * `AnfasScreenHeaderTest` and `IntakeReviewLayoutTest` both document: measure the width the
     * component receives, not the width of the device.
     *
     * It also earns its place as a regression guard. Remove the screen's page padding, or lower the
     * threshold, and this flips.
     */
    @Test
    fun `a window at the table's minimum still gets cards, because of the page padding`() {
        runComposeUiTest {
            setQueue(widthDp = AnfasTableMinWidth.value.toInt())

            onNodeWithText(memberName).assertIsDisplayed()
            assertTrue(
                onAllNodesWithText(s.reminders.columnMember).fetchSemanticsNodes().isEmpty(),
                "the table rendered at exactly ${AnfasTableMinWidth.value.toInt()}dp of window, " +
                    "so it received less than its minimum and is being squeezed",
            )
        }
    }

    private fun ComposeUiTest.setQueue(widthDp: Int) {
        val component = ReminderQueueComponent(
            componentContext = DefaultComponentContext(
                lifecycle = LifecycleRegistry().also { it.resume() },
            ),
            repository = FakeReminderRepository(listOf(reminder("1", name = memberName)), null),
            scheduler = FakeScheduler(),
            sender = FakeSender(),
            auth = FakeAuth(mayRetry = true),
            dispatchers = TestDispatchers(Dispatchers.Unconfined),
            onOpenMemberClicked = {},
            onCloseClicked = {},
        )
        setContent {
            AnfasTheme {
                // requiredSize, NOT size: `size` is still clamped by the test surface's incoming
                // constraints, so a width wider than the default window is silently squeezed back
                // under the threshold -- which would make the desktop case quietly exercise the
                // narrow branch and pass for the wrong reason. The trap IntakeReviewLayoutTest
                // documents.
                Box(Modifier.requiredSize(width = widthDp.dp, height = TALL_ENOUGH.dp)) {
                    ReminderQueueScreen(component)
                }
            }
        }
    }

    private companion object {
        /** An iPhone 17 at 402dp, less the 16dp page margins every screen applies. */
        const val PHONE_CONTENT_WIDTH = 370
        const val DESKTOP_CONTENT_WIDTH = 1_100
        const val TALL_ENOUGH = 1_200
    }
}
