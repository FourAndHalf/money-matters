package com.moneymatters.app.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UnitsTest {

    @Test fun `units and prices parse to integers without a float`() {
        assertEquals(Units(12_345_678), parseUnits("12.345678"))
        assertEquals(Units(10_000_000), parseUnits("10"))
        assertEquals(Units(500_000), parseUnits("0.5"))
        assertEquals(UnitPrice(812_345), parseUnitPrice("81.2345"))
        assertEquals(UnitPrice(810_000), parseUnitPrice("₹81"))
    }

    @Test fun `too many decimals, signs and junk are rejected`() {
        assertNull(parseUnits("1.1234567"))
        assertNull(parseUnits("-1"))
        assertNull(parseUnits("abc"))
        assertNull(parseUnitPrice("81.23456"))
    }

    @Test fun `market value is units times price in paise`() {
        // 123.456 units × ₹81.00 = ₹9,999.936 → ₹9,999.94
        assertEquals(Paise(999_994), marketValue(parseUnits("123.456")!!, parseUnitPrice("81")!!))
        assertEquals(Paise(1_000_000), marketValue(Units.whole(100), UnitPrice.rupees(100)))
        assertEquals(Paise.Zero, marketValue(Units.Zero, UnitPrice.rupees(100)))
    }

    @Test fun `market value rounds half up to the paise`() {
        // 1 unit at ₹0.0050 = 0.5 paise → 1 paise; at ₹0.0049 → 0.49 paise → 0
        assertEquals(Paise(1), marketValue(Units.whole(1), UnitPrice(50)))
        assertEquals(Paise(0), marketValue(Units.whole(1), UnitPrice(49)))
    }

    @Test fun `negative inputs are refused`() {
        assertThrows(IllegalArgumentException::class.java) { marketValue(Units(-1), UnitPrice.rupees(1)) }
    }
}
