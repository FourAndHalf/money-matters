package com.moneymatters.app.core.money

import java.math.BigInteger

private const val UNIT_SCALE = 1_000_000L
private const val PRICE_SCALE = 10_000L

/** A quantity of shares or fund units, in integer millionths (1 unit = 1,000,000). Never a float. */
@JvmInline
value class Units(val micro: Long) : Comparable<Units> {
    operator fun plus(other: Units) = Units(micro + other.micro)
    operator fun minus(other: Units) = Units(micro - other.micro)
    override fun compareTo(other: Units) = micro.compareTo(other.micro)

    companion object {
        val Zero = Units(0)
        fun whole(units: Long) = Units(units * UNIT_SCALE)
    }
}

/** A price per unit in integer 1/10,000 rupees, since NAVs carry four decimals. */
@JvmInline
value class UnitPrice(val tenThousandths: Long) {
    companion object {
        fun rupees(rupees: Long) = UnitPrice(rupees * PRICE_SCALE)
    }
}

/**
 * Units × price, rounded half-up to whole paise. This is the only place that converts the two,
 * and it is for display and valuation only: a trade's posted amount is the broker's net rupee figure.
 */
fun marketValue(units: Units, price: UnitPrice): Paise {
    require(units.micro >= 0 && price.tenThousandths >= 0) { "Units and price must not be negative" }
    // paise = micro/1e6 units × tenThousandths/1e4 rupees × 100 paise = micro × tenThousandths / 1e8
    val product = BigInteger.valueOf(units.micro) * BigInteger.valueOf(price.tenThousandths)
    val divisor = BigInteger.valueOf(100_000_000L)
    return Paise(((product * BigInteger.valueOf(2) + divisor) / (divisor * BigInteger.valueOf(2))).toLong())
}

private val UNITS_INPUT = Regex("""(\d{1,12})(?:\.(\d{1,6}))?""")
private val PRICE_INPUT = Regex("""(\d{1,12})(?:\.(\d{1,4}))?""")

/** Parses typed units ("12.345678") straight to micro-units, never via a float; null for anything else. */
fun parseUnits(text: String): Units? {
    val match = UNITS_INPUT.matchEntire(text.trim().replace(",", "")) ?: return null
    val (whole, fraction) = match.destructured
    return Units(whole.toLong() * UNIT_SCALE + fraction.padEnd(6, '0').toLong())
}

/** Parses a typed per-unit price ("81.2345", "₹81") straight to 1/10,000 rupees; null for anything else. */
fun parseUnitPrice(text: String): UnitPrice? {
    val match = PRICE_INPUT.matchEntire(text.trim().removePrefix("₹").replace(",", "").trim()) ?: return null
    val (whole, fraction) = match.destructured
    return UnitPrice(whole.toLong() * PRICE_SCALE + fraction.padEnd(4, '0').toLong())
}

/** The per-unit price implied by a net [amount] paid for [units], to the nearest 1/10,000 rupee. For display and lot records only. */
fun impliedPrice(units: Units, amount: Paise): UnitPrice {
    require(units.micro > 0) { "Units must be positive" }
    // price (1/10,000 ₹) = paise / 100 / (micro / 1e6) × 1e4 = paise × 1e8 / micro
    val numerator = BigInteger.valueOf(amount.value) * BigInteger.valueOf(100_000_000L)
    val denominator = BigInteger.valueOf(units.micro)
    return UnitPrice(((numerator * BigInteger.valueOf(2) + denominator) / (denominator * BigInteger.valueOf(2))).toLong())
}
