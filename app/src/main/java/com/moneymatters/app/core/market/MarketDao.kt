package com.moneymatters.app.core.market

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MarketDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBars(bars: List<MarketBarEntity>)

    @Query("SELECT * FROM market_bars WHERE date >= :from ORDER BY date")
    suspend fun barsSince(from: String): List<MarketBarEntity>

    @Query("SELECT * FROM market_bars WHERE symbol = :symbol AND date >= :from ORDER BY date")
    suspend fun barsFor(symbol: String, from: String): List<MarketBarEntity>

    @Query("SELECT * FROM market_bars WHERE symbol IN (:symbols) AND date >= :from ORDER BY symbol, date")
    suspend fun barsForSymbols(symbols: List<String>, from: String): List<MarketBarEntity>

    @Query("SELECT DISTINCT date FROM market_bars ORDER BY date")
    suspend fun storedDates(): List<String>

    @Query("DELETE FROM market_bars WHERE date < :before")
    suspend fun pruneBars(before: String)

    @Query("SELECT * FROM watchlist ORDER BY groupName, symbol")
    suspend fun watchlist(): List<WatchlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWatch(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun removeWatch(symbol: String)

    @Query("DELETE FROM alert_rules WHERE symbol = :symbol")
    suspend fun deleteRulesFor(symbol: String)

    @Query("SELECT * FROM alert_rules ORDER BY createdAt DESC")
    suspend fun rules(): List<AlertRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRule(rule: AlertRuleEntity)

    @Update suspend fun updateRules(rules: List<AlertRuleEntity>)

    @Query("DELETE FROM alert_rules WHERE id = :id")
    suspend fun deleteRule(id: String)

    @Query("SELECT * FROM alert_events ORDER BY firedAt DESC LIMIT 100")
    suspend fun events(): List<AlertEventEntity>

    @Insert suspend fun insertEvents(events: List<AlertEventEntity>)

    @Query("UPDATE alert_events SET seen = 1")
    suspend fun markEventsSeen()

    @Query("SELECT * FROM saved_screens ORDER BY createdAt")
    suspend fun savedScreens(): List<SavedScreenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertScreen(screen: SavedScreenEntity)

    @Query("DELETE FROM saved_screens WHERE id = :id")
    suspend fun deleteScreen(id: String)
}
