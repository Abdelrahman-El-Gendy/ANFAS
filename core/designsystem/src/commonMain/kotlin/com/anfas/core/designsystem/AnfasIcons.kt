package com.anfas.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The design specifies Material Symbols Outlined, which we cannot depend on: JetBrains
 * stopped publishing `org.jetbrains.compose.material:material-icons-*` at **1.7.3** while this
 * project is on Compose 1.11.1, and pulling a 1.7.x Compose artifact into an 1.11 runtime is
 * not worth it. `material-icons-extended` is also enormous and deprecated upstream.
 *
 * So these are built from geometry, on Material's 24dp / 24-unit viewport with a 2-unit
 * outlined stroke, which is what Material Symbols Outlined is. Colour is irrelevant here —
 * `Icon()` tints the whole vector — so everything is declared black.
 *
 * This set covers the members feature only. Glyphs for navigation and the other features
 * (`document_scanner`, `card_membership`, `fitness_center`, …) are genuinely complex shapes;
 * decide on a real icon source before building those rather than approximating them here.
 */
object AnfasIcons {

    val Search: ImageVector by lazy {
        stroked("Search") {
            // lens
            moveTo(17f, 10.5f)
            arcTo(6.5f, 6.5f, 0f, true, true, 4f, 10.5f)
            arcTo(6.5f, 6.5f, 0f, true, true, 17f, 10.5f)
            // handle
            moveTo(15.6f, 15.6f)
            lineTo(20f, 20f)
        }
    }

