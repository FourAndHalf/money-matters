package com.moneymatters.app.core.market

import androidx.room.withTransaction
import com.moneymatters.app.core.invest.NseBar
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import java.time.LocalDate
import java.util.UUID

/** What the stored price history covers, for the data strip on the Markets screen. */
data class MarketStatus(val newest: LocalDate?, val oldest: LocalDate?, val days: Int)

/** Only about a year of daily files is useful (52-week ranges), so older days are dropped as new ones arrive. */
private const val KEEP_DAYS = 380L
private const val BATCH = 300

class MarketRepository(
    private val database: MoneyMattersDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao get() = database.marketDao()

    suspend fun ingestDay(bars: List<NseBar>): Int {
        if (bars.isEmpty()) return 0
        database.withTransaction {
            dao.upsertBars(bars.map { MarketBarEntity(it.symbol, it.date.toString(), it.name, it.series, it.open, it.high, it.low, it.close, it.prevClose, it.volume) })
            dao.pruneBars(today().minusDays(KEEP_DAYS).toString())
        }
        return bars.size
    }

    suspend fun storedDates(): Set<LocalDate> = dao.storedDates().map(LocalDate::parse).toSet()

    suspend fun status(): MarketStatus {
        val dates = dao.storedDates()
        return MarketStatus(dates.lastOrNull()?.let(LocalDate::parse), dates.firstOrNull()?.let(LocalDate::parse), dates.size)
    }

    private fun from() = today().minusDays(KEEP_DAYS).toString()

    /** Metrics for every stock, read in batches so a year of the whole market is never in memory at once. */
    suspend fun universe(): List<StockMetrics> {
        val latest = dao.storedDates().lastOrNull() ?: return emptyList()
        val newest = dao.barsSince(latest)
        val out = mutableListOf<StockMetrics>()
        for (batch in newest.map { it.symbol }.chunked(BATCH)) {
            out += universeMetrics(dao.barsForSymbols(batch, from()), maxStaleDays = Long.MAX_VALUE)
        }
        return out
    }

    suspend fun metricsFor(symbols: Collection<String>): Map<String, StockMetrics> {
        val out = HashMap<String, StockMetrics>()
        for (batch in symbols.map { it.uppercase() }.distinct().chunked(BATCH)) {
            universeMetrics(dao.barsForSymbols(batch, from()), maxStaleDays = Long.MAX_VALUE).forEach { out[it.symbol] = it }
        }
        return out
    }

    suspend fun barsOf(symbol: String): List<Bar> = dao.barsFor(symbol.uppercase(), from()).map { it.toBar() }

    suspend fun mood(): MoodReading? {
        val dates = dao.storedDates()
        if (dates.isEmpty()) return null
        val builder = MoodBuilder(dates)
        val all = dao.barsSince(dates.last()).filter { it.series == "EQ" }.map { it.symbol }
        for (batch in all.chunked(BATCH)) {
            dao.barsForSymbols(batch, from()).groupBy { it.symbol }.values.forEach { builder.add(it) }
        }
        return builder.build()
    }

    suspend fun watchlist() = dao.watchlist()

    suspend fun watch(symbol: String, name: String, group: String = "Watchlist") =
        dao.upsertWatch(WatchlistEntity(symbol.uppercase(), name, group.ifBlank { "Watchlist" }, now()))

    suspend fun unwatch(symbol: String) = database.withTransaction {
        dao.removeWatch(symbol.uppercase())
        dao.deleteRulesFor(symbol.uppercase())
    }

    suspend fun rules() = dao.rules()

    /** Adds a rule, remembering whether its condition already holds so it fires on the next change, not at once. */
    suspend fun addRule(symbol: String, kind: AlertKind, threshold: Double?) {
        val sym = symbol.uppercase()
        val m = metricsFor(listOf(sym))[sym]
        val rule = AlertRuleEntity(newId(), sym, kind, threshold, active = true, lastState = false, createdAt = now())
        dao.upsertRule(rule.copy(lastState = m?.let { conditionHolds(rule, it) } ?: false))
    }

    suspend fun deleteRule(id: String) = dao.deleteRule(id)

    suspend fun setRuleActive(rule: AlertRuleEntity, active: Boolean) {
        val m = metricsFor(listOf(rule.symbol))[rule.symbol]
        dao.upsertRule(rule.copy(active = active, lastState = m?.let { conditionHolds(rule, it) } ?: rule.lastState))
    }

    suspend fun events() = dao.events()

    suspend fun markEventsSeen() = dao.markEventsSeen()

    /** Checks every rule against the latest stored day and records what fired. Returns the new events. */
    suspend fun runAlerts(): List<AlertEventEntity> {
        val rules = dao.rules().filter { it.active }
        if (rules.isEmpty()) return emptyList()
        val metrics = metricsFor(rules.map { it.symbol }.toSet())
        val run = evaluateAlerts(rules, metrics, now(), newId)
        database.withTransaction {
            if (run.rules.isNotEmpty()) dao.updateRules(run.rules)
            if (run.events.isNotEmpty()) dao.insertEvents(run.events)
        }
        return run.events
    }

    suspend fun savedScreens() = dao.savedScreens()

    suspend fun saveScreen(name: String, conditions: List<ScreenCondition>) =
        dao.upsertScreen(SavedScreenEntity(newId(), name.trim(), encodeConditions(conditions), now()))

    suspend fun deleteScreen(id: String) = dao.deleteScreen(id)
}
