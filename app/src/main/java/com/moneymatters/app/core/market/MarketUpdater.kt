package com.moneymatters.app.core.market

import com.moneymatters.app.core.invest.FetchResult
import com.moneymatters.app.core.invest.PriceFetcher
import com.moneymatters.app.core.invest.PriceSources
import com.moneymatters.app.core.invest.parseNseBars
import com.moneymatters.app.core.invest.unzipFirst
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What one download run did, in words fit to show as they are. */
data class FillResult(val added: Int, val holidays: Int, val ok: Boolean, val message: String)

private val FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

/**
 * Brings the stored daily history up to date from the same NSE files the weekly price download uses. It asks for each
 * weekday it does not have, newest first; a missing file (404) is a holiday or not yet published and is skipped; any
 * other failure stops the run and says why, keeping what was already stored.
 */
class MarketUpdater(
    private val market: MarketRepository,
    private val fetcher: PriceFetcher,
    private val sources: () -> PriceSources,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend fun fill(weekdays: Int, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): FillResult {
        val have = market.storedDates()
        val wanted = generateSequence(today()) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(weekdays).filter { it !in have }.toList()
        if (wanted.isEmpty()) return FillResult(0, 0, true, "Already up to date.")
        var added = 0
        var holidays = 0
        wanted.forEachIndexed { i, day ->
            onProgress(i, wanted.size)
            when (val result = fetcher.fetch(sources().nseUrlTemplate.replace("{yyyyMMdd}", FILE_DATE.format(day)))) {
                is FetchResult.Ok -> {
                    val bars = parseNseBars(unzipFirst(result.bytes) ?: result.bytes.toString(Charsets.UTF_8))
                    if (bars.isEmpty()) return FillResult(added, holidays, false, "The file for $day downloaded, but no prices could be read from it. Its format may have changed.")
                    market.ingestDay(bars)
                    added++
                }
                is FetchResult.Failed ->
                    if (result.httpStatus == 404) holidays++
                    else return FillResult(added, holidays, false, "Stopped at $day: ${result.reason}" + if (added > 0) " Kept the $added day${if (added == 1) "" else "s"} already downloaded." else "")
            }
        }
        onProgress(wanted.size, wanted.size)
        val note = if (holidays > 0) " ($holidays with no file: holidays or not published yet)" else ""
        return FillResult(added, holidays, true, "Added $added trading day${if (added == 1) "" else "s"}$note.")
    }
}
