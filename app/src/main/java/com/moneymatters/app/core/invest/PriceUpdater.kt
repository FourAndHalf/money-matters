package com.moneymatters.app.core.invest

import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class PriceSourceId(val key: String, val label: String) {
    Nse("nse", "NSE"),
    Amfi("amfi", "AMFI"),
}

/**
 * Where prices come from. The NSE address has a `{yyyyMMdd}` placeholder for the trading day, and both are
 * settings because NSE has moved its file before and is changing distribution again (plan.html → "Prices").
 */
data class PriceSources(val nseUrlTemplate: String, val amfiUrl: String) {
    companion object {
        val Default = PriceSources(
            nseUrlTemplate = "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_{yyyyMMdd}_F_0000.csv.zip",
            amfiUrl = "https://www.amfiindia.com/spages/NAVAll.txt",
        )
    }
}

/** The investor's chosen sources, kept in plain preferences (they are addresses, not secrets). */
class PriceSourcePrefs(private val prefs: SharedPreferences) {
    fun sources(): PriceSources = PriceSources(
        prefs.getString(KEY_NSE, null) ?: PriceSources.Default.nseUrlTemplate,
        prefs.getString(KEY_AMFI, null) ?: PriceSources.Default.amfiUrl,
    )

    fun save(sources: PriceSources) {
        prefs.edit().putString(KEY_NSE, sources.nseUrlTemplate.trim()).putString(KEY_AMFI, sources.amfiUrl.trim()).apply()
    }

    private companion object {
        const val KEY_NSE = "nse_url_template"
        const val KEY_AMFI = "amfi_url"
    }
}

/** What one source's run did. [message] is shown as is; [httpStatus] is set when the download itself failed. */
data class SourceResult(
    val source: PriceSourceId,
    val ok: Boolean,
    val message: String,
    val rowsParsed: Int = 0,
    val rowsMatched: Int = 0,
    val pricesStored: Int = 0,
    val priceDate: LocalDate? = null,
    val httpStatus: Int? = null,
)

private const val NSE_LOOKBACK_WEEKDAYS = 7
private val NSE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

/** The last weekday on or before [date]: the trading day to ask for (holidays are found by the file not existing). */
fun lastWeekdayOnOrBefore(date: LocalDate): LocalDate {
    var d = date
    while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.minusDays(1)
    return d
}

/**
 * Downloads NSE closing prices (for stocks) and AMFI NAVs (for funds), matches them to holdings by ISIN
 * (stocks fall back to their symbol), and stores each with the status it has earned. Every attempt, good or
 * bad, is logged, and a source that fails never stops the other.
 */
