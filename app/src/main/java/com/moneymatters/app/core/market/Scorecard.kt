package com.moneymatters.app.core.market

enum class Verdict { Good, Fair, Weak, Unknown }

/** One card of a stock's report: a 0–10 score against the whole market, or a plain finding when there is no score. */
data class ScoreCard(val title: String, val score: Double?, val verdict: Verdict, val lines: List<String>)

data class RedFlag(val title: String, val detail: String)

data class Scorecard(val symbol: String, val cards: List<ScoreCard>, val redFlags: List<RedFlag>)

/** Where [value] ranks among [all], 0 (lowest) to 1 (highest). */
internal fun percentile(all: List<Double>, value: Double): Double? {
    if (all.size < 20) return null
    val below = all.count { it < value } + 0.5 * all.count { it == value }
    return below / all.size
}

private fun verdict(score: Double?) = when {
    score == null -> Verdict.Unknown
    score >= 6.5 -> Verdict.Good
    score >= 4.0 -> Verdict.Fair
    else -> Verdict.Weak
}

private fun fmt(v: Double?, suffix: String = "%") = v?.let { "%+.1f".format(java.util.Locale.US, it) + suffix } ?: "—"

/**
 * A report card built only from price and volume. Scores rank the stock against every stock in [universe] (there is no
 * sector data offline, so the peer group is the whole market), then scale to 0–10. Nothing here judges the business.
 */
fun scorecardFor(m: StockMetrics, universe: List<StockMetrics>): Scorecard {
    fun rank(read: (StockMetrics) -> Double?): Double? = read(m)?.let { v -> percentile(universe.mapNotNull(read), v) }
    val performance = listOfNotNull(rank { it.ret1y }, rank { it.ret6m }, rank { it.ret3m }).takeIf { it.isNotEmpty() }?.average()?.times(10)
    val trendParts = listOfNotNull(
        m.sma50?.let { if (m.last > it) 1.0 else 0.0 },
        m.sma200?.let { if (m.last > it) 1.0 else 0.0 },
        rank { x -> x.rsi14 }?.let { 1 - kotlin.math.abs(it - 0.6) },
    )
    val trend = trendParts.takeIf { it.isNotEmpty() }?.average()?.times(10)
    val liquidity = rank { it.turnoverLakh }?.times(10)
    val entry = when {
        m.rsi14 == null && m.historyDays < 60 -> null
        else -> {
            val nearHigh = 1 - (kotlin.math.abs(m.fromHighPct).coerceAtMost(40.0) / 40.0)
            val rsiOk = m.rsi14?.let { if (it > 70) 0.0 else if (it < 30) 1.0 else 0.6 } ?: 0.5
            (0.4 * nearHigh + 0.6 * rsiOk) * 10
        }
    }
    val cards = listOf(
        ScoreCard("Performance", performance, verdict(performance), listOf("1 year ${fmt(m.ret1y)}", "6 months ${fmt(m.ret6m)}", "3 months ${fmt(m.ret3m)}")),
        ScoreCard("Trend", trend, verdict(trend), listOf(
            m.sma50?.let { "Price is ${if (m.last > it) "above" else "below"} the 50-day average" } ?: "50-day average needs 50 days of prices",
            m.sma200?.let { "Price is ${if (m.last > it) "above" else "below"} the 200-day average" } ?: "200-day average needs 200 days of prices",
            m.rsi14?.let { "RSI ${"%.0f".format(it)}" } ?: "RSI needs 15 days of prices",
        )),
        ScoreCard("Entry point", entry, verdict(entry), listOf(
            "${fmt(m.fromHighPct)} from the 52-week high", m.rsi14?.let { if (it > 70) "Overbought (RSI above 70)" else if (it < 30) "Oversold (RSI below 30)" else "RSI in a normal range" } ?: "RSI not available yet",
        )),
        ScoreCard("Liquidity", liquidity, verdict(liquidity), listOf(m.turnoverLakh?.let { "About ₹${"%,.0f".format(java.util.Locale.US, it)} lakh traded a day" } ?: "Needs 20 days of volume")),
    )
    return Scorecard(m.symbol, cards, redFlagsFor(m))
}

/** Red flags a price-and-volume file can show. Surveillance lists and pledging need other feeds. */
fun redFlagsFor(m: StockMetrics): List<RedFlag> = buildList {
    if (m.series != "EQ") add(RedFlag("Not in the normal series", "NSE trades this in series ${m.series}, which is used for stocks under settlement, surveillance or trade-for-trade limits."))
    if ((m.dayChangePct ?: 0.0) <= -10.0) add(RedFlag("Sharp one-day fall", "Down ${fmt(m.dayChangePct)} in a day."))
    m.sma200?.let { if (m.last < it * 0.8) add(RedFlag("Far below its 200-day average", "More than 20% under the long-run trend.")) }
    if (m.fromHighPct <= -50.0) add(RedFlag("Halved from its high", "${fmt(m.fromHighPct)} from the 52-week high."))
    m.turnoverLakh?.let { if (it < 10.0) add(RedFlag("Thinly traded", "Under ₹10 lakh a day, so it may be hard to sell at a fair price.")) }
}
