package com.moneymatters.app.core.market

import java.util.UUID

/** Whether [rule]'s condition holds on [m] today, or null when there is not enough history to say. */
fun conditionHolds(rule: AlertRuleEntity, m: StockMetrics): Boolean? {
    val t = rule.threshold
    return when (rule.kind) {
        AlertKind.PriceAbove -> t?.let { m.last > it }
        AlertKind.PriceBelow -> t?.let { m.last < it }
        AlertKind.MoveOneDay -> t?.let { th -> m.dayChangePct?.let { kotlin.math.abs(it) >= th } }
        AlertKind.SmaCrossUp -> m.sma50?.let { m.last > it }
        AlertKind.SmaCrossDown -> m.sma50?.let { m.last < it }
        AlertKind.NewHigh -> m.priorHigh52?.let { m.barCount > 60 && m.dayHigh > it }
        AlertKind.NewLow -> m.priorLow52?.let { m.barCount > 60 && m.dayLow < it }
        AlertKind.RsiOverbought -> m.rsi14?.let { it > 70 }
        AlertKind.RsiOversold -> m.rsi14?.let { it < 30 }
        AlertKind.VolumeSpike -> m.volumeRatio?.let { it >= 2.0 }
    }
}

private fun money(v: Double) = "₹" + "%,.2f".format(java.util.Locale.US, v)

fun alertMessage(rule: AlertRuleEntity, m: StockMetrics): String {
    val t = rule.threshold
    val what = when (rule.kind) {
        AlertKind.PriceAbove -> "rose above ${money(t ?: 0.0)}"
        AlertKind.PriceBelow -> "fell below ${money(t ?: 0.0)}"
        AlertKind.MoveOneDay -> "moved ${"%+.1f".format(java.util.Locale.US, m.dayChangePct ?: 0.0)}% in a day"
        AlertKind.SmaCrossUp -> "crossed above its 50-day average"
        AlertKind.SmaCrossDown -> "crossed below its 50-day average"
        AlertKind.NewHigh -> "reached a new 52-week high"
        AlertKind.NewLow -> "reached a new 52-week low"
        AlertKind.RsiOverbought -> "is overbought (RSI ${"%.0f".format(m.rsi14 ?: 0.0)})"
        AlertKind.RsiOversold -> "is oversold (RSI ${"%.0f".format(m.rsi14 ?: 0.0)})"
        AlertKind.VolumeSpike -> "is trading at ${"%.1f".format(java.util.Locale.US, m.volumeRatio ?: 0.0)}× its usual volume"
    }
    return "${m.symbol} $what (closed at ${money(m.last)})"
}

data class AlertRun(val rules: List<AlertRuleEntity>, val events: List<AlertEventEntity>)

/**
 * Checks every active rule against today's metrics. A rule fires only when its condition turns from false to true; its
 * remembered state is updated whenever the condition can be worked out, and left alone when it can't (no data today).
 */
fun evaluateAlerts(
    rules: List<AlertRuleEntity>,
    metrics: Map<String, StockMetrics>,
    now: Long,
    newId: () -> String = { UUID.randomUUID().toString() },
): AlertRun {
    val updated = mutableListOf<AlertRuleEntity>()
    val events = mutableListOf<AlertEventEntity>()
    for (rule in rules) {
        if (!rule.active) continue
        val m = metrics[rule.symbol] ?: continue
        val holds = conditionHolds(rule, m) ?: continue
        if (holds && !rule.lastState) {
            events += AlertEventEntity(newId(), rule.id, rule.symbol, alertMessage(rule, m), m.date.toString(), now, seen = false)
        }
        if (holds != rule.lastState) updated += rule.copy(lastState = holds)
    }
    return AlertRun(updated, events)
}