class PriceUpdater(
    private val invest: InvestRepository,
    private val fetcher: PriceFetcher,
    private val sources: () -> PriceSources,
    private val today: () -> LocalDate = LocalDate::now,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun refresh(): List<SourceResult> = listOf(refreshNse(), refreshAmfi())

    suspend fun refreshNse(): SourceResult = record(downloadNse())

    suspend fun refreshAmfi(): SourceResult = record(downloadAmfi())

    private suspend fun record(result: SourceResult): SourceResult {
        invest.logFetch(
            PriceFetchLogEntity(
                source = result.source.key, attemptedAt = now(), ok = result.ok, httpStatus = result.httpStatus,
                rowsParsed = result.rowsParsed, rowsMatched = result.rowsMatched, message = result.message, priceDate = result.priceDate?.toString(),
            ),
        )
        return result
    }

    private fun failed(source: PriceSourceId, message: String, httpStatus: Int? = null, parsed: Int = 0) =
        SourceResult(source, ok = false, message = message, rowsParsed = parsed, httpStatus = httpStatus)

    private suspend fun downloadNse(): SourceResult {
        val holdings = invest.holdings().filter { it.instrument == Instrument.Stock }
        if (holdings.isEmpty()) return SourceResult(PriceSourceId.Nse, ok = true, message = "No stocks to price.")
        val template = sources().nseUrlTemplate
        var day = lastWeekdayOnOrBefore(today())
        repeat(NSE_LOOKBACK_WEEKDAYS) {
            when (val result = fetcher.fetch(template.replace("{yyyyMMdd}", NSE_DATE.format(day)))) {
                is FetchResult.Ok -> return processNse(result.bytes, holdings)
                // No file that day: a holiday, or not published yet. Try the trading day before.
                is FetchResult.Failed -> if (result.httpStatus == 404) day = lastWeekdayOnOrBefore(day.minusDays(1)) else return failed(PriceSourceId.Nse, result.reason, result.httpStatus)
            }
        }
        return failed(PriceSourceId.Nse, "No NSE file found for the last $NSE_LOOKBACK_WEEKDAYS trading days (404). The address may have changed.", 404)
    }

    private suspend fun processNse(bytes: ByteArray, holdings: List<HoldingEntity>): SourceResult {
        val rows = parseNse(unzipFirst(bytes) ?: bytes.toString(Charsets.UTF_8))
        if (rows.isEmpty()) return failed(PriceSourceId.Nse, "The file downloaded, but no prices could be read from it. Its format may have changed.")
        val index = NseIndex(rows)
        val names = invest.holdingNames()
        var matched = 0
        var stored = 0
        val missing = mutableListOf<String>()
        for (h in holdings) {
            val row = h.isin?.let(index::byIsin) ?: h.symbol?.let(index::bySymbol)
            if (row == null) { missing += names[h.id] ?: h.symbol ?: h.id; continue }
            matched++
            if (invest.ingestPrice(h.id, row.date, row.close, PriceSourceId.Nse.key, today(), now()) != null) stored++
        }
        val date = rows.maxOf { it.date }
        val note = if (missing.isEmpty()) "" else ". Not found: ${missing.joinToString()}"
        return SourceResult(
            PriceSourceId.Nse, ok = matched > 0,
            message = "Closing prices for $date: matched $matched of ${holdings.size} stocks$note",
            rowsParsed = rows.size, rowsMatched = matched, pricesStored = stored, priceDate = date,
        )
    }

    private suspend fun downloadAmfi(): SourceResult {
        val holdings = invest.holdings().filter { it.instrument == Instrument.MutualFund }
        if (holdings.isEmpty()) return SourceResult(PriceSourceId.Amfi, ok = true, message = "No funds to price.")
        return when (val result = fetcher.fetch(sources().amfiUrl)) {
            is FetchResult.Failed -> failed(PriceSourceId.Amfi, result.reason, result.httpStatus)
            is FetchResult.Ok -> processAmfi(result.bytes.toString(Charsets.UTF_8), holdings)
        }
    }

    private suspend fun processAmfi(text: String, holdings: List<HoldingEntity>): SourceResult {
        val rows = parseAmfi(text)
        if (rows.isEmpty()) return failed(PriceSourceId.Amfi, "The file downloaded, but no NAVs could be read from it. Its format may have changed.")
        // One ISIN can appear on several lines (plans, old copies); the newest line with a real NAV wins.
        val byIsin = HashMap<String, AmfiRow>()
        for (row in rows) {
            val nav = row.nav ?: continue
            if (nav.tenThousandths <= 0 || row.date == null) continue
            for (isin in row.isins) if (byIsin[isin]?.date?.isBefore(row.date) != false) byIsin[isin] = row
        }
        val names = invest.holdingNames()
        var matched = 0
        var stored = 0
        val missing = mutableListOf<String>()
        for (h in holdings) {
            val label = names[h.id] ?: h.id
            val row = h.isin?.let(byIsin::get)
            if (row == null) { missing += if (h.isin == null) "$label (no ISIN saved)" else label; continue }
            matched++
            if (invest.ingestPrice(h.id, row.date!!, row.nav!!, PriceSourceId.Amfi.key, today(), now()) != null) stored++
        }
        val date = rows.mapNotNull { it.date }.maxOrNull()
        val note = if (missing.isEmpty()) "" else ". Not found: ${missing.joinToString()}"
        return SourceResult(
            PriceSourceId.Amfi, ok = matched > 0,
            message = "NAVs${date?.let { " for $it" } ?: ""}: matched $matched of ${holdings.size} funds$note",
            rowsParsed = rows.size, rowsMatched = matched, pricesStored = stored, priceDate = date,
        )
    }
}
