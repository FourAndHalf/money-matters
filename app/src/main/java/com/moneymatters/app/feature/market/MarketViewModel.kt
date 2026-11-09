package com.moneymatters.app.feature.market

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymatters.app.core.invest.Instrument
import com.moneymatters.app.core.market.AlertEventEntity
import com.moneymatters.app.core.market.AlertKind
import com.moneymatters.app.core.market.AlertRuleEntity
import com.moneymatters.app.core.market.Bar
import com.moneymatters.app.core.market.HeldPosition
import com.moneymatters.app.core.market.MarketRepository
import com.moneymatters.app.core.market.MarketStatus
import com.moneymatters.app.core.market.MarketUpdater
import com.moneymatters.app.core.market.MoodReading
import com.moneymatters.app.core.market.PortfolioCheck
import com.moneymatters.app.core.market.SavedScreenEntity
import com.moneymatters.app.core.market.ScreenCondition
import com.moneymatters.app.core.market.StockMetrics
import com.moneymatters.app.core.market.WatchlistEntity
import com.moneymatters.app.core.market.portfolioCheck
import com.moneymatters.app.feature.invest.HoldingRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Days of NSE files to ask for: about a year, which is what 52-week ranges and the 200-day average need. */
const val FULL_HISTORY_WEEKDAYS = 260
const val CATCH_UP_WEEKDAYS = 10

/** Markets: stored price history, watchlist, screener, mood, alerts and portfolio checks. */
class MarketViewModel(
    private val market: MarketRepository,
    private val updater: MarketUpdater,
) : ViewModel() {
    var status by mutableStateOf(MarketStatus(null, null, 0)); private set
    var universe by mutableStateOf<List<StockMetrics>>(emptyList()); private set
    var mood by mutableStateOf<MoodReading?>(null); private set
    var watch by mutableStateOf<List<WatchlistEntity>>(emptyList()); private set
    var rules by mutableStateOf<List<AlertRuleEntity>>(emptyList()); private set
    var events by mutableStateOf<List<AlertEventEntity>>(emptyList()); private set
    var saved by mutableStateOf<List<SavedScreenEntity>>(emptyList()); private set
    var portfolio by mutableStateOf<PortfolioCheck?>(null); private set
    var loaded by mutableStateOf(false); private set

    var busy by mutableStateOf(false); private set
    var progress by mutableStateOf<Pair<Int, Int>?>(null); private set
    var message by mutableStateOf<String?>(null)

    var openSymbol by mutableStateOf<String?>(null)
    var openBars by mutableStateOf<List<Bar>>(emptyList()); private set
    var conditions by mutableStateOf<List<ScreenCondition>>(emptyList())

    private val bySymbol get() = universe.associateBy { it.symbol }

    fun metricsOf(symbol: String): StockMetrics? = bySymbol[symbol.uppercase()]

    /** Re-reads everything from the store. Safe to call often; it never touches the network. */
    fun refresh() {
        viewModelScope.launch {
            val read = withContext(Dispatchers.Default) {
                Loaded(market.status(), market.universe(), market.mood(), market.watchlist(), market.rules(), market.events(), market.savedScreens())
            }
            status = read.status; universe = read.universe; mood = read.mood; watch = read.watch; rules = read.rules; events = read.events; saved = read.saved
            loaded = true
        }
    }

    private class Loaded(val status: MarketStatus, val universe: List<StockMetrics>, val mood: MoodReading?, val watch: List<WatchlistEntity>, val rules: List<AlertRuleEntity>, val events: List<AlertEventEntity>, val saved: List<SavedScreenEntity>)

    /** Downloads missing trading days from NSE, then checks alerts against the new prices. */
    fun download(weekdays: Int) {
        if (busy) return
        busy = true; progress = null; message = null
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    updater.fill(weekdays) { done, total -> progress = done to total }
                }
                val fired = withContext(Dispatchers.Default) { market.runAlerts() }
                message = result.message + if (fired.isNotEmpty()) " ${fired.size} alert${if (fired.size == 1) "" else "s"} fired." else ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't update: ${e.message ?: e::class.simpleName}"
            } finally {
                busy = false; progress = null
                refresh()
            }
        }
    }

    fun open(symbol: String) {
        openSymbol = symbol.uppercase()
        viewModelScope.launch { openBars = withContext(Dispatchers.Default) { market.barsOf(symbol) } }
    }

    fun close() { openSymbol = null; openBars = emptyList() }

    fun watch(symbol: String, name: String) = viewModelScope.launch { market.watch(symbol, name); refresh() }

    fun unwatch(symbol: String) = viewModelScope.launch { market.unwatch(symbol); refresh() }

    fun addRule(symbol: String, kind: AlertKind, threshold: Double?) = viewModelScope.launch { market.addRule(symbol, kind, threshold); refresh() }

    fun deleteRule(id: String) = viewModelScope.launch { market.deleteRule(id); refresh() }

    fun setRuleActive(rule: AlertRuleEntity, active: Boolean) = viewModelScope.launch { market.setRuleActive(rule, active); refresh() }

    fun markEventsSeen() = viewModelScope.launch { market.markEventsSeen(); refresh() }

    fun saveScreen(name: String) = viewModelScope.launch { if (name.isNotBlank() && conditions.isNotEmpty()) { market.saveScreen(name, conditions); refresh() } }

    fun deleteScreen(id: String) = viewModelScope.launch { market.deleteScreen(id); refresh() }

    /** Works out concentration and red flags for what the investor holds, using the stored prices where a stock has them. */
    fun checkPortfolio(rows: List<HoldingRow>) {
        val index = bySymbol
        val positions = rows.map { r ->
            HeldPosition(r.id, r.name, r.symbol, r.instrument == Instrument.Stock, (r.marketValue ?: r.cost).value, if (r.instrument == Instrument.Stock) index[r.symbol.uppercase()] else null)
        }
        portfolio = portfolioCheck(positions)
    }
}
