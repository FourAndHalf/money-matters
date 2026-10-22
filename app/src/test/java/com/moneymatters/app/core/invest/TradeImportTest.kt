package com.moneymatters.app.core.invest

import com.moneymatters.app.core.money.Paise
import com.moneymatters.app.core.money.Units
import com.moneymatters.app.core.money.rupees
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeImportTest {

    private val header = "date,symbol,type,units,amount"

    @Test fun `parses columns in any order and case, quoted amounts and both date styles`() {
        val csv = """
            Amount,TYPE,Symbol,Date,Units,ISIN,Name
            "1,234.50",Buy,infy,2025-01-10,10,INE009A01021,Infosys
            500,SELL,infy,15/03/2025,2.5,,
        """.trimIndent()
        val parsed = parseTrades(csv)
        assertEquals(emptyList<RowError>(), parsed.errors)
        assertEquals(2, parsed.rows.size)
        val buy = parsed.rows[0]
        assertEquals(TradeRow(2, LocalDate.of(2025, 1, 10), "INFY", "INE009A01021", "Infosys", TradeSide.Buy, Units.whole(10), Paise(123_450)), buy)
        assertEquals(LocalDate.of(2025, 3, 15), parsed.rows[1].date)
        assertEquals(Units(2_500_000), parsed.rows[1].units)
    }

    @Test fun `every bad row is reported with its line number and nothing is dropped silently`() {
        val csv = """
            $header
            2025-01-10,INFY,buy,10,1000
            not-a-date,INFY,buy,10,1000
            2025-01-11,,buy,10,1000
            2025-01-12,INFY,hold,10,1000
            2025-01-13,INFY,buy,0,1000
            2025-01-14,INFY,buy,10,-5
            2025-01-15,INFY,buy,1.1234567,1000
        """.trimIndent()
        val parsed = parseTrades(csv)
        assertEquals(1, parsed.rows.size)
        assertEquals(listOf(3, 4, 5, 6, 7, 8), parsed.errors.map { it.line })
    }

    @Test fun `a missing column or an empty file is an error`() {
        assertEquals("Missing column(s): units, amount", parseTrades("date,symbol,type\n2025-01-10,INFY,buy").errors.single().message)
        assertEquals("The file is empty", parseTrades("  \n").errors.single().message)
    }

    @Test fun `preview replays trades first-in-first-out and reports what will be held`() {
        val rows = parseTrades(
            """
            $header
            2025-01-10,INFY,buy,10,1000
            2025-06-01,INFY,buy,10,1200
            2026-03-01,INFY,sell,15,2400
            2025-02-01,TCS,buy,5,2000
            """.trimIndent(),
        ).rows
        val preview = previewTrades(rows)
        assertEquals(emptyList<RowError>(), preview.errors)
        val infy = preview.symbols.single { it.symbol == "INFY" }
        assertEquals(Units.whole(5), infy.unitsHeld)
        assertEquals(600.rupees, infy.costHeld)
        assertEquals(3, infy.trades)
        assertEquals(Units.whole(5), preview.symbols.single { it.symbol == "TCS" }.unitsHeld)
    }

    @Test fun `a sale of more than was held on that date is flagged, even if bought later`() {
        val rows = parseTrades(
            """
            $header
            2025-03-01,INFY,sell,5,500
            2025-06-01,INFY,buy,10,1000
            """.trimIndent(),
        ).rows
        val error = previewTrades(rows).errors.single()
        assertEquals(2, error.line)
        assertTrue(error.message, error.message.contains("only 0 were held"))
    }

    @Test fun `a buy and sell on the same day is replayed buy first`() {
        val rows = parseTrades("$header\n2025-03-01,INFY,sell,5,600\n2025-03-01,INFY,buy,5,500").rows
        assertEquals(emptyList<RowError>(), previewTrades(rows).errors)
    }

    @Test fun `existing lots count towards what can be sold`() {
        val existing = mapOf("INFY" to listOf(OpenLot("old", LocalDate.of(2024, 1, 1), Units.whole(10), 1_000.rupees)))
        val rows = parseTrades("$header\n2025-03-01,INFY,sell,4,500").rows
        val preview = previewTrades(rows, existing)
        assertEquals(emptyList<RowError>(), preview.errors)
        assertEquals(Units.whole(6), preview.symbols.single().unitsHeld)
    }

    @Test fun `units format without trailing zeros`() {
        assertEquals("12", formatUnits(Units.whole(12)))
        assertEquals("12.5", formatUnits(Units(12_500_000)))
        assertEquals("0.000001", formatUnits(Units(1)))
    }

    @Test fun `split and bonus rows need a ratio but no amount`() {
        val parsed = parseTrades("$header\n2025-01-10,INFY,buy,10,1000\n2025-06-01,INFY,split,5,\n2025-07-01,INFY,bonus,0.5,")
        assertEquals(emptyList<RowError>(), parsed.errors)
        assertEquals(listOf(TradeSide.Buy, TradeSide.Split, TradeSide.Bonus), parsed.rows.map { it.side })
        assertEquals(Paise.Zero, parsed.rows[1].amount)
        assertEquals(Units(500_000), parsed.rows[2].units)
        assertEquals(1, parseTrades("$header\n2025-06-01,INFY,split,0,").errors.size)
    }

    @Test fun `preview carries a split and a bonus through the lots so a later sale is checked in new units`() {
        val rows = parseTrades(
            """
            $header
            2025-01-10,INFY,buy,10,1000
            2025-06-01,INFY,split,5,
            2025-07-01,INFY,bonus,1,
            2026-03-01,INFY,sell,95,9000
            """.trimIndent(),
        ).rows
        val preview = previewTrades(rows)
        assertEquals(emptyList<RowError>(), preview.errors)
        // 10 → 50 after the split → 100 with a 1:1 bonus; selling 95 leaves 5.
        assertEquals(Units.whole(5), preview.symbols.single().unitsHeld)
    }

    @Test fun `a split with nothing held is reported, and a trade on the day of a split is read in the new terms`() {
        assertEquals(1, previewTrades(parseTrades("$header\n2025-06-01,INFY,split,5,").rows).errors.size)
        val sameDay = parseTrades("$header\n2025-01-10,INFY,buy,10,1000\n2025-06-01,INFY,buy,5,900\n2025-06-01,INFY,split,2,").rows
        // The split comes first on its day, so it doubles only the January lot: 20 + 5.
        assertEquals(Units.whole(25), previewTrades(sameDay).symbols.single().unitsHeld)
    }
}
