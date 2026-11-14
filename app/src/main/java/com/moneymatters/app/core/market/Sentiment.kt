package com.moneymatters.app.core.market

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

enum class MoodZone(val label: String, val meaning: String) {
    ExtremeFear("Extreme fear", "Panic. Often a better time to add to quality stocks gradually."),
    Fear("Fear", "Caution in the market."),
    Greed("Greed", "Optimism building."),
    ExtremeGreed("Extreme greed", "Overbought. Be careful before fresh buying."),
}

fun moodZone(score: Double) = when {
    score < 30 -> MoodZone.ExtremeFear
    score < 50 -> MoodZone.Fear
    score <= 70 -> MoodZone.Greed
    else -> MoodZone.ExtremeGreed
}

data class MoodFactor(val name: String, val reads: String, val today: Double?, val score: Double?, val note: String?)

data class MoodReading(val date: LocalDate, val score: Double?, val zone: MoodZone?, val factors: List<MoodFactor>, val daysOfHistory: Int)

/** Standard normal CDF (Abramowitz–Stegun 7.1.26), fast and good to about 1e-7. */
internal fun normalCdf(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1 / (1 + 0.3275911 * x)
    val poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741 + t * (-1.453152027 + t * 1.061405429))))
    val erf = 1 - poly * exp(-x * x)
    return 0.5 * (1 + if (z >= 0) erf else -erf)
}

/** Where [today] sits among [window] in standard deviations, mapped to 0–100. Null with too little history or no spread. */
internal fun zScore100(window: List<Double>, today: Double, minimum: Int = 20): Double? {
    if (window.size < minimum) return null
    val mean = window.average()
    val sd = sqrt(window.sumOf { (it - mean) * (it - mean) } / window.size)
    if (sd < 1e-9) return 50.0
    return normalCdf((today - mean) / sd) * 100
}

private const val WINDOW = 45
private const val NEAR = 0.05

private class Day(val date: LocalDate, var adv: Double = 0.0, var dec: Double = 0.0, var nearHigh: Int = 0, var nearLow: Int = 0, var above: Int = 0, var rated: Int = 0, var rated50: Int = 0)

/**
 * A market mood gauge from what NSE's daily files can tell: how widely buying is spread (breadth, weighted by volume),
 * how many stocks sit near their highs versus lows (price strength) and how many are above their 50-day average
 * (momentum). Each is compared with its own last 45 trading days as a z-score, rescaled to 0–100 and averaged equally.
 * The three factors that need other feeds (FII positions, India VIX and gold) are listed as missing, not guessed.
 *
 * Built one stock at a time so a year of the whole market never has to sit in memory at once: [add] each stock's
 * bars in date order, then [build].
 */
class MoodBuilder(private val storedDates: List<String>, window: Int = WINDOW) {
    private val dates = storedDates.sorted()
    private val needed = dates.takeLast(window)
    private val days = needed.associateWith { Day(LocalDate.parse(it)) }

    fun add(rows: List<MarketBarEntity>) {
        if (rows.isEmpty() || rows.first().series != "EQ") return
        for (i in rows.indices) {
            val row = rows[i]
            val day = days[row.date] ?: continue
            if (row.prevClose > 0) {
                val v = row.volume.toDouble()
                if (row.close > row.prevClose) day.adv += v else if (row.close < row.prevClose) day.dec += v
            }
            if (i >= 29) {
                val from = maxOf(0, i - 251)
                var hi = Long.MIN_VALUE
                var lo = Long.MAX_VALUE
                for (j in from until i) { hi = maxOf(hi, rows[j].high); lo = minOf(lo, rows[j].low) }
                day.rated++
                if (row.close >= hi * (1 - NEAR)) day.nearHigh++
                if (row.close <= lo * (1 + NEAR)) day.nearLow++
            }
            if (i >= 49) {
                day.rated50++
                var sum = 0L
                for (j in i - 49..i) sum += rows[j].close
                if (row.close * 50 > sum) day.above++
            }
        }
    }

    fun build(): MoodReading? {
        if (needed.isEmpty()) return null
        val breadth = needed.map { d -> days.getValue(d).let { if (it.adv + it.dec > 0) it.adv / (it.adv + it.dec) * 100 else null } }
        val strength = needed.map { d -> days.getValue(d).let { if (it.nearHigh + it.nearLow > 0) it.nearHigh.toDouble() / (it.nearHigh + it.nearLow) * 100 else null } }
        val momentum = needed.map { d -> days.getValue(d).let { if (it.rated50 > 0) it.above.toDouble() / it.rated50 * 100 else null } }

        fun factor(name: String, reads: String, series: List<Double?>, need: String): MoodFactor {
            val today = series.lastOrNull()
            val past = series.dropLast(1).filterNotNull()
            if (today == null) return MoodFactor(name, reads, null, null, "Not enough history yet. $need")
            val score = zScore100(past + today, today)
            return MoodFactor(name, reads, today, score, if (score == null) "Needs about 20 days of this measure. Download more history." else null)
        }

        val factors = listOf(
            factor("Market breadth", "Share of volume in rising stocks", breadth, "Needs 2 trading days."),
            factor("Price strength", "Stocks near 52-week highs vs lows", strength, "Needs about 30 days of prices."),
            factor("Momentum", "Stocks above their 50-day average", momentum, "Needs about 50 days of prices."),
            MoodFactor("FII activity", "Foreign investors' futures position", null, null, "Needs a licensed feed. Not available offline."),
            MoodFactor("Volatility", "India VIX and skew", null, null, "Needs a licensed feed. Not available offline."),
            MoodFactor("Demand for gold", "Gold's return against equities", null, null, "Needs a gold price feed. Not available offline."),
        )
        val scored = factors.mapNotNull { it.score }
        val score = if (scored.isEmpty()) null else scored.average()
        return MoodReading(LocalDate.parse(dates.last()), score, score?.let(::moodZone), factors, dates.size)
    }
}

/** Convenience for callers that already hold the bars. */
fun moodReading(bars: List<MarketBarEntity>): MoodReading? {
    val builder = MoodBuilder(bars.map { it.date }.distinct())
    bars.groupBy { it.symbol }.values.forEach { builder.add(it.sortedBy { r -> r.date }) }
    return builder.build()
}
