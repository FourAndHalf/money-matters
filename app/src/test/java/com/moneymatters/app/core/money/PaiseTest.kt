package com.moneymatters.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class PaiseTest {

    @Test fun `groups digits the Indian way`() {
        assertEquals("0", groupIndian("0"))
        assertEquals("999", groupIndian("999"))
        assertEquals("1,000", groupIndian("1000"))
        assertEquals("12,345", groupIndian("12345"))
        assertEquals("1,23,456", groupIndian("123456"))
        assertEquals("12,34,567", groupIndian("1234567"))
        assertEquals("1,23,45,678", groupIndian("12345678"))
    }

    @Test fun `formats whole rupees and paise`() {
        assertEquals("₹18,420", Paise(18_42_000).formatInr())
        assertEquals("₹18,420.00", Paise(18_42_000).formatInr(showPaise = true))
        assertEquals("₹1,23,456.78", Paise(1_23_45_678).formatInr(showPaise = true))
        assertEquals("₹0.05", Paise(5).formatInr(showPaise = true))
        assertEquals("₹0", Paise.Zero.formatInr())
    }

    @Test fun `negative amounts use a true minus sign`() {
        assertEquals("−₹486", (-486).rupees.formatInr())
        assertEquals("+₹72,000", 72_000.rupees.formatInr(signed = true))
        assertEquals("₹0", Paise.Zero.formatInr(signed = true))
    }

    @Test fun `compact form uses lakh and crore with three significant figures`() {
        assertEquals("₹99,999", 99_999.rupees.formatInrCompact())
        assertEquals("₹1L", 1_00_000.rupees.formatInrCompact())
        assertEquals("₹2.96L", 2_96_400.rupees.formatInrCompact())
        assertEquals("₹12.4L", 12_49_999.rupees.formatInrCompact())
        assertEquals("₹97L", 97_04_000.rupees.formatInrCompact())
        assertEquals("₹1.2Cr", 1_20_00_000.rupees.formatInrCompact())
        assertEquals("₹125Cr", 125_00_00_000L.rupees.formatInrCompact())
        assertEquals("−₹3.5L", (-3_50_000).rupees.formatInrCompact())
    }

    @Test fun `compact form truncates instead of rounding up`() {
        assertEquals("₹9.99L", 9_99_999.rupees.formatInrCompact())
    }

    @Test fun `arithmetic stays in integer paise`() {
        val total = Paise(10) + Paise(20) - Paise(5)
        assertEquals(Paise(25), total)
        assertEquals(Paise(-25), -total)
        assertEquals(Paise(150_00), 150.rupees)
    }
}
