package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Units

enum class TradeSide { Buy, Sell }

/** "12.5" for 12,500,000 micro-units: trailing zeros trimmed, whole numbers have no point. */
fun formatUnits(units: Units): String {
    val whole = units.micro / 1_000_000
    val fraction = (units.micro % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
