package com.moneymatters.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Outline icons on a 24-unit grid, 1.6 stroke, round caps (design/DESIGN.md → Icon button).
 * Drawn in black; tint them with `Icon(tint = …)`.
 */
object MoneyIcons {
    val Home by lazy { outline("Home", "M3.5 10.5 12 3.5l8.5 7V20a1 1 0 0 1-1 1H15v-6H9v6H4.5a1 1 0 0 1-1-1z") }
    val Transactions by lazy { outline("Transactions", "M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01", width = 1.8f) }
    val Accounts by lazy {
        outline("Accounts", "M5.5 5.5h13a2.5 2.5 0 0 1 2.5 2.5v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 16V8a2.5 2.5 0 0 1 2.5-2.5zM3 10h18M7 15h3")
    }
    val Invest by lazy { outline("Invest", "M3 17l5-5 4 4 8-8M15 8h5v5") }
    val More by lazy {
        outline(
            "More",
            "M6 4h2.5a2 2 0 0 1 2 2v2.5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" +
                "M15.5 4H18a2 2 0 0 1 2 2v2.5a2 2 0 0 1-2 2h-2.5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" +
                "M6 13.5h2.5a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z" +
                "M15.5 13.5H18a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2h-2.5a2 2 0 0 1-2-2v-2.5a2 2 0 0 1 2-2z",
        )
    }
    val Plus by lazy { outline("Plus", "M12 5v14M5 12h14", width = 2f) }
    val Bell by lazy { outline("Bell", "M6 16v-5a6 6 0 1 1 12 0v5l1.5 2.5h-15zM10 21.5h4") }
    val Sparkle by lazy {
        outline("Insights", "M12 3.5 13.8 9a2 2 0 0 0 1.2 1.2l5.5 1.8-5.5 1.8a2 2 0 0 0-1.2 1.2L12 20.5 10.2 15A2 2 0 0 0 9 13.8L3.5 12 9 10.2A2 2 0 0 0 10.2 9z")
    }
    val ArrowUpRight by lazy { outline("Open", "M7 17 17 7M8 7h9v9", width = 1.8f) }
    val ChevronDown by lazy { outline("Expand", "m6 9 6 6 6-6", width = 2.4f) }
    val Check by lazy { outline("Done", "M5 12.5l4.5 4.5L19 7.5", width = 1.8f) }
    val Pencil by lazy { outline("Draft", "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4") }
    val Question by lazy { outline("Uncategorized", "M9.5 9a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17h.01", width = 1.8f) }
    val Transfer by lazy { outline("In transit", "M5 8h13M15 5l3 3-3 3M19 16H6M9 13l-3 3 3 3") }
    val Refresh by lazy { outline("Recurring", "M20 12a8 8 0 1 1-2.34-5.66M20 4v4h-4") }
    val Checklist by lazy {
        outline("Review", "M7 4h10a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3zM8.5 12l2.5 2.5 4.5-5")
    }
    val Alert by lazy { outline("Alert", "M12 6v8M12 18h.01", width = 2f) }
    val Lock by lazy { outline("Locked", "M7 11V8a5 5 0 0 1 10 0v3M5.5 11h13v9h-13z") }
    val ArrowLeft by lazy { outline("Back", "M19 12H5M11 18l-6-6 6-6", width = 1.8f) }
    val Bank by lazy {
        outline(
            "Bank",
            "M4 10.5 12 5l8 5.5M5 10.5h14V20H5zM8 13.5V17M12 13.5V17M16 13.5V17M4 20h16",
        )
    }
    val Percent by lazy { outline("Loan", "M6 18 18 6M7.5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM16.5 18a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z", width = 1.8f) }

    private fun outline(name: String, path: String, width: Float = 1.6f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
