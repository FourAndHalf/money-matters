package com.moneymatters.app.core.market

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stock's end-of-day figures from NSE's daily file. Prices are in 1/10,000 rupee (the same unit as holdings'
 * prices), so a bar is exact and never a float. Only plain equity-like series are kept.
 */
@Entity(tableName = "market_bars", primaryKeys = ["symbol", "date"], indices = [Index("date")])
data class MarketBarEntity(
    val symbol: String,
    /** ISO date of the trading day. */
    val date: String,
    val name: String,
    val series: String,
    val open: Long,
    val high: Long,
    val low: Long,
    val close: Long,
    /** Yesterday's close as NSE reports it, so a one-day move survives gaps in what has been downloaded. */
    val prevClose: Long,
    val volume: Long,
)

/** A stock the investor follows without owning. */
@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val groupName: String,
    val addedAt: Long,
)

enum class AlertKind(val label: String) {
    PriceAbove("Price rises above"),
    PriceBelow("Price falls below"),
    MoveOneDay("Moves more than (% in a day)"),
    SmaCrossUp("Crosses above its 50-day average"),
    SmaCrossDown("Crosses below its 50-day average"),
    NewHigh("Reaches a 52-week high"),
    NewLow("Reaches a 52-week low"),
    RsiOverbought("RSI goes above 70"),
    RsiOversold("RSI goes below 30"),
    VolumeSpike("Volume is over twice its 1-month average"),
}

/**
 * A stored rule. [lastState] is whether the condition held when it was last checked; a rule fires only when it turns
 * from false to true, so a stock sitting above a level does not alert every day.
 */
@Entity(tableName = "alert_rules", indices = [Index("symbol")])
data class AlertRuleEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val kind: AlertKind,
    /** Rupees for a price rule, percent for a move rule; unused otherwise. */
    val threshold: Double?,
    val active: Boolean,
    val lastState: Boolean,
    val createdAt: Long,
)

/** One time a rule fired, kept so alerts are readable in the app even without notification permission. */
@Entity(tableName = "alert_events", indices = [Index("firedAt")])
data class AlertEventEntity(
    @PrimaryKey val id: String,
    val ruleId: String,
    val symbol: String,
    val message: String,
    val priceDate: String,
    val firedAt: Long,
    val seen: Boolean,
)

/** A screen the investor saved: its conditions as `metric|op|value` lines. */
@Entity(tableName = "saved_screens")
data class SavedScreenEntity(
    @PrimaryKey val id: String,
    val name: String,
    val conditions: String,
    val createdAt: Long,
)
