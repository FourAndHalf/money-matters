package com.moneymatters.app.core.invest

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.ledger.AccountType
import com.moneymatters.app.core.ledger.LedgerRepository
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import com.moneymatters.app.core.money.UnitPrice
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeFetcher(private val responses: MutableMap<String, FetchResult> = mutableMapOf()) : PriceFetcher {
    val requested = mutableListOf<String>()
    fun on(url: String, result: FetchResult) { responses[url] = result }
    override suspend fun fetch(url: String): FetchResult { requested += url; return responses[url] ?: FetchResult.Failed(404, "File not found (404)") }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PriceUpdaterTest {

    private val today = LocalDate.of(2026, 10, 22) // a Thursday
    private val sources = PriceSources("https://nse.test/{yyyyMMdd}.zip", "https://amfi.test/NAV.txt")
    private val rules = CapitalGainsRules(listOf(CapitalGainsRules.Entry(LocalDate.of(2018, 4, 1), 12, 24)))
    private lateinit var db: MoneyMattersDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var invest: InvestRepository
    private lateinit var fetcher: FakeFetcher
    private lateinit var updater: PriceUpdater
    private lateinit var bank: String
    private var clock = 1_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        ledger = LedgerRepository(db)
        invest = InvestRepository(db, ledger, rules)
        fetcher = FakeFetcher()
        updater = PriceUpdater(invest, fetcher, { sources }, { today }, { clock++ })
        runBlocking { bank = ledger.createAccount("HDFC Bank", AccountType.Asset) }
    }

    private fun nseCsv(vararg lines: String) = "TradDt,ISIN,TckrSymb,SctySrs,FinInstrmNm,ClsPric\n" + lines.joinToString("\n")
    private fun zip(text: String) = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("f.csv")); z.write(text.toByteArray()); z.closeEntry() } }.toByteArray()
    private fun nseUrl(date: String) = "https://nse.test/$date.zip"

    private fun stock(symbol: String, isin: String? = null, bought: LocalDate = LocalDate.of(2025, 1, 1)): String = runBlocking {
        val id = invest.findOrCreateHolding(Instrument.Stock, symbol, isin, name = symbol)
        invest.buy(id, bought, Units.whole(10), UnitPrice.rupees(100), 1_000.rupees, bank, "Buy $symbol")
        id
    }

    private fun fund(name: String, isin: String?): String = runBlocking {
        val id = invest.findOrCreateHolding(Instrument.MutualFund, null, isin, name, FundClass.Equity)
        invest.buy(id, LocalDate.of(2025, 1, 1), Units.whole(10), UnitPrice.rupees(100), 1_000.rupees, bank, "Buy $name")
        id
    }

    private fun prices(holdingId: String) = runBlocking { db.investDao().getAllPrices().filter { it.holdingId == holdingId } }
    private fun log() = runBlocking { db.investDao().getAllFetchLog() }

    // ---- NSE ----

    @Test fun `NSE closing prices are matched by ISIN, stored as verified, and logged`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        val result = updater.refreshNse()
        assertTrue(result.message, result.ok)
        assertEquals(1, result.rowsMatched)
        assertEquals(LocalDate.of(2026, 10, 22), result.priceDate)
        val price = prices(infy).single()
        assertEquals(11_680_000L, price.price)
        assertEquals(PriceStatus.Verified, price.status)
        assertEquals("nse", price.source)
        val entry = log().single()
        assertTrue(entry.ok)
        assertEquals("nse", entry.source)
        assertEquals("2026-10-22", entry.priceDate)
        assertEquals("Closing prices for 2026-10-22: matched 1 of 1 stocks", entry.message)
    }

    @Test fun `a holiday is found by the file not existing, and the previous trading day is used`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        fetcher.on(nseUrl("20261021"), FetchResult.Ok(zip(nseCsv("2026-10-21,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        val result = updater.refreshNse() // 22 Oct has no file yet
        assertTrue(result.ok)
        assertEquals(listOf(nseUrl("20261022"), nseUrl("20261021")), fetcher.requested)
        assertEquals("2026-10-21", prices(infy).single().date)
    }

    @Test fun `a weekend asks for the Friday file`() = runBlocking {
        stock("INFY", "INE009A01021")
        val saturday = PriceUpdater(invest, fetcher, { sources }, { LocalDate.of(2026, 10, 24) }, { clock++ })
        saturday.refreshNse()
        assertEquals(nseUrl("20261023"), fetcher.requested.first())
    }

    @Test fun `no file for a whole week is a failure that says the address may have changed`() = runBlocking {
        stock("INFY", "INE009A01021")
        val result = updater.refreshNse()
        assertFalse(result.ok)
        assertEquals(7, fetcher.requested.size)
        assertTrue(result.message, result.message.contains("address may have changed"))
        assertEquals(404, result.httpStatus)
        assertFalse(log().single().ok)
    }

    @Test fun `a blocked request fails at once with its reason`() = runBlocking {
        stock("INFY", "INE009A01021")
        fetcher.on(nseUrl("20261022"), FetchResult.Failed(403, "Blocked (403): the source refused the request."))
        val result = updater.refreshNse()
        assertFalse(result.ok)
        assertEquals("Blocked (403): the source refused the request.", result.message)
        assertEquals(1, fetcher.requested.size)
        assertEquals(403, log().single().httpStatus)
    }

    @Test fun `a file that downloads but cannot be read is reported as a format change`() = runBlocking {
        stock("INFY", "INE009A01021")
        fetcher.on(nseUrl("20261022"), FetchResult.Ok("<html>Access denied</html>".toByteArray()))
        val result = updater.refreshNse()
        assertFalse(result.ok)
        assertTrue(result.message, result.message.contains("format may have changed"))
    }

    @Test fun `a stock with no ISIN is matched by symbol, and unmatched ones are named`() = runBlocking {
        val tcs = stock("TCS")
        stock("GHOST", "INE000000000")
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE467B01029,TCS,EQ,TATA CONSULTANCY,2529.00"))))
        val result = updater.refreshNse()
        assertEquals(1, result.rowsMatched)
        assertTrue(result.message, result.message.endsWith("Not found: GHOST"))
        assertEquals(25_290_000L, prices(tcs).single().price)
    }

    @Test fun `if nothing matches, the source counts as failing rather than quietly fine`() = runBlocking {
        stock("GHOST", "INE000000000")
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE467B01029,TCS,EQ,TATA,2529.00"))))
        assertFalse(updater.refreshNse().ok)
    }

    @Test fun `with no stocks held there is nothing to download`() = runBlocking {
        val result = updater.refreshNse()
        assertTrue(result.ok)
        assertEquals(0, fetcher.requested.size)
    }

    // ---- AMFI ----

    private val amfi = """
        Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date
        Axis Mutual Fund
        1;INF000000001;-;Fund One;Direct Plan;Growth;81.2345;22-Oct-2026
        2;INF000000002;INF000000003;Fund Two;Direct Plan;IDCW;50.0000;22-Oct-2026
        3;INF000000004;-;Wound Up Fund;Direct Plan;Growth;10.0000;16-Sep-2022
        4;INF000000005;-;Segregated;Direct Plan;Growth;0.0000;22-Oct-2026
    """.trimIndent()

    @Test fun `fund NAVs are matched by either ISIN and stored with the file's own date`() = runBlocking {
        val one = fund("Fund One", "INF000000001")
        val two = fund("Fund Two", "INF000000003") // the reinvestment ISIN
        fetcher.on(sources.amfiUrl, FetchResult.Ok(amfi.toByteArray()))
        val result = updater.refreshAmfi()
        assertTrue(result.message, result.ok)
        assertEquals(2, result.rowsMatched)
        assertEquals(812_345L, prices(one).single().price)
        assertEquals(PriceStatus.Verified, prices(one).single().status)
        assertEquals("amfi", prices(one).single().source)
        assertEquals(500_000L, prices(two).single().price)
    }

    @Test fun `a frozen NAV from a wound-up scheme is stored as stale, never as current`() = runBlocking {
        val old = fund("Wound Up Fund", "INF000000004")
        fetcher.on(sources.amfiUrl, FetchResult.Ok(amfi.toByteArray()))
        updater.refreshAmfi()
        val price = prices(old).single()
        assertEquals("2022-09-16", price.date)
        assertEquals(PriceStatus.Stale, price.status)
    }

    @Test fun `a zero NAV is ignored and reported, a fund with no ISIN is named as such`() = runBlocking {
        val seg = fund("Segregated", "INF000000005")
        fund("No Isin Fund", null)
        fetcher.on(sources.amfiUrl, FetchResult.Ok(amfi.toByteArray()))
        val result = updater.refreshAmfi()
        assertEquals(0, result.rowsMatched)
        assertFalse(result.ok)
        assertEquals(emptyList<PriceEntity>(), prices(seg))
        assertTrue(result.message, result.message.contains("Segregated"))
        assertTrue(result.message, result.message.contains("No Isin Fund (no ISIN saved)"))
    }

    @Test fun `when one ISIN has several lines, the newest line with a real NAV is used`() = runBlocking {
        val one = fund("Fund One", "INF000000001")
        val text = amfi + "\n9;INF000000001;-;Fund One Old Copy;Direct Plan;Growth;70.0000;01-Jan-2020"
        fetcher.on(sources.amfiUrl, FetchResult.Ok(text.toByteArray()))
        updater.refreshAmfi()
        assertEquals(812_345L, prices(one).single().price)
    }

    @Test fun `an AMFI failure is logged with its reason`() = runBlocking {
        fund("Fund One", "INF000000001")
        fetcher.on(sources.amfiUrl, FetchResult.Failed(null, "Couldn't connect: timeout"))
        val result = updater.refreshAmfi()
        assertFalse(result.ok)
        assertEquals("Couldn't connect: timeout", log().single().message)
    }

    @Test fun `one source failing does not stop the other`() = runBlocking {
        stock("INFY", "INE009A01021")
        val one = fund("Fund One", "INF000000001")
        fetcher.on(sources.amfiUrl, FetchResult.Ok(amfi.toByteArray()))
        val results = updater.refresh() // NSE has no file at all
        assertEquals(listOf(false, true), results.map { it.ok })
        assertEquals(1, prices(one).size)
        assertEquals(listOf("nse", "amfi"), log().map { it.source })
    }

    // ---- status rules ----

    @Test fun `running again stores nothing twice`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        updater.refreshNse()
        val second = updater.refreshNse()
        assertEquals(0, second.pricesStored)
        assertEquals(1, prices(infy).size)
        assertEquals(2, log().size)
    }

    @Test fun `a price that jumps more than 20 percent in a week is suspicious, a normal move is not`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        invest.recordPrice(infy, LocalDate.of(2026, 10, 15), UnitPrice.rupees(1000), "nse", PriceStatus.Verified)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1300.00"))))
        updater.refreshNse()
        assertEquals(PriceStatus.Suspicious, prices(infy).last().status)

        val tcs = stock("TCS", "INE467B01029")
        invest.recordPrice(tcs, LocalDate.of(2026, 10, 15), UnitPrice.rupees(1000), "nse", PriceStatus.Verified)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE467B01029,TCS,EQ,TATA,1100.00"))))
        updater.refreshNse()
        assertEquals(PriceStatus.Verified, prices(tcs).last().status)
    }

    @Test fun `a large move from a price weeks old is not judged suspicious`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        invest.recordPrice(infy, LocalDate.of(2026, 9, 1), UnitPrice.rupees(1000), "nse", PriceStatus.Verified)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1500.00"))))
        updater.refreshNse()
        assertEquals(PriceStatus.Verified, prices(infy).last().status)
    }

    @Test fun `a download that disagrees with your manual price by more than 1 percent flags both`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        invest.recordPrice(infy, LocalDate.of(2026, 10, 22), UnitPrice.rupees(1100), MANUAL_SOURCE, PriceStatus.Manual)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        updater.refreshNse()
        assertEquals(listOf(PriceStatus.Mismatch, PriceStatus.Mismatch), prices(infy).map { it.status })
    }

    @Test fun `a download within 1 percent of your manual price is simply verified`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        invest.recordPrice(infy, LocalDate.of(2026, 10, 22), UnitPrice.rupees(1165), MANUAL_SOURCE, PriceStatus.Manual)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        updater.refreshNse()
        assertEquals(listOf(PriceStatus.Manual, PriceStatus.Verified), prices(infy).map { it.status })
    }

    @Test fun `resolving a mismatch keeps the one you choose and settles its status`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        val day = LocalDate.of(2026, 10, 22)
        invest.recordPrice(infy, day, UnitPrice.rupees(1100), MANUAL_SOURCE, PriceStatus.Manual)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        updater.refreshNse()

        invest.resolveMismatch(infy, day, keepDownloaded = true)
        assertEquals(listOf("nse" to PriceStatus.Verified), prices(infy).map { it.source to it.status })
    }

    @Test fun `resolving in favour of your own price drops the download`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        val day = LocalDate.of(2026, 10, 22)
        invest.recordPrice(infy, day, UnitPrice.rupees(1100), MANUAL_SOURCE, PriceStatus.Manual)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1168.00"))))
        updater.refreshNse()
        invest.resolveMismatch(infy, day, keepDownloaded = false)
        assertEquals(listOf(MANUAL_SOURCE to PriceStatus.Manual), prices(infy).map { it.source to it.status })
        assertEquals(11_000_000L, prices(infy).single().price) // ₹1,100.00
        runCatching { invest.resolveMismatch(infy, day, true) }.let { assertTrue(it.isFailure) } // nothing left to resolve
    }

    @Test fun `confirming a suspicious price makes it a confirmed one`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        invest.recordPrice(infy, LocalDate.of(2026, 10, 15), UnitPrice.rupees(1000), "nse", PriceStatus.Verified)
        fetcher.on(nseUrl("20261022"), FetchResult.Ok(zip(nseCsv("2026-10-22,INE009A01021,INFY,EQ,INFOSYS,1300.00"))))
        updater.refreshNse()
        val suspicious = prices(infy).last()
        invest.confirmPrice(suspicious.id)
        assertEquals(PriceStatus.Manual, db.investDao().getPrice(suspicious.id)!!.status)
    }

    @Test fun `a zero or negative price is never stored`() = runBlocking {
        val infy = stock("INFY", "INE009A01021")
        assertNull(invest.ingestPrice(infy, today, UnitPrice(0), "nse", today, 1L))
        assertEquals(0, prices(infy).size)
    }

    @Test fun `verified and manual prices go stale after eight days, whoever supplied them`() {
        fun price(daysOld: Long, status: PriceStatus) = PriceEntity(1, "h", today.minusDays(daysOld).toString(), 1, "x", status, 0)
        assertEquals(PriceStatus.Verified, price(8, PriceStatus.Verified).effectiveStatus(today))
        assertEquals(PriceStatus.Stale, price(9, PriceStatus.Verified).effectiveStatus(today))
        assertEquals(PriceStatus.Stale, price(30, PriceStatus.Manual).effectiveStatus(today))
        assertEquals(PriceStatus.Suspicious, price(30, PriceStatus.Suspicious).effectiveStatus(today)) // other statuses are left as they are
    }

    @Test fun `source addresses are remembered`() {
        val prefs = PriceSourcePrefs(ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("test_sources", android.content.Context.MODE_PRIVATE))
        assertEquals(PriceSources.Default, prefs.sources())
        prefs.save(PriceSources(" https://x/{yyyyMMdd}.zip ", "https://y/nav.txt"))
        assertEquals(PriceSources("https://x/{yyyyMMdd}.zip", "https://y/nav.txt"), prefs.sources())
    }

    @Test fun `the default NSE address names the trading day`() {
        assertTrue(PriceSources.Default.nseUrlTemplate.contains("{yyyyMMdd}"))
        assertEquals(
            "https://nsearchives.nseindia.com/content/cm/BhavCopy_NSE_CM_0_0_0_20261021_F_0000.csv.zip",
            PriceSources.Default.nseUrlTemplate.replace("{yyyyMMdd}", "20261021"),
        )
    }

    @Test fun `the weekly job waits for the next Saturday 9 am`() {
        val thursday = java.time.LocalDateTime.of(2026, 10, 22, 10, 0)
        assertEquals(java.time.Duration.ofDays(2).minusHours(1).toMillis(), millisUntilNextSaturdayMorning(thursday))
        val saturdayEarly = java.time.LocalDateTime.of(2026, 10, 24, 8, 0)
        assertEquals(java.time.Duration.ofHours(1).toMillis(), millisUntilNextSaturdayMorning(saturdayEarly))
        val saturdayLate = java.time.LocalDateTime.of(2026, 10, 24, 10, 0)
        assertEquals(java.time.Duration.ofDays(7).minusHours(1).toMillis(), millisUntilNextSaturdayMorning(saturdayLate))
    }
}
