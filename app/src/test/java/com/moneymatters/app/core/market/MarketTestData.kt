package com.moneymatters.app.core.market

import java.time.LocalDate

internal const val U = 10_000L

/** A stock whose closes follow [closes], one weekday apart ending on [end]; high/low are 1% either side. */
internal fun rows(symbol: String, closes: List<Double>, end: LocalDate = LocalDate.of(2026, 11, 6), series: String = "EQ", volume: Long = 100_000, name: String = symbol): List<MarketBarEntity> {
    val dates = generateSequence(end) { it.minusDays(1) }.filter { it.dayOfWeek.value <= 5 }.take(closes.size).toList().reversed()
    return closes.indices.map { i ->
        val c = (closes[i] * U).toLong()
        MarketBarEntity(symbol, dates[i].toString(), name, series, c, (c * 1.01).toLong(), (c * 0.99).toLong(), c, if (i == 0) c else (closes[i - 1] * U).toLong(), volume)
    }
}
internal fun metrics(symbol: String, closes: List<Double>, volume: Long = 100_000, series: String = "EQ") =
    computeMetrics(symbol, series, rows(symbol, closes, series = series, volume = volume).map { it.toBar() })!!