    val Add: ImageVector by lazy {
        stroked("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Close: ImageVector by lazy {
        stroked("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val ArrowBack: ImageVector by lazy {
        stroked("ArrowBack") {
            moveTo(19.5f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        stroked("ChevronLeft") {
            moveTo(15f, 5.5f)
            lineTo(8.5f, 12f)
            lineTo(15f, 18.5f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        stroked("ChevronRight") {
            moveTo(9f, 5.5f)
            lineTo(15.5f, 12f)
            lineTo(9f, 18.5f)
        }
    }

    /** Row overflow affordance. Dots are filled, not stroked — a stroked dot reads as a ring. */
    val MoreVert: ImageVector by lazy {
        ImageVector.Builder(
            name = "MoreVert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            listOf(5.6f, 12f, 18.4f).forEach { cy ->
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, cy - 1.7f)
                    arcTo(1.7f, 1.7f, 0f, true, true, 12f, cy + 1.7f)
                    arcTo(1.7f, 1.7f, 0f, true, true, 12f, cy - 1.7f)
                    close()
                }
            }
        }.build()
    }

    val Person: ImageVector by lazy {
        stroked("Person") {
            moveTo(15.5f, 8f)
            arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 8f)
            arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 8f)
            moveTo(4.5f, 20f)
            arcTo(7.5f, 7.5f, 0f, false, true, 19.5f, 20f)
        }
    }

    /** `person_add` — the directory's primary action and the empty state's badge. */
    val PersonAdd: ImageVector by lazy {
        stroked("PersonAdd") {
            moveTo(12.6f, 8f)
            arcTo(3.2f, 3.2f, 0f, true, true, 6.2f, 8f)
            arcTo(3.2f, 3.2f, 0f, true, true, 12.6f, 8f)
            moveTo(2.6f, 20f)
            arcTo(6.8f, 6.8f, 0f, false, true, 16.2f, 20f)
            moveTo(19f, 6f)
            lineTo(19f, 12f)
            moveTo(16f, 9f)
            lineTo(22f, 9f)
        }
    }

    /** `person_search` — the no-results badge. */
    val PersonSearch: ImageVector by lazy {
        stroked("PersonSearch") {
            moveTo(12.2f, 7.5f)
            arcTo(3.1f, 3.1f, 0f, true, true, 6f, 7.5f)
            arcTo(3.1f, 3.1f, 0f, true, true, 12.2f, 7.5f)
            moveTo(2.5f, 19.5f)
            arcTo(6.6f, 6.6f, 0f, false, true, 13.5f, 15f)
            moveTo(21f, 15f)
            arcTo(3f, 3f, 0f, true, true, 15f, 15f)
            arcTo(3f, 3f, 0f, true, true, 21f, 15f)
            moveTo(20.2f, 17.2f)
            lineTo(22.5f, 19.5f)
        }
    }

    val Notifications: ImageVector by lazy {
        stroked("Notifications") {
            moveTo(6.5f, 17f)
            lineTo(6.5f, 11f)
            arcTo(5.5f, 5.5f, 0f, false, true, 17.5f, 11f)
            lineTo(17.5f, 17f)
            close()
            moveTo(10.2f, 19.8f)
            arcTo(2.2f, 2.2f, 0f, false, false, 13.8f, 19.8f)
        }
    }

    // --- added for :feature:subscriptions -------------------------------------------------

    /** `send` — send test message, InstaPay. */
    val Send: ImageVector by lazy {
        stroked("Send") {
            moveTo(3.5f, 20.5f)
            lineTo(21f, 12f)
            lineTo(3.5f, 3.5f)
            lineTo(6.5f, 12f)
            close()
        }
    }

    /** `schedule` — "Daily job last ran 06:00 today". */
    val Schedule: ImageVector by lazy {
        stroked("Schedule") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7f)
            lineTo(12f, 12f)
            lineTo(15.5f, 14f)
        }
    }

    val ErrorOutline: ImageVector by lazy {
        stroked("ErrorOutline") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7.5f)
            lineTo(12f, 13f)
            moveTo(12f, 16.2f)
            lineTo(12f, 16.4f)
        }
    }

    /** Bare tick, for selection controls where a circled check would read as a badge. */
    val Check: ImageVector by lazy {
        stroked("Check") {
            moveTo(5f, 12.5f)
            lineTo(9.5f, 17f)
            lineTo(19f, 7f)
        }
    }

    /** `check_circle` — the "No failed reminders" health badge. */
    val CheckCircle: ImageVector by lazy {
        stroked("CheckCircle") {
            circle(12f, 12f, 8.5f)
            moveTo(7.8f, 12.3f)
            lineTo(10.8f, 15.3f)
            lineTo(16.2f, 9.4f)
        }
    }

    val Info: ImageVector by lazy {
        stroked("Info") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 11f)
            lineTo(12f, 16.5f)
            moveTo(12f, 7.7f)
            lineTo(12f, 7.9f)
        }
    }

    val ContentCopy: ImageVector by lazy {
        stroked("ContentCopy") {
            moveTo(9f, 9f)
            lineTo(20f, 9f)
            lineTo(20f, 20f)
            lineTo(9f, 20f)
            close()
            moveTo(15.5f, 5.5f)
            lineTo(4.5f, 5.5f)
            lineTo(4.5f, 16.5f)
        }
    }

    val ExpandMore: ImageVector by lazy {
        stroked("ExpandMore") {
            moveTo(5.5f, 9f)
            lineTo(12f, 15.5f)
            lineTo(18.5f, 9f)
        }
    }

    val FilterList: ImageVector by lazy {
        stroked("FilterList") {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(7f, 12f)
            lineTo(17f, 12f)
            moveTo(10f, 17f)
            lineTo(14f, 17f)
        }
    }

    /** `play_arrow` — "Run Queue". Filled, as Material draws it. */
    val PlayArrow: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f)
                lineTo(19f, 12f)
                lineTo(8f, 19f)
                close()
            }
        }.build()
    }

    /** `payments` — cash. Two stacked cards. */
    val Payments: ImageVector by lazy {
        stroked("Payments") {
            moveTo(6f, 9.5f)
            lineTo(21f, 9.5f)
            lineTo(21f, 18f)
            lineTo(6f, 18f)
            close()
            moveTo(3f, 6.5f)
            lineTo(18f, 6.5f)
        }
    }

    val CreditCard: ImageVector by lazy {
        stroked("CreditCard") {
            moveTo(3f, 6f)
            lineTo(21f, 6f)
            lineTo(21f, 18f)
            lineTo(3f, 18f)
            close()
            moveTo(3f, 10.5f)
            lineTo(21f, 10.5f)
        }
    }

    /** `smartphone` — Vodafone Cash. */
    val Smartphone: ImageVector by lazy {
        stroked("Smartphone") {
            moveTo(7.5f, 3.5f)
            lineTo(16.5f, 3.5f)
            lineTo(16.5f, 20.5f)
            lineTo(7.5f, 20.5f)
            close()
            moveTo(10.5f, 17.8f)
            lineTo(13.5f, 17.8f)
        }
    }

    /** `chat` — Open WhatsApp. */
    val Chat: ImageVector by lazy {
        stroked("Chat") {
            moveTo(4.5f, 5f)
            lineTo(19.5f, 5f)
            lineTo(19.5f, 16f)
            lineTo(9.5f, 16f)
            lineTo(5.5f, 20f)
            lineTo(5.5f, 16f)
            lineTo(4.5f, 16f)
            close()
        }
    }

    // --- added for :feature:intake-ocr ----------------------------------------------------

    val ZoomIn: ImageVector by lazy {
        stroked("ZoomIn") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.6f, 15.6f)
            lineTo(20f, 20f)
            moveTo(10.5f, 7.5f)
            lineTo(10.5f, 13.5f)
            moveTo(7.5f, 10.5f)
            lineTo(13.5f, 10.5f)
        }
    }

    val ZoomOut: ImageVector by lazy {
        stroked("ZoomOut") {
            circle(10.5f, 10.5f, 6.5f)
            moveTo(15.6f, 15.6f)
            lineTo(20f, 20f)
            moveTo(7.5f, 10.5f)
            lineTo(13.5f, 10.5f)
        }
    }

    val Warning: ImageVector by lazy {
        stroked("Warning") {
            moveTo(12f, 3.5f)
            lineTo(21.5f, 20f)
            lineTo(2.5f, 20f)
            close()
            moveTo(12f, 9.5f)
            lineTo(12f, 14f)
            moveTo(12f, 16.8f)
            lineTo(12f, 17f)
        }
    }

    val Upload: ImageVector by lazy {
        stroked("Upload") {
            moveTo(12f, 16f)
            lineTo(12f, 4f)
            moveTo(6.5f, 9.5f)
            lineTo(12f, 4f)
            lineTo(17.5f, 9.5f)
            moveTo(4f, 19.5f)
            lineTo(20f, 19.5f)
        }
    }

    /** `document_scanner` — corner brackets around a page, which is exactly what Material draws. */
    val DocumentScanner: ImageVector by lazy {
        stroked("DocumentScanner") {
            moveTo(4f, 8f)
            lineTo(4f, 5f)
            lineTo(7f, 5f)
            moveTo(17f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 8f)
            moveTo(20f, 16f)
            lineTo(20f, 19f)
            lineTo(17f, 19f)
            moveTo(7f, 19f)
            lineTo(4f, 19f)
            lineTo(4f, 16f)
            moveTo(8f, 10.5f)
            lineTo(16f, 10.5f)
            moveTo(8f, 14f)
            lineTo(14f, 14f)
        }
    }

    /**
     * A full circle as two half arcs. `arcTo` cannot sweep 360 degrees in one call — the start
     * and end points would coincide and the arc collapses to nothing.
     */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
    }

    private fun stroked(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()
}
