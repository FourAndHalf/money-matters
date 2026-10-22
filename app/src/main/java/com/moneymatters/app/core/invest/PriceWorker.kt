package com.moneymatters.app.core.invest

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moneymatters.app.core.ledger.LedgerDatabaseProvider
import com.moneymatters.app.core.ledger.LedgerRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "weekly-price-download"
private const val SATURDAY_HOUR = 9L

/** Milliseconds from [now] until the next Saturday 09:00 (plan.html: "Saturday morning, for the last trading day"). */
fun millisUntilNextSaturdayMorning(now: LocalDateTime): Long {
    var next = now.toLocalDate().atTime(SATURDAY_HOUR.toInt(), 0)
    while (next.dayOfWeek != DayOfWeek.SATURDAY || !next.isAfter(now)) next = next.plusDays(1)
    return java.time.Duration.between(now, next).toMillis()
}

/** Runs the weekly download in the background, even when the app isn't open. */
class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val database = LedgerDatabaseProvider.get(context)
        val ledger = LedgerRepository(database)
        val rules = CapitalGainsRules.load(context)
        val invest = InvestRepository(database, ledger, rules)
        val sources = PriceSourcePrefs(context.getSharedPreferences("price_sources", Context.MODE_PRIVATE))
        val results = PriceUpdater(invest, HttpPriceFetcher(), sources::sources).refresh()
        // A failed source is logged for the Prices screen; retrying only helps when the connection (not the file) was the problem.
        return if (results.any { !it.ok && it.message.startsWith("Couldn't connect") }) Result.retry() else Result.success()
    }

    companion object {
        /** Schedules the weekly run once; calling it again on every launch leaves the existing schedule alone. */
        fun schedule(context: Context, now: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())) {
            val request = PeriodicWorkRequestBuilder<PriceWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextSaturdayMorning(now), TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
