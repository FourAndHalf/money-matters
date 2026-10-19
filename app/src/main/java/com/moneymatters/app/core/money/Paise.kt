package com.moneymatters.app.core.money

import kotlin.math.abs

/** An amount of money in integer paise (1 rupee = 100 paise). Never use floats for money. */
@JvmInline
value class Paise(val value: Long) : Comparable<Paise> {
    operator fun plus(other: Paise) = Paise(value + other.value)
    operator fun minus(other: Paise) = Paise(value - other.value)
    operator fun unaryMinus() = Paise(-value)
    override fun compareTo(other: Paise) = value.compareTo(other.value)

    val isNegative get() = value < 0

    companion object {
        val Zero = Paise(0)
        fun rupees(rupees: Long) = Paise(rupees * 100)
    }
}

val Long.rupees get() = Paise.rupees(this)
val Int.rupees get() = Paise.rupees(toLong())

private const val MINUS = '−'

/** Groups digits the Indian way: last three, then pairs. 1234567 -> "12,34,567". */
internal fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    val pairs = head.reversed().chunked(2).joinToString(",").reversed()
    return "$pairs,$tail"
}

/** Rupees part and two-digit paise part, without sign. */
fun Paise.parts(): Pair<String, String> {
    val a = abs(value)
    return groupIndian((a / 100).toString()) to (a % 100).toString().padStart(2, '0')
}

/**
 * Formats as Indian rupees: `₹1,23,456`, or `₹1,23,456.78` with [showPaise].
 * Negative amounts use a true minus sign: `−₹486`. [signed] adds `+` to positive amounts.
 */
fun Paise.formatInr(showPaise: Boolean = false, signed: Boolean = false): String {
    val (r, p) = parts()
    val sign = when {
        value < 0 -> MINUS.toString()
        signed && value > 0 -> "+"
        else -> ""
    }
    return if (showPaise) "$sign₹$r.$p" else "$sign₹$r"
}

/**
 * Compact Indian form with three significant figures: `₹2.96L`, `₹12.4L`, `₹1.2Cr`.
 * Amounts under ₹1 lakh are shown in full. Truncates (never rounds up) so a compact
 * figure never overstates a balance.
 */
fun Paise.formatInrCompact(): String {
    val rupees = abs(value) / 100
    val sign = if (value < 0) MINUS.toString() else ""
    fun scaled(unit: Long, suffix: String): String {
        val whole = rupees / unit
        val decimals = when {
            whole >= 100 -> 0
            whole >= 10 -> 1
            else -> 2
        }
        var scale = 1L
        repeat(decimals) { scale *= 10 }
        val frac = (rupees % unit) * scale / unit
        val fracText = frac.toString().padStart(decimals, '0').trimEnd('0')
        val body = if (fracText.isEmpty()) "$whole" else "$whole.$fracText"
        return "$sign₹$body$suffix"
    }
    return when {
        rupees >= 1_00_00_000 -> scaled(1_00_00_000, "Cr")
        rupees >= 1_00_000 -> scaled(1_00_000, "L")
        else -> formatInr()
    }
}
