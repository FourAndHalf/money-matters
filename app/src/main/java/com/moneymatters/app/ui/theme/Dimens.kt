package com.moneymatters.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii (design/DESIGN.md → Shapes). Nested elements use a smaller radius than their parent. */
@Immutable
data class MoneyShapes(
    /** Chips and badges. */
    val sm: RoundedCornerShape = RoundedCornerShape(8.dp),
    /** Inputs, list groups, inner panels, tooltips. */
    val md: RoundedCornerShape = RoundedCornerShape(14.dp),
    /** Standard cards and modules. */
    val lg: RoundedCornerShape = RoundedCornerShape(20.dp),
    /** Hero card, account cards, sheets. */
    val xl: RoundedCornerShape = RoundedCornerShape(28.dp),
    /** Buttons, pills, avatar, icon buttons. */
    val full: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

/** 4/8 spacing scale (design/DESIGN.md → Layout & Spacing), phone values. */
@Immutable
data class MoneySpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 40.dp,
    /** Gap between bento modules. */
    val gutter: Dp = 10.dp,
    /** Screen side margin. */
    val margin: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    /** Standard list row height. */
    val rowHeight: Dp = 56.dp,
    val touchTarget: Dp = 44.dp,
)
