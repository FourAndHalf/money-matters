package com.moneymatters.app.core.market

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A day's figures in rupees. Display and screening only: floats here are never stored or posted. */
data class Bar(
    val symbol: String,
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val prevClose: Double,
    val volume: Long,
)

private const val UNIT = 10_000.0

fun MarketBarEntity.toBar() = Bar(symbol, LocalDate.parse(date), open / UNIT, high / UNIT, low / UNIT, close / UNIT, prevClose / UNIT, volume)

/** Simple average of the last [n] values, or null with fewer than [n]. */
fun sma(values: List<Double>, n: Int): Double? = if (n <= 0 || values.size < n) null else values.takeLast(n).average()

/** Wilder's RSI over [period]; needs [period] + 1 closes. 100 when there were no losses. */
fun rsi(closes: List<Double>, period: Int = 14): Double? {
    if (closes.size < period + 1) return null
    var gain = 0.0
    var loss = 0.0
    for (i in 1..period) {
        val d = closes[i] - closes[i - 1]
        if (d >= 0) gain += d else loss -= d
    }
    var avgGain = gain / period
    var avgLoss = loss / period
    for (i in period + 1 until closes.size) {
        val d = closes[i] - closes[i - 1]
        avgGain = (avgGain * (period - 1) + maxOf(d, 0.0)) / period
        avgLoss = (avgLoss * (period - 1) + maxOf(-d, 0.0)) / period
    }
    if (avgLoss == 0.0) return if (avgGain == 0.0) 50.0 else 100.0
    return 100 - 100 / (1 + avgGain / avgLoss)
}

/** Everything the screener, scorecard and alerts read about one stock, worked out from its stored bars. */
data class StockMetrics(
    val symbol: String,
    val name: String,
    val series: String,
    val date: LocalDate,
    val last: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val dayChangePct: Double?,
    val ret1m: Double?,
    val ret3m: Double?,
    val ret6m: Double?,
    val ret1y: Double?,
    /** Highest high / lowest low of the year before the latest bar, so "a new high" can be told from "at the high". */
    val priorHigh52: Double?,
    val priorLow52: Double?,
    val high52: Double,
    val low52: Double,
    val fromHighPct: Double,
    val aboveLowPct: Double,
    val sma50: Double?,
    val sma200: Double?,
    val rsi14: Double?,
    val avgVolume20: Double?,
    val volumeRatio: Double?,
    val turnoverLakh: Double?,
    val barCount: Int,
    val historyDays: Int,
)

private fun pct(now: Double, then: Double): Double? = if (then <= 0.0) null else (now / then - 1) * 100

/** The close on the last bar on or before [target], if the stored history reaches back that far (within a week). */
private fun closeOnOrBefore(bars: List<Bar>, target: LocalDate): Double? {
    val first = bars.first().date
    if (first.isAfter(target.plusDays(7))) return null
    return bars.lastOrNull { !it.date.isAfter(target) }?.close ?: bars.first().close
}

/** Metrics for one stock from its [bars] in date order, or null with none. */
fun computeMetrics(name: String, series: String, bars: List<Bar>): StockMetrics? {
    if (bars.isEmpty()) return null
    val last = bars.last()
    val closes = bars.map { it.close }
    val yearAgo = last.date.minusDays(365)
    val year = bars.filter { it.date.isAfter(yearAgo) }
    val prior = year.dropLast(1)
    val high52 = year.maxOf { it.high }
    val low52 = year.minOf { it.low }
    val volumes = bars.map { it.volume.toDouble() }
    val avgVol = if (bars.size > 20) volumes.dropLast(1).takeLast(20).average() else null
    val avg20Now = sma(volumes, 20)
    return StockMetrics(
        symbol = last.symbol, name = name, series = series, date = last.date, last = last.close,
        dayHigh = last.high, dayLow = last.low,
        dayChangePct = pct(last.close, last.prevClose),
        ret1m = closeOnOrBefore(bars, last.date.minusDays(30))?.let { pct(last.close, it) },
        ret3m = closeOnOrBefore(bars, last.date.minusDays(91))?.let { pct(last.close, it) },
        ret6m = closeOnOrBefore(bars, last.date.minusDays(182))?.let { pct(last.close, it) },
        ret1y = closeOnOrBefore(bars, yearAgo)?.let { pct(last.close, it) },
        priorHigh52 = prior.maxOfOrNull { it.high }, priorLow52 = prior.minOfOrNull { it.low },
        high52 = high52, low52 = low52,
        fromHighPct = (last.close / high52 - 1) * 100,
        aboveLowPct = if (low52 > 0) (last.close / low52 - 1) * 100 else 0.0,
        sma50 = sma(closes, 50), sma200 = sma(closes, 200), rsi14 = rsi(closes),
        avgVolume20 = avg20Now,
        volumeRatio = avgVol?.takeIf { it > 0 }?.let { last.volume / it },
        turnoverLakh = avg20Now?.let { it * last.close / 100_000 },
        barCount = bars.size,
        historyDays = ChronoUnit.DAYS.between(bars.first().date, last.date).toInt(),
    )
}

/** Metrics for every stock in [entities], skipping stocks whose latest bar is older than [maxStaleDays] (delisted, suspended). */
fun universeMetrics(entities: List<MarketBarEntity>, maxStaleDays: Long = 10): List<StockMetrics> {
    if (entities.isEmpty()) return emptyList()
    val latest = LocalDate.parse(entities.maxOf { it.date })
    return entities.groupBy { it.symbol }.mapNotNull { (_, rows) ->
        val sorted = rows.sortedBy { it.date }
        val newest = sorted.last()
        if (ChronoUnit.DAYS.between(LocalDate.parse(newest.date), latest) > maxStaleDays) return@mapNotNull null
        computeMetrics(newest.name, newest.series, sorted.map { it.toBar() })
    }
}

internal fun absOrNull(v: Double?) = v?.let { abs(it) }
