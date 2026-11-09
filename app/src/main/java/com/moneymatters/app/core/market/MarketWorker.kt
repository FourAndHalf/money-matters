package com.moneymatters.app.core.market

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneymatters.app.core.invest.HttpPriceFetcher
import com.moneymatters.app.core.invest.PriceSourcePrefs
import com.moneymatters.app.core.ledger.LedgerDatabaseProvider
import com.moneymatters.app.feature.market.AlertNotifier
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "daily-market-update"
private const val EVENING_HOUR = 19
private const val CATCH_UP_WEEKDAYS = 7

/** Milliseconds from [now] until the next 19:00, when NSE's file for the day is normally out. */
fun millisUntilNextEvening(now: LocalDateTime): Long {
    var next = now.toLocalDate().atTime(EVENING_HOUR, 0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return java.time.Duration.between(now, next).toMillis()
}

/**
 * Each evening, tops up the stored prices and checks the investor's alert rules. It does nothing at all (no download)
 * until there are rules or stored history, so a user who never opens Markets never has this run.
 */
class MarketWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val market = MarketRepository(LedgerDatabaseProvider.get(context))
        if (market.rules().none { it.active } && market.status().days == 0) return Result.success()
        val sources = PriceSourcePrefs(context.getSharedPreferences("price_sources", Context.MODE_PRIVATE))
        val result = MarketUpdater(market, HttpPriceFetcher(), sources::sources).fill(CATCH_UP_WEEKDAYS)
        val fired = market.runAlerts()
        AlertNotifier.notify(context, fired)
        return if (!result.ok && result.message.contains("Couldn't connect")) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())) {
            val request = PeriodicWorkRequestBuilder<MarketWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextEvening(now), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
