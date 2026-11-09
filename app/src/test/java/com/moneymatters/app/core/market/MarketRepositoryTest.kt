package com.moneymatters.app.core.market

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moneymatters.app.core.invest.FetchResult
import com.moneymatters.app.core.invest.PriceFetcher
import com.moneymatters.app.core.invest.PriceSources
import com.moneymatters.app.core.invest.parseNseBars
import com.moneymatters.app.core.ledger.MoneyMattersDatabase
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEADER = "TradDt,BizDt,Sgmt,Src,FinInstrmTp,FinInstrmId,ISIN,TckrSymb,SctySrs,FinInstrmNm,OpnPric,HghPric,LwPric,ClsPric,LastPric,PrvsClsgPric,TtlTradgVol"

private fun csv(date: String, vararg rows: String) = (listOf(HEADER) + rows.map { it.replace("{d}", date) }).joinToString("\n")
private fun zip(text: String): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { it.putNextEntry(ZipEntry("f.csv")); it.write(text.toByteArray()); it.closeEntry() } }.toByteArray()

private class DayFetcher(val files: Map<String, ByteArray>) : PriceFetcher {
    val asked = mutableListOf<String>()
    var failWith: FetchResult.Failed? = null
    override suspend fun fetch(url: String): FetchResult {
        asked += url
        failWith?.let { return it }
        return files[url]?.let { FetchResult.Ok(it) } ?: FetchResult.Failed(404, "File not found (404)")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketRepositoryTest {
    private lateinit var db: MoneyMattersDatabase
    private val today = LocalDate.of(2026, 11, 6) // Friday
    private lateinit var repo: MarketRepository
    private val sources = PriceSources("https://nse.test/{yyyyMMdd}.zip", "x")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoneyMattersDatabase::class.java).allowMainThreadQueries().build()
        repo = MarketRepository(db, today = { today }, now = { 1000L })
    }

    @After fun tearDown() = db.close()

    private fun file(day: LocalDate, close: String = "100", symbol: String = "ABC", series: String = "EQ") =
        "https://nse.test/${day.toString().replace("-", "")}.zip" to zip(csv(day.toString(), "{d},{d},CM,NSE,STK,1,INE000A01010,$symbol,$series,$symbol LTD,99,102,98,$close,$close,97,5000"))

    @Test fun parsesPricesVolumeAndSeriesAndKeepsOneRowPerSymbol() {
        val bars = parseNseBars(csv("2026-11-06",
            "{d},{d},CM,NSE,STK,1,INE1,ABC,BE,ABC LTD,1,2,0.5,1.5,1.5,1.2,10",
            "{d},{d},CM,NSE,STK,1,INE1,ABC,EQ,ABC LTD,99,102,98,100.55,100,97,5000",
            "{d},{d},CM,NSE,STK,2,INE2,GOLDBEES,N1,GOLD ETF,1,1,1,1,1,1,1",
            "{d},{d},CM,NSE,STK,3,INE3,BAD,EQ,BAD LTD,1,1,1,0,0,1,1"))
        assertEquals(1, bars.size)
        val b = bars.single()
        assertEquals("EQ", b.series)
        assertEquals(1_005_500L, b.close)
        assertEquals(970_000L, b.prevClose)
        assertEquals(5000L, b.volume)
    }

    @Test fun fillAsksForEachMissingWeekdayAndSkipsHolidays() = runBlocking {
        val files = mapOf(file(LocalDate.of(2026, 11, 6)), file(LocalDate.of(2026, 11, 5)), file(LocalDate.of(2026, 11, 3)))
        val fetcher = DayFetcher(files)
        val result = MarketUpdater(repo, fetcher, { sources }, { today }).fill(weekdays = 5)
        assertTrue(result.ok)
        assertEquals(3, result.added)
        assertEquals(2, result.holidays) // Nov 4 and Nov 2 had no file
        assertEquals(5, fetcher.asked.size)
        assertEquals(3, repo.status().days)
        // A second run only asks for the days it does not have (the two holidays again).
        fetcher.asked.clear()
        assertEquals(0, MarketUpdater(repo, fetcher, { sources }, { today }).fill(5).added)
        assertEquals(2, fetcher.asked.size)
    }

    @Test fun aFailureStopsTheRunButKeepsWhatWasStored() = runBlocking {
        val fetcher = DayFetcher(mapOf(file(LocalDate.of(2026, 11, 6))))
        val updater = MarketUpdater(repo, fetcher, { sources }, { today })
        updater.fill(1)
        fetcher.failWith = FetchResult.Failed(429, "Too many requests")
        val result = updater.fill(3)
        assertFalse(result.ok)
        assertTrue(result.message.contains("Too many requests"))
        assertEquals(1, repo.status().days)
    }

    @Test fun unreadableFileIsReportedNotStored() = runBlocking {
        val bad = "https://nse.test/20261106.zip" to zip("nothing,useful\n1,2")
        val result = MarketUpdater(repo, DayFetcher(mapOf(bad)), { sources }, { today }).fill(1)
        assertFalse(result.ok)
        assertTrue(result.message.contains("format may have changed"))
        assertEquals(0, repo.status().days)
    }

    @Test fun oldDaysArePrunedAsNewOnesArrive() = runBlocking {
        val old = parseNseBars(csv("2025-01-02", "{d},{d},CM,NSE,STK,1,INE1,ABC,EQ,ABC,1,1,1,1,1,1,1"))
        repo.ingestDay(old)
        assertEquals(0, repo.status().days) // already older than the window
        repo.ingestDay(parseNseBars(csv("2026-11-06", "{d},{d},CM,NSE,STK,1,INE1,ABC,EQ,ABC,1,1,1,1,1,1,1")))
        assertEquals(1, repo.status().days)
    }

    @Test fun watchlistRulesAndAlertsEndToEnd() = runBlocking {
        val closes = List(70) { 100.0 + it }
        val all = rows("ABC", closes, end = today)
        db.marketDao().upsertBars(all)
        repo.watch("abc", "ABC LTD")
        // Condition already true when the rule is made: remembered, so nothing fires yet.
        repo.addRule("ABC", AlertKind.PriceAbove, 150.0)
        assertTrue(repo.runAlerts().isEmpty())
        // A level not yet reached fires once, when the stock gets there.
        repo.addRule("ABC", AlertKind.PriceAbove, 200.0)
        assertTrue(repo.runAlerts().isEmpty())
        db.marketDao().upsertBars(rows("ABC", closes + 230.0, end = today))
        val fired = repo.runAlerts()
        assertEquals(1, fired.size)
        assertTrue(fired.single().message.contains("rose above ₹200.00"))
        assertTrue(repo.runAlerts().isEmpty())
        assertEquals(1, repo.events().size)
        repo.markEventsSeen()
        assertTrue(repo.events().all { it.seen })
        repo.unwatch("ABC")
        assertTrue(repo.rules().isEmpty())
    }

    @Test fun universeAndMoodReadFromTheStore() = runBlocking {
        (1..5).forEach { db.marketDao().upsertBars(rows("S$it", List(70) { d -> 100.0 + d }, end = today)) }
        assertEquals(5, repo.universe().size)
        assertTrue(repo.mood()!!.score != null)
        assertEquals(setOf("S1"), repo.metricsFor(listOf("s1")).keys)
    }
}
