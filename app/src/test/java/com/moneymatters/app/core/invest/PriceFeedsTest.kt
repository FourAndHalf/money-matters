package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.UnitPrice
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are trimmed from the real AMFI and NSE files as downloaded on 22 Oct 2026. */
class PriceFeedsTest {

    private val amfi = """
        Scheme Code;ISIN Div Payout/ ISIN Growth;ISIN Div Reinvestment;Scheme Name;Plan;Option;Net Asset Value;Date

        Open Ended Schemes(Debt Scheme - Banking and PSU Fund)

        Franklin Templeton Mutual Fund

        129008;INF090I01KR8;-;Franklin India Banking & PSU Debt Fund;Direct Plan;Growth;25.2786;22-Oct-2026
        129009;INF090I01KS6;INF090I01KT4;Franklin India Banking & PSU Debt Fund;Direct Plan;IDCW;11.4388;22-Oct-2026
        148304;INF090I01VZ8;-;Franklin India Credit Risk Fund (No. of segregated portfolios-3);Direct Plan;Growth;0.0000;22-Oct-2026
        147956;INF090I01TK4;-;Franklin India Credit Risk Fund (No. of segregated portfolios-3);Direct Plan;;N.A.;20-Jul-2020
        100001;INF000000001;-;A fund with a long NAV;Direct Plan;Growth;12.345678;16-Sep-2022
    """.trimIndent()

    private val nseHeader = "TradDt,BizDt,Sgmt,Src,FinInstrmTp,FinInstrmId,ISIN,TckrSymb,SctySrs,XpryDt,FininstrmActlXpryDt,StrkPric,OptnTp,FinInstrmNm,OpnPric,HghPric,LwPric,ClsPric,LastPric,PrvsClsgPric,UndrlygPric,SttlmPric,OpnIntrst,ChngInOpnIntrst,TtlTradgVol,TtlTrfVal,TtlNbOfTxsExctd,SsnId,NewBrdLotQty,Rmks,Rsvd1,Rsvd2,Rsvd3,Rsvd4"
    private val nse = """
        $nseHeader
        2026-10-21,2026-10-21,CM,NSE,STK,1594,INE009A01021,INFY,EQ,,,,,INFOSYS LIMITED,1130.00,1175.00,1128.00,1168.00,1168.00,1130.70,,1168.00,,,100,100,10,F1,1,,,,,
        2026-10-21,2026-10-21,CM,NSE,STK,2885,INE002A01018,RELIANCE,EQ,,,,,RELIANCE INDUSTRIES LTD,1200.00,1215.00,1198.00,1209.00,1209.00,1202.00,,1209.00,,,100,100,10,F1,1,,,,,
        2026-10-21,2026-10-21,CM,NSE,STK,9999,INE009A01021,INFYBE,BE,,,,,INFOSYS LIMITED,1100.00,1100.00,1100.00,1111.00,1111.00,1100.00,,1111.00,,,1,1,1,F1,1,,,,,
    """.trimIndent()

    @Test fun `AMFI lines are read, headings and blank lines skipped`() {
        val rows = parseAmfi(amfi)
        assertEquals(listOf("129008", "129009", "148304", "147956", "100001"), rows.map { it.schemeCode })
        val growth = rows[0]
        assertEquals(listOf("INF090I01KR8"), growth.isins)
        assertEquals(UnitPrice(252_786), growth.nav)
        assertEquals(LocalDate.of(2026, 10, 22), growth.date)
        assertEquals(listOf("INF090I01KS6", "INF090I01KT4"), rows[1].isins) // both ISINs kept; the "-" is dropped
        assertEquals("Franklin India Banking & PSU Debt Fund", growth.name)
    }

    @Test fun `a zero NAV is kept for the caller to report and N·A· becomes no NAV`() {
        val rows = parseAmfi(amfi).associateBy { it.schemeCode }
        assertEquals(UnitPrice(0), rows.getValue("148304").nav)
        assertNull(rows.getValue("147956").nav)
        assertEquals(LocalDate.of(2020, 7, 20), rows.getValue("147956").date)
    }

    @Test fun `a NAV with eight decimals rounds half up to four`() {
        assertEquals(UnitPrice(123_457), parseAmfi(amfi).single { it.schemeCode == "100001" }.nav)
        assertEquals(UnitPrice(10_000), parsePriceText("1.00004999"))
        assertEquals(UnitPrice(10_001), parsePriceText("1.00005"))
        assertNull(parsePriceText("-5"))
        assertNull(parsePriceText("N.A."))
    }

    @Test fun `NSE rows are found by column name`() {
        val rows = parseNse(nse)
        assertEquals(3, rows.size)
        val infy = rows.first()
        assertEquals(NseRow("INE009A01021", "INFY", "EQ", "INFOSYS LIMITED", UnitPrice(11_680_000), LocalDate.of(2026, 10, 21)), infy)
    }

    @Test fun `a reordered NSE file still parses and a file without the needed columns yields nothing`() {
        val reordered = "ClsPric,ISIN,TckrSymb,SctySrs,TradDt\n1168.00,INE009A01021,INFY,EQ,2026-10-21"
        assertEquals(UnitPrice(11_680_000), parseNse(reordered).single().close)
        assertEquals(emptyList<NseRow>(), parseNse("Foo,Bar\n1,2"))
        assertEquals(emptyList<NseRow>(), parseNse(""))
    }

    @Test fun `bad NSE rows are skipped, not fatal`() {
        val csv = "$nseHeader\n" +
            "not-a-date,2026-10-21,CM,NSE,STK,1,INE009A01021,INFY,EQ,,,,,X,1,1,1,1168.00,1,1,,1,,,1,1,1,F1,1,,,,,\n" +
            "2026-10-21,2026-10-21,CM,NSE,STK,1,INE009A01021,INFY,EQ,,,,,X,1,1,1,abc,1,1,,1,,,1,1,1,F1,1,,,,,\n" +
            "2026-10-21,2026-10-21,CM,NSE,STK,1,INE002A01018,RELIANCE,EQ,,,,,X,1,1,1,1209.00,1,1,,1,,,1,1,1,F1,1,,,,,"
        assertEquals(listOf("RELIANCE"), parseNse(csv).map { it.symbol })
    }

    @Test fun `the index prefers plain equity when an ISIN trades in several series, and can look up by symbol`() {
        val index = NseIndex(parseNse(nse))
        assertEquals("INFY", index.byIsin("INE009A01021")!!.symbol) // EQ over BE
        assertEquals("RELIANCE", index.bySymbol("reliance")!!.symbol)
        assertNull(index.byIsin("INE000000000"))
        assertNull(index.bySymbol("NOPE"))
    }

    @Test fun `a zip is unpacked to its first file, and anything else is not a zip`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("BhavCopy.csv"))
                zip.write(nse.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        assertEquals(nse, unzipFirst(bytes))
        assertNull(unzipFirst("<html>not found</html>".toByteArray()))
        assertNotNull(unzipFirst(bytes)?.lines()?.first())
    }

    @Test fun `the last weekday skips weekends`() {
        assertEquals(LocalDate.of(2026, 10, 23), lastWeekdayOnOrBefore(LocalDate.of(2026, 10, 24))) // Saturday → Friday
        assertEquals(LocalDate.of(2026, 10, 23), lastWeekdayOnOrBefore(LocalDate.of(2026, 10, 25))) // Sunday → Friday
        assertEquals(LocalDate.of(2026, 10, 22), lastWeekdayOnOrBefore(LocalDate.of(2026, 10, 22)))
    }
}
