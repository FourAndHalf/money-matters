package com.moneymatters.app.core.market

/** One holding as the portfolio checks see it. [value] is in paise; [metrics] is null for funds or stocks with no stored bars. */
data class HeldPosition(val id: String, val name: String, val symbol: String, val isStock: Boolean, val value: Long, val metrics: StockMetrics?)

data class PositionWeight(val name: String, val symbol: String, val weightPct: Double, val flags: List<RedFlag>)

data class PortfolioCheck(
    val total: Long,
    val weights: List<PositionWeight>,
    val topHoldingPct: Double,
    val topThreePct: Double,
    /** 0–100: 100 is spread evenly over at least ten holdings, 0 is everything in one. Not Tickertape's formula (it is unpublished). */
    val spreadScore: Int?,
    val flaggedPct: Double,
    val flaggedCount: Int,
)

private const val CONCENTRATION_LIMIT = 25.0

/** Concentration and red flags over [positions], weighted by market value. Null with nothing held. */
fun portfolioCheck(positions: List<HeldPosition>): PortfolioCheck? {
    val total = positions.sumOf { it.value }
    if (total <= 0L) return null
    val weights = positions.filter { it.value > 0 }.sortedByDescending { it.value }.map { p ->
        val w = p.value * 100.0 / total
        val flags = buildList {
            if (w > CONCENTRATION_LIMIT) add(RedFlag("Concentrated", "${"%.0f".format(w)}% of the portfolio is in this one holding."))
            p.metrics?.let { addAll(redFlagsFor(it)) }
        }
        PositionWeight(p.name, p.symbol, w, flags)
    }
    val hhi = weights.sumOf { (it.weightPct / 100) * (it.weightPct / 100) }
    val flagged = weights.filter { it.flags.isNotEmpty() }
    return PortfolioCheck(
        total = total, weights = weights,
        topHoldingPct = weights.firstOrNull()?.weightPct ?: 0.0,
        topThreePct = weights.take(3).sumOf { it.weightPct },
        spreadScore = if (weights.size < 2) 0 else Math.round(((1 - hhi) / 0.9 * 100).coerceIn(0.0, 100.0)).toInt(),
        flaggedPct = flagged.sumOf { it.weightPct }, flaggedCount = flagged.size,
    )
}
