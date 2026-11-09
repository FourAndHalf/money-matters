package com.moneymatters.app.core.market

enum class ScreenMetric(val key: String, val label: String, val unit: String, val read: (StockMetrics) -> Double?) {
    Price("price", "Price", "₹", { it.last }),
    DayChange("day", "1-day change", "%", { it.dayChangePct }),
    Ret1m("r1m", "1-month return", "%", { it.ret1m }),
    Ret3m("r3m", "3-month return", "%", { it.ret3m }),
    Ret6m("r6m", "6-month return", "%", { it.ret6m }),
    Ret1y("r1y", "1-year return", "%", { it.ret1y }),
    FromHigh("fromHigh", "Below 52-week high", "%", { it.fromHighPct }),
    AboveLow("aboveLow", "Above 52-week low", "%", { it.aboveLowPct }),
    Rsi("rsi", "RSI (14)", "", { it.rsi14 }),
    VsSma50("vsSma50", "Vs 50-day average", "%", { m -> m.sma50?.let { (m.last / it - 1) * 100 } }),
    VsSma200("vsSma200", "Vs 200-day average", "%", { m -> m.sma200?.let { (m.last / it - 1) * 100 } }),
    VolumeRatio("volRatio", "Volume vs 1-month average", "×", { it.volumeRatio }),
    Turnover("turnover", "Daily turnover", "₹ lakh", { it.turnoverLakh }),
    ;

    companion object {
        fun byKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

enum class ScreenOp(val symbol: String) { Above(">"), Below("<") }

data class ScreenCondition(val metric: ScreenMetric, val op: ScreenOp, val value: Double) {
    fun matches(m: StockMetrics): Boolean {
        val v = metric.read(m) ?: return false
        return if (op == ScreenOp.Above) v > value else v < value
    }

    fun encode() = "${metric.key}|${if (op == ScreenOp.Above) "gt" else "lt"}|$value"
}

/** Conditions as stored: one `key|gt|value` per line. Lines that no longer parse (a retired metric) are dropped. */
fun decodeConditions(text: String): List<ScreenCondition> = text.lines().mapNotNull { line ->
    val p = line.split('|')
    if (p.size != 3) return@mapNotNull null
    val metric = ScreenMetric.byKey(p[0]) ?: return@mapNotNull null
    val op = when (p[1]) { "gt" -> ScreenOp.Above; "lt" -> ScreenOp.Below; else -> return@mapNotNull null }
    val value = p[2].toDoubleOrNull() ?: return@mapNotNull null
    ScreenCondition(metric, op, value)
}

fun encodeConditions(conditions: List<ScreenCondition>) = conditions.joinToString("\n") { it.encode() }

/** Every condition has to hold (AND). Stocks missing a metric a condition needs do not match it. Results sort by [sortBy] high to low. */
fun runScreen(universe: List<StockMetrics>, conditions: List<ScreenCondition>, sortBy: ScreenMetric = ScreenMetric.Turnover): List<StockMetrics> =
    universe.filter { m -> conditions.all { it.matches(m) } }.sortedByDescending { sortBy.read(it) ?: Double.NEGATIVE_INFINITY }

data class PresetScreen(val name: String, val about: String, val conditions: List<ScreenCondition>)

val PresetScreens = listOf(
    PresetScreen("Near 52-week high", "Within 3% of the year's high, trading actively", listOf(
        ScreenCondition(ScreenMetric.FromHigh, ScreenOp.Above, -3.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0),
    )),
    PresetScreen("Oversold", "RSI under 30 on liquid stocks", listOf(
        ScreenCondition(ScreenMetric.Rsi, ScreenOp.Below, 30.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0),
    )),
    PresetScreen("Strong momentum", "Up over 20% in 3 months and above the 50-day average", listOf(
        ScreenCondition(ScreenMetric.Ret3m, ScreenOp.Above, 20.0), ScreenCondition(ScreenMetric.VsSma50, ScreenOp.Above, 0.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0),
    )),
    PresetScreen("Volume surge", "Trading at over twice the usual volume", listOf(
        ScreenCondition(ScreenMetric.VolumeRatio, ScreenOp.Above, 2.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0),
    )),
    PresetScreen("Beaten down", "Over 40% below the 52-week high", listOf(
        ScreenCondition(ScreenMetric.FromHigh, ScreenOp.Below, -40.0), ScreenCondition(ScreenMetric.Turnover, ScreenOp.Above, 500.0),
    )),
)
